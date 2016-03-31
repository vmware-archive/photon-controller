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

import java.util.List;
import java.util.Objects;

/**
 * This class represents a TransportNodeCreateSpec JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class TransportNodeCreateSpec {

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "node_id", required = true)
  private String nodeId;

  @JsonProperty(value = "host_switches", required = true)
  private List<HostSwitch> hostSwitches;

  @JsonProperty(value = "transport_zone_endpoints", required = false)
  private List<TransportZoneEndPoint> transportZoneEndPoints;

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public List<HostSwitch> getHostSwitches() {
    return this.hostSwitches;
  }

  public void setHostSwitches(List<HostSwitch> hostSwitches) {
    this.hostSwitches = hostSwitches;
  }

  public List<TransportZoneEndPoint> getTransportZoneEndPoints() {
    return this.transportZoneEndPoints;
  }

  public void setTransportZoneEndPoints(List<TransportZoneEndPoint> transportZoneEndPoints) {
    this.transportZoneEndPoints = transportZoneEndPoints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransportNodeCreateSpec other = (TransportNodeCreateSpec) o;
    return Objects.equals(getDescription(), other.getDescription())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getNodeId(), other.getNodeId())
        && Objects.deepEquals(getHostSwitches(), other.getHostSwitches())
        && Objects.deepEquals(getTransportZoneEndPoints(), other.getTransportZoneEndPoints());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getDescription(),
        getDisplayName(),
        getNodeId(),
        getHostSwitches(),
        getTransportZoneEndPoints());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
