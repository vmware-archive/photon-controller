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

import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest.InstanceRequestType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.net.URI;
import java.util.List;


/**
 * Provision firewall task service.
 */
public class ProvisionFirewallTaskService extends StatefulService {

  /**
   * Substages of the tasks.
   */
  public enum SubStage {
    CREATED,
    PROVISIONING_FIREWALL,
    FINISHED,
    FAILED
  }

  /**
   * Represents state of a firewall task.
   */
  public static class ProvisionFirewallTaskState extends ServiceDocument {
    public InstanceRequestType requestType;

    /**
     * RegionID -- this is needed for AWS auth -- currently
     * RegionID only exists in the ComputeDescription.
     * <p>
     * Including here, so that the provisioning of the network can
     * be completely isolated from provisioning compute
     * <p>
     * Long term all items required for authentication should
     * be encapsulated in the authentication service
     */
    public String regionID;

    /**
     * Link to secrets.  Required
     */
    public String authCredentialsLink;

    /**
     * The pool which this resource is a part of.
     */
    public String resourcePoolLink;

    /**
     * The description of the firewall instance being realized.
     */
    public String firewallDescriptionLink;

    /**
     * The adapter to use to create the firewall.
     */
    public URI firewallServiceReference;

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

      if (this.authCredentialsLink == null || this.authCredentialsLink.isEmpty()) {
        throw new IllegalArgumentException("authCredentialsLink required");
      }

      if (this.resourcePoolLink == null || this.resourcePoolLink.isEmpty()) {
        throw new IllegalArgumentException("resourcePoolLink required");
      }

      if (this.firewallDescriptionLink == null || this.firewallDescriptionLink.isEmpty()) {
        throw new IllegalArgumentException("firewallDescriptionLink required");
      }

      if (this.firewallServiceReference == null) {
        throw new IllegalArgumentException("firewallServiceReference required");
      }

    }
  }

  public ProvisionFirewallTaskService() {
    super(ProvisionFirewallTaskState.class);
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

    ProvisionFirewallTaskState state = start.getBody(ProvisionFirewallTaskState.class);
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

    ProvisionFirewallTaskState currState = getState(patch);
    ProvisionFirewallTaskState patchState = patch.getBody(ProvisionFirewallTaskState.class);

    if (TaskState.isFailed(patchState.taskInfo)) {
      currState.taskInfo = patchState.taskInfo;
    }

    switch (patchState.taskInfo.stage) {
      case CREATED:
        currState.taskSubStage = nextStage(currState);

        handleSubStages(currState);
        logInfo("%s %s on %s started",
            "Firewall",
            currState.requestType.toString(),
            currState.firewallDescriptionLink);
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

  private SubStage nextStage(ProvisionFirewallTaskState state) {
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
      return SubStage.PROVISIONING_FIREWALL;
    } else if (currStage == SubStage.PROVISIONING_FIREWALL) {
      return SubStage.FINISHED;
    } else {
      return SubStage.values()[currStage.ordinal() + 1];
    }
  }

  private void handleSubStages(ProvisionFirewallTaskState currState) {
    switch (currState.taskSubStage) {
      case PROVISIONING_FIREWALL:
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

  private FirewallInstanceRequest toReq(ProvisionFirewallTaskState state) {
    FirewallInstanceRequest req = new FirewallInstanceRequest();
    req.requestType = state.requestType;
    req.authCredentialsLink = state.authCredentialsLink;
    req.resourcePoolLink = state.resourcePoolLink;
    req.firewallReference = UriUtils.buildUri(this.getHost(), state.firewallDescriptionLink);
    req.provisioningTaskReference = this.getUri();
    req.isMockRequest = state.isMockRequest;

    return req;
  }

  private void patchAdapter(ProvisionFirewallTaskState state) {
    FirewallInstanceRequest req = toReq(state);

    sendRequest(Operation.createPatch(state.firewallServiceReference)
        .setBody(req)
        .setCompletion((o, e) -> {
          if (e != null) {
            sendSelfPatch(TaskState.TaskStage.FAILED, e);
          }
        }));
  }

  private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
    ProvisionFirewallTaskState body = new ProvisionFirewallTaskState();
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

  private void sendSelfPatch(ProvisionFirewallTaskState body) {
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
