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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP micro-service which performs the task of creating
 * a VM.
 */
public class CreateVmTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link CreateVmTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the unique identifier of the project used for the created VM.
     */
    @NotNull
    @Immutable
    public String projectId;

    /**
     * This value represents the interval between querying the state of the
     * create VM task.
     */
    @Immutable
    public Integer queryCreateVmTaskInterval;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the Create contract that will be passed to API-FE to create a VM.
     */
    @NotNull
    @Immutable
    public VmCreateSpec vmCreateSpec;

    /**
     * This value represents the unique identifier of the created VM. It is set after the vm has been
     * successfully created in API-FE.
     */
    @WriteOnce
    public String vmId;
  }

  public CreateVmTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.queryCreateVmTaskInterval) {
      startState.queryCreateVmTaskInterval = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
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
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        createVm(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void createVm(final State currentState) throws IOException {
    FutureCallback<Task> callback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          if (null == result) {
            failTask(new IllegalStateException("createVmAsync returned null"));
            return;
          }

          processTask(currentState, result);
        } catch (Throwable e) {
          failTask(e);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    HostUtils.getApiClient(this).getProjectApi().createVmAsync(currentState.projectId, currentState.vmCreateSpec,
        callback);
  }

  private void processTask(final State currentState, final Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        scheduleGetTaskCall(this, currentState, task.getId());
        break;
      case "ERROR":
        throw new RuntimeException(ApiUtils.getErrors(task));
      case "COMPLETED":
        State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
        patchState.vmId = task.getEntity().getId();

        TaskUtils.sendSelfPatch(this, patchState);
        break;
      default:
        throw new RuntimeException("Unknown task state: " + task.getState());
    }
  }

  private void scheduleGetTaskCall(final Service service, final State currentState, final String taskId) {

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          HostUtils.getApiClient(service).getTasksApi().getTaskAsync(
              taskId,
              new FutureCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                  ServiceUtils.logInfo(service, "GetTask API call returned task %s", result.toString());
                  try {
                    processTask(currentState, result);
                  } catch (Throwable throwable) {
                    failTask(throwable);
                  }
                }

                @Override
                public void onFailure(Throwable t) {
                  failTask(t);
                }
              }
          );
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    getHost().schedule(runnable, currentState.queryCreateVmTaskInterval, TimeUnit.MILLISECONDS);
  }

  protected void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    checkState(!TaskUtils.finalTaskStages.contains(currentState.taskState.stage));

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);

    checkNotNull(currentState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());
    checkState(patchState.taskState.stage.ordinal() >= currentState.taskState.stage.ordinal());
  }

  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
