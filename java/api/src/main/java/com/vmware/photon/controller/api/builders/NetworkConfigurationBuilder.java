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

import com.vmware.photon.controller.api.NetworkConfiguration;

/**
 * This class implements a builder for {@link NetworkConfiguration} object.
 */
public class NetworkConfigurationBuilder {

  private boolean virtualNetworkEnabled;

  private String networkManagerAddress;

  private String networkManagerUsername;

  private String networkManagerPassword;

  public NetworkConfigurationBuilder virtualNetworkEnabled(boolean virtualNetworkEnabled) {
    this.virtualNetworkEnabled = virtualNetworkEnabled;
    return this;
  }

  public NetworkConfigurationBuilder networkManagerAddress(String networkManagerAddress) {
    this.networkManagerAddress = networkManagerAddress;
    return this;
  }

  public NetworkConfigurationBuilder networkManagerUsername(String networkManagerUsername) {
    this.networkManagerUsername = networkManagerUsername;
    return this;
  }

  public NetworkConfigurationBuilder networkManagerPassword(String networkManagerPassword) {
    this.networkManagerPassword = networkManagerPassword;
    return this;
  }

  public NetworkConfiguration build() {
    NetworkConfiguration networkConfiguration = new NetworkConfiguration();
    networkConfiguration.setVirtualNetworkEnabled(this.virtualNetworkEnabled);
    networkConfiguration.setNetworkManagerAddress(this.networkManagerAddress);
    networkConfiguration.setNetworkManagerUsername(this.networkManagerUsername);
    networkConfiguration.setNetworkManagerPassword(this.networkManagerPassword);

    return networkConfiguration;
  }
}
