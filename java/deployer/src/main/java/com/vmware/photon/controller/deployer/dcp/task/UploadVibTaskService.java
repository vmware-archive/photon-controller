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

import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.scheduler.RateLimitedWorkQueueService;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.entity.VibService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import static com.google.common.base.Preconditions.checkState;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements a Xenon service which performs the task of uploading a VIB image to a
 * host.
 */
public class UploadVibTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link UploadVibTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages of a {@link UploadVibTaskService} in the
     * {@link TaskState.TaskStage#STARTED} stage.
     */
    public enum SubStage {
      BEGIN_EXECUTION,
      UPLOAD_VIB,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link UploadVibTaskService} task.
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
     * This value represents the {@link ControlFlags} for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer taskControlFlags;

    /**
     * This value represents the optional document self-link of the parent task service to be
     * notified on completion.
     */
    @Immutable
    public String parentTaskServiceLink;

    /**
     * This value represents the optional patch body, serialized to JSON, to be sent to the parent
     * task service on successful completion.
     */
    @Immutable
    public String parentTaskPatchBody;

    /**
     * This value represents the start time, in microseconds since the Unix epoch, of the current
     * task (e.g. when the task entered the {@link TaskState.TaskStage#STARTED} state).
     */
    @WriteOnce
    public Long taskStartTimeMicros;

    /**
     * This value represents the timeout interval, in microseconds, of the current task (e.g. the
     * interval after which the task should enter the {@link TaskState.TaskStage#FAILED} state).
     * <p>
     * N.B. An initial value of 0 indicates that no timeout should be enforced.
     */
    @WriteOnce
    public Long taskTimeoutMicros;

    /**
     * This value represents the document self-link of the {@link RateLimitedWorkQueueService}
     * which will schedule instances of the current task.
     */
    @NotNull
    @Immutable
    public String workQueueServiceLink;

    /**
     * This value represents the document self-link of the {@link VibService} representing the VIB
     * file to be uploaded.
     */
    @NotNull
    @Immutable
    public String vibServiceLink;
  }

  public UploadVibTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      startOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State initialState = startOp.getBody(State.class);
    InitializationUtils.initialize(initialState);

    if (initialState.taskTimeoutMicros == null) {
      initialState.taskTimeoutMicros = TimeUnit.MINUTES.toMicros(10);
    }

    if (initialState.documentExpirationTimeMicros <= 0) {
      initialState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
          ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      validateInitialState(initialState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    if (initialState.taskTimeoutMicros != 0) {
      super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
      super.setMaintenanceIntervalMicros(initialState.taskTimeoutMicros);
    }

    startOp.setBody(initialState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(initialState.taskControlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else {
        notifyWorkQueueService(initialState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateInitialState(State initialState) {
    ValidationUtils.validateState(initialState);
    ValidationUtils.validateTaskStage(initialState.taskState, initialState.taskState.subStage);
  }

  private void notifyWorkQueueService(State initialState) {
    RateLimitedWorkQueueService.PatchState patchState = new RateLimitedWorkQueueService.PatchState();
    patchState.pendingTaskServiceDelta = 1;
    patchState.taskServiceLink = getSelfLink();
    sendRequest(Operation.createPatch(this, initialState.workQueueServiceLink).setBody(patchState));
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);

    try {
      applyPatch(currentState, patchState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.taskControlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      } else {
        processTerminalStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void applyPatch(State currentState, State patchState) {

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState, patchState.taskState.subStage);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, currentState.taskState.subStage,
        patchState.taskState, patchState.taskState.subStage);

    PatchUtils.patchState(currentState, patchState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {

      //
      // Set the task start time if it has not already been set.
      //

      if (currentState.taskStartTimeMicros == null) {
        currentState.taskStartTimeMicros = Utils.getNowMicrosUtc();
      }

      //
      // Transition to the UPLOAD_VIB sub-stage if appropriate.
      //

      if (currentState.taskState.subStage == TaskState.SubStage.BEGIN_EXECUTION) {
        currentState.taskState.subStage = TaskState.SubStage.UPLOAD_VIB;
      }
    }

    ValidationUtils.validateState(currentState);
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case BEGIN_EXECUTION:
        throw new IllegalStateException("Unexpected task sub-stage " + currentState.taskState.subStage);
      case UPLOAD_VIB:
        processUploadVibSubStage(currentState);
        break;
    }
  }

  private void processTerminalStage(State currentState) {

    //
    // Since the task has reached a terminal state, its state no longer needs to be monitored for
    // timeout enforcement. Disable periodic maintenance.
    //

    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, false);

    //
    // Notify the work queue service that the current task has ceased execution.
    //

    RateLimitedWorkQueueService.PatchState workQueuePatchState = new RateLimitedWorkQueueService.PatchState();
    workQueuePatchState.runningTaskServiceDelta = -1;

    sendRequest(Operation
        .createPatch(this, currentState.workQueueServiceLink)
        .setBody(workQueuePatchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logSevere(this, "Failed to notify work queue service: " + e);
              } else {
                TaskUtils.notifyParentTask(this, currentState.taskState, currentState.parentTaskServiceLink,
                    currentState.parentTaskPatchBody);
              }
            }));
  }

  @Override
  public void handlePeriodicMaintenance(Operation maintenanceOp) {
    ServiceUtils.logInfo(this, "Handling maintenance operation");
    maintenanceOp.complete();

    sendRequest(Operation
        .createGet(this, getSelfLink())
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logWarning(this, "Failed to get state during maintenance: " + e);
              } else {
                handlePeriodicMaintenance(o.getBody(State.class));
              }
            }));
  }

  private void handlePeriodicMaintenance(State currentState) {

    checkState(currentState.taskTimeoutMicros != 0);

    boolean failTask = (currentState.taskState.stage == TaskState.TaskStage.STARTED &&
        currentState.taskStartTimeMicros != null &&
        currentState.taskStartTimeMicros < Utils.getNowMicrosUtc() + currentState.taskTimeoutMicros);

    if (failTask) {
      State patchState = buildPatch(TaskState.TaskStage.FAILED, null, new TimeoutException());
      patchState.taskState.failure.statusCode = Operation.STATUS_CODE_TIMEOUT;
      TaskUtils.sendSelfPatch(this, patchState);
    }
  }

  //
  // UPLOAD_VIB sub-stage routines
  //

  private void processUploadVibSubStage(State currentState) {

    sendRequest(Operation
        .createGet(this, currentState.vibServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processUploadVibSubStage(o.getBody(VibService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processUploadVibSubStage(VibService.State vibState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(vibState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processUploadVibSubStage(vibState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processUploadVibSubStage(VibService.State vibState, HostService.State hostState) {

    File sourceDirectory = new File(HostUtils.getDeployerContext(this).getVibDirectory());
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      throw new IllegalStateException("Invalid VIB source directory " + sourceDirectory);
    }

    File sourceFile = new File(sourceDirectory, vibState.vibName);
    if (!sourceFile.exists() || !sourceFile.isFile()) {
      throw new IllegalStateException("Invalid VIB source file " + sourceFile);
    }

    HttpFileServiceClient httpFileServiceClient = HostUtils.getHttpFileServiceClientFactory(this)
        .create(hostState.hostAddress, hostState.userName, hostState.password);
    String uploadPath = UriUtils.buildUriPath("tmp", "photon-controller-vibs",
        ServiceUtils.getIDFromDocumentSelfLink(vibState.documentSelfLink), sourceFile.getName());
    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(
        httpFileServiceClient.uploadFile(sourceFile.getAbsolutePath(), uploadPath, false));
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    Futures.addCallback(futureTask, new FutureCallback<Integer>() {
      @Override
      public void onSuccess(@javax.validation.constraints.NotNull Integer result) {
        try {
          switch (result) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_CREATED:
              setUploadPath(vibState.documentSelfLink, uploadPath);
              break;
            default:
              throw new IllegalStateException("Unexpected HTTP result " + result + " when uploading VIB file " +
                  vibState.vibName + " to host " + hostState.hostAddress);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        failTask(throwable);
      }
    });
  }

  private void setUploadPath(String vibServiceLink, String uploadPath) {

    VibService.State patchState = new VibService.State();
    patchState.uploadPath = uploadPath;

    sendRequest(Operation
        .createPatch(this, vibServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  sendStageProgressPatch(TaskState.TaskStage.FINISHED);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage " + taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null, null));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  public static State buildPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    return buildPatch(taskStage, subStage, null);
  }

  private static State buildPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage, Throwable failure) {
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
