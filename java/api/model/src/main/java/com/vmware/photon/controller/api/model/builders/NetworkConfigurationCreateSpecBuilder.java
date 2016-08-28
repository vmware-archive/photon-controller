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

package com.vmware.photon.controller.api.model.builders;

import com.vmware.photon.controller.api.model.IpRange;
import com.vmware.photon.controller.api.model.NetworkConfigurationCreateSpec;

import java.util.List;

/**
 * This class implements a builder for {@link NetworkConfigurationCreateSpec} object.
 */
public class NetworkConfigurationCreateSpecBuilder {

  private NetworkConfigurationCreateSpec networkConfigurationCreateSpec;

  public NetworkConfigurationCreateSpecBuilder() {
    networkConfigurationCreateSpec = new NetworkConfigurationCreateSpec();
  }

  public NetworkConfigurationCreateSpecBuilder sdnEnabled(boolean sdnEnabled) {
    this.networkConfigurationCreateSpec.setSdnEnabled(sdnEnabled);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder networkManagerAddress(String networkManagerAddress) {
    this.networkConfigurationCreateSpec.setNetworkManagerAddress(networkManagerAddress);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder networkManagerUsername(String networkManagerUsername) {
    this.networkConfigurationCreateSpec.setNetworkManagerUsername(networkManagerUsername);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder networkManagerPassword(String networkManagerPassword) {
    this.networkConfigurationCreateSpec.setNetworkManagerPassword(networkManagerPassword);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder networkZoneId(String networkZoneId) {
    this.networkConfigurationCreateSpec.setNetworkZoneId(networkZoneId);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder networkTopRouterId(String networkTopRouterId) {
    this.networkConfigurationCreateSpec.setNetworkTopRouterId(networkTopRouterId);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder edgeClusterId(String edgeClusterId) {
    this.networkConfigurationCreateSpec.setEdgeClusterId(edgeClusterId);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder ipRange(String ipRange) {
    this.networkConfigurationCreateSpec.setIpRange(ipRange);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder externalIpRange(IpRange externalIpRange) {
    this.networkConfigurationCreateSpec.setExternalIpRange(externalIpRange);
    return this;
  }

  public NetworkConfigurationCreateSpecBuilder dhcpServers(List<String> dhcpServers) {
    this.networkConfigurationCreateSpec.setDhcpServers(dhcpServers);
    return this;
  }

  public NetworkConfigurationCreateSpec build() {
    return networkConfigurationCreateSpec;
  }
}
