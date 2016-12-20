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

import com.vmware.photon.controller.api.model.ClusterState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * This class implements a Xenon service representing a task to resize a cluster.
 */
public class ClusterResizeTaskService extends StatefulService {

  public ClusterResizeTaskService() {
    super(ClusterResizeTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ClusterResizeTask startState = start.getBody(ClusterResizeTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == ClusterResizeTask.TaskState.TaskStage.CREATED) {
      startState.taskState.stage = ClusterResizeTask.TaskState.TaskStage.STARTED;
      startState.taskState.subStage = ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ClusterResizeTask.TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, startState.taskState.subStage));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    ClusterResizeTask currentState = getState(patch);
    ClusterResizeTask patchState = patch.getBody(ClusterResizeTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (ClusterResizeTask.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(ClusterResizeTask currentState) {
    switch (currentState.taskState.subStage) {
      case INITIALIZE_CLUSTER:
        initializeCluster(currentState);
        break;
      case RESIZE_CLUSTER:
        resizeCluster(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method calculates the delta between the current workers count and the desired workers count,
   * and saves the delta in the current state. On success, the method moves the task sub-stage
   * to RESIZE_CLUSTER.
   */
  private void initializeCluster(
      final ClusterResizeTask currentState) {

    Operation.CompletionHandler handler = (Operation operation, Throwable throwable) -> {
      if (null != throwable) {
        failTask(throwable);
        return;
      }

      ClusterService.State clusterDocument = operation.getBody(ClusterService.State.class);

      if (clusterDocument.clusterState != ClusterState.READY) {
        String errorStr = String.format(
            "Cannot resize cluster if it is not in the READY state. Current ClusterState: %s",
            clusterDocument.clusterState.toString());

        ServiceUtils.logInfo(ClusterResizeTaskService.this, errorStr);
        TaskUtils.sendSelfPatch(ClusterResizeTaskService.this, buildPatch(TaskState.TaskStage.FAILED, null,
            new IllegalStateException(errorStr)));
        return;
      }

      int workerCountDelta = currentState.newWorkerCount - clusterDocument.workerCount;
      if (workerCountDelta < 0) {
        String errorStr = String.format(
            "Reducing cluster size is not supported. Current cluster size: %d. New cluster size: %d.",
            clusterDocument.workerCount,
            currentState.newWorkerCount);

        ServiceUtils.logInfo(ClusterResizeTaskService.this, errorStr);
        TaskUtils.sendSelfPatch(ClusterResizeTaskService.this, buildPatch(TaskState.TaskStage.FAILED, null,
            new IllegalStateException(errorStr)));
        return;
      }

      if (workerCountDelta == 0) {
        ServiceUtils.logInfo(ClusterResizeTaskService.this, "Worker count delta is 0. Skip resizing.");
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        return;
      }

      ClusterResizeTask patchState = buildPatch(
          TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER);

      ClusterService.State clusterPatchState = new ClusterService.State();
      clusterPatchState.workerCount = currentState.newWorkerCount;
      clusterPatchState.clusterState = ClusterState.RESIZING;

      updateStates(currentState, patchState, clusterPatchState);
    };

    getClusterState(currentState, handler);
  }

  /**
   * Performs the resize operation using WorkersNodeRollout. On successful completion, it marks the cluster as READY
   * and moves the current task to FINISHED state.
   */
  private void resizeCluster(final ClusterResizeTask currentState) {
    // Send a patch to manually trigger the maintenance task for the cluster.
    // Since the maintenance task is designed to run in background, we start the task async without waiting for its
    // completion so that the resize task can finish immediately.
    ClusterMaintenanceTaskService.State patchState = new ClusterMaintenanceTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ClusterMaintenanceTaskFactoryService.SELF_LINK + "/" + currentState.clusterId))
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        });
    sendRequest(patchOperation);
  }

  private void getClusterState(final ClusterResizeTask currentState, Operation.CompletionHandler completionHandler) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
            .setCompletion(completionHandler));
  }

  private void updateStates(final ClusterResizeTask currentState,
                            final ClusterResizeTask patchState,
                            final ClusterService.State clusterPatchState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
            .setBody(clusterPatchState)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  TaskUtils.sendSelfPatch(ClusterResizeTaskService.this, patchState);
                }
            ));
  }

  private void validateStartState(ClusterResizeTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == ClusterResizeTask.TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case INITIALIZE_CLUSTER:
        case RESIZE_CLUSTER:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(ClusterResizeTask currentState, ClusterResizeTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == ClusterResizeTask.TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private ClusterResizeTask buildPatch(ClusterResizeTask.TaskState.TaskStage stage,
                                       ClusterResizeTask.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private ClusterResizeTask buildPatch(ClusterResizeTask.TaskState.TaskStage stage,
                                       ClusterResizeTask.TaskState.SubStage subStage,
                                       @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private ClusterResizeTask buildPatch(
      ClusterResizeTask.TaskState.TaskStage stage,
      ClusterResizeTask.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    ClusterResizeTask state = new ClusterResizeTask();
    state.taskState = new ClusterResizeTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(ClusterResizeTask.TaskState.TaskStage.FAILED, null, e));
  }
}
