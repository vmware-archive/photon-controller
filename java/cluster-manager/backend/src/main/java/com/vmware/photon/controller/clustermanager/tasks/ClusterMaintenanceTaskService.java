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

import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceMaintenanceRequest;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class implements a Xenon Service that performs periodic maintenance on a single cluster.
 */
public class ClusterMaintenanceTaskService extends StatefulService {

  public ClusterMaintenanceTaskService() {
    super(State.class);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);

    super.setMaintenanceIntervalMicros(ClusterManagerConstants.DEFAULT_MAINTENANCE_INTERVAL);
  }

  /**
   * Handle service Start calls.
   */
  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);

    InitializationUtils.initialize(startState);
    validateStartState(startState);

    Operation start = startOperation.setBody(startState);
    start.complete();
  }

  /**
   * Handle service Patch calls.
   */
  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch operation for service %s", getSelfLink());
    State currentState = getState(patchOperation);

    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);

    String clusterId = ServiceUtils.getIDFromDocumentSelfLink(getSelfLink());
    MaintenanceOperation maintenanceOperation = processPatchState(currentState, patchState, clusterId);

    // Complete MUST be called only after we have decided to run maintenance. This way
    // we avoid race conditions related to multiple Patch calls trying to start maintenance.
    patchOperation.complete();

    try {
      switch (maintenanceOperation) {
        case RUN:
          startMaintenance(currentState, clusterId);
          break;
        case RETRY:
          getHost().schedule(
              () -> startMaintenance(currentState, clusterId),
              currentState.retryIntervalSecond * currentState.retryCount,
              TimeUnit.SECONDS);
          break;
        case SKIP:
          ServiceUtils.logInfo(this, "Skipping maintenance");
          break;
        default:
          ServiceUtils.logSevere(this,
              "Unknown maintenance operation %s. Skipping maintenance",
              maintenanceOperation.toString());
          break;
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  private MaintenanceOperation processPatchState(State currentState, State patchState, String clusterId) {
    MaintenanceOperation maintenanceOperation = MaintenanceOperation.SKIP;

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      if (patchState.taskState.stage == TaskState.TaskStage.FINISHED) {
        // The previous maintenance task succeeded. We need to reset the retry counter.
        ServiceUtils.logInfo(this, "Not run maintenance because patching the task from %s to %s",
            currentState.taskState.stage.toString(),
            patchState.taskState.stage.toString());
        patchState.retryCount = 0;
        patchState.errors = new ArrayList<>();

        maintenanceOperation = MaintenanceOperation.SKIP;
      } else if (patchState.taskState.stage == TaskState.TaskStage.FAILED) {
        // The previous maintenance task failed. We need to record the error message and determine
        // whether we want to retry.
        patchState.errors = currentState.errors == null ?
            new ArrayList<>() : currentState.errors;
        if (patchState.taskState.failure != null) {
          patchState.errors.add(patchState.taskState.failure.message);
        } else {
          patchState.errors.add("Missing failure message");
        }

        if (currentState.retryCount < currentState.maxRetryCount) {
          // We still have retry available. Increase the retry counter by one.
          ServiceUtils.logInfo(this, "Retry maintenance because current retry count is %d",
              currentState.retryCount);

          patchState.retryCount = currentState.retryCount + 1;
          patchState.taskState.stage = TaskState.TaskStage.STARTED;

          maintenanceOperation = MaintenanceOperation.RETRY;
        } else {
          // We have used all retries. Fail the maintenance task and set the cluster to ERROR state.
          ServiceUtils.logInfo(this, "Not retry maintenance because maximum retries have been reached");
          for (String error : patchState.errors) {
            ServiceUtils.logInfo(this, error);
          }

          ClusterService.State clusterPatchState = new ClusterService.State();
          clusterPatchState.clusterState = ClusterState.ERROR;

          sendRequest(
              HostUtils.getCloudStoreHelper(this)
                  .createPatch(getClusterDocumentLink(clusterId))
                  .setBody(clusterPatchState)
                  .setCompletion(
                      (Operation operation, Throwable throwable) -> {
                        if (null != throwable) {
                          // Ignore the failure. Otherwise if we fail the maintenance task we may end up
                          // in a dead loop.
                          ServiceUtils.logSevere(this, "Failed to patch cluster to ERROR: %s", throwable.toString());
                        }
                      }
                  ));

          maintenanceOperation = MaintenanceOperation.SKIP;
        }
      } else if (patchState.taskState.stage == TaskState.TaskStage.CANCELLED) {
        // The previous maintenance task was cancelled. We don't want to run maintenance task but we want
        // to set the cluster to ERROR state.
        ServiceUtils.logInfo(this, "Not retry maintenance because maintenance was cancelled");

        ClusterService.State clusterPatchState = new ClusterService.State();
        clusterPatchState.clusterState = ClusterState.ERROR;

        sendRequest(
            HostUtils.getCloudStoreHelper(this)
                .createPatch(getClusterDocumentLink(clusterId))
                .setBody(clusterPatchState)
                .setCompletion(
                    (Operation operation, Throwable throwable) -> {
                      if (null != throwable) {
                        // Ignore the failure. Otherwise if we fail the maintenance task we may end up
                        // in a dead loop.
                        ServiceUtils.logSevere(this, "Failed to patch cluster to ERROR: %s", throwable.toString());
                      }
                    }
                ));

        maintenanceOperation = MaintenanceOperation.SKIP;
      }
    } else {
      if (patchState.taskState.stage == TaskState.TaskStage.STARTED) {
        // The maintenance task was not started, and now we patch it to start.
        ServiceUtils.logInfo(this, "Run maintenance because patching the task from %s to %s",
            currentState.taskState.stage.toString(),
            patchState.taskState.stage.toString());

        maintenanceOperation = MaintenanceOperation.RUN;
      } else {
        // The maintenance task was not started, and it is not patched to start.
        ServiceUtils.logInfo(this, "Not run maintenance because patching the task from %s to %s",
            currentState.taskState.stage.toString(),
            patchState.taskState.stage.toString());

        maintenanceOperation = MaintenanceOperation.SKIP;
      }
    }

    PatchUtils.patchState(currentState, patchState);
    return maintenanceOperation;
  }

  /**
   * Handle service periodic maintenance calls.
   */
  @Override
  public void handleMaintenance(Operation maintenance) {
    ServiceUtils.logInfo(this, "Periodic maintenance triggered. %s", getSelfLink());

    ServiceMaintenanceRequest request = maintenance.getBody(ServiceMaintenanceRequest.class);
    if (!request.reasons.contains(ServiceMaintenanceRequest.MaintenanceReason.PERIODIC_SCHEDULE)) {
      ServiceUtils.logInfo(this, "Skipping handleMaintenance. REASON: %s", request.reasons.toString());
      maintenance.complete();
      return;
    }

    try {
      // Mark the current maintenance operation as completed.
      maintenance.complete();

      // Send a self-patch to kick-off cluster maintenance.
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED, null));

    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Maintenance trigger failed with the failure: %s", e.toString());
    }
  }

  /**
   * Starts processing maintenance request for a single cluster.
   */
  private void startMaintenance(State currentState, String clusterId) {
    ServiceUtils.logInfo(this, "Starting maintenance for clusterId: %s", clusterId);

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(getClusterDocumentLink(clusterId))
            .setCompletion(
                (Operation op, Throwable t) -> {
                  if (t != null) {
                    if (op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND
                        || op.getStatusCode() == Operation.STATUS_CODE_TIMEOUT
                        || t.getClass().equals(TimeoutException.class)) {
                      sendRequest(Operation
                          .createDelete(UriUtils.buildUri(getHost(), getSelfLink()))
                          .setBody(new ServiceDocument())
                          .setReferer(getHost().getUri()));

                      return;
                    }
                    failTask(t);
                    return;
                  }

                  try {
                    ClusterService.State cluster = op.getBody(ClusterService.State.class);
                    switch (cluster.clusterState) {
                      case CREATING:
                      case RESIZING:
                      case READY:
                        performGarbageInspection(currentState, clusterId);
                        break;

                      case PENDING_DELETE:
                        deleteCluster(clusterId);
                        break;

                      case ERROR:
                        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
                        break;

                      default:
                        failTask(new IllegalStateException(String.format(
                            "Unknown clusterState. ClusterId: %s. ClusterState: %s", clusterId, cluster.clusterState)));
                        break;
                    }
                  } catch (Throwable e) {
                    failTask(e);
                  }
                }
            ));
  }

  private void performGarbageInspection(final State currentState, final String clusterId) {
    GarbageInspectionTaskService.State startState = new GarbageInspectionTaskService.State();
    startState.clusterId = clusterId;

    TaskUtils.startTaskAsync(
        this,
        GarbageInspectionTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        GarbageInspectionTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<GarbageInspectionTaskService.State>() {
          @Override
          public void onSuccess(@Nullable GarbageInspectionTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                performGarbageCollection(currentState, clusterId);
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "GarbageInspectionTaskService was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "GarbageInspectionTaskService failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void performGarbageCollection(final State currentState, final String clusterId) {
    GarbageCollectionTaskService.State startState = new GarbageCollectionTaskService.State();
    startState.clusterId = clusterId;

    TaskUtils.startTaskAsync(
        this,
        GarbageCollectionTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        GarbageCollectionTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<GarbageCollectionTaskService.State>() {
          @Override
          public void onSuccess(@Nullable GarbageCollectionTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                expandCluster(currentState, clusterId);
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "GarbageCollectionTaskService was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "GarbageCollectionTaskService failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void expandCluster(final State currentState, final String clusterId) {
    ClusterExpandTaskService.State startState = new ClusterExpandTaskService.State();
    startState.clusterId = clusterId;
    startState.batchExpansionSize = currentState.batchExpansionSize;

    TaskUtils.startTaskAsync(
        this,
        ClusterExpandTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ClusterExpandTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<ClusterExpandTaskService.State>() {
          @Override
          public void onSuccess(@Nullable ClusterExpandTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                ClusterService.State clusterPatch = new ClusterService.State();
                clusterPatch.clusterState = ClusterState.READY;
                updateStates(clusterId, clusterPatch, buildPatch(TaskState.TaskStage.FINISHED, null));
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "ClusterExpandTaskService was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "ClusterExpandTaskService failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void deleteCluster(String clusterId) {

    FutureCallback<ClusterDeleteTask> callback = new FutureCallback<ClusterDeleteTask>() {
      @Override
      public void onSuccess(@Nullable ClusterDeleteTask result) {
        switch (result.taskState.stage) {
          case FINISHED:
            TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                buildPatch(TaskState.TaskStage.FINISHED, null));
            break;
          case CANCELLED:
            IllegalStateException cancelled = new IllegalStateException(String.format(
                "ClusterDeleteTaskService was canceled. %s", result.documentSelfLink));
            TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
            break;
          case FAILED:
            IllegalStateException failed = new IllegalStateException(String.format(
                "ClusterDeleteTaskService failed with error %s. %s",
                result.taskState.failure.message,
                result.documentSelfLink));
            TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this,
                buildPatch(TaskState.TaskStage.FAILED, failed));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    ClusterDeleteTask startState = new ClusterDeleteTask();
    startState.clusterId = clusterId;

    TaskUtils.startTaskAsync(
        this,
        ClusterDeleteTaskFactoryService.SELF_LINK,
        startState,
        (ClusterDeleteTask state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ClusterDeleteTask.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        callback);
  }

  private void validateStartState(State state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
    if (state.taskState.stage == TaskState.TaskStage.STARTED) {
      throw new IllegalStateException("Cannot create a maintenance task in STARTED state. " +
          "Use PATCH method to set the task state to STARTED.");
    }
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);

    // This task is restartable from any of the terminal states. Hence, if the patch is to START the task,
    // we ignore validating taskStage progression.
    if (patchState.taskState.stage != TaskState.TaskStage.STARTED) {
      ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
    }
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(
        this, "Cluster Maintenance failed. SelfLink: %s. Error: %s", getSelfLink(), t.toString());
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void updateStates(String clusterId,
                            ClusterService.State clusterPatch,
                            State patch) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(getClusterDocumentLink(clusterId))
            .setBody(clusterPatch)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    // Ignore the failure. Otherwise if we fail the maintenance task may end up
                    // in a dead loop.
                    ServiceUtils.logSevere(this, "Failed to patch cluster to READY: %s", throwable.toString());
                  }

                  TaskUtils.sendSelfPatch(ClusterMaintenanceTaskService.this, patch);
                }
            ));
  }


  private State buildPatch(TaskState.TaskStage patchStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.failure = (null != t) ? Utils.toServiceErrorResponse(t) : null;
    return patchState;
  }

  private String getClusterDocumentLink(String clusterId) {
    return ClusterServiceFactory.SELF_LINK + "/" + clusterId;
  }

  /**
   * This class represents the document state associated with a
   * {@link ClusterMaintenanceTaskService} task.
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
     * This value represents a list of errors that the maintenance task has encountered.
     */
    public List<String> errors;

    /**
     * The threshold for each expansion batch.
     */
    @DefaultInteger(value = ClusterManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
    @Immutable
    public Integer batchExpansionSize;

    /**
     * This value represents the maximum number of retries that maintenance task can take
     * before entering FAILED state.
     */
    @DefaultInteger(value = ClusterManagerConstants.DEFAULT_MAINTENANCE_RETRY_COUNT)
    @Immutable
    public Integer maxRetryCount;

    /**
     * This value represents the number of available retries that maintenance task has taken
     * before entering FAILED state.
     */
    @DefaultInteger(value = 0)
    public Integer retryCount;

    /**
     * This value represents the retry interval, in seconds, that maintenance task should wait
     * before retry again.
     */
    @DefaultInteger(value = ClusterManagerConstants.DEFAULT_MAINTENANCE_RETRY_INTERVAL_SECOND)
    public Integer retryIntervalSecond;
  }

  /**
   * This enum represents the operations that the maintenance task should take
   * when entering each maintenance cycle.
   */
  private static enum MaintenanceOperation {
    // RUN means that we run maintenance task in this cycle without any delay.
    RUN,
    // SKIP means that we do not run maintenance task in this cycle.
    SKIP,
    // RETRY means that we run maintenance task in this cycle but with the retry delay.
    RETRY
  }
}
