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
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterDeleteTask.TaskState;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.utils.ExceptionUtils;
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
 * This class implements a Xenon service representing a task to delete a Kubernetes cluster.
 */
public class ClusterDeleteTaskService extends StatefulService {

  public ClusterDeleteTaskService() {
    super(ClusterDeleteTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ClusterDeleteTask startState = start.getBody(ClusterDeleteTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPDATE_CLUSTER_DOCUMENT;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ClusterDeleteTask.TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
    ClusterDeleteTask currentState = getState(patch);
    ClusterDeleteTask patchState = patch.getBody(ClusterDeleteTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (ClusterDeleteTask.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(ClusterDeleteTask currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case UPDATE_CLUSTER_DOCUMENT:
        updateClusterDocument(currentState);
        break;

      case DELETE_VMS:
        getVms(currentState);
        break;

      case DELETE_CLUSTER_DOCUMENT:
        deleteClusterDocument(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * Updates the cluster document to PENDING_DELETE state.
   *
   * @param currentState
   * @throws Throwable
   */
  private void updateClusterDocument(final ClusterDeleteTask currentState) throws Throwable {

    ClusterService.State patchDocument = new ClusterService.State();
    patchDocument.clusterState = ClusterState.PENDING_DELETE;

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(getClusterDocumentLink(currentState))
            .setBody(patchDocument)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  ClusterDeleteTask patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_VMS);
                  TaskUtils.sendSelfPatch(ClusterDeleteTaskService.this, patchState);
                }
            ));
  }

  /**
   * Gets the identifiers of all the VMs in the cluster.
   *
   * @param currentState
   * @throws Throwable
   */
  private void getVms(final ClusterDeleteTask currentState) throws Throwable {
    ApiClient client = HostUtils.getApiClient(this);

    client.getClusterApi().getVmsInClusterAsync(
        currentState.clusterId,
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
   * Deletes the VMs in the cluster.
   *
   * @param currentState
   */
  private void deleteVms(final ClusterDeleteTask currentState, List<String> vmIds) {

    final AtomicInteger latch = new AtomicInteger(vmIds.size());
    final Map<String, Throwable> exceptions = new ConcurrentHashMap<>();

    // If there are no vms to delete, just move to the next sub-stage
    // of deleting the cluster service.
    if (vmIds.size() == 0) {
      TaskUtils.sendSelfPatch(ClusterDeleteTaskService.this,
          buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_CLUSTER_DOCUMENT));
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
              TaskUtils.sendSelfPatch(ClusterDeleteTaskService.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_CLUSTER_DOCUMENT));
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
          ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
          callback);
    }
  }

  /**
   * Deletes the Cluster service document.
   *
   * @param currentState
   */
  private void deleteClusterDocument(final ClusterDeleteTask currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createDelete(getClusterDocumentLink(currentState))
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }
                  addClusterTombstone(currentState);
                }
            ));
  }

  private void addClusterTombstone(final ClusterDeleteTask currentState) {
    TombstoneService.State state = new TombstoneService.State();
    state.entityId = currentState.clusterId;
    state.entityKind = "cluster";
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
    TaskUtils.sendSelfPatch(this, buildPatch(ClusterDeleteTask.TaskState.TaskStage.FAILED, null, e));
  }

  private void validateStartState(ClusterDeleteTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == ClusterDeleteTask.TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case UPDATE_CLUSTER_DOCUMENT:
        case DELETE_VMS:
        case DELETE_CLUSTER_DOCUMENT:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(ClusterDeleteTask currentState,
                                  ClusterDeleteTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == KubernetesClusterCreateTask.TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private ClusterDeleteTask buildPatch(ClusterDeleteTask.TaskState.TaskStage stage,
                                       ClusterDeleteTask.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private ClusterDeleteTask buildPatch(ClusterDeleteTask.TaskState.TaskStage stage,
                                       ClusterDeleteTask.TaskState.SubStage subStage,
                                       @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private ClusterDeleteTask buildPatch(
      ClusterDeleteTask.TaskState.TaskStage stage,
      ClusterDeleteTask.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    ClusterDeleteTask state = new ClusterDeleteTask();
    state.taskState = new ClusterDeleteTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private String getClusterDocumentLink(ClusterDeleteTask currentState) {
    return ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId;
  }
}
