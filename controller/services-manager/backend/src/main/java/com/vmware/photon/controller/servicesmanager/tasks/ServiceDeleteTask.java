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

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState.TaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.utils.ExceptionUtils;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a Xenon service representing a task to delete a service.
 */
public class ServiceDeleteTask extends StatefulService {

  public ServiceDeleteTask() {
    super(ServiceDeleteTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ServiceDeleteTaskState startState = start.getBody(ServiceDeleteTaskState.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPDATE_SERVICE_DOCUMENT;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ServiceDeleteTaskState.TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
    ServiceDeleteTaskState currentState = getState(patch);
    ServiceDeleteTaskState patchState = patch.getBody(ServiceDeleteTaskState.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (ServiceDeleteTaskState.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(ServiceDeleteTaskState currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case UPDATE_SERVICE_DOCUMENT:
        updateServiceDocument(currentState);
        break;

      case DELETE_VMS:
        getVms(currentState);
        break;

      case DELETE_SERVICE_DOCUMENT:
        deleteServiceDocument(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * Updates the service document to PENDING_DELETE state.
   *
   * @param currentState
   * @throws Throwable
   */
  private void updateServiceDocument(final ServiceDeleteTaskState currentState) throws Throwable {

    ServiceState.State patchDocument = new ServiceState.State();
    patchDocument.serviceState = com.vmware.photon.controller.api.model.ServiceState.PENDING_DELETE;

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(getServiceDocumentLink(currentState))
            .setBody(patchDocument)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  ServiceDeleteTaskState patchState =
                      buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_VMS);
                  TaskUtils.sendSelfPatch(ServiceDeleteTask.this, patchState);
                }
            ));
  }

  /**
   * Gets the identifiers of all the VMs in the service.
   *
   * @param currentState
   * @throws Throwable
   */
  private void getVms(final ServiceDeleteTaskState currentState) throws Throwable {
    ApiClient client = HostUtils.getApiClient(this);

    client.getServiceApi().getVmsInServiceAsync(
        currentState.serviceId,
        new FutureCallback<ResourceList<Vm>>() {
          @Override
          public void onSuccess(@Nullable ResourceList<Vm> result) {
            List<String> vmIds = new ArrayList<>();
            for (Vm vm : result.getItems()) {
              vmIds.add(vm.getId());
            }

            deleteVms(currentState, vmIds);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  /**
   * Deletes the VMs in the service.
   *
   * @param currentState
   */
  private void deleteVms(final ServiceDeleteTaskState currentState, List<String> vmIds) {

    final AtomicInteger latch = new AtomicInteger(vmIds.size());
    final Map<String, Throwable> exceptions = new ConcurrentHashMap<>();

    // If there are no vms to delete, just move to the next sub-stage
    // of deleting the service service.
    if (vmIds.size() == 0) {
      TaskUtils.sendSelfPatch(ServiceDeleteTask.this,
          buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SERVICE_DOCUMENT));
      return;
    }

    for (final String vmId : vmIds) {
      FutureCallback<VmDeprovisionTaskService.State> callback = new FutureCallback<VmDeprovisionTaskService.State>() {
        @Override
        public void onSuccess(@Nullable VmDeprovisionTaskService.State state) {
          if (state.taskState.stage != TaskState.TaskStage.FINISHED) {
            String exceptionMessage = String.format("VmDeprovisionTaskService.State did not finish for vm %s.", vmId);
            if (null != state.taskState.failure) {
              exceptionMessage += String.format(" Failure: %s", state.taskState.failure.message);
            }
            exceptions.put(vmId,
                new RuntimeException(exceptionMessage));
          }

          if (0 == latch.decrementAndGet()) {
            if (0 == exceptions.size()) {
              TaskUtils.sendSelfPatch(ServiceDeleteTask.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SERVICE_DOCUMENT));
            } else {
              failTask(ExceptionUtils.createMultiException(exceptions.values()));
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          exceptions.put(vmId, t);
          if (0 == latch.decrementAndGet()) {
            failTask(ExceptionUtils.createMultiException(exceptions.values()));
          }
        }
      };

      VmDeprovisionTaskService.State startState = new VmDeprovisionTaskService.State();
      startState.vmId = vmId;

      TaskUtils.startTaskAsync(
          this,
          VmDeprovisionTaskFactoryService.SELF_LINK,
          startState,
          state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          VmDeprovisionTaskService.State.class,
          ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
          callback);
    }
  }

  /**
   * Deletes the Service service document.
   *
   * @param currentState
   */
  private void deleteServiceDocument(final ServiceDeleteTaskState currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createDelete(getServiceDocumentLink(currentState))
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }
                  addServiceTombstone(currentState);
                }
            ));
  }

  private void addServiceTombstone(final ServiceDeleteTaskState currentState) {
    TombstoneService.State state = new TombstoneService.State();
    state.entityId = currentState.serviceId;
    state.entityKind = "service";
    state.tombstoneTime = System.currentTimeMillis();

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(TombstoneServiceFactory.SELF_LINK)
        .setBody(state)
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
          } else {
            TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
          }
        }));
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(ServiceDeleteTaskState.TaskState.TaskStage.FAILED, null, e));
  }

  private void validateStartState(ServiceDeleteTaskState startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == ServiceDeleteTaskState.TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case UPDATE_SERVICE_DOCUMENT:
        case DELETE_VMS:
        case DELETE_SERVICE_DOCUMENT:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(ServiceDeleteTaskState currentState,
                                  ServiceDeleteTaskState patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private ServiceDeleteTaskState buildPatch(ServiceDeleteTaskState.TaskState.TaskStage stage,
                                       ServiceDeleteTaskState.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private ServiceDeleteTaskState buildPatch(ServiceDeleteTaskState.TaskState.TaskStage stage,
                                       ServiceDeleteTaskState.TaskState.SubStage subStage,
                                       @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private ServiceDeleteTaskState buildPatch(
      ServiceDeleteTaskState.TaskState.TaskStage stage,
      ServiceDeleteTaskState.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    ServiceDeleteTaskState state = new ServiceDeleteTaskState();
    state.taskState = new ServiceDeleteTaskState.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private String getServiceDocumentLink(ServiceDeleteTaskState currentState) {
    return ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId;
  }
}
