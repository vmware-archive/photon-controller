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

import com.vmware.photon.controller.api.model.UsageTag;
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
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements allocation of docker vms to management hosts given by hostQuerySpecification.
 */
public class CreateVmSpecLayoutTaskService extends StatefulService {

  @VisibleForTesting
  public static final String DOCKER_VM_PREFIX = "ec-mgmt-";

  /**
   * This class defines the document state associated with a single
   * {@link CreateVmSpecLayoutTaskService} instance.
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

  public CreateVmSpecLayoutTaskService() {
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
        scheduleQueryManagementHosts(currentState);
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

  /**
   * Schedule query for all hosts tagged as MGMT.
   */
  private void scheduleQueryManagementHosts(final State currentState) {
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
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(CreateVmSpecLayoutTaskService.this, documentLinks);
                    getHostEntities(currentState, documentLinks);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void getHostEntities(final State currentState, Collection<String> documentLinks) {
    if (null == documentLinks || documentLinks.size() == 0) {
      throw new RuntimeException("Found 0 hosts with usageTag: " + UsageTag.MGMT.name());
    }

    OperationJoin
        .create(documentLinks.stream()
            .map(documentLink -> HostUtils.getCloudStoreHelper(this).createGet(documentLink)))
        .setCompletion(
            (ops, failures) -> {
              if (null != failures && failures.size() > 0) {
                failTask(failures);
                return;
              }

              try {
                List<HostService.State> hostStates = new ArrayList<>();
                for (Operation getOperation : ops.values()) {
                  HostService.State hostState = getOperation.getBody(HostService.State.class);
                  hostStates.add(hostState);
                }
                scheduleCreateManagementVmTasks(currentState, hostStates);
              } catch (Throwable t) {
                failTask(t);
              }
            }
        )
        .sendWith(this);
  }

  private void scheduleCreateManagementVmTasks(State currentState, List<HostService.State> managementHosts) {
    final AtomicInteger pendingCreates = new AtomicInteger(managementHosts.size());
    final Service service = this;

    FutureCallback<CreateVmSpecTaskService.State> callback =
        new FutureCallback<CreateVmSpecTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateVmSpecTaskService.State result) {
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

            ServiceUtils.logInfo(service, "Docker VM: %s created", result.name);

            if (0 == pendingCreates.decrementAndGet()) {
              sendStageProgressPatch(TaskState.TaskStage.FINISHED);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (HostService.State host : managementHosts) {
      CreateVmSpecTaskService.State serviceState = new CreateVmSpecTaskService.State();
      serviceState.name = DOCKER_VM_PREFIX + host.hostAddress.replaceAll("\\.", "-")
          + UUID.randomUUID().toString().substring(0, 5);
      serviceState.hostServiceLink = host.documentSelfLink;

      TaskUtils.startTaskAsync(
          this,
          CreateVmSpecTaskFactoryService.SELF_LINK,
          serviceState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CreateVmSpecTaskService.State.class,
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
    TaskUtils.sendSelfPatch(this, buildPatch(com.vmware.xenon.common.TaskState.TaskStage.FAILED, e));
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
