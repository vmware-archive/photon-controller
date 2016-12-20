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

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.photon.controller.dhcpagent.xenon.DHCPAgentXenonHost;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This class communicates with DHCP server for subnet configuration related operations.
 * This service will be invoked by individual subnet.
 */
public class SubnetConfigurationService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.DHCPAGENT_ROOT + "/subnet-configuration";

  /**
   * This class implements a Xenon micro-service that provides a factory for
   * {@link com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationService} instances.
   */
  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(SubnetConfigurationService.class);
  }

  public SubnetConfigurationService() {
    super(SubnetConfigurationTask.class);

    // The service handles each task of subnet as a single request so there is no need for
    // multiple nodes to have the same information nor a specific node to be the leader of
    // this operation. Persistence is not needed since on a failure subnet configuration will
    // be retried.
    super.toggleOption(ServiceOption.PERSISTENCE, false);
    super.toggleOption(ServiceOption.REPLICATION, false);
    super.toggleOption(ServiceOption.OWNER_SELECTION, false);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    SubnetConfigurationTask startState = startOperation.getBody(SubnetConfigurationTask.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        startOperation.setBody(startState).complete();
        return;
      }

      if (startState.subnetConfiguration.subnetOperation == SubnetConfigurationTask.SubnetOperation.CREATE) {
        handleCreateSubnetConfiguration(startState, startOperation);
      } else if (startState.subnetConfiguration.subnetOperation == SubnetConfigurationTask.SubnetOperation.DELETE) {
        handleDeleteSubnetConfiguration(startState, startOperation);
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, t), t, startOperation);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    SubnetConfigurationTask currentState = getState(patchOperation);
    SubnetConfigurationTask patchState = patchOperation.getBody(SubnetConfigurationTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
        return;
      }

      if (TaskState.TaskStage.STARTED == currentState.taskState.stage
          && SubnetConfigurationTask.SubnetOperation.CREATE == currentState.subnetConfiguration.subnetOperation) {
        handleCreateSubnetConfiguration(currentState, patchOperation);
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage
          && SubnetConfigurationTask.SubnetOperation.DELETE == currentState.subnetConfiguration.subnetOperation) {
        handleDeleteSubnetConfiguration(currentState, patchOperation);
      }
    } catch (Throwable t) {
      failTask(buildPatch(TaskState.TaskStage.FAILED, t), t, null);
    }
  }

  private void handleCreateSubnetConfiguration(SubnetConfigurationTask currentState, Operation operation) {
    try {
      DHCPDriver dhcpDriver = ((DHCPAgentXenonHost) getHost()).getDHCPDriver();

      dhcpDriver.createSubnet(
          currentState.subnetConfiguration.subnetId,
          currentState.subnetConfiguration.subnetGateway,
          currentState.subnetConfiguration.subnetCidr,
          currentState.subnetConfiguration.subnetLowIp,
          currentState.subnetConfiguration.subnetHighIp);

      SubnetConfigurationTask patchState;
      if (dhcpDriver.reload()) {
        patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
      } else {
        patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      }

      if (operation == null) {
        TaskUtils.sendSelfPatch(this, patchState);
      } else {
        operation.setBody(patchState).complete();
      }

    } catch (Throwable ex) {
      SubnetConfigurationTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      failTask(patchState, ex, operation);
    }
  }

  private void handleDeleteSubnetConfiguration(SubnetConfigurationTask currentState, Operation operation) {
    try {
      DHCPDriver dhcpDriver = ((DHCPAgentXenonHost) getHost()).getDHCPDriver();

      dhcpDriver.deleteSubnet(
          currentState.subnetConfiguration.subnetId);

      SubnetConfigurationTask patchState;
      if (dhcpDriver.reload()) {
        patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
      } else {
        patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      }

      if (operation == null) {
        TaskUtils.sendSelfPatch(this, patchState);
      } else {
        operation.setBody(patchState).complete();
      }

    } catch (Throwable ex) {
      SubnetConfigurationTask patchState = buildPatch(TaskState.TaskStage.FAILED, null);
      failTask(patchState, ex, operation);
    }
  }

  private void validateState(SubnetConfigurationTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);

    checkArgument(state.subnetConfiguration.subnetId != null,
        "subnetId field cannot be null");
  }

  private void validatePatchState(SubnetConfigurationTask currentState, SubnetConfigurationTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void failTask(SubnetConfigurationTask state, Throwable t, Operation operation) {
    ServiceUtils.logSevere(this, t);

    if (operation == null) {
      TaskUtils.sendSelfPatch(this, state);
    } else {
      operation.setBody(state).complete();
    }
  }

  protected static SubnetConfigurationTask buildPatch(TaskState.TaskStage patchStage, Throwable t) {
    SubnetConfigurationTask state = new SubnetConfigurationTask();
    state.taskState = new TaskState();
    state.taskState.stage = patchStage;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }
}
