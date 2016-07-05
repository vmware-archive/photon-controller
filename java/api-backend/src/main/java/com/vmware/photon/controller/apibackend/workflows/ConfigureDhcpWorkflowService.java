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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.apibackend.exceptions.ConfigureDhcpException;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

/**
 * Implements an Xenon service that represents a workflow to configure the DHCP service for logical networks.
 */
public class ConfigureDhcpWorkflowService extends BaseWorkflowService<ConfigureDhcpWorkflowDocument,
    ConfigureDhcpWorkflowDocument.TaskState, ConfigureDhcpWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-dhcp";

  public static FactoryService createFactory() {
    return FactoryService.create(ConfigureDhcpWorkflowService.class, ConfigureDhcpWorkflowDocument.class);
  }

  public ConfigureDhcpWorkflowService() {
    super(ConfigureDhcpWorkflowDocument.class,
        ConfigureDhcpWorkflowDocument.TaskState.class,
        ConfigureDhcpWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    ConfigureDhcpWorkflowDocument state = createOperation.getBody(ConfigureDhcpWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      getDeployment(state, createOperation);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(createOperation)) {
        createOperation.fail(t);
      }
    }
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ConfigureDhcpWorkflowDocument state = startOperation.getBody(ConfigureDhcpWorkflowDocument.class);

    try {
      initializeState(state);
      validateStartState(state);

      startOperation.setBody(state).complete();

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleStartDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        return;
      }

      start(state);
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
      ConfigureDhcpWorkflowDocument currentState = getState(patchOperation);
      ConfigureDhcpWorkflowDocument patchState = patchOperation.getBody(ConfigureDhcpWorkflowDocument.class);
      validatePatchState(currentState, patchState);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags) ||
          ControlFlags.isHandlePatchDisabled(currentState.controlFlags) ||
          TaskState.TaskStage.STARTED != currentState.taskState.stage) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
        return;
      }

      processPatch(currentState);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(ConfigureDhcpWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case CREATE_DHCP_RELAY_PROFILE:
          progress(state, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE);
          break;
        case CREATE_DHCP_RELAY_SERVICE:
          finish(state);
          break;
        default:
          throw new ConfigureDhcpException("Invalid task substage " + state.taskState.subStage);
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Gets the DeploymentService.State entity in cloud-store.
   */
  private void getDeployment(
      ConfigureDhcpWorkflowDocument state,
      Operation operation) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(DeploymentServiceFactory.SELF_LINK + "/" + state.deploymentId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            operation.fail(ex);
            fail(state, ex);
            return;
          }

          state.taskServiceEntity = op.getBody(DeploymentService.State.class);
          create(state, operation);
        })
        .sendWith(this);
  }
}
