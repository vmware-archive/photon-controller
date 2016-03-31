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

package com.vmware.photon.controller.nsxclient.models;

import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * This class represents a TransportZoneSummary JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class TransportZoneSummary {

  @JsonProperty(value = "num_logical_ports", required = true)
  private Integer numLogicalPorts;

  @JsonProperty(value = "num_logical_switches", required = true)
  private Integer numLogicalSwitches;

  @JsonProperty(value = "num_transport_nodes", required = true)
  private Integer numTransportNodes;

  public Integer getNumLogicalPorts() {
    return this.numLogicalPorts;
  }

  public void setNumLogicalPorts(Integer numLogicalPorts) {
    this.numLogicalPorts = numLogicalPorts;
  }

  public Integer getNumLogicalSwitches() {
    return this.numLogicalSwitches;
  }

  public void setNumLogicalSwitches(Integer numLogicalSwitches) {
    this.numLogicalSwitches = numLogicalSwitches;
  }

  public Integer getNumTransportNodes() {
    return this.numTransportNodes;
  }

  public void setNumTransportNodes(Integer numTransportNodes) {
    this.numTransportNodes = numTransportNodes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransportZoneSummary other = (TransportZoneSummary) o;
    return Objects.equals(getNumLogicalPorts(), other.getNumLogicalPorts())
        && Objects.equals(getNumLogicalSwitches(), other.getNumLogicalSwitches())
        && Objects.equals(getNumTransportNodes(), other.getNumTransportNodes());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getNumLogicalPorts(),
        getNumLogicalSwitches(),
        getNumTransportNodes());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
