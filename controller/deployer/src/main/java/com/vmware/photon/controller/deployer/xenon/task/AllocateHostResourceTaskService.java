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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.photon.controller.deployer.xenon.util.MiscUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon micro-service which performs the task of
 * scaling and allocating the memory and cpu of containers and vms based
 * on the host's total memory and cpu count.
 */
public class AllocateHostResourceTaskService extends StatefulService {
  /**
   * This class defines the document state associated with a single
   * {@link AllocateHostResourceTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the URL of the HostService object.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;
  }

  public AllocateHostResourceTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed on the current
   * service instance.
   *
   * @param start Supplies a patch operation to be handled.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current
   * service instance.
   *
   * @param patch Supplies a patch operation to be handled.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getHostService(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getHostService(State currentState) {

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion((o, e) -> {
          if (null != e) {
            failTask(e);
            return;
          }

          try {
            HostService.State hostState = o.getBody(HostService.State.class);
            checkState(hostState.cpuCount != null && hostState.memoryMb != null);
            queryManagementVm(hostState);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void queryManagementVm(HostService.State hostState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, hostState.documentSelfLink)
            .build())
        .build();

    Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((o, e) -> {
          if (e != null) {
            failTask(e);
            return;
          }

          try {
            NodeGroupBroadcastResponse rsp = o.getBody(NodeGroupBroadcastResponse.class);
            Set<String> vmServiceLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(rsp);
            checkState(vmServiceLinks.size() == 1);
            queryManagementContainers(hostState, vmServiceLinks.iterator().next());
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void queryManagementContainers(HostService.State hostState, String vmServiceLink) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerService.State.class)
            .addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, vmServiceLink)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build();

    Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((o, e) -> {
          if (e != null) {
            failTask(e);
            return;
          }

          try {
            List<ContainerService.State> containerStates =
                QueryTaskUtils.getBroadcastQueryDocuments(ContainerService.State.class, o);
            getContainerTemplates(hostState, containerStates);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getContainerTemplates(HostService.State hostState, List<ContainerService.State> containerStates) {

    OperationJoin
        .create(containerStates.stream()
            .map((state) -> Operation.createGet(this, state.containerTemplateServiceLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs.values());
            return;
          }

          try {
            Map<String, ContainerTemplateService.State> templateMap = ops.values().stream()
                .map((op) -> op.getBody(ContainerTemplateService.State.class))
                .collect(Collectors.toMap(
                    (state) -> state.documentSelfLink,
                    (state) -> state));

            allocateResources(hostState, containerStates, templateMap);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void allocateResources(HostService.State hostState,
                                 List<ContainerService.State> containerStates,
                                 Map<String, ContainerTemplateService.State> templateMap) {

    long totalMemory = templateMap.values().stream().mapToLong((t) -> t.memoryMb).sum();
    int maxCpuCount = templateMap.values().stream().mapToInt((t) -> t.cpuCount).max().getAsInt();

    OperationJoin
        .create(containerStates.stream().map((containerState) -> createContainerPatch(hostState, containerState,
            templateMap.get(containerState.containerTemplateServiceLink), totalMemory, maxCpuCount)))
        .setCompletion((ops, exs) -> {
          if (exs != null && !exs.isEmpty()) {
            failTask(exs.values());
          } else {
            TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
          }
        })
        .sendWith(this);
  }

  private Operation createContainerPatch(HostService.State hostState,
                                         ContainerService.State containerState,
                                         ContainerTemplateService.State templateState,
                                         long totalMemory,
                                         int maxCpuCount) {

    ContainerService.State patchState = new ContainerService.State();
    patchState.memoryMb = templateState.memoryMb * MiscUtils.getAdjustedManagementVmMemory(hostState) / totalMemory;
    patchState.cpuShares = templateState.cpuCount * ContainerService.State.DOCKER_CPU_SHARES_MAX / maxCpuCount;
    patchState.dynamicParameters = new HashMap<>();
    if (null != containerState.dynamicParameters) {
      patchState.dynamicParameters.putAll(containerState.dynamicParameters);
    }

    patchState.dynamicParameters.put("memoryMb", String.valueOf(patchState.memoryMb));
    return Operation.createPatch(this, containerState.documentSelfLink).setBody(patchState);
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies the current state of the service instance.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method validates a patch object against a valid document state
   * object.
   *
   * @param startState Supplies the state of the current service instance.
   * @param patchState Supplies the state object specified in the patch
   *                   operation.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the initial state of the current service
   *                   instance.
   * @param patchState Supplies the patch state associated with a patch
   *                   operation.
   * @return The updated state of the current service instance.
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
   * @param stage Supplies the state to which the service instance should be
   *              transitioned.
   * @param e     Supplies an optional Throwable object representing the failure
   *              encountered by the service instance.
   * @return A State object which can be used to submit a stage progress self-
   * patch.
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * transition to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the failure encountered by the service instance.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Collection<Throwable> exs) {
    ServiceUtils.logSevere(this, exs);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.iterator().next()));
  }
}
