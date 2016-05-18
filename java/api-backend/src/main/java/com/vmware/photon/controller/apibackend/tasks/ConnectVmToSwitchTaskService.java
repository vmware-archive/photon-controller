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
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortAttachment;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpecBuilder;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

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
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
      failTask(t);
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
        connectVmToSwitch(currentState);
      }
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
      failTask(t);
    }
  }

  private void connectVmToSwitch(ConnectVmToSwitchTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Connecting VM %s to logical switch %s", currentState.vmLocationId,
        currentState.logicalSwitchId);

    LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalSwitchApi();

    LogicalPortAttachment logicalPortAttachment = new LogicalPortAttachment();
    logicalPortAttachment.setId(currentState.vmLocationId);
    logicalPortAttachment.setAttachmentType(NsxSwitch.AttachmentType.VIF);

    LogicalPortCreateSpec spec =  new LogicalPortCreateSpecBuilder()
        .logicalSwitchId(currentState.logicalSwitchId)
        .displayName(currentState.toVmPortDisplayName)
        .adminState(NsxSwitch.AdminState.UP)
        .attachment(logicalPortAttachment)
        .build();

    logicalSwitchApi.createLogicalPort(spec,
        new FutureCallback<LogicalPort>() {
          @Override
          public void onSuccess(@Nullable LogicalPort result) {
            ConnectVmToSwitchTask patch = buildPatch(TaskState.TaskStage.FINISHED, null);
            patch.toVmPortId = result.getId();

            TaskUtils.sendSelfPatch(ConnectVmToSwitchTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
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
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null);
  }

  private ConnectVmToSwitchTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    ConnectVmToSwitchTask state = new ConnectVmToSwitchTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
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
