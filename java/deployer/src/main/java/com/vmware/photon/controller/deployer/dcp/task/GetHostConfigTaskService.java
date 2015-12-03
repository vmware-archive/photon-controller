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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;
import static jersey.repackaged.com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * This class implements a DCP micro-service which performs the task of retrieving host config
 * from the ESX hypervisor.
 */
public class GetHostConfigTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link GetHostConfigTaskService} instance.
   */
  public static class State extends ServiceDocument {

    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    @NotNull
    @Immutable
    public String hostServiceLink;

    public HostConfig hostConfig;
  }

  public GetHostConfigTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    startOperation.setBody(startState).complete();

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

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getHost(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.FINISHED) {
      checkState(null != currentState.hostConfig, "hostConfig cannot be null in FINISHED stage");
    }
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  private void getHost(final State currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    getHostConfig(completedOp.getBody(HostService.State.class));
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void getHostConfig(final HostService.State hostState) {
    final AsyncMethodCallback<Host.AsyncClient.get_host_config_call> handler =
        new AsyncMethodCallback<Host.AsyncClient.get_host_config_call>() {
          @Override
          public void onComplete(Host.AsyncClient.get_host_config_call getHostConfigCall) {
            try {
              GetConfigResponse response = getHostConfigCall.getResult();
              HostClient.ResponseValidator.checkGetConfigResponse(response);
              saveHostConfig(response.getHostConfig());
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    try {
      HostClient hostClient = HostUtils.getHostClient(this);
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.getHostConfig(handler);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void saveHostConfig(HostConfig hostConfig) {
    GetHostConfigTaskService.State patchState = new GetHostConfigTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.FINISHED;
    patchState.hostConfig = hostConfig;
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    if (patchState.hostConfig != null) {
      startState.hostConfig = patchState.hostConfig;
    }

    return startState;
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch %s", taskStage.toString());
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
