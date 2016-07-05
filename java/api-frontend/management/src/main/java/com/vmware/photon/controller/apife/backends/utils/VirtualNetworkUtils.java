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

import com.vmware.photon.controller.api.VirtualSubnet;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

/**
 * Utility class related to virtual network.
 */
public class VirtualNetworkUtils {

  /**
   * Converts virtual network from back-end representation to front-end representation.
   */
  public static VirtualSubnet convert(VirtualNetworkService.State virtualNetworkState) {
    VirtualSubnet virtualSubnet = new VirtualSubnet();
    virtualSubnet.setId(ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkState.documentSelfLink));
    virtualSubnet.setName(virtualNetworkState.name);
    virtualSubnet.setDescription(virtualNetworkState.description);
    virtualSubnet.setState(virtualNetworkState.state);
    virtualSubnet.setRoutingType(virtualNetworkState.routingType);
    virtualSubnet.setIsDefault(virtualNetworkState.isDefault);

    return virtualSubnet;
  }
}
