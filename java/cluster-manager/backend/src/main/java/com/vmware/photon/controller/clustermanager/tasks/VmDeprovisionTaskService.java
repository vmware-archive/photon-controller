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
package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.utils.ApiUtils;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * This class implements a Xenon service representing a task to deprovision a VM.
 */
public class VmDeprovisionTaskService extends StatefulService {

  public VmDeprovisionTaskService() {
    super(State.class);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = State.TaskState.SubStage.STOP_VM;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, State.TaskState.SubStage.STOP_VM));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(State currentState) throws IOException {
    switch (currentState.taskState.subStage) {
      case STOP_VM:
        stopVm(currentState);
        break;
      case DELETE_VM:
        deleteVm(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * Shuts down the VM. On success, moves the task to the next sub-stage i.e. DELETE_VM.
   *
   * @param currentState
   */
  private void stopVm(State currentState) throws IOException {

    HostUtils.getApiClient(this).getVmApi().performStopOperationAsync(
        currentState.vmId,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            processTask(result,
                buildPatch(TaskState.TaskStage.STARTED, State.TaskState.SubStage.DELETE_VM));
          }

          @Override
          public void onFailure(Throwable t) {
            // We ignore the exception if stopping VM fails.
            ServiceUtils.logInfo(VmDeprovisionTaskService.this, "Stopping VM failed: %s", t.getMessage());
            TaskUtils.sendSelfPatch(VmDeprovisionTaskService.this,
                buildPatch(TaskState.TaskStage.STARTED, State.TaskState.SubStage.DELETE_VM));
          }
        }
    );
  }

  /**
   * Deletes the VM. On success, moves the task to the FINISHED stage.
   *
   * @param currentState
   */
  private void deleteVm(State currentState) throws IOException {
    HostUtils.getApiClient(this).getVmApi().deleteAsync(
        currentState.vmId,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            processTask(result,
                buildPatch(TaskState.TaskStage.FINISHED, null));
          }

          @Override
          public void onFailure(Throwable t) {
            // We ignore the VmNotFound exception if delete VM fails.
            if (t.getMessage().contains("VmNotFound")) {
              TaskUtils.sendSelfPatch(VmDeprovisionTaskService.this,
                  buildPatch(TaskState.TaskStage.FINISHED, null));
            } else {
              failTask(t);
            }
          }
        }
    );
  }

  private void processTask(Task task, final State patchState) {
    ApiUtils.pollTaskAsync(
        task,
        HostUtils.getApiClient(this),
        this,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            TaskUtils.sendSelfPatch(VmDeprovisionTaskService.this, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            // We handle similar failure scenarios like in deleteVm.
            if (t.getMessage().contains("VmNotFound")) {
              TaskUtils.sendSelfPatch(VmDeprovisionTaskService.this, patchState);
            } else {
              failTask(t);
            }
          }
        }
    );
  }

  private void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
    validateSubStage(startState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
    validateSubStage(patchState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void validateSubStage(State state) {

    if (state.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(state.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (state.taskState.subStage) {
        case STOP_VM:
        case DELETE_VM:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + state.taskState.subStage.toString());
      }
    } else {
      checkState(state.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }

  }

  private State buildPatch(
      TaskState.TaskStage stage, State.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private State buildPatch(
      TaskState.TaskStage stage, State.TaskState.SubStage subStage, @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private State buildPatch(
      State.TaskState.TaskStage stage,
      State.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new State.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This class defines the document state associated with a single VmDeprovisionTaskService class.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * The state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This valure represents the identifier of the VM.
     */
    @NotNull
    @Immutable
    public String vmId;

    /**
     * This is the task state.
     */
    public static class TaskState extends com.vmware.xenon.common.TaskState {

      /**
       * The current sub-stage of the task.
       */
      public SubStage subStage;

      /**
       * The sub-states for this this.
       */
      public enum SubStage {
        STOP_VM,
        DELETE_VM
      }
    }
  }
}
