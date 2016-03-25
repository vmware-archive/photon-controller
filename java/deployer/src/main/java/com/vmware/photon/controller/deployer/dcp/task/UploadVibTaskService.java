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
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
     * This value represents the document link of the DeploymentService in whose context the task is being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link HostService} on which to upload the VIB.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the set of VIBs which have been uploaded.
     */
    public Map<String, String> vibPaths;
  }

  public UploadVibTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    //
    // Do not automatically transition to STARTED state. The task scheduler service will transition tasks to the
    // STARTED state as executor slots become available.
    //

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    operation.setBody(startState).complete();

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
  public void handlePatch(Operation operation) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = this.getState(operation);
    State patchState = operation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    operation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
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

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            processUploadVib(currentState, op.getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processUploadVib(State currentState, HostService.State hostState) {
    File sourceDirectory = new File(HostUtils.getDeployerContext(this).getVibDirectory());
    if (!sourceDirectory.exists()) {
      throw new IllegalStateException("VIB directory " + sourceDirectory.getAbsolutePath() + " does not exist");
    } else if (!sourceDirectory.isDirectory()) {
      throw new IllegalStateException("VIB directory " + sourceDirectory.getAbsolutePath() + " is not a directory");
    }

    File[] sourceFiles = sourceDirectory.listFiles((file) -> file.getName().toUpperCase().endsWith(".VIB"));
    if (sourceFiles.length == 0) {
      throw new IllegalStateException("No VIB files were found in " + sourceDirectory.getAbsolutePath());
    }

    for (File sourceFile : sourceFiles) {

      //
      // If this file has already been uploaded, then skip to the next file.
      //

      if (currentState.vibPaths != null &&
          currentState.vibPaths.containsKey(sourceFile.getName())) {
        continue;
      }

      HttpFileServiceClient httpFileServiceClient = HostUtils.getHttpFileServiceClientFactory(this)
          .create(hostState.hostAddress, hostState.userName, hostState.password);
      String uploadPath = "/tmp/photon-controller-vibs/" +
          ServiceUtils.getIDFromDocumentSelfLink(currentState.deploymentServiceLink) + "/" + sourceFile.getName();
      ListenableFutureTask<Integer> task = ListenableFutureTask.create(
          httpFileServiceClient.uploadFile(sourceFile.getAbsolutePath(), uploadPath, false));
      HostUtils.getListeningExecutorService(this).submit(task);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(@Nullable Integer result) {
          try {
            if (result != HttpsURLConnection.HTTP_OK && result != HttpsURLConnection.HTTP_CREATED) {
              throw new IllegalStateException("Unexpected HTTP result " + result + " when uploading " +
                  sourceFile.getAbsolutePath());
            }

            Map<String, String> vibPaths = new HashMap<>(sourceFiles.length);
            if (currentState.vibPaths != null) {
              vibPaths.putAll(currentState.vibPaths);
            }

            vibPaths.put(sourceFile.getName(), uploadPath);

            State patchState = buildPatch(currentState.taskState.stage, null);
            patchState.vibPaths = vibPaths;
            TaskUtils.sendSelfPatch(UploadVibTaskService.this, patchState);
          } catch (Throwable t) {
            failTask(t);
          }
        }

        @Override
        public void onFailure(Throwable throwable) {
          failTask(throwable);
        }
      });

      return;
    }

    sendStageProgressPatch(TaskState.TaskStage.FINISHED);
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
