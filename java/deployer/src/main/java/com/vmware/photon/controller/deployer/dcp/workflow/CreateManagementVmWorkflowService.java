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

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * This class implements a DCP service representing the creation of a management vm.
 */
public class CreateManagementVmWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link CreateManagementVmWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this work-flow.
     */
    public enum SubStage {
      CREATE_VM,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link CreateManagementVmWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the workflow.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the link to the vm entity.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the polling interval override value to use for child tasks.
     */
    @Immutable
    public Integer childPollInterval;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the NTP server configured at VM.
     */
    @Immutable
    public String ntpEndpoint;
  }

  public CreateManagementVmWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_VM;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

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
    validateState(startState);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
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
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(null != currentState.taskState.subStage, "Sub-stage cannot be null in STARTED stage.");
      switch (currentState.taskState.subStage) {
        case CREATE_VM:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage.toString());
      }
    } else {
      checkState(null == currentState.taskState.subStage, "Sub-stage must be null in stages other than STARTED.");
    }
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
    checkNotNull(startState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    // The task sub-state must be at least equal to the current task sub-state
    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
    // A document can never be patched to the CREATED state.
    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patchState.taskState.stage.ordinal() >= startState.taskState.stage.ordinal());

    // Patches cannot be applied to documents in terminal states.
    checkState(startState.taskState.subStage == null
        || startState.taskState.stage.ordinal() <= TaskState.TaskStage.STARTED.ordinal());
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage
          || patchState.taskState.subStage != startState.taskState.subStage) {
        ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      }
      startState.taskState = patchState.taskState;
    }
    return startState;
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) {
    switch (currentState.taskState.subStage) {
      case CREATE_VM:
        createVm(currentState);
        break;
    }
  }

  /**
   * This method starts a {@link CreateManagementVmTaskService} task.
   *
   * @param currentState Supplies the current state object.
   */
  private void createVm(final State currentState) {

    FutureCallback<CreateManagementVmTaskService.State> callback =
        new FutureCallback<CreateManagementVmTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateManagementVmTaskService.State result) {
            sendProgressPatch(result.taskState, TaskState.TaskStage.FINISHED, null);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateManagementVmTaskService.State startState = createVmTaskState(currentState);

    TaskUtils.startTaskAsync(
        this,
        CreateManagementVmTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateManagementVmTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * Creates a {@link CreateManagementVmTaskService.State} object from the current state.
   *
   * @param currentState Supplies the current state object.
   * @return
   */
  private CreateManagementVmTaskService.State createVmTaskState(final State currentState) {
    CreateManagementVmTaskService.State state = new CreateManagementVmTaskService.State();
    state.taskState = new CreateManagementVmTaskService.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.vmServiceLink = currentState.vmServiceLink;
    state.ntpEndpoint = currentState.ntpEndpoint;
    state.taskPollDelay = currentState.taskPollDelay;
    return state;
  }

  /**
   * This method sends a progress patch depending of the taskState of the provided state.
   *
   * @param state    Supplies the state.
   * @param stage    Supplies the stage to progress to.
   * @param subStage Supplies the substate to progress to.
   */
  private void sendProgressPatch(
      com.vmware.xenon.common.TaskState state,
      TaskState.TaskStage stage,
      TaskState.SubStage subStage) {
    switch (state.stage) {
      case FINISHED:
        TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
        break;
      case CANCELLED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.CANCELLED, null));
        break;
      case FAILED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, state.failure));
        break;
    }
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage));
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
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
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
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param errorResponse
   * @return
   */
  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }
}
