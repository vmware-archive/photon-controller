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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.DefaultUuid;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.host.gen.HostMode;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * This class implements a DCP service representing the workflow of tearing down a host.
 */
public class DeprovisionHostWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link DeprovisionHostWorkflowService} task.
   */
  public static class TaskState extends com.vmware.dcp.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum SubStage {
      PUT_HOST_TO_DEPROVISION_MODE,
      DELETE_AGENT,
    }
  }

  /**
   * This class defines the document state associated with a single {@link DeprovisionHostWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link HostService}
     * object which represents the host to be torn down.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the unique ID of the current task.
     */
    @DefaultUuid
    @Immutable
    public String uniqueId;

    /**
     * This value represents the interval, in milliseconds, to use when polling the state of a task object returned by
     * an API call.
     */
    @Immutable
    public Integer taskPollDelay;
  }

  public DeprovisionHostWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PUT_HOST_TO_DEPROVISION_MODE;
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch operation for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        handleStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
    validateTaskSubStage(state.taskState);
  }

  private void validateTaskSubStage(TaskState taskState) {
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  private State applyPatch(State startState, State patchState) {
    if (startState.taskState.stage != patchState.taskState.stage
        || startState.taskState.subStage != patchState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", startState.taskState.stage, startState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void handleStartedStage(final State currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  HostService.State hostState = completedOp.getBody(HostService.State.class);
                  if (hostState.state == HostState.CREATING ||
                      hostState.state == HostState.NOT_PROVISIONED ||
                      hostState.state == HostState.DELETED) {
                    ServiceUtils.logInfo(this, "The host is not provisioned but %s, returning....",
                        hostState.state.name());
                    sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
                    return;
                  }

                  boolean ignoreError = hostState.state == HostState.ERROR;
                  if (ignoreError) {
                    ServiceUtils.logInfo(this, "We are ignoring deprovision errors since the host is in ERROR state");
                  }

                  switch (currentState.taskState.subStage) {
                    case PUT_HOST_TO_DEPROVISION_MODE:
                      putHostToDeprovisionMode(currentState, ignoreError);
                      break;
                    case DELETE_AGENT:
                      handleDeleteAgent(currentState, ignoreError);
                      break;
                  }
                }
            ));
  }

  private void putHostToDeprovisionMode(State currentState, boolean ignoreError) {
    final Service service = this;

    ChangeHostModeTaskService.State state = new ChangeHostModeTaskService.State();
    state.hostServiceLink = currentState.hostServiceLink;
    state.hostMode = HostMode.DEPROVISIONED;

    FutureCallback<ChangeHostModeTaskService.State> futureCallback = new
        FutureCallback<ChangeHostModeTaskService.State>() {
      @Override
      public void onSuccess(@Nullable ChangeHostModeTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_AGENT);
            break;
          case FAILED:
            if (ignoreError) {
              sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_AGENT);
            } else {
              State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
              patchState.taskState.failure = result.taskState.failure;
              TaskUtils.sendSelfPatch(service, patchState);
            }
            break;
          case CANCELLED:
            sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (ignoreError) {
          sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_AGENT);
        } else {
          failTask(t);
        }
      }
    };

    TaskUtils.startTaskAsync(this,
        ChangeHostModeTaskFactoryService.SELF_LINK,
        state,
        (taskState) -> TaskUtils.finalTaskStages.contains(taskState.taskState.stage),
        ChangeHostModeTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private void handleDeleteAgent(State currentState, boolean ignoreError) {
    final Service service = this;

    FutureCallback<DeleteAgentTaskService.State> futureCallback = new FutureCallback<DeleteAgentTaskService.State>() {
      @Override
      public void onSuccess(@Nullable DeleteAgentTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            HostService.State hostService = new HostService.State();
            hostService.state = HostState.NOT_PROVISIONED;

            sendRequest(
                HostUtils.getCloudStoreHelper(service)
                    .createPatch(currentState.hostServiceLink)
                    .setBody(hostService)
                    .setCompletion(
                        (completedOp, failure) -> {
                          if (null != failure) {
                            failTask(failure);
                          } else {
                            sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
                          }
                        }
                    ));

            break;
          case FAILED:
            if (ignoreError) {
              sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
            } else {
              State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
              patchState.taskState.failure = result.taskState.failure;
              TaskUtils.sendSelfPatch(service, patchState);
            }
            break;
          case CANCELLED:
            sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (ignoreError) {
          sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        } else {
          failTask(t);
        }
      }
    };

    TaskUtils.startTaskAsync(this,
        DeleteAgentTaskFactoryService.SELF_LINK,
        createDeleteAgentStartState(currentState),
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        DeleteAgentTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private DeleteAgentTaskService.State createDeleteAgentStartState(State currentState) {
    DeleteAgentTaskService.State startState = new DeleteAgentTaskService.State();
    startState.taskState = new com.vmware.dcp.common.TaskState();
    startState.taskState.stage = com.vmware.dcp.common.TaskState.TaskStage.CREATED;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.uniqueID = currentState.uniqueId;
    return startState;
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  @VisibleForTesting
  protected static State buildPatch(
      TaskState.TaskStage patchStage,
      @Nullable TaskState.SubStage patchSubStage,
      @Nullable Throwable t) {

    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
