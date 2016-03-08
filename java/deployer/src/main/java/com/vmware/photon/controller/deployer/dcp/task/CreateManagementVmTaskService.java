/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon task service which creates a management VM.
 */
public class CreateManagementVmTaskService extends StatefulService {

  private static final String CONTAINERS_METADATA_PREFIX = "CONTAINER_";

  /**
   * This class defines the state of a {@link CreateManagementVmTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This class defines the possible sub-stages for a task.
     */
    public enum SubStage {
      CREATE_VM,
      WAIT_FOR_VM_CREATION,
      UPDATE_METADATA,
      WAIT_FOR_METADATA_UPDATE,
    }

    /**
     * This value defines the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link CreateManagementVmTaskService} task.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the document self-link of the parent task service to be notified when
     * the current task completes. If this value is not specified, then no notification will be sent
     * on completion.
     */
    @Immutable
    public String parentTaskServiceLink;

    /**
     * This value represents the body of the patch message to be sent to the parent task service on
     * successful completion. If this value is not specified, then the patch body will contain a
     * simple {@link TaskServiceState} indicating successful completion.
     */
    @Immutable
    public String parentPatchBody;

    /**
     * This value represents the document self-link of the {@link VmService} to be created.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the delay, in milliseconds, to use when polling a child task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the ID of the API-level task to create the VM.
     */
    @WriteOnce
    public String createVmTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the VM to be created. It is informational only.
     */
    @DefaultInteger(value = 0)
    public Integer createVmPollCount;

    /**
     * This value represents the ID of the API-level VM object created by the current task.
     */
    @WriteOnce
    public String vmId;

    /**
     * This value represents the ID of the API-level task to update the VM metadata.
     */
    @WriteOnce
    public String updateVmMetadataTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the VM metadata to be updated. It is informational only.
     */
    @DefaultInteger(value = 0)
    public Integer updateVmMetadataPollCount;
  }

  public CreateManagementVmTaskService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_VM;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);
    validatePatch(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      } else {
        notifyParentTask(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void validateTaskState(TaskState taskState) {
    ValidationUtils.validateTaskStage(taskState);
    switch (taskState.stage) {
      case CREATED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(taskState.subStage == null);
        break;
      case STARTED:
        checkState(taskState.subStage != null);
        switch (taskState.subStage) {
          case CREATE_VM:
          case WAIT_FOR_VM_CREATION:
          case UPDATE_METADATA:
          case WAIT_FOR_METADATA_UPDATE:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case CREATE_VM:
        processCreateVmSubStage(currentState);
        break;
      case WAIT_FOR_VM_CREATION:
        processWaitForVmCreationSubStage(currentState);
        break;
      case UPDATE_METADATA:
        processUpdateMetadataSubStage(currentState);
        break;
      case WAIT_FOR_METADATA_UPDATE:
        processWaitForMetadataUpdateSubStage(currentState);
        break;
    }
  }

  private void notifyParentTask(State currentState) {

    if (currentState.parentTaskServiceLink == null) {
      ServiceUtils.logInfo(this, "Skipping parent task notification");
      return;
    }

    Operation patchOp = Operation.createPatch(this, currentState.parentTaskServiceLink);
    switch (currentState.taskState.stage) {
      case FINISHED:
        if (currentState.parentPatchBody != null) {
          patchOp.setBody(currentState.parentPatchBody);
          break;
        }
        // Fall through
      case FAILED:
      case CANCELLED:
        TaskServiceState patchState = new TaskServiceState();
        patchState.taskState = currentState.taskState;
        patchOp.setBody(patchState);
        break;
      default:
        throw new IllegalStateException("Unexpected state: " + currentState.taskState.stage);
    }

    sendRequest(patchOp);
  }

  //
  // CREATE_VM sub-stage methods
  //

  private void processCreateVmSubStage(State currentState) {

    sendRequest(Operation
        .createGet(this, currentState.vmServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processCreateVmSubStage(currentState, o.getBody(VmService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processCreateVmSubStage(State currentState, VmService.State vmState) {

    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    Operation diskFlavorGetOp = cloudStoreHelper.createGet(vmState.diskFlavorServiceLink);
    Operation hostGetOp = cloudStoreHelper.createGet(vmState.hostServiceLink);
    Operation vmFlavorGetOp = cloudStoreHelper.createGet(vmState.vmFlavorServiceLink);

    OperationJoin
        .create(diskFlavorGetOp, hostGetOp, vmFlavorGetOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processCreateVmSubStage(currentState, vmState,
                    ops.get(diskFlavorGetOp.getId()).getBody(FlavorService.State.class),
                    ops.get(hostGetOp.getId()).getBody(HostService.State.class),
                    ops.get(vmFlavorGetOp.getId()).getBody(FlavorService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processCreateVmSubStage(State currentState,
                                       VmService.State vmState,
                                       FlavorService.State diskFlavorState,
                                       HostService.State hostState,
                                       FlavorService.State vmFlavorState)
      throws Throwable {

    AttachedDiskCreateSpec bootDiskCreateSpec = new AttachedDiskCreateSpec();
    bootDiskCreateSpec.setName(vmState.name + "-bootdisk");
    bootDiskCreateSpec.setBootDisk(true);
    bootDiskCreateSpec.setFlavor(diskFlavorState.name);
    bootDiskCreateSpec.setKind(EphemeralDisk.KIND);

    LocalitySpec hostLocalitySpec = new LocalitySpec();
    hostLocalitySpec.setId(hostState.hostAddress);
    hostLocalitySpec.setKind("host");

    LocalitySpec datastoreLocalitySpec = new LocalitySpec();
    checkState(hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE));
    datastoreLocalitySpec.setId(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE));
    datastoreLocalitySpec.setKind("datastore");

    LocalitySpec portGroupLocalitySpec = new LocalitySpec();
    checkState(hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP));
    portGroupLocalitySpec.setId(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP));
    portGroupLocalitySpec.setKind("portGroup");

    VmCreateSpec vmCreateSpec = new VmCreateSpec();
    vmCreateSpec.setName(vmState.name);
    vmCreateSpec.setFlavor(vmFlavorState.name);
    vmCreateSpec.setSourceImageId(ServiceUtils.getIDFromDocumentSelfLink(vmState.imageServiceLink));
    vmCreateSpec.setEnvironment(new HashMap<>());
    vmCreateSpec.setAttachedDisks(Collections.singletonList(bootDiskCreateSpec));
    vmCreateSpec.setAffinities(Arrays.asList(hostLocalitySpec, datastoreLocalitySpec, portGroupLocalitySpec));

    HostUtils.getApiClient(this)
        .getProjectApi()
        .createVmAsync(ServiceUtils.getIDFromDocumentSelfLink(vmState.projectServiceLink), vmCreateSpec,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processCreateVmTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processCreateVmTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_VM_CREATION, null);
        patchState.createVmTaskId = task.getId();
        patchState.createVmPollCount = 1;
        sendStageProgressPatch(patchState);
        break;
      case "COMPLETED":
        updateVmId(currentState, task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_VM sub-stage routines
  //

  private void processWaitForVmCreationSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.createVmTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processVmCreationTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processVmCreationTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_VM_CREATION, null);
              patchState.createVmPollCount = currentState.createVmPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        updateVmId(currentState, task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  private void updateVmId(State currentState, String vmId) {

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.vmId = vmId;

    sendRequest(Operation
        .createPatch(this, currentState.vmServiceLink)
        .setBody(vmPatchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_METADATA, null);
                patchState.vmId = vmId;
                sendStageProgressPatch(patchState);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // UPDATE_METADATA sub-stage routines
  //

  private void processUpdateMetadataSubStage(State currentState) {

    QueryTask containerQueryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerService.State.class)
            .addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, currentState.vmServiceLink)
            .build())
        .addOptions(EnumSet.of(
            QueryTask.QuerySpecification.QueryOption.BROADCAST,
            QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(containerQueryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processUpdateMetadataSubStage(currentState, o.getBody(QueryTask.class).results.documents);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processUpdateMetadataSubStage(State currentState, Map<String, Object> containerDocuments) {

    OperationJoin
        .create(containerDocuments.values().stream()
            .map((containerDocument) -> Utils.fromJson(containerDocument, ContainerService.State.class))
            .map((containerState) -> containerState.containerTemplateServiceLink)
            .distinct()
            .map((templateServiceLink) -> Operation.createGet(this, templateServiceLink)))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                updateMetadata(currentState, ops.values().stream()
                    .map((op) -> op.getBody(ContainerTemplateService.State.class))
                    .map(this::getPortMappings)
                    .flatMap((portMap) -> portMap.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private Map<String, String> getPortMappings(ContainerTemplateService.State templateState) {
    return templateState.portBindings.values().stream()
        .collect(Collectors.toMap(
            (portBinding) -> CONTAINERS_METADATA_PREFIX + portBinding,
            (portBinding) -> templateState.name));
  }

  private void updateMetadata(State currentState, Map<String, String> metadata) throws Throwable {

    VmMetadata vmMetadata = new VmMetadata();
    vmMetadata.setMetadata(metadata);

    HostUtils.getApiClient(this)
        .getVmApi()
        .setMetadataAsync(currentState.vmId, vmMetadata,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processSetMetadataResult(task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processSetMetadataResult(Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_METADATA_UPDATE, null);
        patchState.updateVmMetadataTaskId = task.getId();
        patchState.updateVmMetadataPollCount = 1;
        sendStageProgressPatch(patchState);
        break;
      case "COMPLETED":
        sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_METADATA_UPDATE sub-stage methods
  //

  private void processWaitForMetadataUpdateSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.updateVmMetadataTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processMetadataUpdateTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processMetadataUpdateTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_METADATA_UPDATE,
                  null);
              patchState.updateVmMetadataPollCount = currentState.updateVmMetadataPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    sendStageProgressPatch(buildPatch(taskStage, subStage, null));
  }

  private void sendStageProgressPatch(State patchState) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", patchState.taskState.stage,
        patchState.taskState.subStage);
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  private void failTask(Collection<Throwable> failures) {
    ServiceUtils.logSevere(this, failures);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;
    patchState.taskState.subStage = subStage;
    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
