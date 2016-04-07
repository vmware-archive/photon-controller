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

package com.vmware.photon.controller.apibackend.workflow;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.lang.reflect.Field;

/**
 * This class implements base service for api-backend workflow services.
 */
public class BaseWorkflowService extends StatefulService {

  public static final String FIELD_NAME_TASK_STATE = "taskState";
  public static final String FIELD_NAME_TASK_STATE_SUB_STAGE = "subStage";
  public static final String FIELD_NAME_CONTROL_FLAGS = "controlFlags";

  public BaseWorkflowService(Class<? extends ServiceDocument> stateType) {
    super(stateType);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ServiceDocument startState = startOperation.getBody(getStateType());
    initializeState(startState);
    try {
      validateStartState(startState);
      startOperation.setBody(startState).complete();
      if (ControlFlags.isOperationProcessingDisabled(getControlFlags(startState))) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else {
        ServiceUtils.logInfo(this, "Sending stage progress patch %s", TaskState.TaskStage.STARTED);
        TaskUtils.sendSelfPatch(this,
            buildPatch(TaskState.TaskStage.STARTED, getTaskStateSubStage(startState), null));
      }
    } catch (Throwable t) {
      failTask(t, startState);
    }
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(ServiceDocument current) {
    InitializationUtils.initialize(current);

    if (current.documentExpirationTimeMicros <= 0) {
      current.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }
  }

  /**
   * Build a state object that can be used to submit a stage progress self patch.
   *
   * @param stage
   * @param taskStateSubStage
   * @param t
   * @return
   * @throws Throwable
   */
  protected ServiceDocument buildPatch(TaskState.TaskStage stage, Enum taskStateSubStage, @Nullable Throwable t)
      throws Throwable {
    ServiceDocument patchState = getStateType().newInstance();
    TaskState taskState = getTaskStateType(patchState).newInstance();
    taskState.stage = stage;
    setTaskStateSubStage(taskState, taskStateSubStage);
    if (null != t) {
      taskState.failure = Utils.toServiceErrorResponse(t);
    }

    setTaskState(patchState, taskState);
    return patchState;
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param currentState
   * @param patchState
   * @param <T>
   * @throws Throwable
   */
  protected <T extends ServiceDocument> void applyPatch(T currentState, T patchState) throws Throwable {
    TaskState currentTaskState = getTaskState(currentState);
    TaskState patchTaskState = getTaskState(patchState);
    if (currentTaskState.stage != patchTaskState.stage ||
        getTaskStateSubStage(currentTaskState) != getTaskStateSubStage(patchTaskState)) {

      ServiceUtils.logInfo(this, "Moving from %s:%s to stage %s:%s",
          currentTaskState.stage, getTaskStateSubStage(currentTaskState),
          patchTaskState.stage, getTaskStateSubStage(patchTaskState));

      setTaskState(currentState, getTaskState(patchState));
    }
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param t
   * @param state
   */

  protected void failTask(Throwable t, ServiceDocument state) {
    ServiceUtils.logSevere(this, t);
    try {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, getTaskStateSubStage(state), t));
    } catch (Throwable throwable) {
      ServiceUtils.logSevere(this, "Failed to patch %s to FAILED stage: %s", state.documentSelfLink,
          throwable.toString());
    }
  }

  /**
   * Validates start state.
   *
   * @param state
   * @param <T>
   * @param <E>
   * @throws Throwable
   */
  protected <T extends ServiceDocument, E extends Enum> void validateStartState(T state) throws Throwable {
    ValidationUtils.validateState(state);
    TaskState taskState = getTaskState(state);
    ValidationUtils.validateTaskStage(taskState);

    checkState(taskState.stage == TaskState.TaskStage.CREATED, "Cannot proceed in " + taskState.stage + " state. " +
        "Expected state is CREATED.");

    E subStage = getTaskStateSubStage(taskState);
    checkState (subStage != null, "Sub-stage cannot be null in STARTED stage. ");
    checkState(subStage == getTaskStateSubStageFirstEnumEntry(taskState), "Sub-stage cannot be " + subStage + " in " +
        "STARTED stage. ");
  }

  /**
   * Validate service state coherence.
   *
   * @param state
   * @param <T>
   * @throws Throwable
   */
  protected <T extends ServiceDocument> void validateState(T state) throws Throwable {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(getTaskState(state));
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param currentState
   * @param patchState
   * @param <T>
   * @throws Throwable
   */
  protected <T extends ServiceDocument> void validatePatchState(T currentState, T patchState) throws Throwable {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(getTaskState(patchState));
    ValidationUtils.validateTaskStageProgression(getTaskState(currentState), getTaskState(patchState));
    checkState(getTaskStateSubStage(patchState).ordinal() >= getTaskStateSubStage(currentState).ordinal(),
        "Sub-stage cannot set from " + getTaskStateSubStage(currentState) + " to " + getTaskStateSubStage(patchState));
  }

  /**
   * This method returns controlFlags value from document state object.
   *
   * @param state
   * @param <T>
   * @return
   * @throws Throwable
   */
  private <T extends ServiceDocument> Integer getControlFlags(T state) throws Throwable {
    Field controlFlags = state.getClass().getField(FIELD_NAME_CONTROL_FLAGS);
    return (Integer) controlFlags.get(state);
  }

  /**
   * This method returns taskState object from document state object.
   *
   * @param state
   * @param <T>
   * @return
   * @throws Throwable
   */
  private <T extends ServiceDocument> TaskState getTaskState(T state) throws Throwable {
    Field taskStateField = state.getClass().getField(FIELD_NAME_TASK_STATE);
    return (TaskState) taskStateField.get(state);
  }

  /**
   * This method returns taskState type from document state object.
   *
   * @param state
   * @param <T>
   * @param <S>
   * @return
   * @throws Throwable
   */
  private <T extends ServiceDocument, S extends TaskState> Class<S> getTaskStateType(T state) throws
      Throwable {
    Field taskStateField = state.getClass().getField(FIELD_NAME_TASK_STATE);
    return (Class<S>) taskStateField.getType();
  }

  /**
   * This method returns subStage object from document state object.
   *
   * @param state
   * @param <T>
   * @param <E>
   * @return
   * @throws Throwable
   */
  private <T extends ServiceDocument, E extends Enum> E getTaskStateSubStage(T state) throws Throwable {
    TaskState taskState = getTaskState(state);
    Field taskStateSubStageField = taskState.getClass().getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    return (E) taskStateSubStageField.get(taskState);
  }

  /**
   * This method returns subStage object from TaskState object.
   *
   * @param taskState
   * @param <E>
   * @return
   * @throws Throwable
   */
  private <E extends Enum> E getTaskStateSubStage(TaskState taskState) throws Throwable {
    Field taskStateSubStageField = taskState.getClass().getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    return (E) taskStateSubStageField.get(taskState);
  }

  /**
   * This method returns enum subStage first entry from TaskState object.
   *
   * @param taskState
   * @param <E>
   * @return
   * @throws Throwable
   */
  private <E extends Enum> E getTaskStateSubStageFirstEnumEntry(TaskState taskState) throws Throwable {
    Field taskStateSubStageField = taskState.getClass().getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    return (E) taskStateSubStageField.getType().getEnumConstants()[0];
  }

  /**
   * This method sets subStage value in TaskState object.
   *
   * @param taskState
   * @param taskStateSubStage
   * @param <E>
   * @throws Throwable
   */
  private <E extends Enum> void setTaskStateSubStage(TaskState taskState, E taskStateSubStage)
      throws Throwable {
    Field taskStateSubStageField = taskState.getClass().getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    taskStateSubStageField.set(taskState, taskStateSubStage);
  }

  /**
   * This method sets TaskState object in document state object.
   *
   * @param state
   * @param taskState
   * @param <T>
   * @throws Throwable
   */
  private <T extends ServiceDocument> void setTaskState(T state, TaskState taskState) throws Throwable {
    Field taskStateField = state.getClass().getField(FIELD_NAME_TASK_STATE);
    taskStateField.set(state, taskState);
  }
}
