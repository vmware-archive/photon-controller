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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Implements scheduling of allocation of containers.
 */
public class CreateContainerSpecLayoutTaskService extends StatefulService {

  /**
   * Map with incompatible container types matrix.
   */
  @VisibleForTesting
  public static final Map<String, List<String>> INCOMPATIBLE_CONTAINER_TYPES
    = ImmutableMap.<String, List<String>>builder()
      .put("LoadBalancer", Arrays.asList("Lightwave"))
      .put("Lightwave", Arrays.asList("LoadBalancer"))
      .build();

  /**
   * This class defines the document state associated with a single
   * {@link CreateContainerSpecLayoutTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * Task State.
     */
    @NotNull
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the query specification which can be used to identify the hosts to create vms on.
     */
    @Immutable
    public QueryTask.QuerySpecification hostQuerySpecification;
  }

  public CreateContainerSpecLayoutTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  /**
   * This method is called when a start operation is performed for the current service instance.
   *
   * @param start
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service");
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateStartState(startState);

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
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current service.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        retrieveManagementHosts(currentState);
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
  protected void validateStartState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState
   * @param patchState
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }
    return startState;
  }

  private void retrieveManagementHosts(State currentState) {
    QueryTask queryTask = QueryTask.create(currentState.hostQuerySpecification).setDirect(true);

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(queryTask)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }
                  try {
                    NodeGroupBroadcastResponse queryResponse = completedOp.getBody(NodeGroupBroadcastResponse.class);
                    Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
                    if (documentLinks.isEmpty()) {
                      failTask(new XenonRuntimeException("No HostService.State documents found"));
                    } else {
                      retrieveManagementHosts(currentState, documentLinks);
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void retrieveManagementHosts(State currentState, Set<String> hostLinks) {

    OperationJoin
        .create(hostLinks.stream()
            .map(hostLink -> HostUtils.getCloudStoreHelper(this).createGet(hostLink)))
        .setCompletion(
            (ops, failures) -> {
              if (failures != null && failures.size() > 0) {
                failTask(failures);
                return;
              }

              Map<String, HostService.State> hosts = new HashMap<>();
              for (Operation getOperation : ops.values()) {
                HostService.State host = getOperation.getBody(HostService.State.class);
                hosts.put(host.documentSelfLink, host);
              }

              retrieveVms(currentState, hosts);
            }
        )
        .sendWith(this);
  }

  private void retrieveVms(State currentState, Map<String, HostService.State> managementHosts) {

    URI forwardingService = UriUtils.buildBroadcastRequestUri(
        UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS), ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    final Operation vmQueryPostOperation = createVmQuery(forwardingService);
    final Operation containerTemplateQueryPostOperation = createContainerTemplateQuery(currentState, forwardingService);

    OperationJoin.JoinedCompletionHandler completionHandler =
        (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
          if (failures != null && failures.size() > 0) {
            failTask(failures.values().iterator().next());
            return;
          }

          try {
            NodeGroupBroadcastResponse vmQueryResponse =
                ops.get(vmQueryPostOperation.getId()).getBody(NodeGroupBroadcastResponse.class);
            Set<String> vmLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(vmQueryResponse);
            QueryTaskUtils.logQueryResults(CreateContainerSpecLayoutTaskService.this, vmLinks);
            if (vmLinks.isEmpty()) {
              throw new XenonRuntimeException("Found 0 VmService entities representing docker vms");
            }

            NodeGroupBroadcastResponse containerTemplateQueryResponse =
                ops.get(containerTemplateQueryPostOperation.getId()).getBody(NodeGroupBroadcastResponse.class);
            Set<String> containerTemplateLinks =
                QueryTaskUtils.getBroadcastQueryDocumentLinks(containerTemplateQueryResponse);
            QueryTaskUtils.logQueryResults(CreateContainerSpecLayoutTaskService.this, containerTemplateLinks);
            if (containerTemplateLinks.isEmpty()) {
              throw new XenonRuntimeException(
                  String.format("Found 0 container templates"));
            }
            retrieveVms(currentState, managementHosts, vmLinks, containerTemplateLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        };

    OperationJoin.create(vmQueryPostOperation, containerTemplateQueryPostOperation)
        .setCompletion(completionHandler)
        .sendWith(this);
  }

  private void createVmContainerAllocations(
      State currentState,
      Map<String, HostService.State> managementHosts,
      Set<VmService.State> vms,
      Set<ContainerTemplateService.State> containerTemplates) {

    Map<String, String> singletonServiceToVmMap = new Hashtable<>();
    List<CreateContainerSpecTaskService.State> startStates = new ArrayList<>();
    for (ContainerTemplateService.State containerTemplate : containerTemplates) {
      CreateContainerSpecTaskService.State startState = new CreateContainerSpecTaskService.State();
      startState.dockerVmDocumentLinks = computeValidVms(containerTemplate, vms, managementHosts);
      startState.containerTemplateDocumentLink = containerTemplate.documentSelfLink;
      if (!containerTemplate.isReplicated) {
        String vmServiceLink = getAvailableVm(containerTemplate.name, vms, singletonServiceToVmMap);
        if (null == vmServiceLink) {
          throw new XenonRuntimeException("Unable to find available VM for container " + containerTemplate.name);
        }
        startState.singletonVmServiceLink = vmServiceLink;
        singletonServiceToVmMap.put(containerTemplate.name, vmServiceLink);
      }
      startStates.add(startState);
    }
    performContainerAllocations(currentState, startStates);
  }

  private String getAvailableVm(String containerName, Set<VmService.State> vms,
                                Map<String, String> singletonServiceToVmMap) {
    String vmLinkResult = null;

    log(Level.INFO, "Selecting VM for a non-replicated container %s", containerName);
    if (INCOMPATIBLE_CONTAINER_TYPES.containsKey(containerName)) {
      log(Level.INFO, "Incompatible containers matrix contains entry for %s", containerName);
      List<String> vmsWithIncompatibleContainers = new ArrayList<>();
      for (String incompatibleContainer : INCOMPATIBLE_CONTAINER_TYPES.get(containerName)) {
        log(Level.INFO, "Checking VMs for incompatible container %s", incompatibleContainer);
        if (singletonServiceToVmMap.containsKey(incompatibleContainer)) {
          log(Level.INFO, "Current VM contains incompatible container %s, discarding", incompatibleContainer);
          vmsWithIncompatibleContainers.add(singletonServiceToVmMap.get(incompatibleContainer));
        }
      }
      log(Level.INFO, "Total VMs %d, VMs with incompatible containers %d", vms.size(),
          vmsWithIncompatibleContainers.size());
      for (VmService.State vm : vms) {
        if (!vmsWithIncompatibleContainers.contains(vm.documentSelfLink)) {
          log(Level.INFO, "Found VM without incompatible container for %s", containerName);
          vmLinkResult = vm.documentSelfLink;
          break;
        }
      }
    } else {
      log(Level.INFO,
          "Incompatible containers matrix does not contain entry for %s. Selecting random VM", containerName);
      List<VmService.State> vmList = new ArrayList<>();
      vmList.addAll(vms);
      Collections.shuffle(vmList);
      vmLinkResult = vmList.get(0).documentSelfLink;
    }
    log(Level.INFO, "VM selection for non-replicated container %s - %s", containerName, vmLinkResult);
    return vmLinkResult;
  }

  private Operation createVmQuery(URI forwardingService) {
    QueryTask.Query vmKindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.QuerySpecification vmQuerySpecification = new QueryTask.QuerySpecification();
    vmQuerySpecification.query = vmKindClause;
    QueryTask vmQueryTask = QueryTask.create(vmQuerySpecification).setDirect(true);

    return Operation
        .createPost(forwardingService)
        .forceRemote()
        .setBody(vmQueryTask);
  }

  private Operation createContainerTemplateQuery(State currentState, URI forwardingService) {
    QueryTask.Query containerTemplateKindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.QuerySpecification containerTemplateQuerySpecification = new QueryTask.QuerySpecification();
    containerTemplateQuerySpecification.query = containerTemplateKindClause;
    QueryTask containerTemplateQueryTask = QueryTask.create(containerTemplateQuerySpecification).setDirect(true);

    return Operation
        .createPost(forwardingService)
        .forceRemote()
        .setBody(containerTemplateQueryTask);
  }

  private void retrieveVms(
      State currentState,
      Map<String, HostService.State> managementHosts,
      Set<String> vmLinks,
      Set<String> containerTemplateLinks) {

    List<Operation> opList = new ArrayList<>(vmLinks.size());
    for (String vmLink : vmLinks) {
      opList.add(Operation.createGet(this, vmLink).forceRemote());
    }

    OperationJoin.create(opList)
        .setCompletion(
            (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
              if (failures != null && failures.size() > 0) {
                failTask(failures.values().iterator().next());
                return;
              }

              Set<VmService.State> vms = new HashSet<>();
              for (Operation getOperation : ops.values()) {
                VmService.State vm = getOperation.getBody(VmService.State.class);
                vms.add(vm);
              }

              retrieveContainerTemplates(currentState, managementHosts, vms, containerTemplateLinks);
            })
        .sendWith(this);
  }

  private void retrieveContainerTemplates(
      State currentState,
      Map<String, HostService.State> managementHosts,
      Set<VmService.State> vms,
      Set<String> containerTemplateLinks) {

    List<Operation> opList = new ArrayList<>(containerTemplateLinks.size());
    for (String containerTemplateLink : containerTemplateLinks) {
      opList.add(Operation.createGet(this, containerTemplateLink).forceRemote());
    }

    OperationJoin.create(opList)
        .setCompletion(
            (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
              if (failures != null && failures.size() > 0) {
                failTask(failures.values().iterator().next());
                return;
              }

              Set<ContainerTemplateService.State> containerTemplates = new HashSet<>();
              for (Operation getOperation : ops.values()) {
                ContainerTemplateService.State template = getOperation.getBody(ContainerTemplateService.State.class);
                containerTemplates.add(template);
              }

              try {
                createVmContainerAllocations(currentState, managementHosts, vms, containerTemplates);
              } catch (XenonRuntimeException e) {
                failTask(e);
              }
            })
        .sendWith(this);
  }

  private List<String> computeValidVms(
      ContainerTemplateService.State template,
      Set<VmService.State> vms,
      Map<String, HostService.State> managementHosts) {

    Map<String, HostService.State> allowedHosts = managementHosts.entrySet().stream()
        .filter(entry -> entry.getValue()
            .metadata.getOrDefault(HostService.State.METADATA_KEY_NAME_ALLOWED_SERVICES, "").contains(template.name))
        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));

    if (allowedHosts.isEmpty()) {
      allowedHosts = managementHosts;

      allowedHosts = allowedHosts.entrySet().stream()
          .filter(entry ->
                  entry.getValue().metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_SERVICES) == null
          )
          .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
    }

    final Map<String, HostService.State> hosts = allowedHosts;

    return vms.stream()
        .filter(vm -> hosts.containsKey(vm.hostServiceLink))
        .map(vm -> vm.documentSelfLink)
        .collect(Collectors.toList());
  }

  private void performContainerAllocations(
      final State currentState,
      final List<CreateContainerSpecTaskService.State> containerAllocationTaskList) {

    ServiceUtils.logInfo(this, "Scheduling: %s AllocationContainerTaskService instances",
        containerAllocationTaskList.size());
    final AtomicInteger pendingCreates = new AtomicInteger(containerAllocationTaskList.size());
    final Service service = this;

    FutureCallback<CreateContainerSpecTaskService.State> callback =
        new FutureCallback<CreateContainerSpecTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainerSpecTaskService.State result) {
            if (result.taskState.stage == TaskState.TaskStage.FAILED) {
              State state = buildPatch(TaskState.TaskStage.FAILED, null);
              state.taskState.failure = result.taskState.failure;
              TaskUtils.sendSelfPatch(service, state);
              return;
            }

            if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
              TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
              return;
            }

            if (0 == pendingCreates.decrementAndGet()) {
              sendStageProgressPatch(TaskState.TaskStage.FINISHED);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (CreateContainerSpecTaskService.State startState : containerAllocationTaskList) {
      TaskUtils.startTaskAsync(
          this,
          CreateContainerSpecTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CreateContainerSpecTaskService.State.class,
          currentState.taskPollDelay,
          callback);
    }
  }

  /**
   * This method sends a patch operation to the current service instance
   * to move to a new state.
   *
   * @param stage
   */
  private void sendStageProgressPatch(com.vmware.xenon.common.TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "sendStageProgressPatch %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Map<Long, Throwable> failures) {
    failures.values().forEach(failure -> ServiceUtils.logSevere(this, failure));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failures.values().iterator().next()));
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage
   * @param e
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(com.vmware.xenon.common.TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new com.vmware.xenon.common.TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }
}
