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

package com.vmware.photon.controller.apife.backends.utils;

import com.vmware.photon.controller.api.VirtualNetwork;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

/**
 * Utility class related to virtual network.
 */
public class VirtualNetworkUtils {

  /**
   * Converts virtual network from back-end representation to front-end representation.
   */
  public static VirtualNetwork convert(VirtualNetworkService.State virtualNetworkState) {
    VirtualNetwork virtualNetwork = new VirtualNetwork();
    virtualNetwork.setId(ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkState.documentSelfLink));
    virtualNetwork.setName(virtualNetworkState.name);
    virtualNetwork.setDescription(virtualNetworkState.description);
    virtualNetwork.setState(virtualNetworkState.state);
    virtualNetwork.setRoutingType(virtualNetworkState.routingType);
    virtualNetwork.setIsDefault(virtualNetworkState.isDefault);

    return virtualNetwork;
  }
}
