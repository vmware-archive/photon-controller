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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.OperationSequence;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.NodeGroupBroadcastResponse;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements a DCP micro-service which performs the task of
 * scaling and allocating the memory and cpu of containers and vms based
 * on the host's total memory and cpu count.
 */
public class AllocateHostResourceTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link AllocateHostResourceTaskService} instance.
   */
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

  private void getHostService(final State currentState) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    HostService.State hostState = completedOp.getBody(HostService.State.class);
                    if (hostState.cpuCount != null && hostState.memoryMb != null) {
                      allocateResources(currentState, hostState);
                    } else {
                      ServiceUtils.logInfo(this, "Skip host resource allocation as host cpu and memory are not set");
                      State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
                      TaskUtils.sendSelfPatch(this, patchState);
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void allocateResources(final State currentState, HostService.State hostState) {
    Operation queryVms = getQueryOperation();
    Operation queryContainers = getQueryOperation();

    Map<String, String> templateMap = new HashMap<>();
    List<ContainerService.State> containerServices = new ArrayList<>();
    queryVms.setBody(buildVmQueryTask(currentState));

    OperationSequence
        // Get the vm entity for this particular host
        .create(queryVms.setBody(buildVmQueryTask(currentState)))
        .setCompletion(((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          NodeGroupBroadcastResponse queryResponse = ops.get(queryVms.getId())
              .getBody(NodeGroupBroadcastResponse.class);
          String vmServiceLink = QueryTaskUtils.getBroadcastQueryResults(queryResponse).iterator().next();
          queryContainers.setBody(buildContainerQueryTask(vmServiceLink));
        }))
        // Get all the containers which belong to the particular vm
        .next(queryContainers)
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          NodeGroupBroadcastResponse queryResponse = ops.get(queryContainers.getId())
              .getBody(NodeGroupBroadcastResponse.class);
          containerServices.addAll(QueryTaskUtils.getBroadcastQueryDocuments(
              ContainerService.State.class, queryResponse));
          containerServices.stream().forEach(cs -> templateMap.put(cs.containerTemplateServiceLink,
              cs.documentSelfLink));
          getContainerTemplates(hostState, templateMap);
        })
        .sendWith(this);
  }

  // For each container, get the respective container template
  private void getContainerTemplates(HostService.State hostState, Map<String, String> templateMap) {
    Map<String, ContainerService.State> containerMap = new HashMap<>();
    OperationJoin
        .create(templateMap.keySet().stream().map(t -> Operation.createGet(this, t)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          containerMap.putAll(getContainerAllocation(ops.values().stream()
              .map(op -> op.getBody(ContainerTemplateService.State.class)), hostState, templateMap));
          patchContainersWithResoure(containerMap);
        })
        .sendWith(this);
  }

  // Patch all the containers with the newly allocated memory and cpu
  private void patchContainersWithResoure(Map<String, ContainerService.State> containerMap) {
    OperationJoin
        .create(containerMap.keySet().stream().map(link -> Operation.createPatch(UriUtils.buildUri(getHost(), link))
        .setBody(containerMap.get(link))))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
          TaskUtils.sendSelfPatch(this, patchState);
        })
        .sendWith(this);
  }

  private Map<String, ContainerService.State> getContainerAllocation(Stream<ContainerTemplateService.State> templates,
                                                                     HostService.State hostState,
                                                                     Map<String, String> templateMap) {
    List<ContainerTemplateService.State> templateList = templates.collect(Collectors.toList());
    int totalMemory = templateList.stream().mapToInt(t -> t.memoryMb).sum();
    int maxCpu = templateList.stream().mapToInt(t -> t.cpuCount).max().getAsInt();
    Map<String, ContainerService.State> containerMap = new HashMap<>();
    for (ContainerTemplateService.State template : templateList) {
      ContainerService.State state = new ContainerService.State();
      state.memoryMb = template.memoryMb * hostState.memoryMb / totalMemory;
      state.cpuShares = template.cpuCount * ContainerService.State.DOCKER_CPU_SHARES_MAX / maxCpu;
      containerMap.put(templateMap.get(template.documentSelfLink), state);
    }
    return containerMap;
  }

  private Operation getQueryOperation() {
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR));
  }

  private QueryTask buildVmQueryTask(final State currentState) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(VmService.State.FIELD_NAME_HOST_SERVICE_LINK)
        .setTermMatchValue(currentState.hostServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(vmServiceLinkClause);
    return QueryTask.create(querySpecification).setDirect(true);
  }

  private QueryTask buildContainerQueryTask(String vmServiceLink) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
        .setTermMatchValue(vmServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(vmServiceLinkClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    return QueryTask.create(querySpecification).setDirect(true);
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

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }
}
