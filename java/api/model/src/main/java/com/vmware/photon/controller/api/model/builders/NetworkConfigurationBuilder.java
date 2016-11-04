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
import com.vmware.photon.controller.api.model.NetworkConfiguration;

import java.util.List;

/**
 * This class implements a builder for {@link NetworkConfiguration} object.
 */
public class NetworkConfigurationBuilder {

  private NetworkConfiguration networkConfiguration;

  public NetworkConfigurationBuilder() {
    networkConfiguration = new NetworkConfiguration();
  }

  public NetworkConfigurationBuilder sdnEnabled(boolean sdnEnabled) {
    this.networkConfiguration.setSdnEnabled(sdnEnabled);
    return this;
  }

  public NetworkConfigurationBuilder networkManagerAddress(String networkManagerAddress) {
    this.networkConfiguration.setNetworkManagerAddress(networkManagerAddress);
    return this;
  }

  public NetworkConfigurationBuilder networkManagerUsername(String networkManagerUsername) {
    this.networkConfiguration.setNetworkManagerUsername(networkManagerUsername);
    return this;
  }

  public NetworkConfigurationBuilder networkManagerPassword(String networkManagerPassword) {
    this.networkConfiguration.setNetworkManagerPassword(networkManagerPassword);
    return this;
  }

  public NetworkConfigurationBuilder networkZoneId(String networkZoneId) {
    this.networkConfiguration.setNetworkZoneId(networkZoneId);
    return this;
  }

  public NetworkConfigurationBuilder networkTopRouterId(String networkTopRouterId) {
    this.networkConfiguration.setNetworkTopRouterId(networkTopRouterId);
    return this;
  }

  public NetworkConfigurationBuilder networkEdgeIpPoolId(String networkEdgeIpPoolId) {
    this.networkConfiguration.setNetworkEdgeIpPoolId(networkEdgeIpPoolId);
    return this;
  }

  public NetworkConfigurationBuilder networkHostUplinkPnic(String networkHostUplinkPnic) {
    this.networkConfiguration.setNetworkHostUplinkPnic(networkHostUplinkPnic);
    return this;
  }

  public NetworkConfigurationBuilder edgeClusterId(String edgeClusterId) {
    this.networkConfiguration.setEdgeClusterId(edgeClusterId);
    return this;
  }

  public NetworkConfigurationBuilder ipRange(String ipRange) {
    this.networkConfiguration.setIpRange(ipRange);
    return this;
  }

  public NetworkConfigurationBuilder floatingIpRange(IpRange floatingIpRange) {
    this.networkConfiguration.setFloatingIpRange(floatingIpRange);
    return this;
  }

  public NetworkConfigurationBuilder dhcpServers(List<String> dhcpServers) {
    this.networkConfiguration.setDhcpServers(dhcpServers);
    return this;
  }

  public NetworkConfigurationBuilder snatIp(String snatIp) {
    this.networkConfiguration.setSnatIp(snatIp);
    return this;
  }

  public NetworkConfiguration build() {
    return networkConfiguration;
  }
}
