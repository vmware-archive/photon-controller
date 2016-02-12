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
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements a DCP micro-service which performs the task of creating
 * a VM.
 */
public class CreateManagementVmTaskService extends StatefulService {

  private static final String CONTAINERS_METADATA_PREFIX = "CONTAINER_";

  /**
   * This class defines the document state associated with a single
   * {@link CreateVmTaskService}
   * instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the link of the VM service document.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the interval between querying the state of the
     * create VM task.
     */
    @Immutable
    public Integer queryCreateVmTaskInterval;
  }

  public CreateManagementVmTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.queryCreateVmTaskInterval) {
      startState.queryCreateVmTaskInterval = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, (Throwable) null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current service.
   *
   * @param patch Supplies the patch operation object.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    PatchUtils.patchState(startState, patchState);
    validateState(startState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        processStartedStage(startState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies current state object.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param currentState Supplies the start state object.
   * @param patchState   Supplies the patch state object.
   */
  protected void validatePatchState(State currentState, State patchState) {
    checkState(!TaskUtils.finalTaskStages.contains(currentState.taskState.stage));

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);

    checkNotNull(currentState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());
    checkState(patchState.taskState.stage.ordinal() >= currentState.taskState.stage.ordinal());
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedStage(final State currentState) throws Throwable {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          processStartedStage(currentState, vmState);

        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(completionHandler);
    sendRequest(getOperation);
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   * @param vmState      Supplies the state object of the VmService entity.
   */
  private void processStartedStage(final State currentState, final VmService.State vmState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(vmState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    HostService.State hostState = completedOp.getBody(HostService.State.class);
                    processStartedStage(currentState, vmState, hostState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void processStartedStage(final State currentState, final VmService.State vmState, final HostService.State
      hostState) {
    Operation imageGetOperation = HostUtils.getCloudStoreHelper(this).createGet(vmState.imageServiceLink);
    Operation vmFlavorGetOperation = HostUtils.getCloudStoreHelper(this).createGet(vmState.vmFlavorServiceLink);
    Operation diskFlavorGetOperation = HostUtils.getCloudStoreHelper(this).createGet(vmState.diskFlavorServiceLink);
    Operation projectGetOperation = HostUtils.getCloudStoreHelper(this).createGet(vmState.projectServiceLink);

    OperationJoin
        .create(
            Arrays.asList(imageGetOperation, projectGetOperation, vmFlavorGetOperation, diskFlavorGetOperation))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            ImageService.State imageState = ops.get(imageGetOperation.getId()).getBody(ImageService.State.class);
            ProjectService.State projectState = ops.get(projectGetOperation.getId()).getBody(
                ProjectService.State.class);
            FlavorService.State vmFlavorState = ops.get(vmFlavorGetOperation.getId()).getBody(FlavorService.State
                .class);

            FlavorService.State diskFlavorState = ops.get(diskFlavorGetOperation.getId()).getBody(FlavorService.State
                .class);

            processStartedStage(
                currentState,
                vmState,
                hostState,
                imageState,
                projectState,
                vmFlavorState,
                diskFlavorState);

          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState    Supplies the current state object.
   * @param vmState         Supplies the state object of the VmService entity.
   * @param hostState       Supplies the state object of the HostService entity.
   * @param projectState    Supplies the state object of the ProjectService entity.
   * @param imageState      Supplies the state object of the Image Service entity.
   * @param projectState    Supplies the state object of the Project Service entity.
   * @param vmFlavorState   Supplies the state object of the FlavorService entity.
   * @param diskFlavorState Supplies the state object of the FlavorService entity.
   */
  private void processStartedStage(final State currentState,
                                   final VmService.State vmState, final HostService.State hostState,
                                   final ImageService.State imageState, final ProjectService.State projectState,
                                   final FlavorService.State vmFlavorState, final FlavorService.State diskFlavorState)
      throws IOException {

    final Service service = this;

    FutureCallback<CreateVmTaskService.State> callback = new FutureCallback<CreateVmTaskService.State>() {
      @Override
      public void onSuccess(@Nullable CreateVmTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            updateVmId(currentState, result.vmId);
            break;
          case CANCELLED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, (ServiceErrorResponse) null));
            break;
          case FAILED:
            ServiceErrorResponse failed = new ServiceErrorResponse();
            failed.message = String.format(
                "CreateVmTaskService failed to create the vm. documentSelfLink: %s. failureReason: %s",
                result.documentSelfLink,
                result.taskState.failure.message);
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FAILED, failed));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    CreateVmTaskService.State startState = new CreateVmTaskService.State();
    startState.projectId = ServiceUtils.getIDFromDocumentSelfLink(projectState.documentSelfLink);
    startState.vmCreateSpec = composeVmCreateSpec(vmState, hostState, imageState, vmFlavorState, diskFlavorState);
    startState.queryCreateVmTaskInterval = currentState.queryCreateVmTaskInterval;

    TaskUtils.startTaskAsync(
        this,
        CreateVmTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateVmTaskService.State.class,
        HostUtils.getDeployerContext(this).getTaskPollDelay(),
        callback);
  }


  /**
   * Updates the vm associated to the vmServiceLink with the vm Identifier.
   *
   * @param currentState
   * @param vmId
   */
  private void updateVmId(final State currentState, String vmId) {
    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }
        queryContainers(currentState);
      }
    };

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.vmId = vmId;
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setBody(vmPatchState)
        .setCompletion(completionHandler);

    sendRequest(patchOperation);
  }

  /**
   * This method retrieves the container templates of all the containers
   * that are running on this VM.
   *
   * @param currentState Supplies the current state object.
   */
  private void queryContainers(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
        .setTermMatchValue(currentState.vmServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    sendRequest(Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(CreateManagementVmTaskService.this, documentLinks);
              checkState(documentLinks.size() > 0);
              getContainerTemplates(currentState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        }));
  }

  private void getContainerTemplates(final State currentState, Collection<String> documentLinks) {

    OperationJoin
        .create(documentLinks.stream().map(documentLink -> Operation.createGet(this, documentLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Set<String> containerTemplateServiceLinks = ops.values().stream()
                .map(operation -> operation.getBody(ContainerService.State.class).containerTemplateServiceLink)
                .collect(Collectors.toSet());
            loadNamesFromTemplates(currentState, containerTemplateServiceLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  /**
   * This method retrieves the names of the containers from the set of
   * container templates and generates the vm metadata.
   *
   * @param currentState  Supplies the current state object.
   * @param templateLinks Supplies a set of container template links.
   */
  private void loadNamesFromTemplates(final State currentState, Set<String> templateLinks) {

    if (templateLinks.isEmpty()) {
      throw new XenonRuntimeException("Template links set is empty");
    }

    OperationJoin
        .create(templateLinks.stream()
            .map(templateLink -> Operation.createGet(this, templateLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Map<String, String> metadata = new HashMap<>();
            ops.values().stream().forEach(getOperation -> {
              ContainerTemplateService.State containerTemplateState = getOperation.getBody(
                  ContainerTemplateService.State.class);
              if (containerTemplateState.portBindings != null) {
                for (Integer port : containerTemplateState.portBindings.keySet()) {
                  metadata.put(CONTAINERS_METADATA_PREFIX + port.toString(), containerTemplateState.name);
                }
              }
            });

            getVmId(currentState, metadata);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  /**
   * This method retrieves the vmId from the vm service link.
   *
   * @param currentState Supplies the current state object.
   * @param metadata     Supplies the metadata map for vm.
   */
  private void getVmId(final State currentState, final Map<String, String> metadata) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          setVmMetadata(currentState, vmState.vmId, metadata);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method sets the metadata by making an async call to the setMetadata api.
   *
   * @param currentState Supplies the current state object.
   * @param vmId         Supplies the vm id returned by API-FE during vm creation.
   * @param metadata     Supplies the metadata map for vm.
   */
  private void setVmMetadata(final State currentState, final String vmId, final Map<String, String> metadata) throws
      IOException {

    VmMetadata vmMetadata = new VmMetadata();
    vmMetadata.setMetadata(metadata);

    HostUtils.getApiClient(this).getVmApi().setMetadataAsync(
        vmId,
        vmMetadata,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            try {
              if (null == result) {
                failTask(new IllegalStateException("setMetadataAsync returned null"));
                return;
              }

              processSetMetadataTask(currentState, result);
            } catch (Throwable e) {
              failTask(e);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  /**
   * This method is the completion handler for the setMetadataTask.
   *
   * @param currentState Supplies the current state object.
   * @param task         Supplies the task object.
   */
  private void processSetMetadataTask(final State currentState, final Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        scheduleSetMetadataTaskCall(this, currentState, task.getId());
        break;
      case "ERROR":
        throw new RuntimeException(ApiUtils.getErrors(task));
      case "COMPLETED":
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, (Throwable) null));
        break;
      default:
        throw new RuntimeException("Unknown task state: " + task.getState());
    }
  }

  /**
   * This method polls the status of the set metadata task.
   *
   * @param service      Supplies the DCP micro-service instance.
   * @param currentState Supplies the current state object.
   * @param taskId       Supplies the ID of the create VM task.
   */
  private void scheduleSetMetadataTaskCall(final Service service, final State currentState, final String taskId) {

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          HostUtils.getApiClient(service).getTasksApi().getTaskAsync(
              taskId,
              new FutureCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                  ServiceUtils.logInfo(service, "GetTask API call returned task %s", result.toString());
                  try {
                    processSetMetadataTask(currentState, result);
                  } catch (Throwable throwable) {
                    failTask(throwable);
                  }
                }

                @Override
                public void onFailure(Throwable t) {
                  failTask(t);
                }
              }
          );
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    getHost().schedule(runnable, currentState.queryCreateVmTaskInterval, TimeUnit.MILLISECONDS);
  }

  /**
   * This method creates a VmCreateSpec object for creating an VM.
   *
   * @param vmState         Supplies the state object of the VmService entity.
   * @param hostState       Supplies the state object of the HostService entity.
   * @param imageState      Supplies the state object of the ImageService entity.
   * @param vmFlavorState   Supplies the state object of the FlavorService entity.
   * @param diskFlavorState Supplies the state object of the FlavorService entity.
   * @return Returns the VmCreateSpec object.
   */
  private VmCreateSpec composeVmCreateSpec(final VmService.State vmState,
                                           final HostService.State hostState,
                                           final ImageService.State imageState,
                                           final FlavorService.State vmFlavorState,
                                           final FlavorService.State diskFlavorState) {

    // Craft the VM creation spec.
    VmCreateSpec spec = new VmCreateSpec();
    spec.setName(vmState.name);
    spec.setFlavor(vmFlavorState.name);

    spec.setSourceImageId(ServiceUtils.getIDFromDocumentSelfLink(imageState.documentSelfLink));

    List<AttachedDiskCreateSpec> attachedDisks = new ArrayList<>();

    AttachedDiskCreateSpec bootDisk = new AttachedDiskCreateSpec();
    bootDisk.setName(vmState.name + "-bootdisk");
    bootDisk.setBootDisk(true);
    bootDisk.setFlavor(diskFlavorState.name);
    bootDisk.setKind(EphemeralDisk.KIND);
    attachedDisks.add(bootDisk);

    spec.setAttachedDisks(attachedDisks);

    Map<String, String> environment = new HashMap<>();
    spec.setEnvironment(environment);

    List<LocalitySpec> affinities = new ArrayList<>();

    LocalitySpec hostSpec = new LocalitySpec();
    hostSpec.setId(hostState.hostAddress);
    hostSpec.setKind("host");
    affinities.add(hostSpec);

    LocalitySpec datastoreSpec = new LocalitySpec();
    datastoreSpec.setId(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE));
    datastoreSpec.setKind("datastore");
    affinities.add(datastoreSpec);

    LocalitySpec portGroupSpec = new LocalitySpec();
    portGroupSpec.setId(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP));
    portGroupSpec.setKind("portGroup");
    affinities.add(portGroupSpec);

    spec.setAffinities(affinities);

    return spec;
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage) {
        ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      }

      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage Supplies the stage that the current service instance is moving to.
   * @param e     Supplies the exception that the current service instance encountered if any.
   * @return Returns a patch state object that the current service instance is moving to.
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    return buildPatch(stage, (e != null) ? Utils.toServiceErrorResponse(e) : null);
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage         Supplies the stage that the current service instance is moving to.
   * @param errorResponse Supplies the {@link ServiceErrorResponse} response object
   * @return Returns a patch state object that the current service instance is moving to.
   */
  private State buildPatch(TaskState.TaskStage stage, @Nullable ServiceErrorResponse errorResponse) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;
    return state;
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the exception that the current service instance encountered.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }
}
