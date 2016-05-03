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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

/**
 * This class implements a Xenon service representing a workflow to create a virtual network.
 */
public class CreateVirtualNetworkWorkflowService extends BaseWorkflowService<CreateVirtualNetworkWorkflowDocument,
    CreateVirtualNetworkWorkflowDocument.TaskState, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-virtual-network";

  public static FactoryService createFactory() {
    return FactoryService.create(CreateVirtualNetworkWorkflowService.class, CreateVirtualNetworkWorkflowDocument.class);
  }

  public CreateVirtualNetworkWorkflowService() {
    super(CreateVirtualNetworkWorkflowDocument.class,
        CreateVirtualNetworkWorkflowDocument.TaskState.class,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    CreateVirtualNetworkWorkflowDocument state =
        createOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      createVirtualNetwork(state, createOperation);
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(createOperation)) {
        createOperation.fail(t);
      }
      fail(state, t);
    }
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    CreateVirtualNetworkWorkflowDocument state =
        startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);

    try {
      initializeState(state);
      validateStartState(state);

      startOperation.setBody(state).complete();

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleStartDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled");
        return;
      }

      start(state);
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
      fail(state, t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    CreateVirtualNetworkWorkflowDocument currentState = getState(patchOperation);

    try {
      CreateVirtualNetworkWorkflowDocument patchState =
          patchOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);
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
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
      fail(currentState, t);
    }
  }

  private void processPatch(CreateVirtualNetworkWorkflowDocument state) throws Throwable {
    switch(state.taskState.subStage) {
      case GET_NSX_CONFIGURATION:
        progress(state, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH);
        break;
      case CREATE_LOGICAL_SWITCH:
        progress(state, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER);
        break;
      case CREATE_LOGICAL_ROUTER:
        progress(state, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER);
        break;
      case SET_UP_LOGICAL_ROUTER:
        finish(state);
        break;
    }
  }

  /**
   * Create VirtualNetwork entity in cloud store.
   */
  private void createVirtualNetwork(
      CreateVirtualNetworkWorkflowDocument state,
      Operation operation) {

    VirtualNetworkService.State postState = new VirtualNetworkService.State();
    postState.name = state.name;
    postState.description = state.description;
    postState.state = NetworkState.CREATING;
    postState.routingType = RoutingType.ROUTED;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPost(VirtualNetworkService.FACTORY_LINK)
        .setBody(postState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            operation.fail(ex);
            fail(state, ex);
            return;
          }

          state.taskServiceEntity = op.getBody(VirtualNetworkService.State.class);
          create(state, operation);
        })
        .sendWith(this);
  }
}
