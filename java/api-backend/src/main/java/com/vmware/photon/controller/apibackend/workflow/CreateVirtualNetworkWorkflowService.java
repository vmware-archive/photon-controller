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

package com.vmware.photon.controller.apibackend.workflow;

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.builders.TaskStateBuilder;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

/**
 * This class implements a Xenon service representing a workflow to create a virtual network.
 */
public class CreateVirtualNetworkWorkflowService extends BaseWorkflowService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-virtual-network";
  public static final String VIRTUAL_NETWORK_ENTITY_KIND = "virtual-network";

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

    try {
      CreateVirtualNetworkWorkflowDocument state = createOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);
      InitializationUtils.initialize(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
      } else if (TaskState.TaskStage.CREATED == state.taskState.stage) {
        createVirtualNetwork(state, createOperation);
      }
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(createOperation)) {
        createOperation.fail(t);
      }
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    try {
      CreateVirtualNetworkWorkflowDocument currentState = getState(patchOperation);
      CreateVirtualNetworkWorkflowDocument patchState =
          patchOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);
      validatePatchState(currentState, patchState);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
      failTask(t);
    }
  }

  private void processPatch(CreateVirtualNetworkWorkflowDocument state) throws Throwable {
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
  }

  /**
   * Create VirtualNetwork entity in cloud store.
   *
   * @param createState
   * @param createOperation
   */
  private void createVirtualNetwork(final CreateVirtualNetworkWorkflowDocument createState, Operation createOperation) {

    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation op, Throwable failure) {
        if (failure != null) {
          RuntimeException e = new RuntimeException(String.format("Failed to create VirtualNetworkEntity %s", failure));
          createOperation.fail(e);
          failTask(e);
          return;
        }
        VirtualNetworkService.State rsp = op.getBody(VirtualNetworkService.State.class);
        createState.virtualNetworkServiceState = rsp;
        createTask(createState, createOperation);
      }
    };

    VirtualNetworkService.State postState = new VirtualNetworkService.State();
    postState.name = createState.name;
    postState.description = createState.description;
    postState.state = NetworkState.CREATING;
    postState.routingType = RoutingType.ROUTED;

    Operation op = Operation
        .createPost(UriUtils.buildUri(getHost(), VirtualNetworkService.FACTORY_LINK))
        .setBody(postState)
        .setCompletion(handler);
    this.sendRequest(op);
  }

  /**
   * Create TaskService entity in cloud store.
   *
   * @param createState
   * @param createOperation
   */
  private void createTask(CreateVirtualNetworkWorkflowDocument createState, Operation createOperation) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation op, Throwable failure) {
        if (failure != null) {
          RuntimeException e = new RuntimeException(String.format("Failed to create TaskEntity %s", failure));
          createOperation.fail(e);
          failTask(e);
          return;
        }
        TaskService.State rsp = op.getBody(TaskService.State.class);
        createState.virtualNetworkTaskServiceState = rsp;
        createOperation.complete();
      }
    };

    String id = ServiceUtils.getIDFromDocumentSelfLink(createState.virtualNetworkServiceState.documentSelfLink);
    Operation op  = Operation
        .createPost(UriUtils.buildUri(getHost(), TaskServiceFactory.SELF_LINK))
        .setBody(buildTask(id))
        .setCompletion(handler);
    this.sendRequest(op);
  }

  private TaskService.State buildTask(String entityId) {
    return new TaskStateBuilder()
        .setEntityId(entityId)
        .setEntityKind(VIRTUAL_NETWORK_ENTITY_KIND)
        .setOperation(com.vmware.photon.controller.api.Operation.CREATE_VIRTUAL_NETWORK)
        .addStep(com.vmware.photon.controller.api.Operation.GET_NSX_CONFIGURATION)
        .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_SWITCH)
        .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_ROUTER)
        .addStep(com.vmware.photon.controller.api.Operation.SET_UP_LOGICAL_ROUTER)
        .build();
  }
}
