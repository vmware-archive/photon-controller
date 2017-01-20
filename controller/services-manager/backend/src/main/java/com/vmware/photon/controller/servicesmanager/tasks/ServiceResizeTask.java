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
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceResizeTaskState;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
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
 * This class implements a Xenon service representing a task to resize a service.
 */
public class ServiceResizeTask extends StatefulService {

  public ServiceResizeTask() {
    super(ServiceResizeTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ServiceResizeTaskState startState = start.getBody(ServiceResizeTaskState.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == ServiceResizeTaskState.TaskState.TaskStage.CREATED) {
      startState.taskState.stage = ServiceResizeTaskState.TaskState.TaskStage.STARTED;
      startState.taskState.subStage = ServiceResizeTaskState.TaskState.SubStage.INITIALIZE_SERVICE;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ServiceResizeTaskState.TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
    ServiceResizeTaskState currentState = getState(patch);
    ServiceResizeTaskState patchState = patch.getBody(ServiceResizeTaskState.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (ServiceResizeTaskState.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(ServiceResizeTaskState currentState) {
    switch (currentState.taskState.subStage) {
      case INITIALIZE_SERVICE:
        initializeService(currentState);
        break;
      case RESIZE_SERVICE:
        resizeService(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method calculates the delta between the current workers count and the desired workers count,
   * and saves the delta in the current state. On success, the method moves the task sub-stage
   * to RESIZE_SERVICE.
   */
  private void initializeService(
      final ServiceResizeTaskState currentState) {

    Operation.CompletionHandler handler = (Operation operation, Throwable throwable) -> {
      if (null != throwable) {
        failTask(throwable);
        return;
      }

      ServiceState.State serviceDocument = operation.getBody(ServiceState.State.class);

      if (serviceDocument.serviceState != com.vmware.photon.controller.api.model.ServiceState.READY) {
        String errorStr = String.format(
            "Cannot resize service if it is not in the READY state. Current ServiceState: %s",
            serviceDocument.serviceState.toString());

        ServiceUtils.logInfo(ServiceResizeTask.this, errorStr);
        TaskUtils.sendSelfPatch(ServiceResizeTask.this, buildPatch(TaskState.TaskStage.FAILED, null,
            new IllegalStateException(errorStr)));
        return;
      }

      int workerCountDelta = currentState.newWorkerCount - serviceDocument.workerCount;
      if (workerCountDelta < 0) {
        String errorStr = String.format(
            "Reducing service size is not supported. Current service size: %d. New service size: %d.",
            serviceDocument.workerCount,
            currentState.newWorkerCount);

        ServiceUtils.logInfo(ServiceResizeTask.this, errorStr);
        TaskUtils.sendSelfPatch(ServiceResizeTask.this, buildPatch(TaskState.TaskStage.FAILED, null,
            new IllegalStateException(errorStr)));
        return;
      }

      if (workerCountDelta == 0) {
        ServiceUtils.logInfo(ServiceResizeTask.this, "Worker count delta is 0. Skip resizing.");
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        return;
      }

      ServiceResizeTaskState patchState = buildPatch(
          TaskState.TaskStage.STARTED, ServiceResizeTaskState.TaskState.SubStage.RESIZE_SERVICE);

      ServiceState.State servicePatchState = new ServiceState.State();
      servicePatchState.workerCount = currentState.newWorkerCount;
      servicePatchState.serviceState = com.vmware.photon.controller.api.model.ServiceState.RESIZING;

      updateStates(currentState, patchState, servicePatchState);
    };

    getServiceState(currentState, handler);
  }

  /**
   * Performs the resize operation using WorkersNodeRollout. On successful completion, it marks the service as READY
   * and moves the current task to FINISHED state.
   */
  private void resizeService(final ServiceResizeTaskState currentState) {
    // Send a patch to manually trigger the maintenance task for the service.
    // Since the maintenance task is designed to run in background, we start the task async without waiting for its
    // completion so that the resize task can finish immediately.
    ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ServiceMaintenanceTaskFactory.SELF_LINK + "/" + currentState.serviceId))
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

  private void getServiceState(final ServiceResizeTaskState currentState,
                               Operation.CompletionHandler completionHandler) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
            .setCompletion(completionHandler));
  }

  private void updateStates(final ServiceResizeTaskState currentState,
                            final ServiceResizeTaskState patchState,
                            final ServiceState.State servicePatchState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
            .setBody(servicePatchState)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  TaskUtils.sendSelfPatch(ServiceResizeTask.this, patchState);
                }
            ));
  }

  private void validateStartState(ServiceResizeTaskState startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == ServiceResizeTaskState.TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case INITIALIZE_SERVICE:
        case RESIZE_SERVICE:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(ServiceResizeTaskState currentState, ServiceResizeTaskState patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == ServiceResizeTaskState.TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private ServiceResizeTaskState buildPatch(ServiceResizeTaskState.TaskState.TaskStage stage,
                                       ServiceResizeTaskState.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private ServiceResizeTaskState buildPatch(ServiceResizeTaskState.TaskState.TaskStage stage,
                                       ServiceResizeTaskState.TaskState.SubStage subStage,
                                       @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private ServiceResizeTaskState buildPatch(
      ServiceResizeTaskState.TaskState.TaskStage stage,
      ServiceResizeTaskState.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    ServiceResizeTaskState state = new ServiceResizeTaskState();
    state.taskState = new ServiceResizeTaskState.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(ServiceResizeTaskState.TaskState.TaskStage.FAILED, null, e));
  }
}
