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
import com.vmware.photon.controller.apibackend.builders.TaskStateBuilder;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;

/**
 * This class implements a Xenon service representing a workflow to create a virtual network.
 */
public class CreateVirtualNetworkWorkflowService extends BaseWorkflowService<CreateVirtualNetworkWorkflowDocument,
    CreateVirtualNetworkWorkflowDocument.TaskState, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage> {

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

  protected TaskService.State buildTaskServiceStartState(CreateVirtualNetworkWorkflowDocument document) {
    return new TaskStateBuilder()
        .setEntityId(ServiceUtils.getIDFromDocumentSelfLink(document.virtualNetworkServiceState.documentSelfLink))
        .setEntityKind(VIRTUAL_NETWORK_ENTITY_KIND)
        .setOperation(com.vmware.photon.controller.api.Operation.CREATE_VIRTUAL_NETWORK)
        .addStep(com.vmware.photon.controller.api.Operation.GET_NSX_CONFIGURATION)
        .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_SWITCH)
        .addStep(com.vmware.photon.controller.api.Operation.CREATE_LOGICAL_ROUTER)
        .addStep(com.vmware.photon.controller.api.Operation.SET_UP_LOGICAL_ROUTER)
        .build();
  }

  @Override
  protected void handleCreateHook(CreateVirtualNetworkWorkflowDocument createState,
                                  Operation createOperation) throws Throwable {
    VirtualNetworkService.State postState = new VirtualNetworkService.State();
    postState.name = createState.name;
    postState.description = createState.description;
    postState.state = NetworkState.CREATING;
    postState.routingType = RoutingType.ROUTED;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPost(VirtualNetworkService.FACTORY_LINK)
        .setBody(postState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            RuntimeException e = new RuntimeException(String.format("Failed to create VirtualNetworkEntity %s", ex));
            createOperation.fail(e);
            failTask(e);
            return;
          }

          try {
            createState.virtualNetworkServiceState = op.getBody(VirtualNetworkService.State.class);
            super.handleCreateHook(createState, createOperation);
          } catch (Throwable t) {
            RuntimeException e = new RuntimeException(String.format("Failed to create VirtualNetworkEntity %s", t));
            createOperation.fail(e);
            failTask(e);
          }
        })
        .sendWith(this);
  }
}
