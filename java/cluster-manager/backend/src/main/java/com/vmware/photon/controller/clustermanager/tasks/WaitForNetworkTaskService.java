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

import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.utils.ApiUtils;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
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
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a task service which waits for a VM to acquire an IP address.
 */
public class WaitForNetworkTaskService extends StatefulService {

  public WaitForNetworkTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY;
    }

    validateState(startState);

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
        sendStageProgressPatch(TaskState.TaskStage.STARTED);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch operation for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    PatchUtils.patchState(startState, patchState);
    validateState(startState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        callGetNetworks(startState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void callGetNetworks(final State currentState) throws IOException {

    HostUtils.getApiClient(this).getVmApi().getNetworksAsync(currentState.vmId,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task task) {
            try {
              processTask(currentState, task);
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

  private void processTask(final State currentState, Task task) {

    ApiUtils.pollTaskAsync(task,
        HostUtils.getApiClient(this),
        this,
        currentState.taskPollDelay,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task task) {
            try {
              processVmNetworks(currentState, task);
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

  private void processVmNetworks(final State currentState, Task task) throws IOException {
    VmNetworks vmNetworks = VmApi.parseVmNetworksFromTask(task);
    ServiceUtils.logInfo(this, "Received VM networks response: " + Utils.toJson(false, true, vmNetworks));
    for (NetworkConnection networkConnection : vmNetworks.getNetworkConnections()) {
      if (null != networkConnection.getNetwork() && null != networkConnection.getIpAddress()) {
        State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
        patchState.vmIpAddress = networkConnection.getIpAddress();
        TaskUtils.sendSelfPatch(this, patchState);
        return;
      }
    }

    if (currentState.apiCallPollIterations >= currentState.maxApiCallPollIterations) {
      failTask(new IllegalStateException("VM failed to acquire an IP address"));
      return;
    }

    final Service service = this;

    getHost().schedule(new Runnable() {
      @Override
      public void run() {
        State patchState = buildPatch(TaskState.TaskStage.STARTED, null);
        patchState.apiCallPollIterations = currentState.apiCallPollIterations + 1;
        TaskUtils.sendSelfPatch(service, patchState);
      }
    }, currentState.apiCallPollDelay, TimeUnit.MILLISECONDS);
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != patchState.apiCallPollIterations) {
      checkState(patchState.apiCallPollIterations > startState.apiCallPollIterations);
    }
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", patchStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage patchStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.failure = (null != t) ? Utils.toServiceErrorResponse(t) : null;
    return patchState;
  }

  /**
   * This class represents the document state associated with a {@link WaitForNetworkTaskService} task.
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
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the ID of the VM for which to await network connectivity.
     */
    @NotNull
    @Immutable
    public String vmId;

    /**
     * This value represents the number of "get networks" API call cycles which have been performed.
     */
    @DefaultInteger(value = 0)
    public Integer apiCallPollIterations;

    /**
     * This value represents the number of polling iterations to perform before giving up.
     */
    @DefaultInteger(value = 120)
    @Positive
    @Immutable
    public Integer maxApiCallPollIterations;

    /**
     * This value represents the delay interval to use between the completion of one "get networks" API call cycle and
     * the beginning of another.
     */
    @DefaultInteger(value = 5000)
    @Positive
    @Immutable
    public Integer apiCallPollDelay;

    /**
     * This value represents the delay interval to use when polling the API-FE task returned by the "get networks" API
     * call.
     */
    @Positive
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the IP Address of the VM.
     */
    @WriteOnce
    public String vmIpAddress;
  }
}
