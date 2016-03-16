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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.base.Named;
import com.vmware.photon.controller.api.constraints.NullableDomainOrIP;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * VirtualNetworkCreateSpec is used to represent the payload to create an NSX network.
 * It includes the necessary information to create a logical switch and a logical router
 * as well as the data to connection to tier-0 router, edge cluster, and dhcp server.
 */
@ApiModel(value = "This class represents the payload to create an nsx network.")
public class VirtualNetworkCreateSpec implements Named {

  public static final SwitchReplicationMode DEFAULT_SWITCH_REPLICATION_MODE = SwitchReplicationMode.MTEP;

  @JsonProperty
  @ApiModelProperty(value = "Name of the virtual network", required = true)
  @NotNull
  @Pattern(regexp = Named.PATTERN,
      message = ": The specific virtual network name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Description to the virtual network", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "Replication Mode", required = true)
  private SwitchReplicationMode switchReplicationMode = DEFAULT_SWITCH_REPLICATION_MODE;

  @JsonProperty
  @ApiModelProperty(value = "Name of the transport zone", required = true)
  @NotNull
  @Size(min = 1)
  private String transportZone;

  @JsonProperty
  @ApiModelProperty(value = "Name of the tier-0 router that it connects to", required = false)
  private String tierZeroRouterName;

  @JsonProperty
  @ApiModelProperty(value = "Name of the edge cluster that it connects to", required = false)
  private String edgeClusterName;

  @JsonProperty
  @ApiModelProperty(value = "Endpoint of the DHCP server if DHCP is being supported", required = false)
  @NullableDomainOrIP
  private String dhcpServerEndpoint;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SwitchReplicationMode getSwitchReplicationMode() {
    return switchReplicationMode;
  }

  public void setSwitchReplicationMode(SwitchReplicationMode switchReplicationMode) {
    this.switchReplicationMode = switchReplicationMode;
  }

  public String getTransportZone() {
    return transportZone;
  }

  public void setTransportZone(String transportZone) {
    this.transportZone = transportZone;
  }

  public String getTierZeroRouterName() {
    return tierZeroRouterName;
  }

  public void setTierZeroRouterName(String tierZeroRouterName) {
    this.tierZeroRouterName = tierZeroRouterName;
  }

  public String getEdgeClusterName() {
    return edgeClusterName;
  }

  public void setEdgeClusterName(String edgeClusterName) {
    this.edgeClusterName = edgeClusterName;
  }

  public String getDhcpServerEndpoint() {
    return dhcpServerEndpoint;
  }

  public void setDhcpServerEndpoint(String dhcpServerEndpoint) {
    this.dhcpServerEndpoint = dhcpServerEndpoint;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    VirtualNetworkCreateSpec other = (VirtualNetworkCreateSpec) o;
    return Objects.equals(this.name, other.name)
        && Objects.equals(this.description, other.description)
        && Objects.equals(this.switchReplicationMode, other.switchReplicationMode)
        && Objects.equals(this.transportZone, other.transportZone)
        && Objects.equals(this.tierZeroRouterName, other.tierZeroRouterName)
        && Objects.equals(this.edgeClusterName, other.edgeClusterName)
        && Objects.equals(this.dhcpServerEndpoint, other.dhcpServerEndpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), this.name, this.description, this.switchReplicationMode, this.transportZone,
        this.tierZeroRouterName, this.edgeClusterName, this.dhcpServerEndpoint);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", name)
        .add("description", description)
        .add("switchReplicationMode", switchReplicationMode)
        .add("transportZone", transportZone)
        .add("tierZeroRouterName", tierZeroRouterName)
        .add("edgeClusterName", edgeClusterName)
        .add("dhcpServerEndpoint", dhcpServerEndpoint)
        .toString();
  }
}
