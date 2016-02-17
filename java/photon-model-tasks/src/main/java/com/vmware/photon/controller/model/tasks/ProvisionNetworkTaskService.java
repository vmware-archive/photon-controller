/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.util.List;

/**
 * Provision network task service.
 */
public class ProvisionNetworkTaskService extends StatefulService {

  /**
   * SubStage.
   */
  public enum SubStage {
    CREATED,
    PROVISIONING_NETWORK,
    FINISHED,
    FAILED
  }

  /**
   * Represent state of a provision task.
   */
  public static class ProvisionNetworkTaskState extends ServiceDocument {

    /**
     * The type of an instance request. Required
     */
    public InstanceRequestType requestType;


    /**
     * The description of the network instance being realized. Required
     */
    public String networkDescriptionLink;

    /**
     * Tracks the task state. Set by run-time.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Tracks the sub stage (creating network or firewall).  Set by the run-time.
     */
    public SubStage taskSubStage;

    /**
     * For testing. If set, the request will not actuate any computes directly but will patch back
     * success.
     */
    public boolean isMockRequest = false;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;

    public void validate() throws Exception {
      if (this.requestType == null) {
        throw new IllegalArgumentException("requestType required");
      }

      if (this.networkDescriptionLink == null || this.networkDescriptionLink.isEmpty()) {
        throw new IllegalArgumentException("networkDescriptionLink required");
      }
    }
  }

  public ProvisionNetworkTaskService() {
    super(ProvisionNetworkTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      start.fail(new IllegalArgumentException("body is required"));
      return;
    }

    ProvisionNetworkTaskState state = start.getBody(ProvisionNetworkTaskState.class);
    try {
      state.validate();
    } catch (Exception e) {
      start.fail(e);
    }

    state.taskInfo.stage = TaskState.TaskStage.CREATED;
    state.taskSubStage = SubStage.CREATED;
    start.complete();

    // start the task
    sendSelfPatch(TaskState.TaskStage.CREATED, null);
  }

  @Override
  public void handlePatch(Operation patch) {
    if (!patch.hasBody()) {
      patch.fail(new IllegalArgumentException("body is required"));
      return;
    }

    ProvisionNetworkTaskState currState = getState(patch);
    ProvisionNetworkTaskState patchState = patch.getBody(ProvisionNetworkTaskState.class);

    if (TaskState.isFailed(patchState.taskInfo)) {
      currState.taskInfo = patchState.taskInfo;
    }

    switch (patchState.taskInfo.stage) {
      case CREATED:
        currState.taskSubStage = nextStage(currState);

        handleSubStages(currState);
        logInfo("%s %s on %s started",
            "Network",
            currState.requestType.toString(),
            currState.networkDescriptionLink);
        break;
      case STARTED:
        currState.taskInfo.stage = TaskState.TaskStage.STARTED;
        break;
      case FINISHED:
        SubStage nextStage = nextStage(currState);
        if (nextStage == SubStage.FINISHED) {
          currState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          logInfo("task is complete");
        } else {
          sendSelfPatch(TaskState.TaskStage.CREATED, null);
        }
        break;
      case FAILED:
        logWarning("Task failed with %s", Utils.toJsonHtml(currState.taskInfo.failure));
        break;
      case CANCELLED:
        break;
      default:
        break;
    }

    patch.complete();
  }

  private SubStage nextStage(ProvisionNetworkTaskState state) {
    return state.requestType == InstanceRequestType.CREATE ? nextSubStageOnCreate(state.taskSubStage)
        :
        nextSubstageOnDelete(state.taskSubStage);
  }

  private SubStage nextSubStageOnCreate(SubStage currStage) {
    return SubStage.values()[currStage.ordinal() + 1];
  }

  // deletes follow the inverse order;
  private SubStage nextSubstageOnDelete(SubStage currStage) {
    if (currStage == SubStage.CREATED) {
      return SubStage.PROVISIONING_NETWORK;
    } else if (currStage == SubStage.PROVISIONING_NETWORK) {
      return SubStage.FINISHED;
    } else {
      return SubStage.values()[currStage.ordinal() + 1];
    }
  }

  private void handleSubStages(ProvisionNetworkTaskState currState) {
    switch (currState.taskSubStage) {
      case PROVISIONING_NETWORK:
        patchAdapter(currState);
        break;
      case FINISHED:
        sendSelfPatch(TaskState.TaskStage.FINISHED, null);
        break;
      case FAILED:
        break;
      default:
        break;
    }
  }

  private NetworkInstanceRequest toReq(NetworkState networkState, ProvisionNetworkTaskState taskState) {
    NetworkInstanceRequest req = new NetworkInstanceRequest();
    req.requestType = taskState.requestType;
    req.authCredentialsLink = networkState.authCredentialsLink;
    req.resourcePoolLink = networkState.resourcePoolLink;
    req.networkReference = UriUtils.buildUri(this.getHost(), taskState.networkDescriptionLink);
    req.provisioningTaskReference = this.getUri();
    req.isMockRequest = taskState.isMockRequest;

    return req;
  }

  private void patchAdapter(ProvisionNetworkTaskState taskState) {
      sendRequest(Operation
              .createGet(UriUtils.buildUri(this.getHost(), taskState.networkDescriptionLink))
              .setTargetReplicated(true)
              .setCompletion((o, e) -> {
                if (e != null) {
                  sendSelfPatch(TaskState.TaskStage.FAILED, e);
                  return;
                }
                NetworkState networkState = o.getBody(NetworkState.class);
                NetworkInstanceRequest req = toReq(networkState, taskState);

                sendRequest(Operation.createPatch(networkState.instanceAdapterReference)
                    .setBody(req)
                    .setCompletion((oo, ee) -> {
                      if (ee != null) {
                        sendSelfPatch(TaskState.TaskStage.FAILED, ee);
                      }
                    }));
              }));
  }

  private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
    ProvisionNetworkTaskState body = new ProvisionNetworkTaskState();
    body.taskInfo = new TaskState();
    if (e == null) {
      body.taskInfo.stage = stage;
    } else {
      body.taskInfo.stage = TaskState.TaskStage.FAILED;
      body.taskInfo.failure = Utils.toServiceErrorResponse(e);
      logWarning("Patching to failed: %s", Utils.toString(e));
    }

    sendSelfPatch(body);
  }

  private void sendSelfPatch(ProvisionNetworkTaskState body) {
    Operation patch = Operation
        .createPatch(getUri())
        .setBody(body)
        .setCompletion(
            (o, ex) -> {
              if (ex != null) {
                logWarning("Self patch failed: %s", Utils.toString(ex));
              }
            });
    sendRequest(patch);
  }
}
