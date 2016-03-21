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

import com.vmware.photon.controller.api.InternetAccessState;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.VirtualNetwork;

/**
 * Builder class for {@link com.vmware.photon.controller.api.VirtualNetwork}.
 */
public class VirtualNetworkBuilder {
  private String name;
  private String description;
  private NetworkState state;
  private InternetAccessState internetAccessState;

  public VirtualNetworkBuilder() {
  }

  public VirtualNetworkBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VirtualNetworkBuilder description(String description) {
    this.description = description;
    return this;
  }

  public VirtualNetworkBuilder state(NetworkState state) {
    this.state = state;
    return this;
  }

  public VirtualNetworkBuilder internetAccessState(InternetAccessState internetAccessState) {
    this.internetAccessState = internetAccessState;
    return this;
  }

  public VirtualNetwork build() {
    VirtualNetwork virtualNetwork = new VirtualNetwork();
    virtualNetwork.setName(name);
    virtualNetwork.setDescription(description);
    virtualNetwork.setState(state);
    virtualNetwork.setInternetAccessState(internetAccessState);

    return virtualNetwork;
  }
}
