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
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.builders.LogicalPortCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortAttachment;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements an Xenon service that represents a task to connect VM with a switch.
 */
public class ConnectVmToSwitchTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/connect-vm-switch-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(ConnectVmToSwitchTaskService.class, ConnectVmToSwitchTask.class);
  }

  public ConnectVmToSwitchTaskService() {
    super(ConnectVmToSwitchTask.class);

    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      ConnectVmToSwitchTask startState = startOperation.getBody(ConnectVmToSwitchTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.CONNECT_VM_TO_SWITCH;
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
      ConnectVmToSwitchTask currentState = getState(patchOperation);
      ConnectVmToSwitchTask patchState = patchOperation.getBody(ConnectVmToSwitchTask.class);

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

  public void processPatch(ConnectVmToSwitchTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case CONNECT_VM_TO_SWITCH:
          connectVmToSwitch(currentState);
          break;

        case UPDATE_VIRTUAL_NETWORK:
          getVirtualNetwork(currentState);
          break;

        default:
          throw new RuntimeException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void connectVmToSwitch(ConnectVmToSwitchTask currentState) {
    ServiceUtils.logInfo(this, "Connecting VM %s to logical switch %s", currentState.vmLocationId,
        currentState.logicalSwitchId);

    try {
      LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
          currentState.username, currentState.password).getLogicalSwitchApi();

      LogicalPortAttachment logicalPortAttachment = new LogicalPortAttachment();
      logicalPortAttachment.setId(currentState.vmLocationId);
      logicalPortAttachment.setAttachmentType(NsxSwitch.AttachmentType.VIF);

      LogicalPortCreateSpec spec = new LogicalPortCreateSpecBuilder()
          .logicalSwitchId(currentState.logicalSwitchId)
          .displayName(currentState.toVmPortDisplayName)
          .adminState(NsxSwitch.AdminState.UP)
          .attachment(logicalPortAttachment)
          .build();

      logicalSwitchApi.createLogicalPort(spec,
          new FutureCallback<LogicalPort>() {
            @Override
            public void onSuccess(@Nullable LogicalPort result) {
              ConnectVmToSwitchTask patch = buildPatch(TaskState.TaskStage.STARTED,
                  TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
              patch.toVmPortId = result.getId();

              TaskUtils.sendSelfPatch(ConnectVmToSwitchTaskService.this, patch);
            }

            @Override
            public void onFailure(Throwable t) {
              failTask(t);
            }
          }
      );
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getVirtualNetwork(ConnectVmToSwitchTask currentState) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + currentState.networkId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          VirtualNetworkService.State virtualNetwork = op.getBody(VirtualNetworkService.State.class);
          updateVirtualNetwork(virtualNetwork.documentSelfLink,
              virtualNetwork.logicalSwitchDownlinkPortIds,
              currentState);
        })
        .sendWith(this);
  }

  private void updateVirtualNetwork(String virtualNetworkSelfLink,
                                    Map<String, String> currLogicalSwitchDownlinkPortIds,
                                    ConnectVmToSwitchTask currentState) {

    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();

    if (currLogicalSwitchDownlinkPortIds == null) {
      virtualNetwork.logicalSwitchDownlinkPortIds = new HashMap<>();
    } else {
      virtualNetwork.logicalSwitchDownlinkPortIds = new HashMap<>(currLogicalSwitchDownlinkPortIds);
    }
    virtualNetwork.logicalSwitchDownlinkPortIds.put(currentState.vmId, currentState.toVmPortId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(virtualNetworkSelfLink)
        .setBody(virtualNetwork)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            ConnectVmToSwitchTask patch = buildPatch(TaskState.TaskStage.FINISHED);
            TaskUtils.sendSelfPatch(ConnectVmToSwitchTaskService.this, patch);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void validateStartState(ConnectVmToSwitchTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != TaskState.TaskStage.STARTED);
  }

  private void validateState(ConnectVmToSwitchTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(ConnectVmToSwitchTask currentState, ConnectVmToSwitchTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
    }
  }

  private void validateTaskSubStageProgression(TaskState startState, TaskState patchState) {
    if (patchState.stage.ordinal() > TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == TaskState.TaskStage.FINISHED) {
      checkState(startState.subStage == TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
    } else if (patchState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.subStage.ordinal() + 1 == patchState.subStage.ordinal()
              || startState.subStage.ordinal() == patchState.subStage.ordinal());
    }
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    ConnectVmToSwitchTask state = new ConnectVmToSwitchTask();
    state.taskState = new ConnectVmToSwitchTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      ConnectVmToSwitchTask patchState = buildPatch(TaskState.TaskStage.FAILED, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + e.toString());
    }
  }
}
