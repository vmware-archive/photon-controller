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

package com.vmware.photon.controller.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Networks of VM.
 */
@ApiModel(value = "This class represents networks of a VM.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmNetworks {

  @JsonProperty
  @ApiModelProperty(value = "Network connections of VM")
  private Set<NetworkConnection> networkConnections = new HashSet<>();

  public Set<NetworkConnection> getNetworkConnections() {
    return networkConnections;
  }

  public void setNetworkConnections(Set<NetworkConnection> networkConnections) {
    this.networkConnections = networkConnections;
  }

  /**
   * Add a networkConnection to a VM's networks.
   *
   * @param networkConnection the network connection
   * @return <tt>true</tt> if this network did not already contain the specified
   * networkConnection. If this network already contains the networkConnection,
   * the call leaves it unchanged and returns <tt>false</tt>
   */
  public boolean addNetworkConnection(NetworkConnection networkConnection) {
    return networkConnections.add(networkConnection);
  }

  /**
   * Remove a networkConnection from a VM's networks.
   *
   * @param networkConnection the network connection
   * @return <tt>true</tt> if this network contained the specified network,
   * otherwise returns <tt>false</tt>
   */
  public boolean removeNetworkConnection(NetworkConnection networkConnection) {
    return networkConnections.remove(networkConnection);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VmNetworks other = (VmNetworks) o;

    return Objects.equals(networkConnections, other.networkConnections);
  }

  @Override
  public int hashCode() {
    return Objects.hash(networkConnections);
  }
}
