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

import com.vmware.photon.controller.clustermanager.entities.InactiveVmFactoryService;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmService;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.utils.ExceptionUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a Xenon service representing a task to delete inactive slave vms of a cluster.
 */
public class GarbageCollectionTaskService extends StatefulService {

  public GarbageCollectionTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getInactiveVms(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * Query for inactive vms in a cluster.
   *
   * @param currentState
   */
  private void getInactiveVms(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(InactiveVmService.State.class));
    QueryTask.Query clusterIdClause = new QueryTask.Query()
        .setTermPropertyName("clusterId")
        .setTermMatchValue(currentState.clusterId);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(clusterIdClause);

    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
            List<String> vmIds = new ArrayList<>(documentLinks.size());
            for (String documentLink : documentLinks) {
              vmIds.add(ServiceUtils.getIDFromDocumentSelfLink(documentLink));
            }
            deleteInactiveVms(vmIds);
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(queryPostOperation);
  }

  /**
   * Deletes all inactive vms in the cluster.
   */
  private void deleteInactiveVms(List<String> vmIds) {

    // If there are no vms to delete, set stage to finished.
    if (vmIds.size() == 0) {
      TaskUtils.sendSelfPatch(GarbageCollectionTaskService.this,
          buildPatch(TaskState.TaskStage.FINISHED));
      return;
    }

    final AtomicInteger latch = new AtomicInteger(vmIds.size());
    final Map<String, Throwable> exceptions = new ConcurrentHashMap<>();

    for (final String vmId : vmIds) {
      deleteVm(vmId, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void v) {
          if (0 == latch.decrementAndGet()) {
            if (0 == exceptions.size()) {
              TaskUtils.sendSelfPatch(GarbageCollectionTaskService.this,
                  buildPatch(TaskState.TaskStage.FINISHED));
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
      });
    }
  }

  /**
   * Call VmDeprovisionTask to delete a VM.
   */
  private void deleteVm(String vmId, FutureCallback<Void> callback) {
    try {
      VmDeprovisionTaskService.State vmDeprovisionTask = new VmDeprovisionTaskService.State();
      vmDeprovisionTask.vmId = vmId;

      TaskUtils.startTaskAsync(
          this,
          VmDeprovisionTaskFactoryService.SELF_LINK,
          vmDeprovisionTask,
          state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          VmDeprovisionTaskService.State.class,
          ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
          new FutureCallback<VmDeprovisionTaskService.State>() {
            @Override
            public void onSuccess(@Nullable VmDeprovisionTaskService.State state) {
              if (state.taskState.stage != TaskState.TaskStage.FINISHED) {
                String exceptionMessage = String.format("VmDeprovisionTask did not finish for vm %s.", vmId);
                if (null != state.taskState.failure) {
                  exceptionMessage += String.format(" Failure: %s", state.taskState.failure.message);
                }
                callback.onFailure(new RuntimeException(exceptionMessage));
              } else {
                deleteInactiveVmEntity(vmId, callback);
              }
            }

            @Override
            public void onFailure(Throwable t) {
              callback.onFailure(t);
            }
          });

      VmDeprovisionTaskService.State startState = new VmDeprovisionTaskService.State();
      startState.vmId = vmId;
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  /**
   * Delete an Inactive VM entity.
   */
  private void deleteInactiveVmEntity(String vmId, FutureCallback<Void> callback) {
    String documentLink = InactiveVmFactoryService.SELF_LINK + "/" + vmId;

    try {
      Operation deleteOperation =
          Operation.createDelete(UriUtils.buildUri(getHost(), documentLink))
              .setBody(new ServiceDocument())
              .setCompletion((Operation operation, Throwable throwable) -> {
                if (null != throwable) {
                  callback.onFailure(throwable);
                  return;
                }
                callback.onSuccess(null);
              });
      sendRequest(deleteOperation);
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  private void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private State buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private State buildPatch(
      TaskState.TaskStage stage, @Nullable Throwable t) {
    return buildPatch(stage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private State buildPatch(
      TaskState.TaskStage stage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * This class represents the document state associated with a
   * {@link GarbageCollectionTaskService} task.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * The state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the identifier of the cluster.
     */
    @NotNull
    @Immutable
    public String clusterId;
  }
}
