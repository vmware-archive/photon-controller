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

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerFactoryProvider;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusChecker;
import com.vmware.photon.controller.clustermanager.statuschecks.WorkersStatusChecker;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a task service which waits for a VM to acquire an IP address.
 */
public class ClusterWaitTaskService extends StatefulService {

  public ClusterWaitTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage patchStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.failure = (null != t) ? Utils.toServiceErrorResponse(t) : null;
    return patchState;
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
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
        getNodeAddresses(startState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getNodeAddresses(final State currentState) throws IOException {

    FutureCallback<Boolean> callback = new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean isReady) {
        try {
          processTask(currentState, isReady);
        } catch (Throwable t) {
          failTask(t);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    switch (currentState.nodeType) {
      case KubernetesWorker:
      case MesosWorker:
      case SwarmWorker:
        Preconditions.checkNotNull(currentState.nodeAddresses, "nodeAddresses should not be null");
        Preconditions.checkArgument(currentState.nodeAddresses.size() > 0, "nodeAddresses should not be empty");

        WorkersStatusChecker workersStatusChecker = getStatusCheckHelper()
            .createWorkersStatusChecker(this, currentState.nodeType);
        workersStatusChecker.checkWorkersStatus(currentState.serverAddress, currentState.nodeAddresses, callback);
        break;

      case KubernetesEtcd:
      case KubernetesMaster:
      case MesosZookeeper:
      case MesosMaster:
      case MesosMarathon:
      case SwarmEtcd:
      case SwarmMaster:
        StatusChecker statusChecker = getStatusCheckHelper()
            .createStatusChecker(this, currentState.nodeType);
        statusChecker.checkNodeStatus(currentState.serverAddress, callback);
        break;

      default:
        failTask(new UnsupportedOperationException(
            "NodeType is not supported. NodeType: " + currentState.nodeType));
    }
  }

  private void processTask(final State currentState, Boolean isReady) {
    if (!isReady) {

      if (currentState.apiCallPollIterations >= currentState.maxApiCallPollIterations) {
        failTask(new IllegalStateException("Wait period expired"));
        return;
      }

      getHost().schedule(
          () -> {
            State patchState = buildPatch(TaskState.TaskStage.STARTED, null);
            patchState.apiCallPollIterations = currentState.apiCallPollIterations + 1;
            sendSelfPatch(patchState);
          },
          currentState.apiCallPollDelay,
          TimeUnit.MILLISECONDS);

    } else {
      sendStageProgressPatch(TaskState.TaskStage.FINISHED);
    }
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
    sendSelfPatch(buildPatch(patchStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void sendSelfPatch(State patchState) {
    final Service service = this;
    sendRequest(Operation
        .createPatch(this, getSelfLink())
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            ServiceUtils.logWarning(service, "Failed to send self-patch: %s", throwable.toString());
          }
        }));
  }

  @VisibleForTesting
  protected StatusCheckHelper getStatusCheckHelper() {
    PhotonControllerXenonHost photonControllerXenonHost = (PhotonControllerXenonHost) getHost();
    ClusterManagerFactory clusterManagerFactory =
        ((ClusterManagerFactoryProvider) photonControllerXenonHost.getDeployer()).getClusterManagerFactory();
    return clusterManagerFactory.createStatusCheckHelper();
  }

  /**
   * This class represents the document state associated with a {@link ClusterWaitTaskService} task.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the node type that the task will wait for.
     */
    @NotNull
    @Immutable
    public NodeType nodeType;

    /**
     * This value represents the server address to query the status of the node type.
     */
    @NotNull
    @Immutable
    public String serverAddress;

    /**
     * This value represents the addresses of the nodes whose status will be queried by the task.
     */
    @Immutable
    public List<String> nodeAddresses;

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
  }
}
