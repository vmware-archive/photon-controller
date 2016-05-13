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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.backends.utils.TaskUtils;

import com.google.inject.Inject;

/**
 * Frontend client for virtual network related operations.
 */
public class VirtualNetworkFeClient {

  // We host api-backend in the Housekeeper service.
  private final HousekeeperXenonRestClient backendClient;

  @Inject
  public VirtualNetworkFeClient(HousekeeperXenonRestClient housekeeperClient) {
    this.backendClient = housekeeperClient;
    this.backendClient.start();
  }

  /**
   * Creates a virtual network by the given creation spec.
   */
  public Task create(String projectId,
                     VirtualNetworkCreateSpec spec) throws ExternalException {
    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.parentId = projectId;
    startState.parentKind = Project.KIND;
    startState.name = spec.getName();
    startState.description = spec.getDescription();
    startState.routingType = spec.getRoutingType();

    CreateVirtualNetworkWorkflowDocument finalState = backendClient.post(
        CreateVirtualNetworkWorkflowService.FACTORY_LINK,
        startState).getBody(CreateVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }

  /**
   * Delete the given virtual network.
   *
   * @param networkId
   * @return
   * @throws ExternalException
   */
  public Task delete(String networkId) throws ExternalException {
    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.virtualNetworkId = networkId;

    DeleteVirtualNetworkWorkflowDocument finalState = backendClient
        .post(DeleteVirtualNetworkWorkflowService.FACTORY_LINK, startState)
        .getBody(DeleteVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }
}
