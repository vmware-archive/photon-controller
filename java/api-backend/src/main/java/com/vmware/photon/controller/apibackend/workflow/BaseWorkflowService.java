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
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkState;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * This class implements base service for api-backend workflow services.
 *
 * @param <S>
 * @param <T>
 * @param <E>
 */
public class BaseWorkflowService <S extends ServiceDocument, T extends TaskState, E extends Enum>
    extends StatefulService {

  public static final String FIELD_NAME_TASK_STATE = "taskState";
  public static final String FIELD_NAME_TASK_STATE_SUB_STAGE = "subStage";
  public static final String FIELD_NAME_CONTROL_FLAGS = "controlFlags";

  protected final Class<T> taskStateType;
  protected final Class<E> taskSubStageType;


  public BaseWorkflowService(Class<S> stateType, Class<T> taskStateType, Class<E> taskSubStage) {
    super(stateType);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);

    this.taskStateType = taskStateType;
    this.taskSubStageType = taskSubStage;
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      S startState = (S) startOperation.getBody(getStateType());
      initializeState(startState);
      validateStartState(startState);

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(getControlFlags(startState))) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        return;
      }

      ServiceUtils.logInfo(this, "Sending stage progress patch %s:%s. ", TaskState.TaskStage.STARTED,
          getTaskStateSubStageFirstEntry());
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED, getTaskStateSubStageFirstEntry()));
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
      failTask(t);
    }
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(S current) {
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
   * @return
   * @throws Throwable
   */
  protected S buildPatch(TaskState.TaskStage stage, E subStage) throws Throwable {
    return buildPatch(stage, subStage, null);
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
  protected S buildPatch(TaskState.TaskStage stage, E taskStateSubStage, Throwable t)
      throws Throwable {
    S patchState = (S) getStateType().newInstance();
    T taskState = taskStateType.newInstance();
    taskState.stage = stage;

    if (taskStateSubStage != null) {
      setTaskStateSubStage(taskState, taskStateSubStage);
    }

    if (t != null) {
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
   * @throws Throwable
   */
  protected void applyPatch(S currentState, S patchState) throws Throwable {
    T currentTaskState = getTaskState(currentState);
    E currentSubstage = getTaskStateSubStage(currentTaskState);

    T patchTaskState = getTaskState(patchState);
    E patchSubstage = getTaskStateSubStage(patchTaskState);

    if (currentTaskState.stage != patchTaskState.stage || currentSubstage != patchSubstage) {

      String currentStage = currentTaskState.stage.toString();
      if (currentSubstage != null) {
        currentStage += ":" + currentSubstage;
      }

      String patchStage = patchTaskState.stage.toString();
      if (patchSubstage != null) {
        patchStage += ":" + patchSubstage;
      }

      ServiceUtils.logInfo(this, "Moving from %s to stage %s", currentStage, patchStage);
      setTaskState(currentState, patchTaskState);
    }
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param t
   */

  protected void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      S patchState = buildPatch(TaskState.TaskStage.FAILED, null, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable throwable) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + throwable.toString());
    }
  }

  /**
   * Validate service start state.
   *
   * @param state
   * @throws Throwable
   */
  protected void validateStartState(S state) throws Throwable {
    validateState(state);

    T taskState = getTaskState(state);
    checkState(taskState.stage == TaskState.TaskStage.CREATED,
        "Expected state is CREATED. Cannot proceed in " + taskState.stage + " state. ");
  }
  /**
   * Validate service state coherence.
   *
   * @param state
   * @throws Throwable
   */
  protected void validateState(S state) throws Throwable {
    ValidationUtils.validateState(state);
    T taskState = getTaskState(state);
    ValidationUtils.validateTaskStage(taskState);

    switch (taskState.stage) {
      case STARTED:
        E subStage = getTaskStateSubStage(taskState);
        checkState(subStage != null, "Invalid stage update. SubStage cannot be null");
        E[] validSubStages = getTaskStateSubStageAllEntries();
        if (!Arrays.asList(validSubStages).contains(subStage)) {
          checkState(false, "unsupported subStage: " + taskState.stage.toString());
        }
        break;
      case CREATED:
      case FAILED:
      case FINISHED:
      case CANCELLED:
        checkState(getTaskStateSubStage(taskState) == null, "Invalid stage update. SubStage must be null");
        break;
      default:
        checkState(false, "cannot process patches in state: " + taskState.stage.toString());
    }
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param currentState
   * @param patchState
   * @throws Throwable
   */
  protected void validatePatchState(S currentState, S patchState) throws Throwable {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(getTaskState(patchState));
    ValidationUtils.validateTaskStageProgression(getTaskState(currentState), getTaskState(patchState));

    E currentSubStage = getTaskStateSubStage(currentState);
    E patchSubStage = getTaskStateSubStage(patchState);
    if (currentSubStage != null && patchSubStage != null) {
      checkState(patchSubStage.ordinal() >= currentSubStage.ordinal(),
          "Sub-stage cannot set from " + currentSubStage + " to " + patchSubStage);
    }
  }

  /**
   * This method returns controlFlags value from document state object.
   *
   * @param state
   * @return
   * @throws Throwable
   */
  private Integer getControlFlags(S state) throws Throwable {
    Field controlFlags = getStateType().getField(FIELD_NAME_CONTROL_FLAGS);
    return (Integer) controlFlags.get(state);
  }

  /**
   * This method returns taskState object from document state object.
   *
   * @param state
   * @return
   * @throws Throwable
   */
  private T getTaskState(S state) throws Throwable {
    Field taskStateField = getStateType().getField(FIELD_NAME_TASK_STATE);
    return (T) taskStateField.get(state);
  }

  /**
   * This method returns subStage object from document state object.
   *
   * @param state
   * @return
   * @throws Throwable
   */
  private E getTaskStateSubStage(S state) throws Throwable {
    T taskState = getTaskState(state);
    Field taskStateSubStageField = taskStateType.getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    return (E) taskStateSubStageField.get(taskState);
  }

  /**
   * This method returns subStage object from TaskState object.
   *
   * @param taskState
   * @return
   * @throws Throwable
   */
  private E getTaskStateSubStage(T taskState) throws Throwable {
    Field taskStateSubStageField = taskStateType.getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    return (E) taskStateSubStageField.get(taskState);
  }

  /**
   * This method returns enum SubStage's first entry.
   *
   * @return
   * @throws Throwable
   */
  private E getTaskStateSubStageFirstEntry() throws Throwable {
    return getTaskStateSubStageAllEntries()[0];
  }

  /**
   * This method returns enum SubStage's all entries.
   *
   * @return
   * @throws Throwable
   */
  private E[] getTaskStateSubStageAllEntries() throws Throwable {
    return taskSubStageType.getEnumConstants();
  }

  /**
   * This method sets subStage value in TaskState object.
   *
   * @param taskState
   * @param taskStateSubStage
   * @throws Throwable
   */
  private void setTaskStateSubStage(T taskState, E taskStateSubStage) throws Throwable {
    Field taskStateSubStageField = taskStateType.getField(FIELD_NAME_TASK_STATE_SUB_STAGE);
    taskStateSubStageField.set(taskState, taskStateSubStage);
  }

  /**
   * This method sets TaskState object in document state object.
   *
   * @param state
   * @param taskState
   * @throws Throwable
   */
  private void setTaskState(S state, T taskState) throws Throwable {
    Field taskStateField = getStateType().getField(FIELD_NAME_TASK_STATE);
    taskStateField.set(state, taskState);
  }
}
