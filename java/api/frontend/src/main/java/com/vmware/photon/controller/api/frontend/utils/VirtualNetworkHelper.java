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

package com.vmware.photon.controller.api.frontend.utils;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

/**
 * This class defines utility functions for virtual network.
 */
public class VirtualNetworkHelper implements NetworkHelper {

  private ApiFeXenonRestClient client;
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;

  @Inject
  public VirtualNetworkHelper(
      ApiFeXenonRestClient client,
      PhotonControllerXenonRestClient pcClient) {
    this.client = client;
    this.photonControllerXenonRestClient = pcClient;
  }

  @Override
  public boolean isSdnEnabled() {
    return true;
  }

  @Override
  public VmService.NetworkInfo convertAgentNetworkToVmNetwork(VmNetworkInfo agentNetwork) throws ExternalException {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(VirtualNetworkService.State.FIELD_NAME_LOGICAL_SWITCH_ID, agentNetwork.getNetwork());
    ServiceDocumentQueryResult queryResult = client.queryDocuments(
        VirtualNetworkService.State.class,
        termsBuilder.build(),
        Optional.absent(),
        true);
    ResourceList<VirtualNetworkService.State> networkStateList =
        PaginationUtils.xenonQueryResultToResourceList(VirtualNetworkService.State.class, queryResult);

    // Check only one network document is found if any matches the criteria.
    if (networkStateList == null || networkStateList.getItems() == null || networkStateList.getItems().isEmpty()) {
      return null;
    }

    VirtualNetworkService.State networkState = networkStateList.getItems().get(0);

    // Converts the cloud-store network document to VM network format.
    VmService.NetworkInfo vmNetwork = new VmService.NetworkInfo();
    vmNetwork.id = ServiceUtils.getIDFromDocumentSelfLink(networkState.documentSelfLink);
    // TODO(ysheng): we need to figure out what this means in virtual network.
    vmNetwork.dhcpAgentIP = null;
    vmNetwork.macAddress = agentNetwork.getMac_address();

    return vmNetwork;
  }

  @Override
  public void tombstone(String networkId) throws ExternalException {
    // Virtual network
    DeleteVirtualNetworkWorkflowDocument startState = new DeleteVirtualNetworkWorkflowDocument();
    startState.networkId = networkId;

    // Do not wait for the task to finish
    photonControllerXenonRestClient.post(DeleteVirtualNetworkWorkflowService.FACTORY_LINK, startState);
  }

  @Override
  public void checkSubnetState(String subnetId, SubnetState desiredState) throws ExternalException {
    VirtualNetworkService.State entity = findSubnet(subnetId);
    if (!desiredState.equals(entity.state)) {
      throw new InvalidNetworkStateException(
          String.format("Subnet %s is in %s state", subnetId, entity.state));
    }
  }

  private VirtualNetworkService.State findSubnet(String subnetId) throws NetworkNotFoundException {
    try {
      String documentLink = VirtualNetworkService.FACTORY_LINK + "/" + subnetId;
      return client.get(documentLink).getBody(VirtualNetworkService.State.class);
    } catch (DocumentNotFoundException ex) {
      throw new NetworkNotFoundException(subnetId);
    }
  }
}
