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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon task service which calls agent to perform upgrade.
 */
public class UpgradeAgentTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link UpgradeAgentTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This class defines the possible sub-stages for a task.
     */
    public enum SubStage {
      UPGRADE_AGENT,
      WAIT_FOR_AGENT,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link UpgradeAgentTaskService} task.
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
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the document link of the {@link HostService} on which to upgrade the agent.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the maximum number of agent status polling iterations which should be attempted before
     * declaring failure.
     */
    @DefaultInteger(value = 360)
    @Positive
    @Immutable
    public Integer maximumPollCount;

    /**
     * This value represents the interval between polling iterations in milliseconds.
     */
    @DefaultInteger(value = 5000)
    @Positive
    @Immutable
    public Integer pollInterval;

    /**
     * This value represents the number of polling iterations which have been attempted by the current task.
     */
    @DefaultInteger(value = 0)
    public Integer pollCount;
  }

  public UpgradeAgentTaskService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPGRADE_AGENT;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    operation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation operation) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(operation);
    State patchState = operation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    operation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (patchState.pollCount != null && currentState.pollCount != null) {
      checkState(patchState.pollCount >= currentState.pollCount);
    }
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
          case UPGRADE_AGENT:
          case WAIT_FOR_AGENT:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (patchState.subStage != null && currentState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case UPGRADE_AGENT:
        processUpgradeAgentSubStage(currentState);
        break;
      case WAIT_FOR_AGENT:
        processWaitForAgentSubStage(currentState);
        break;
    }
  }

  //
  // UPGRADE_AGENT sub-stage methods
  //

  private void processUpgradeAgentSubStage(State currentState) {

    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    Operation hostOp = cloudStoreHelper.createGet(currentState.hostServiceLink);

    hostOp.setCompletion(
        (op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            processUpgradeAgentSubStage(currentState, op.getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processUpgradeAgentSubStage(State currentState, HostService.State hostState) {

    try {
      AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
      agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      agentControlClient.upgrade(
          new AsyncMethodCallback<AgentControl.AsyncClient.upgrade_call>() {
            @Override
            public void onComplete(AgentControl.AsyncClient.upgrade_call upgradeCall) {
              try {
                AgentControlClient.ResponseValidator.checkUpgradeResponse(upgradeCall.getResult());
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT);
              } catch (Throwable t) {
                logErrorAndFail(hostState, t);
              }
            }

            @Override
            public void onError(Exception e) {
              logErrorAndFail(hostState, e);
            }
          });

    } catch (Throwable t) {
      logErrorAndFail(hostState, t);
    }
  }

  private void logErrorAndFail(HostService.State hostState, Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, new IllegalStateException(
        "Upgrading the agent on host " + hostState.hostAddress + " failed with error: " + failure)));
  }

  //
  // WAIT_FOR_AGENT sub-stage routines
  //

  private void processWaitForAgentSubStage(State currentState) {

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processWaitForAgentSubStage(currentState, o.getBody(HostService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processWaitForAgentSubStage(State currentState, HostService.State hostState) {
    try {
      AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
      agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      agentControlClient.getAgentStatus(new AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>() {
        @Override
        public void onComplete(AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall) {
          try {
            AgentStatusResponse agentStatusResponse = getAgentStatusCall.getResult();
            AgentControlClient.ResponseValidator.checkAgentStatusResponse(agentStatusResponse, hostState.hostAddress);
            sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
          } catch (Throwable t) {
            retryGetAgentStatusOrFail(currentState, hostState, t);
          }
        }

        @Override
        public void onError(Exception e) {
          retryGetAgentStatusOrFail(currentState, hostState, e);
        }
      });
    } catch (Throwable t) {
      retryGetAgentStatusOrFail(currentState, hostState, t);
    }
  }

  private void retryGetAgentStatusOrFail(State currentState, HostService.State hostState, Throwable failure) {
    if (currentState.pollCount + 1 >= currentState.maximumPollCount) {
      ServiceUtils.logSevere(this, failure);
      State patchState = buildPatch(TaskState.TaskStage.FAILED, null, new IllegalStateException(
          "The agent on host " + hostState.hostAddress + " failed to become ready after upgrading after " +
              Integer.toString(currentState.maximumPollCount) + " retries"));
      patchState.pollCount = currentState.pollCount + 1;
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      ServiceUtils.logTrace(this, failure);
      State patchState = buildPatch(currentState.taskState.stage, currentState.taskState.subStage, null);
      patchState.pollCount = currentState.pollCount + 1;
      getHost().schedule(() -> TaskUtils.sendSelfPatch(this, patchState), currentState.pollInterval,
          TimeUnit.MILLISECONDS);
    }
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s : %s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage, null));
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
