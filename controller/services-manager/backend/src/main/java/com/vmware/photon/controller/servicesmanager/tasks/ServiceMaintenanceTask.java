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
package com.vmware.photon.controller.servicesmanager.tasks;

import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
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
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.concurrent.TimeoutException;

/**
 * This class implements a Xenon Service that performs periodic maintenance on a single service.
 */
public class ServiceMaintenanceTask extends StatefulService {

  public ServiceMaintenanceTask() {
    super(State.class);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);

    super.setMaintenanceIntervalMicros(ServicesManagerConstants.DEFAULT_MAINTENANCE_INTERVAL);
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

    String serviceId = ServiceUtils.getIDFromDocumentSelfLink(getSelfLink());
    MaintenanceOperation maintenanceOperation = processPatchState(currentState, patchState, serviceId);

    // Complete MUST be called only after we have decided to run maintenance. This way
    // we avoid race conditions related to multiple Patch calls trying to start maintenance.
    patchOperation.complete();

    try {
      switch (maintenanceOperation) {
        case RUN:
          startMaintenance(currentState, serviceId);
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
      failTask(currentState, e);
    }
  }

  private MaintenanceOperation processPatchState(State currentState, State patchState, String serviceId) {
    MaintenanceOperation maintenanceOperation = MaintenanceOperation.SKIP;

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      if (patchState.taskState.stage == TaskState.TaskStage.FINISHED) {
        // The previous maintenance task succeeded. We increment the maintenanceOperation, reset the patch error state
        // to null
        ServiceUtils.logInfo(this, "Maintainence finished for service with ID %s", serviceId);
        patchState.maintenanceIteration = currentState.maintenanceIteration + 1;
        patchState.error = "";

        // recover the service service document state to READY in case of any previous RECOVERABLE_ERROR state
        ServiceState.State servicePatchState = new ServiceState.State();
        servicePatchState.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
        // service maintenance finished successfully, clear out any errorReason
        servicePatchState.errorReason = "";

        updateStates(serviceId, servicePatchState, null, com.vmware.photon.controller.api.model.ServiceState.READY);

        maintenanceOperation = MaintenanceOperation.SKIP;
      } else if (patchState.taskState.stage == TaskState.TaskStage.FAILED) {
        // The previous maintenance task failed. We need to record the error message and set the service to error state
        // We will still run the maintenance in the future because this is a recoverable error
        if (patchState.taskState.failure != null) {
          patchState.error = patchState.taskState.failure.message;
        } else {
          patchState.error = "Missing failure message";
        }

        ServiceState.State servicePatchState = new ServiceState.State();
        servicePatchState.serviceState = com.vmware.photon.controller.api.model.ServiceState.RECOVERABLE_ERROR;
        servicePatchState.errorReason = patchState.error;

        updateStates(serviceId, servicePatchState, null,
            com.vmware.photon.controller.api.model.ServiceState.RECOVERABLE_ERROR);

        maintenanceOperation = MaintenanceOperation.SKIP;
      } else if (patchState.taskState.stage == TaskState.TaskStage.CANCELLED) {
        // The previous maintenance task was cancelled. We don't want to run maintenance task but we want
        // to set the service to FATAL_ERROR state. WE will not run any future maintainence for this service
        ServiceUtils.logInfo(this, "Maintenance for service %s was cancelled, not retrying", serviceId);

        ServiceState.State servicePatchState = new ServiceState.State();
        servicePatchState.serviceState = com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR;
        servicePatchState.errorReason = "Service Maintenance was cancelled, putting service into FATAL_ERROR";

        updateStates(serviceId, servicePatchState, null,
            com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR);

        maintenanceOperation = MaintenanceOperation.SKIP;
      }
    } else {
      if (patchState.taskState.stage == TaskState.TaskStage.STARTED) {
        // The maintenance task was not started, and now we patch it to start and update the
        // maintenance iteration.
        ServiceUtils.logInfo(this, "Running maintenance %d for service %s because patching the task from %s to %s",
            currentState.maintenanceIteration, serviceId,
            currentState.taskState.stage.toString(),
            patchState.taskState.stage.toString());

        maintenanceOperation = MaintenanceOperation.RUN;
      } else {
        // The maintenance task was not started, and it is not patched to start.
        ServiceUtils.logInfo(this, "Not running maintenance for service %s because patching the task from %s to %s",
            serviceId,
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
  public void handlePeriodicMaintenance(Operation maintenance) {
    ServiceUtils.logInfo(this, "Periodic maintenance triggered. %s", getSelfLink());

    try {
      // Mark the current maintenance operation as completed.
      maintenance.complete();

      // Send a self-patch to kick-off service maintenance.
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED, null));

    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Maintenance trigger failed with the failure: %s", e.toString());
    }
  }

  /**
   * Starts processing maintenance request for a single service.
   */
  private void startMaintenance(State currentState, String serviceId) {
    ServiceUtils.logInfo(this, "Starting maintenance %d for service: %s", currentState.maintenanceIteration, serviceId);

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(getServiceDocumentLink(serviceId))
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
                    failTask(currentState, t);
                    return;
                  }

                  try {
                    ServiceState.State service = op.getBody(ServiceState.State.class);
                    switch (service.serviceState) {
                      case CREATING:
                      case RESIZING:
                      case READY:
                      case RECOVERABLE_ERROR:
                        performGarbageInspection(currentState, serviceId);
                        break;

                      case PENDING_DELETE:
                        deleteService(currentState, serviceId);
                        break;

                      case FATAL_ERROR:
                        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.CANCELLED, null));
                        break;

                      default:
                        failTask(currentState, new IllegalStateException(String.format(
                            "Unknown serviceState. ServiceId: %s. ServiceState: %s", serviceId, service.serviceState)));
                        break;
                    }
                  } catch (Throwable e) {
                    failTask(currentState, e);
                  }
                }
            ));
  }

  private void performGarbageInspection(final State currentState, final String serviceId) {
    GarbageInspectionTaskService.State startState = new GarbageInspectionTaskService.State();
    startState.serviceId = serviceId;

    TaskUtils.startTaskAsync(
        this,
        GarbageInspectionTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        GarbageInspectionTaskService.State.class,
        ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<GarbageInspectionTaskService.State>() {
          @Override
          public void onSuccess(@Nullable GarbageInspectionTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                performGarbageCollection(currentState, serviceId);
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "GarbageInspectionTaskService was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "GarbageInspectionTaskService failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(currentState, t);
          }
        });
  }

  private void performGarbageCollection(final State currentState, final String serviceId) {
    GarbageCollectionTaskService.State startState = new GarbageCollectionTaskService.State();
    startState.serviceId = serviceId;

    TaskUtils.startTaskAsync(
        this,
        GarbageCollectionTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        GarbageCollectionTaskService.State.class,
        ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<GarbageCollectionTaskService.State>() {
          @Override
          public void onSuccess(@Nullable GarbageCollectionTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                expandService(currentState, serviceId);
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "GarbageCollectionTaskService was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "GarbageCollectionTaskService failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(currentState, t);
          }
        });
  }

  private void expandService(final State currentState, final String serviceId) {
    ServiceExpandTask.State startState = new ServiceExpandTask.State();
    startState.serviceId = serviceId;
    startState.batchExpansionSize = currentState.batchExpansionSize;

    TaskUtils.startTaskAsync(
        this,
        ServiceExpandTaskFactory.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ServiceExpandTask.State.class,
        ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<ServiceExpandTask.State>() {
          @Override
          public void onSuccess(@Nullable ServiceExpandTask.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                ServiceState.State servicePatch = new ServiceState.State();
                servicePatch.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
                updateStates(serviceId, servicePatch, buildPatch(TaskState.TaskStage.FINISHED, null),
                    com.vmware.photon.controller.api.model.ServiceState.READY);
                break;
              case CANCELLED:
                IllegalStateException cancelled = new IllegalStateException(String.format(
                    "ServiceExpandTask was canceled. %s", result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
                break;
              case FAILED:
                IllegalStateException failed = new IllegalStateException(String.format(
                    "ServiceExpandTask failed with error %s. %s",
                    result.taskState.failure.message,
                    result.documentSelfLink));
                TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                    buildPatch(TaskState.TaskStage.FAILED, failed));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(currentState, t);
          }
        });
  }

  private void deleteService(State currentState, String serviceId) {

    FutureCallback<ServiceDeleteTaskState> callback = new FutureCallback<ServiceDeleteTaskState>() {
      @Override
      public void onSuccess(@Nullable ServiceDeleteTaskState result) {
        switch (result.taskState.stage) {
          case FINISHED:
            TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                buildPatch(TaskState.TaskStage.FINISHED, null));
            break;
          case CANCELLED:
            IllegalStateException cancelled = new IllegalStateException(String.format(
                "ServiceDeleteTask was canceled. %s", result.documentSelfLink));
            TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                buildPatch(TaskState.TaskStage.CANCELLED, cancelled));
            break;
          case FAILED:
            IllegalStateException failed = new IllegalStateException(String.format(
                "ServiceDeleteTask failed with error %s. %s",
                result.taskState.failure.message,
                result.documentSelfLink));
            TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this,
                buildPatch(TaskState.TaskStage.FAILED, failed));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(currentState, t);
      }
    };

    ServiceDeleteTaskState startState = new ServiceDeleteTaskState();
    startState.serviceId = serviceId;

    TaskUtils.startTaskAsync(
        this,
        ServiceDeleteTaskFactory.SELF_LINK,
        startState,
        (ServiceDeleteTaskState state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ServiceDeleteTaskState.class,
        ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
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

  private void failTask(State currentState, Throwable t) {
    ServiceUtils.logSevere(
        this, "Service Maintenance %d, failed. SelfLink: %s. Error: %s", currentState.maintenanceIteration,
        getSelfLink(), t.toString());
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  // This is a helper class that patch the service document and the current maintainence task.
  // if patch == null, we only need to patch to service Service
  private void updateStates(String serviceId,
                            ServiceState.State servicePatch,
                            State patch, com.vmware.photon.controller.api.model.ServiceState targetState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(getServiceDocumentLink(serviceId))
            .setBody(servicePatch)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    // Ignore the failure. Otherwise if we fail the maintenance task may end up
                    // in a dead loop.
                    ServiceUtils.logSevere(this, "Failed to patch service %s to %s : %s", serviceId,
                        targetState.toString(), throwable.toString());
                  }
                  if (patch != null) {
                    TaskUtils.sendSelfPatch(ServiceMaintenanceTask.this, patch);
                  }
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

  private String getServiceDocumentLink(String serviceId) {
    return ServiceStateFactory.SELF_LINK + "/" + serviceId;
  }

  /**
   * This class represents the document state associated with a
   * {@link ServiceMaintenanceTask} task.
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
     * This value represents an error that the maintenance task has encountered.
     */
    public String error;

    /**
     * The threshold for each expansion batch.
     */
    @DefaultInteger(value = ServicesManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
    @Immutable
    public Integer batchExpansionSize;

    /**
     * This value represents the number of times the maintenance task has been triggered. It will
     * increment by one for each maintenance task.
     */
    @DefaultInteger(value = 0)
    public Integer maintenanceIteration;
  }

  /**
   * This enum represents the operations that the maintenance task should take
   * when entering each maintenance cycle.
   */
  private static enum MaintenanceOperation {
    // RUN means that we run maintenance task in this cycle without any delay.
    RUN,
    // SKIP means that we do not run maintenance task in this cycle.
    SKIP
  }
}
