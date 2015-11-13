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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.DefaultUuid;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP micro-service which performs the task of
 * uploading an VIB image to the image datastore.
 */
public class UploadVibTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link UploadVibTaskService}
   * instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link DeploymentService} in whose context the task operation is
     * being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link to the {@link HostService} instance representing the ESX host on which
     * the agent should be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the unique ID of the VIB upload operation.
     */
    @DefaultUuid
    @Immutable
    public String uniqueId;

    /**
     * This value represents the path on the host to which the VIB file was
     * uploaded. It is used subsequently when installing the agent.
     */
    @WriteOnce
    public String vibPath;
  }

  public UploadVibTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed on the current
   * service instance.
   *
   * @param start Supplies a patch operation to be handled.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current
   * service instance.
   *
   * @param patch Supplies a patch operation to be handled.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        retrieveDocuments(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies the current state of the service instance.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage Supplies the state to which the service instance should be
   *              transitioned.
   * @param e     Supplies an optional Throwable object representing the failure
   *              encountered by the service instance.
   * @return A State object which can be used to submit a stage progress self-
   * patch.
   */
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

  /**
   * This method validates a patch object against a valid document state
   * object.
   *
   * @param startState Supplies the state of the current service instance.
   * @param patchState Supplies the state object specified in the patch
   *                   operation.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  private void retrieveDocuments(final State currentState) {
    CloudStoreHelper cloudStoreHelper = ((DeployerDcpServiceHost) getHost()).getCloudStoreHelper();
    cloudStoreHelper.getEntities(this,
        Arrays.asList(currentState.deploymentServiceLink, currentState.hostServiceLink),
        (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
          if (failures != null && failures.size() > 0) {
            failTask(failures.values().iterator().next());
            return;
          }

          try {
            DeploymentService.State deploymentState = null;
            HostService.State hostState = null;
            for (Operation op : ops.values()) {
              String link = op.getUri().toString();
              if (link.contains(currentState.deploymentServiceLink)) {
                deploymentState = op.getBody(DeploymentService.State.class);
              }
              if (link.contains(currentState.hostServiceLink)) {
                hostState = op.getBody(HostService.State.class);
              }
            }
            processUploadVib(currentState, deploymentState, hostState);
          } catch (Throwable t) {
            failTask(t);
          }
        });
  }

  private void processUploadVib(State currentState,
                                DeploymentService.State deploymentState,
                                HostService.State hostState) throws Throwable {

    File sourceDirectory = new File(HostUtils.getDeployerContext(this).getVibDirectory());
    ServiceUtils.logInfo(this, "Uploading VIB files from %s", sourceDirectory.getAbsolutePath());
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      throw new IllegalStateException("VIB directory " + sourceDirectory.getAbsolutePath() + " must exist");
    }

    File[] sourceFiles = sourceDirectory.listFiles(file -> file.getName().endsWith(".vib"));
    if (sourceFiles.length == 0) {
      throw new IllegalStateException("No VIB files were found in " + sourceDirectory.getAbsolutePath());
    }

    checkState(sourceFiles.length == 1);

    ConcurrentHashMap<File, Throwable> errors = new ConcurrentHashMap<>();
    AtomicInteger pending = new AtomicInteger(sourceFiles.length);
    for (File sourceFile : sourceFiles) {
      HttpFileServiceClient httpFileServiceClient = HostUtils.getHttpFileServiceClientFactory(this).create(
          hostState.hostAddress, hostState.userName, hostState.password);
      String dsPath = "/vibs/" + currentState.uniqueId + "/" + sourceFile.getName();
      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFileToDatastore(
          sourceFile.getAbsolutePath(), deploymentState.imageDataStoreNames.iterator().next(), dsPath));
      HostUtils.getListeningExecutorService(this).submit(task);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(@Nullable Integer result) {
          if (0 == pending.decrementAndGet()) {
            handleCompletion(errors, dsPath);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          errors.put(sourceFile, t);
          if (0 == pending.decrementAndGet()) {
            handleCompletion(errors, dsPath);
          }
        }
      });
    }
  }

  private void handleCompletion(ConcurrentHashMap<File, Throwable> errors, String vibPath) {
    if (!errors.isEmpty()) {
      errors.values().stream().forEach(t -> ServiceUtils.logSevere(this, t));
      failTask(errors.values().iterator().next());
    } else {
      State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
      patchState.vibPath = vibPath;
      TaskUtils.sendSelfPatch(this, patchState);
    }
  }

  /**
   * This method sends a patch operation to the current service instance to
   * transition to a new state.
   *
   * @param stage Supplies the state to which the service instance should be
   *              transitioned.
   */
  private void sendStageProgressPatch(TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the initial state of the current service
   *                   instance.
   * @param patchState Supplies the patch state associated with a patch
   *                   operation.
   * @return The updated state of the current service instance.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage) {
        ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      }

      startState.taskState = patchState.taskState;

      if (null != patchState.vibPath) {
        startState.vibPath = patchState.vibPath;
      }
    }

    return startState;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * transition to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the failure encountered by the service instance.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
