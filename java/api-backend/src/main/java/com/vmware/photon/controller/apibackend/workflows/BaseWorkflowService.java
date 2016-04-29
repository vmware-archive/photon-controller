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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.apibackend.utils.ServiceDocumentUtils;
import com.vmware.photon.controller.apibackend.utils.TaskServiceUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;

/**
 * This class implements base service for api-backend workflow services.
 *
 * @param <S> Generic type which extends ServiceDocument class.
 * @param <T> Generic type which extends TaskState class.
 * @param <E> Generic type which extends Enum class.
 */
public abstract class BaseWorkflowService<S extends ServiceDocument, T extends TaskState, E extends Enum>
    extends StatefulService {

  protected final Class<T> taskStateType;
  protected final Class<E> taskSubStageType;


  public BaseWorkflowService(Class<S> stateType, Class<T> taskStateType, Class<E> taskSubStageType) {
    super(stateType);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);

    this.taskStateType = taskStateType;
    this.taskSubStageType = taskSubStageType;
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());

    try {
      S document = (S) createOperation.getBody(getStateType());
      initializeState(document);
      validateState(document);

      Integer controlFlags = ServiceDocumentUtils.getControlFlags(document);
      if (ControlFlags.isOperationProcessingDisabled(controlFlags) ||
          ControlFlags.isHandleCreateDisabled(controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
      }

      handleCreateHook(document, createOperation);
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(createOperation)) {
        createOperation.fail(t);
      }
      failTask(t);
    }
  }

  protected void handleCreateHook(S document, Operation operation) throws Throwable {
    createTaskService(document, operation);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      S document = (S) startOperation.getBody(getStateType());
      validateStartState(document);
      startOperation.setBody(document).complete();

      Integer controlFlags = ServiceDocumentUtils.getControlFlags(document);
      if (ControlFlags.isOperationProcessingDisabled(controlFlags) ||
          ControlFlags.isHandleStartDisabled(ServiceDocumentUtils.getControlFlags(document))) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        return;
      }

      handleStartHook(document, startOperation);
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
      failTask(t);
    }
  }

  protected void handleStartHook(S document, Operation operation) throws Throwable {
    TaskUtils.sendSelfPatch(this,
        buildPatch(TaskState.TaskStage.STARTED,
            ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType)[0]));
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      S currentDocument = getState(patchOperation);
      S patchDocument = (S) patchOperation.getBody(getStateType());
      validatePatchState(currentDocument, patchDocument);
      applyPatch(currentDocument, patchDocument);
      validateState(currentDocument);
      patchOperation.complete();

      Integer controlFlags = ServiceDocumentUtils.getControlFlags(currentDocument);
      if (ControlFlags.isOperationProcessingDisabled(controlFlags) ||
          ControlFlags.isHandlePatchDisabled(controlFlags) ||
          TaskState.TaskStage.STARTED != ServiceDocumentUtils.getTaskStateStage(currentDocument)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      }

      processPatch(currentDocument);
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
      failTask(t);
    }
  }

  protected void handlePatchHook(S document) throws Throwable {
    completeTaskService(document);
  }

  private void processPatch(S document) throws Throwable {
    TaskService.State taskServiceState = ServiceDocumentUtils.getTaskServiceState(document);

    switch (taskServiceState.state) {
      case QUEUED:
        startTaskService(document);
        break;
      case STARTED:
        handlePatchHook(document);
        break;
      default:
        break;
    }
  }

  protected abstract TaskService.State buildTaskServiceStartState(S document);

  private void createTaskService(S document, Operation createOperation) {
    TaskServiceUtils.create(
        this,
        buildTaskServiceStartState(document),
        (state) -> {
          try {
            ServiceDocumentUtils.setTaskServiceState(document, state);
            createOperation.setBody(document).complete();
          } catch (Throwable t) {
            RuntimeException e = new RuntimeException(String.format("Failed to create TaskService.State %s", t));
            createOperation.fail(e);
            failTask(e);
          }
        },
        (failure) -> {
          RuntimeException e = new RuntimeException(String.format("Failed to create TaskService.State %s", failure));
          createOperation.fail(e);
          failTask(e);
        }
    );
  }

  private void startTaskService(S document) throws Throwable {
    TaskServiceUtils.start(
        this,
        ServiceDocumentUtils.getTaskServiceState(document).documentSelfLink,
        (state) -> refreshTaskAndProgress(document, state, TaskState.TaskStage.STARTED),
        (failure) -> failTask(failure));
  }

  private void completeTaskService(S document) throws Throwable {
    TaskServiceUtils.complete(
        this,
        ServiceDocumentUtils.getTaskServiceState(document).documentSelfLink,
        (state) -> refreshTaskAndProgress(document, state, TaskState.TaskStage.FINISHED),
        (failure) -> failTask(failure));
  }

  private void failTaskService(S document) throws Throwable {
    TaskServiceUtils.fail(
        this,
        ServiceDocumentUtils.getTaskServiceState(document).documentSelfLink,
        (state) -> refreshTaskAndProgress(document, state, TaskState.TaskStage.FAILED),
        (failure) -> failTask(failure));
  }

  private void refreshTaskAndProgress(S document,
                                      TaskService.State taskServiceState,
                                      TaskState.TaskStage nextStage) {
    refreshTaskAndProgress(document, taskServiceState, nextStage, null);
  }

  private void refreshTaskAndProgress(S document,
                                      TaskService.State taskServiceState,
                                      TaskState.TaskStage nextStage,
                                      E nextSubStage) {
    try {
      if (nextStage == TaskState.TaskStage.STARTED && nextSubStage == null) {
        nextSubStage = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType)[0];
      }

      S patchDocument = buildPatchWithLatestTaskService(document, taskServiceState, nextStage, nextSubStage);
      TaskUtils.sendSelfPatch(this, patchDocument);
    } catch (Throwable t) {
      RuntimeException e = new RuntimeException(String.format("Failed to patch TaskService.State %s", t));
      failTask(e);
    }

  }

  private S buildPatchWithLatestTaskService(S document,
                                            TaskService.State taskServicePatchState,
                                            TaskState.TaskStage nextStage,
                                            E nextSubStage) throws Throwable {
    TaskService.State taskServiceCurrentState = ServiceDocumentUtils.getTaskServiceState(document);
    PatchUtils.patchState(taskServiceCurrentState, taskServicePatchState);
    S patchDocument = buildPatch(nextStage, nextSubStage, null);
    ServiceDocumentUtils.setTaskServiceState(patchDocument, taskServiceCurrentState);
    return patchDocument;
  }

  /**
   * Initialize state with defaults.
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
   */
  protected S buildPatch(TaskState.TaskStage stage, E taskStateSubStage) throws Throwable {
    return buildPatch(stage, taskStateSubStage, null);
  }

  /**
   * Build a state object that can be used to submit a stage progress self patch.
   */
  protected S buildPatch(TaskState.TaskStage stage, E taskStateSubStage, Throwable t)
      throws Throwable {
    S patchState = (S) getStateType().newInstance();

    ServiceDocumentUtils.setTaskState(patchState,
        buildPatchTaskState(stage, taskStateSubStage, t));
    return patchState;
  }

  /**
   * Build a task state object that can be used to submit a stage progress self patch.
   */
  protected T buildPatchTaskState(TaskState.TaskStage stage, E taskStateSubStage, Throwable t) throws Throwable {
    T taskState = taskStateType.newInstance();
    taskState.stage = stage;

    if (taskStateSubStage != null) {
      ServiceDocumentUtils.setTaskStateSubStage(taskState, taskStateSubStage);
    }

    if (t != null) {
      taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return taskState;
  }

  /**
   * This method applies a patch to a state object.
   */
  protected void applyPatch(S currentState, S patchState) throws Throwable {
    T currentTaskState = ServiceDocumentUtils.getTaskState(currentState);
    E currentSubstage = ServiceDocumentUtils.getTaskStateSubStage(currentTaskState);

    T patchTaskState = ServiceDocumentUtils.getTaskState(patchState);
    E patchSubstage = ServiceDocumentUtils.getTaskStateSubStage(patchTaskState);

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
      ServiceDocumentUtils.setTaskState(currentState, patchTaskState);
    }

    PatchUtils.patchState(currentState, patchState);
  }

  /**
   * Moves the service into the FAILED state.
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
   */
  protected void validateStartState(S state) throws Throwable {
    validateState(state);

    T taskState = ServiceDocumentUtils.getTaskState(state);
    checkState(taskState.stage == TaskState.TaskStage.CREATED,
        "Expected state is CREATED. Cannot proceed in " + taskState.stage + " state. ");
  }

  /**
   * Validate service state coherence.
   */
  protected void validateState(S state) throws Throwable {
    ValidationUtils.validateState(state);
    T taskState = ServiceDocumentUtils.getTaskState(state);
    ValidationUtils.validateTaskStage(taskState);

    switch (taskState.stage) {
      case STARTED:
        E subStage = ServiceDocumentUtils.getTaskStateSubStage(taskState);
        checkState(subStage != null, "Invalid stage update. SubStage cannot be null");
        E[] validSubStages = ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType);
        if (!Arrays.asList(validSubStages).contains(subStage)) {
          checkState(false, "unsupported subStage: " + taskState.stage.toString());
        }
        break;
      case CREATED:
      case FAILED:
      case FINISHED:
      case CANCELLED:
        checkState(ServiceDocumentUtils.getTaskStateSubStage(taskState) == null,
            "Invalid stage update. SubStage must be null");
        break;
      default:
        checkState(false, "cannot process patches in state: " + taskState.stage.toString());
    }
  }

  /**
   * This method checks a patch object for validity against a document state object.
   */
  protected void validatePatchState(S currentState, S patchState) throws Throwable {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(ServiceDocumentUtils.getTaskState(patchState));
    ValidationUtils.validateTaskStageProgression(
        ServiceDocumentUtils.getTaskState(currentState),
        ServiceDocumentUtils.getTaskState(patchState));

    E currentSubStage = ServiceDocumentUtils.getTaskStateSubStage(currentState);
    E patchSubStage = ServiceDocumentUtils.getTaskStateSubStage(patchState);
    if (currentSubStage != null && patchSubStage != null) {
      checkState(patchSubStage.ordinal() >= currentSubStage.ordinal(),
          "Sub-stage cannot set from " + currentSubStage + " to " + patchSubStage);
    }
  }
}
