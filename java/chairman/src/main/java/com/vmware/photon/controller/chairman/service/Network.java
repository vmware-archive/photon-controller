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

package com.vmware.photon.controller.chairman.service;

import com.vmware.photon.controller.resource.gen.NetworkType;

import java.util.List;

/**
 * Network model which represents a network that a host is connected to. A network
 * is associated with only one fault domain and many networks can be associated with
 * a single fault domain.
 */
public class Network {
  private final String id;
  private final List<NetworkType> type;


  public Network(String id, List<NetworkType> type) {
      this.id = id;
      this.type = type;
  }

  public String getId() {
    return id;
  }

  public List<NetworkType> getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Network network = (Network) o;

    return id.equals(network.id);
  }

  @Override
  public int hashCode() {
    // Not using faultDomain since it's mutable.
    return id.hashCode();
  }

  @Override
  public String toString() {
    return String.format("Network{id=%s, type=%s}", id, type);
  }
}
