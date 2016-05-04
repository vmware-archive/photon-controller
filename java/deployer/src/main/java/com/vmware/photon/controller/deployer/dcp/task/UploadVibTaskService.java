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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.VibService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;

import java.io.File;

/**
 * This class implements a DCP micro-service which performs the task of uploading one or more VIB images to a host.
 */
public class UploadVibTaskService extends StatefulService {

  /**
   * This value defines the document state associated with a {@link UploadVibTaskService} task.
   */
  @NoMigrationDuringUpgrade
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
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the optional document self-link of the parent task service to be
     * notified on completion.
     */
    @Immutable
    public String parentTaskServiceLink;

    /**
     * This value represents the optional patch body to be sent to the parent task service on
     * successful completion.
     */
    @Immutable
    public String parentPatchBody;

    /**
     * This value represents the document link of the {@link VibService} representing the VIB file
     * to upload and install on the host.
     */
    @NotNull
    @Immutable
    public String vibServiceLink;
  }

  public UploadVibTaskService() {
    super(State.class);

    /**
     * These attributes are required because the {@link UploadVibTaskService} task is scheduled by
     * the task scheduler. If and when this is not the case -- either these attributes are no
     * longer required, or this task is not scheduled by the task scheduler -- then they should be
     * removed, along with the same attributes in higher-level task services which create instances
     * of this task.
     */
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, new IllegalArgumentException("Body is required"));
      return;
    }

    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    try {
      validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    //
    // N.B. Do not automatically transition to STARTED state. The task scheduler service will
    // patch tasks to the STARTED state as executor slots become available.
    //

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
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
      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      } else {
        TaskUtils.notifyParentTask(this, currentState.taskState, currentState.parentTaskServiceLink,
            currentState.parentPatchBody);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void processStartedStage(State currentState) {

    sendRequest(Operation
        .createGet(this, currentState.vibServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processStartedStage(currentState, o.getBody(VibService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processStartedStage(State currentState, VibService.State vibState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(vibState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processStartedStage(currentState, vibState, o.getBody(HostService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processStartedStage(State currentState,
                                   VibService.State vibState,
                                   HostService.State hostState) {

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
              setUploadPath(currentState, uploadPath);
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

  private void setUploadPath(State currentState, String uploadPath) {

    VibService.State patchState = new VibService.State();
    patchState.uploadPath = uploadPath;

    sendRequest(Operation
        .createPatch(this, currentState.vibServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.FINISHED);
              }
            }));
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s", taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failure));
  }

  //
  // N.B. This routine is required for services which are started by the task scheduler service.
  //
  public static State buildStartPatch() {
    return buildPatch(TaskState.TaskStage.STARTED, null);
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
