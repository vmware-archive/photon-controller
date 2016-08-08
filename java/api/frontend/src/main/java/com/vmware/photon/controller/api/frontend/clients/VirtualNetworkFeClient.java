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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.utils.TaskUtils;
import com.vmware.photon.controller.api.frontend.backends.utils.VirtualNetworkUtils;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidReservedStaticIpSizeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VirtualNetworkCreateSpec;
import com.vmware.photon.controller.api.model.VirtualSubnet;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.util.List;

/**
 * Frontend client for virtual network related operations.
 */
public class VirtualNetworkFeClient {
  public static final int DEFAULT_RESERVED_IP_LIST_SIZE = 5;

  // We host api-backend in the Housekeeper service.
  private final PhotonControllerXenonRestClient backendClient;
  private final ApiFeXenonRestClient cloudStoreClient;
  private final TaskBackend taskBackend;

  @Inject
  public VirtualNetworkFeClient(PhotonControllerXenonRestClient photonControllerXenonRestClient,
                                ApiFeXenonRestClient cloudStoreClient, TaskBackend taskBackend) {
    this.backendClient = photonControllerXenonRestClient;
    this.backendClient.start();

    this.cloudStoreClient = cloudStoreClient;
    this.cloudStoreClient.start();

    this.taskBackend = taskBackend;
  }

  /**
   * Creates a virtual network by the given creation spec.
   *
   * Parent ID can be project/tenant ID, or can be null in case the virtual network is global.
   */
  public Task create(String parentId,
                     String parentKind,
                     VirtualNetworkCreateSpec spec) throws ExternalException {

    if (spec.getReservedStaticIpSize() + DEFAULT_RESERVED_IP_LIST_SIZE > spec.getSize()) {
      throw new InvalidReservedStaticIpSizeException(
          String.format("Static IP size (%s) exceeds total IP size (%s) minus reserved IP size (%s)",
              spec.getReservedStaticIpSize(), spec.getSize(), DEFAULT_RESERVED_IP_LIST_SIZE));
    }

    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.parentId = parentId;
    startState.parentKind = parentKind;
    startState.name = spec.getName();
    startState.description = spec.getDescription();
    startState.routingType = spec.getRoutingType();
    startState.size = spec.getSize();
    startState.reservedStaticIpSize = spec.getReservedStaticIpSize();

    CreateVirtualNetworkWorkflowDocument finalState = backendClient.post(
        CreateVirtualNetworkWorkflowService.FACTORY_LINK,
        startState).getBody(CreateVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }

  /**
   * Deletes the given virtual network by ID.
   */
  public Task delete(String networkId) throws ExternalException {
    VirtualNetworkService.State virtualNetworkState = getNetworkById(networkId);
    if (virtualNetworkState == null) {
      throw new NetworkNotFoundException(networkId);
    }

    if (SubnetState.PENDING_DELETE.equals(virtualNetworkState.state)) {
      throw new InvalidNetworkStateException(
          String.format("Invalid operation to delete virtual network %s in state PENDING_DELETE", networkId));
    }

    VirtualNetworkService.State networkState = new VirtualNetworkService.State();
    networkState.state = SubnetState.PENDING_DELETE;
    networkState.deleteRequestTime = System.currentTimeMillis();
    this.patchNetworkService(networkId, networkState);

    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.networkId = networkId;

    DeleteVirtualNetworkWorkflowDocument finalState = backendClient
        .post(DeleteVirtualNetworkWorkflowService.FACTORY_LINK, startState)
        .getBody(DeleteVirtualNetworkWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }

  /**
   * Gets the virtual network by ID.
   */
  public VirtualSubnet get(String networkId) throws ExternalException {
    VirtualNetworkService.State virtualNetworkState = getNetworkById(networkId);
    if (virtualNetworkState == null) {
      throw new NetworkNotFoundException(networkId);
    }

    return VirtualNetworkUtils.convert(virtualNetworkState);
  }

  /**
   * Gets a list of virtual networks by the given parent ID and kind.
   * The list can be filtered by the optional name of the virtual network. The size of the list
   * can be restricted by the optional page size.
   *
   * Parent ID can be project/tenant ID, or can be null in case the virtual network is global.
   */
  public ResourceList<VirtualSubnet> list(String parentId,
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
  public ResourceList<VirtualSubnet> nextList(String pageLink) throws ExternalException {
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

  /**
   * Sets the default virtual network by ID.
   */
  public Task setDefault(String networkId) throws ExternalException {
    VirtualNetworkService.State newDefaultNetwork = getNetworkById(networkId);
    if (newDefaultNetwork == null) {
      throw new NetworkNotFoundException(networkId);
    }

    String projectId = newDefaultNetwork.parentKind != null && newDefaultNetwork.parentKind.equals(Project.KIND) ?
        newDefaultNetwork.parentId : null;

    VirtualNetworkService.State currentDefaultNetwork = getDefaultNetwork(
        newDefaultNetwork.parentKind,
        newDefaultNetwork.parentId);
    if (currentDefaultNetwork != null) {
      VirtualNetworkService.State currentDefaultNetworkPatch = new VirtualNetworkService.State();
      currentDefaultNetworkPatch.isDefault = false;
      try {
        cloudStoreClient.patch(currentDefaultNetwork.documentSelfLink, currentDefaultNetworkPatch);
      } catch (DocumentNotFoundException ex) {
        throw new NetworkNotFoundException(
            "Failed to patch current default network " + currentDefaultNetwork.documentSelfLink);
      }
    }

    VirtualNetworkService.State newDefaultNetworkPatch = new VirtualNetworkService.State();
    newDefaultNetworkPatch.isDefault = true;
    try {
      newDefaultNetwork = cloudStoreClient.patch(newDefaultNetwork.documentSelfLink,
          newDefaultNetworkPatch).getBody(VirtualNetworkService.State.class);
    } catch (DocumentNotFoundException ex) {
      throw new NetworkNotFoundException(
          "Failed to patch new default network " + newDefaultNetwork.documentSelfLink);
    }

    return taskBackend.createCompletedTask(
        ServiceUtils.getIDFromDocumentSelfLink(newDefaultNetwork.documentSelfLink),
        VirtualSubnet.KIND,
        projectId,
        Operation.SET_DEFAULT_NETWORK.toString());
  }

  private VirtualNetworkService.State getNetworkById(String networkId) {
    String documentLink = VirtualNetworkService.FACTORY_LINK + "/" + networkId;

    try {
      return cloudStoreClient.get(documentLink).getBody(VirtualNetworkService.State.class);
    } catch (DocumentNotFoundException ex) {
      return null;
    }
  }

  private void patchNetworkService(String id, VirtualNetworkService.State networkState)
      throws NetworkNotFoundException {
    try {
      cloudStoreClient.patch(VirtualNetworkService.FACTORY_LINK + "/" + id, networkState);
    } catch (DocumentNotFoundException e) {
      throw new NetworkNotFoundException(id);
    }
  }

  private VirtualNetworkService.State getDefaultNetwork(String parentKind, String parentId) {
    ImmutableMap.Builder<String, String> termsBuilder = new ImmutableBiMap.Builder<>();
    termsBuilder.put("isDefault", Boolean.TRUE.toString());

    if (parentKind != null) {
      termsBuilder.put("parentKind", parentKind);
    }

    if (parentId != null) {
      termsBuilder.put("parentId", parentId);
    }

    List<VirtualNetworkService.State> defaultNetworks =
        cloudStoreClient.queryDocuments(VirtualNetworkService.State.class, termsBuilder.build());

    if (defaultNetworks != null && !defaultNetworks.isEmpty()) {
      return defaultNetworks.iterator().next();
    } else {
      return null;
    }
  }
}
