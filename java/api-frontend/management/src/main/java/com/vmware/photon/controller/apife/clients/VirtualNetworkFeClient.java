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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.backends.utils.TaskUtils;
import com.vmware.photon.controller.apife.backends.utils.VirtualNetworkUtils;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

/**
 * Frontend client for virtual network related operations.
 */
public class VirtualNetworkFeClient {

  // We host api-backend in the Housekeeper service.
  private final HousekeeperXenonRestClient backendClient;
  private final ApiFeXenonRestClient cloudStoreClient;

  @Inject
  public VirtualNetworkFeClient(HousekeeperXenonRestClient housekeeperClient,
                                ApiFeXenonRestClient cloudStoreClient) {
    this.backendClient = housekeeperClient;
    this.backendClient.start();

    this.cloudStoreClient = cloudStoreClient;
    this.cloudStoreClient.start();
  }

  /**
   * Creates a virtual network by the given creation spec.
   *
   * Parent ID can be project/tenant ID, or can be null in case the virtual network is global.
   */
  public Task create(String parentId,
                     String parentKind,
                     VirtualNetworkCreateSpec spec) throws ExternalException {
    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.parentId = parentId;
    startState.parentKind = parentKind;
    startState.isDefault = spec.getIsDefault();
    startState.name = spec.getName();
    startState.description = spec.getDescription();
    startState.routingType = spec.getRoutingType();

    CreateVirtualNetworkWorkflowDocument finalState = backendClient.post(
        CreateVirtualNetworkWorkflowService.FACTORY_LINK,
        startState).getBody(CreateVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }

  /**
   * Deletes the given virtual network by ID.
   */
  public Task delete(String networkId) throws ExternalException {
    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.virtualNetworkId = networkId;

    DeleteVirtualNetworkWorkflowDocument finalState = backendClient
        .post(DeleteVirtualNetworkWorkflowService.FACTORY_LINK, startState)
        .getBody(DeleteVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }

  /**
   * Gets the virtual network by ID.
   */
  public VirtualNetwork get(String id) throws ExternalException {
    String documentLink = VirtualNetworkService.FACTORY_LINK + "/" + id;

    try {
      return VirtualNetworkUtils.convert(
          cloudStoreClient.get(documentLink).getBody(VirtualNetworkService.State.class));
    } catch (DocumentNotFoundException ex) {
      throw new NetworkNotFoundException(id);
    }
  }

  /**
   * Gets a list of virtual networks by the given parent ID and kind.
   * The list can be filtered by the optional name of the virtual network. The size of the list
   * can be restricted by the optional page size.
   *
   * Parent ID can be project/tenant ID, or can be null in case the virtual network is global.
   */
  public ResourceList<VirtualNetwork> list(String parentId,
                                           String parentKind,
                                           Optional<String> name,
                                           Optional<Integer> pageSize) throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (parentId != null) {
      termsBuilder.put("parentId", parentId);
    }

    if (parentKind != null) {
      termsBuilder.put("parentKind", parentKind);
    }

    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ImmutableMap<String, String> terms = termsBuilder.build();

    ServiceDocumentQueryResult queryResult = cloudStoreClient.queryDocuments(
        VirtualNetworkService.State.class, terms, pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(
        VirtualNetworkService.State.class,
        queryResult,
        VirtualNetworkUtils::convert);
  }

  /**
   * Gets the remaining list of the virtual network.
   */
  public ResourceList<VirtualNetwork> nextList(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult;
    try {
      queryResult = cloudStoreClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    return PaginationUtils.xenonQueryResultToResourceList(
        VirtualNetworkService.State.class,
        queryResult,
        VirtualNetworkUtils::convert);
  }
}
