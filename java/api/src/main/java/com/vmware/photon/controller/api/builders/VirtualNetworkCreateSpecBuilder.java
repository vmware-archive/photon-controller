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

package com.vmware.photon.controller.api.builders;

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;

/**
 * Builder class for {@link com.vmware.photon.controller.api.VirtualNetworkCreateSpec}.
 */
public class VirtualNetworkCreateSpecBuilder {
  private String name;
  private String description;
  private RoutingType routingType;
  private int size;
  private int reservedStaticIpSize;

  public VirtualNetworkCreateSpecBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder description(String description) {
    this.description = description;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder routingType(RoutingType routingType) {
    this.routingType = routingType;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder size(int size) {
    this.size = size;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder reservedStaticIpSize(int reservedStaticIpSize) {
    this.reservedStaticIpSize = reservedStaticIpSize;
    return this;
  }

  public VirtualNetworkCreateSpec build() {
    VirtualNetworkCreateSpec virtualNetworkCreateSpec = new VirtualNetworkCreateSpec();
    virtualNetworkCreateSpec.setName(name);
    virtualNetworkCreateSpec.setDescription(description);
    virtualNetworkCreateSpec.setRoutingType(routingType);
    virtualNetworkCreateSpec.setReservedStaticIpSize(reservedStaticIpSize);
    virtualNetworkCreateSpec.setSize(size);

    return virtualNetworkCreateSpec;
  }
}
