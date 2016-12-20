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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Set;


/**
 * Implements a Xenon service that represents a task to disconnect VM from a switch.
 */
public class DisconnectVmFromSwitchTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/disconnect-vm-switch-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(DisconnectVmFromSwitchTaskService.class, DisconnectVmFromSwitchTask.class);
  }

  public DisconnectVmFromSwitchTaskService() {
    super(DisconnectVmFromSwitchTask.class);

    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      DisconnectVmFromSwitchTask startState = startOperation.getBody(DisconnectVmFromSwitchTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.GET_NSX_CONFIGURATION;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage));
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      DisconnectVmFromSwitchTask currentState = getState(patchOperation);
      DisconnectVmFromSwitchTask patchState = patchOperation.getBody(DisconnectVmFromSwitchTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(DisconnectVmFromSwitchTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case GET_NSX_CONFIGURATION:
          getNsxConfiguration(currentState);
          break;

        case GET_VIRTUAL_NETWORK:
          getVirtualNetwork(currentState);
          break;

        case DISCONNECT_VM_FROM_SWITCH:
          disconnectVmFromSwitch(currentState);
          break;

        case UPDATE_VIRTUAL_NETWORK:
          updateVirtualNetwork(currentState);
          break;

        default:
          throw new RuntimeException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getNsxConfiguration(DisconnectVmFromSwitchTask state) {
    ServiceUtils.logInfo(this, "Finding the deployment information");

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(queryTask)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
          Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
          if (documentLinks.size() != 1) {
            failTask(new IllegalStateException(
                String.format("Found %d deployment service(s).", documentLinks.size())));
          }

          getNsxConfiguration(state, documentLinks.iterator().next());
        })
        .sendWith(this);
  }

  private void getNsxConfiguration(DisconnectVmFromSwitchTask state,
                                   String deploymentServiceStateLink) {

    ServiceUtils.logInfo(this, "Getting the credentials to connect to the NSX manager");

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(deploymentServiceStateLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            DeploymentService.State deploymentState = op.getBody(DeploymentService.State.class);
            DisconnectVmFromSwitchTask patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                TaskState.SubStage.GET_VIRTUAL_NETWORK);
            patchState.nsxAddress = deploymentState.networkManagerAddress;
            patchState.nsxUsername = deploymentState.networkManagerUsername;
            patchState.nsxPassword = deploymentState.networkManagerPassword;

            TaskUtils.sendSelfPatch(DisconnectVmFromSwitchTaskService.this, patchState);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getVirtualNetwork(DisconnectVmFromSwitchTask currentState) {
    ServiceUtils.logInfo(this, "Getting the current state of virtual network %s", currentState.networkId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + currentState.networkId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            VirtualNetworkService.State virtualNetwork = op.getBody(VirtualNetworkService.State.class);
            DisconnectVmFromSwitchTask patch = buildPatch(
                TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH);
            patch.logicalSwitchDownlinkPortIds = virtualNetwork.logicalSwitchDownlinkPortIds;
            patch.logicalSwitchId = virtualNetwork.logicalSwitchId;

            TaskUtils.sendSelfPatch(DisconnectVmFromSwitchTaskService.this, patch);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void disconnectVmFromSwitch(DisconnectVmFromSwitchTask currentState) {
    ServiceUtils.logInfo(this, "Disconnecting VM %s from logical switch %s", currentState.vmId,
        currentState.logicalSwitchId);

    try {
      if (currentState.logicalSwitchDownlinkPortIds == null ||
          !currentState.logicalSwitchDownlinkPortIds.containsKey(currentState.vmId)) {
        throw new IllegalStateException("No connecting port was found on logical switch " +
            currentState.logicalSwitchId + " for vm " + currentState.vmId);
      }

      String toVmPortId = currentState.logicalSwitchDownlinkPortIds.get(currentState.vmId);

      LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
          currentState.nsxUsername, currentState.nsxPassword).getLogicalSwitchApi();

      logicalSwitchApi.deleteLogicalPort(toVmPortId, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          DisconnectVmFromSwitchTask patch = buildPatch(TaskState.TaskStage.STARTED,
              TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
          TaskUtils.sendSelfPatch(DisconnectVmFromSwitchTaskService.this, patch);
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      }, true);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void updateVirtualNetwork(DisconnectVmFromSwitchTask currentState) {
    currentState.logicalSwitchDownlinkPortIds.remove(currentState.vmId);
    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
    virtualNetwork.logicalSwitchDownlinkPortIds = currentState.logicalSwitchDownlinkPortIds;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(VirtualNetworkService.FACTORY_LINK + "/" + currentState.networkId)
        .setBody(virtualNetwork)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            DisconnectVmFromSwitchTask patch = buildPatch(TaskState.TaskStage.FINISHED);
            TaskUtils.sendSelfPatch(DisconnectVmFromSwitchTaskService.this, patch);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void validateStartState(DisconnectVmFromSwitchTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != DisconnectVmFromSwitchTask.TaskState.TaskStage.STARTED);
  }

  private void validateState(DisconnectVmFromSwitchTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(DisconnectVmFromSwitchTask currentState, DisconnectVmFromSwitchTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == ConnectVmToSwitchTask.TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
    }
  }

  private void validateTaskSubStageProgression(TaskState startState, TaskState patchState) {
    if (patchState.stage.ordinal() > ConnectVmToSwitchTask.TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == TaskState.TaskStage.FINISHED) {
      checkState(startState.subStage == TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
    } else if (patchState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.subStage.ordinal() + 1 == patchState.subStage.ordinal()
          || startState.subStage.ordinal() == patchState.subStage.ordinal());
    }
  }

  private DisconnectVmFromSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private DisconnectVmFromSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private DisconnectVmFromSwitchTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private DisconnectVmFromSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    DisconnectVmFromSwitchTask state = new DisconnectVmFromSwitchTask();
    state.taskState = new DisconnectVmFromSwitchTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      DisconnectVmFromSwitchTask patchState = buildPatch(TaskState.TaskStage.FAILED, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + e.toString());
    }
  }
}
