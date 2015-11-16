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
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.DefaultUuid;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.task.DeployAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeployAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * This class implements a DCP service representing the provision host workflow.
 */
public class ProvisionHostWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link ProvisionHostWorkflowService} task.
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
      DEPLOY_AGENT,
      PROVISION_AGENT,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link ProvisionHostWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the relative path to the uploaded VIB image on the shared image data store.
     */
    @NotNull
    @Immutable
    public String vibPath;

    /**
     * This value represents the document link of the {@link DeploymentService} in whose context the task operation is
     * being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the URL of the {@link HostService} object which
     * represents the host to be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the list of chairman servers used by the {@link DeployAgentTaskService}.
     */
    @NotNull
    @Immutable
    public Set<String> chairmanServerList;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the unique ID of the current task.
     */
    @DefaultUuid
    public String uniqueID;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Positive
    public Integer taskPollDelay;
  }

  public static State buildStartPatch() {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = TaskState.TaskStage.STARTED;
    s.taskState.subStage = TaskState.SubStage.DEPLOY_AGENT;
    return s;
  }

  public ProvisionHostWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed for the current
   * service instance.
   *
   * @param patch Supplies the start operation object.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState, patch.getReferer());
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);

    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStartedState(currentState);
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
  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
    validateTaskSubStage(currentState.taskState);
  }

  private void validateTaskSubStage(TaskState taskState) {
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        switch (taskState.subStage) {
          case DEPLOY_AGENT:
          case PROVISION_AGENT:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private void validatePatchState(State startState, State patchState, URI referer) {
    if (TaskState.TaskStage.CREATED != startState.taskState.stage &&
        referer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage, ignores patch from TaskSchedulerService");
    }

    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(final State currentState) throws IOException {
    switch (currentState.taskState.subStage) {
      case DEPLOY_AGENT:
        deployAgentTask(currentState);
        break;
      case PROVISION_AGENT:
        provisionAgentTask(currentState);
        break;
    }
  }

  /**
   * This method starts the {@link DeployAgentTaskService} task and then continues to monitor it.
   *
   * @param currentState Supplies the current state object.
   */
  private void deployAgentTask(final State currentState) {
    final Service service = this;

    FutureCallback<DeployAgentTaskService.State> callback = new FutureCallback<DeployAgentTaskService.State>() {
      @Override
      public void onSuccess(@Nullable DeployAgentTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_AGENT,
                null));
            break;
          case FAILED:
            State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
            patchState.taskState.failure = result.taskState.failure;
            TaskUtils.sendSelfPatch(service, patchState);
            break;
          case CANCELLED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    DeployAgentTaskService.State startState = createDeployAgentTaskState(currentState);

    TaskUtils.startTaskAsync(
        this,
        DeployAgentTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        DeployAgentTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method creates a {@link DeployAgentTaskService.State} object from the current state.
   *
   * @param currentState Supplies the current state object.
   * @return
   */
  private DeployAgentTaskService.State createDeployAgentTaskState(State currentState) {
    DeployAgentTaskService.State startState = new DeployAgentTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.vibPath = currentState.vibPath;
    return startState;
  }

  private void provisionAgentTask(State currentState) {
    final Service service = this;

    FutureCallback<ProvisionAgentTaskService.State> futureCallback =
        new FutureCallback<ProvisionAgentTaskService.State>() {
          @Override
          public void onSuccess(@Nullable ProvisionAgentTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                HostService.State hostService = new HostService.State();
                hostService.state = HostState.READY;

                sendRequest(
                    HostUtils.getCloudStoreHelper(service)
                        .createPatch(currentState.hostServiceLink)
                        .setBody(hostService)
                        .setCompletion(
                            (completedOp, failure) -> {
                              if (null != failure) {
                                failTask(failure);
                              } else {
                                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED, null, null));
                              }
                            }
                        ));

                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(service, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    TaskUtils.startTaskAsync(this,
        ProvisionAgentTaskFactoryService.SELF_LINK,
        createProvisionAgentTaskState(currentState),
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ProvisionAgentTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private ProvisionAgentTaskService.State createProvisionAgentTaskState(State currentState) {
    ProvisionAgentTaskService.State startState = new ProvisionAgentTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.chairmanServerList = currentState.chairmanServerList;
    return startState;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage, null));
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }
}
