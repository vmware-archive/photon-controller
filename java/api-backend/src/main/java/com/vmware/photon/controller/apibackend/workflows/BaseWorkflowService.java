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
public abstract class BaseWorkflowService <S extends ServiceDocument, T extends TaskState, E extends Enum>
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

  protected abstract TaskService.State buildTaskServiceStartState(S state);

  /**
   * Creates a {@link TaskService.State} entity when the workflow is created.
   */
  protected void create(S state, Operation operation) {
    TaskService.State taskServiceState = buildTaskServiceStartState(state);
    TaskServiceUtils.create(
        this,
        taskServiceState,
        (op, ex) -> {
          if (ex != null) {
            operation.fail(ex);
            fail(state, ex);
            return;
          }

          try {
            ServiceDocumentUtils.setTaskServiceState(state, op.getBody(TaskService.State.class));
            operation.setBody(state).complete();
          } catch (Throwable t) {
            operation.fail(t);
            fail(state, t);
          }
        });
  }

  /**
   * Moves the service to the first sub-stage of the STARTED state.
   */
  protected void start(S state) {
    try {
      TaskServiceUtils.start(
          this,
          ServiceDocumentUtils.getTaskServiceState(state),
          (op, ex) -> {
            if (ex != null) {
              fail(state, ex);
              return;
            }

            try {
              S patchState = buildPatch(TaskState.TaskStage.STARTED,
                  ServiceDocumentUtils.getTaskStateSubStageEntries(taskSubStageType)[0]);
              ServiceDocumentUtils.setTaskServiceState(patchState, op.getBody(TaskService.State.class));
              TaskUtils.sendSelfPatch(this, patchState);
            } catch (Throwable t) {
              fail(state, t);
            }
          });
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Moves the service to the next sub-stage of the STARTED state.
   */
  protected void progress(S state, E nextSubStage) {
    try {
      TaskServiceUtils.progress(
          this,
          ServiceDocumentUtils.getTaskServiceState(state),
          nextSubStage.ordinal(),
          (op, ex) -> {
            if (ex != null) {
              fail(state, ex);
              return;
            }

            try {
              if (ControlFlags.disableOperationProcessingOnStageTransition(
                  ServiceDocumentUtils.getControlFlags(state))) {
                ServiceUtils.logInfo(this, "Operation processing on stage transition disabled");
                return;
              }

              S patchState = buildPatch(TaskState.TaskStage.STARTED, nextSubStage);
              ServiceDocumentUtils.setTaskServiceState(patchState, op.getBody(TaskService.State.class));
              TaskUtils.sendSelfPatch(this, patchState);
            } catch (Throwable t) {
              fail(state, t);
            }
          });
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Moves the service to the next sub-stage of the STARTED state and updates the service
   * document with the given patch.
   */
  protected void progress(S state, S patchState) {
    try {
      E nextSubStage = ServiceDocumentUtils.getTaskStateSubStage(patchState);

      TaskServiceUtils.progress(
          this,
          ServiceDocumentUtils.getTaskServiceState(state),
          nextSubStage.ordinal(),
          (op, ex) -> {
            if (ex != null) {
              fail(state, ex);
              return;
            }

            try {
              if (ControlFlags.disableOperationProcessingOnStageTransition(
                  ServiceDocumentUtils.getControlFlags(state))) {
                ServiceUtils.logInfo(this, "Operation processing on stage transition disabled");
                return;
              }

              ServiceDocumentUtils.setTaskServiceState(patchState, op.getBody(TaskService.State.class));
              TaskUtils.sendSelfPatch(this, patchState);
            } catch (Throwable t) {
              fail(state, t);
            }
          });
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Moves the service to the FINISHED state.
   */
  protected void finish(S state) {
    try {
      TaskServiceUtils.complete(
          this,
          ServiceDocumentUtils.getTaskServiceState(state),
          (op, ex) -> {
            if (ex != null) {
              fail(state, ex);
              return;
            }

            try {
              S patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
              ServiceDocumentUtils.setTaskServiceState(patchState, op.getBody(TaskService.State.class));
              TaskUtils.sendSelfPatch(this, patchState);
            } catch (Throwable t) {
              fail(state, t);
            }
          });
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Moves the service to the FAILED state.
   */
  protected void fail(S state, Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      TaskServiceUtils.fail(
          this,
          ServiceDocumentUtils.getTaskServiceState(state),
          t,
          (op, ex) -> {
            if (ex != null) {
              ServiceUtils.logSevere(this, "Failed to fail task service: %s", ex.toString());
              return;
            }

            try {
              S patchState = buildPatch(TaskState.TaskStage.FAILED, null, t);
              if (op != null) {
                ServiceDocumentUtils.setTaskServiceState(patchState, op.getBody(TaskService.State.class));
              }
              TaskUtils.sendSelfPatch(this, patchState);
            } catch (Throwable tt) {
              ServiceUtils.logSevere(this, "Failed to fail workflow: %s", tt.toString());
            }

          });
    } catch (Throwable throwable) {
      ServiceUtils.logSevere(this, "Failed to fail workflow: %s", throwable.toString());
    }
  }

  /**
   * Build a state object that can be used to submit a stage progress self patch.
   */
  protected S buildPatch(TaskState.TaskStage stage, E subStage) throws Throwable {
    return buildPatch(stage, subStage, null);
  }

  /**
   * Build a state object that can be used to submit a stage progress self patch.
   */
  protected S buildPatch(TaskState.TaskStage stage, E taskStateSubStage, Throwable t)
      throws Throwable {
    S patchState = (S) getStateType().newInstance();
    T taskState = taskStateType.newInstance();
    taskState.stage = stage;

    if (taskStateSubStage != null) {
      ServiceDocumentUtils.setTaskStateSubStage(taskState, taskStateSubStage);
    }

    if (t != null) {
      taskState.failure = Utils.toServiceErrorResponse(t);
    }

    ServiceDocumentUtils.setTaskState(patchState, taskState);
    return patchState;
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
   * Initialize state with defaults.
   */
  protected void initializeState(S current) {
    InitializationUtils.initialize(current);

    if (current.documentExpirationTimeMicros <= 0) {
      current.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
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
