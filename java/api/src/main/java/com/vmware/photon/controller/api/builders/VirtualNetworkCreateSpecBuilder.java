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

import com.vmware.photon.controller.api.SwitchReplicationMode;
import com.vmware.photon.controller.api.VirtualNetworkCreateSpec;

/**
 * Builder class for {@link com.vmware.photon.controller.api.VirtualNetworkCreateSpec}.
 */
public class VirtualNetworkCreateSpecBuilder {
  private String name;
  private String description;
  private SwitchReplicationMode switchReplicationMode;
  private String transportZone;
  private String tierZeroRouterName;
  private String edgeClusterName;
  private String dhcpServerEndpoint;

  public VirtualNetworkCreateSpecBuilder() {
    this.switchReplicationMode = VirtualNetworkCreateSpec.DEFAULT_SWITCH_REPLICATION_MODE;
  }

  public VirtualNetworkCreateSpecBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder description(String description) {
    this.description = description;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder switchReplicationMode(SwitchReplicationMode switchReplicationMode) {
    this.switchReplicationMode = switchReplicationMode;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder transportZone(String transportZone) {
    this.transportZone = transportZone;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder tierZeroRouterName(String tierZeroRouterName) {
    this.tierZeroRouterName = tierZeroRouterName;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder edgeClusterName(String edgeClusterName) {
    this.edgeClusterName = edgeClusterName;
    return this;
  }

  public VirtualNetworkCreateSpecBuilder dhcpServerEndpoint(String dhcpServerEndpoint) {
    this.dhcpServerEndpoint = dhcpServerEndpoint;
    return this;
  }

  public VirtualNetworkCreateSpec build() {
    VirtualNetworkCreateSpec virtualNetworkCreateSpec = new VirtualNetworkCreateSpec();
    virtualNetworkCreateSpec.setName(name);
    virtualNetworkCreateSpec.setDescription(description);
    virtualNetworkCreateSpec.setSwitchReplicationMode(switchReplicationMode);
    virtualNetworkCreateSpec.setTransportZone(transportZone);
    virtualNetworkCreateSpec.setTierZeroRouterName(tierZeroRouterName);
    virtualNetworkCreateSpec.setEdgeClusterName(edgeClusterName);
    virtualNetworkCreateSpec.setDhcpServerEndpoint(dhcpServerEndpoint);

    return virtualNetworkCreateSpec;
  }
}
