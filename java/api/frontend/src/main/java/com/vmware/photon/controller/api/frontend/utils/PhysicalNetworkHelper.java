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
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PortGroupRepeatedInMultipleNetworksException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines utility functions for physical network.
 */
public class PhysicalNetworkHelper implements NetworkHelper {

  private ApiFeXenonRestClient client;

  @Inject
  public PhysicalNetworkHelper(ApiFeXenonRestClient client) {
    this.client = client;
  }

  public boolean isSdnEnabled() {
    return false;
  }

  public VmService.NetworkInfo convertAgentNetworkToVmNetwork(VmNetworkInfo agentNetwork) throws ExternalException {
    // Query cloud-store for network document with the given port group name.
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(NetworkService.State.FIELD_PORT_GROUPS_QUERY_KEY, agentNetwork.getNetwork());
    ServiceDocumentQueryResult queryResult = client.queryDocuments(
        NetworkService.State.class,
        termsBuilder.build(),
        Optional.absent(),
        true);
    ResourceList<NetworkService.State> networkStateList =
        PaginationUtils.xenonQueryResultToResourceList(NetworkService.State.class, queryResult);

    // Check only one network document is found if any matches the criteria.
    if (networkStateList == null || networkStateList.getItems() == null || networkStateList.getItems().isEmpty()) {
      return null;
    } else if (networkStateList.getItems().size() > 1) {
      Map<String, List<NetworkService.State>> violations = new HashMap<>();
      violations.put(agentNetwork.getNetwork(), networkStateList.getItems());
      throw new PortGroupRepeatedInMultipleNetworksException(violations);
    }

    NetworkService.State networkState = networkStateList.getItems().get(0);

    // Converts the cloud-store network document to VM network format.
    VmService.NetworkInfo vmNetwork = new VmService.NetworkInfo();
    vmNetwork.id = ServiceUtils.getIDFromDocumentSelfLink(networkState.documentSelfLink);
    vmNetwork.dhcpAgentIP = networkState.dhcpAgentIP;
    vmNetwork.macAddress = agentNetwork.getMac_address();
    if (agentNetwork.getIp_address() != null) {
      vmNetwork.ipAddress = agentNetwork.getIp_address().getIp_address();
      vmNetwork.netmask = agentNetwork.getIp_address().getNetmask();
    }

    return vmNetwork;
  }
}
