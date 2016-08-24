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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.constraints.Cidr;
import com.vmware.photon.controller.api.model.constraints.DomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Contains network configuration information.
 */
@ApiModel(value = "Contains network configuration information")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetworkConfiguration {

  @JsonProperty
  @ApiModelProperty(value = "Flag that indicates if virtual network support is enabled or not", required = true)
  private boolean sdnEnabled = false;

  @JsonProperty
  @ApiModelProperty(value = "The IP address of the network manager", required = true)
  @NotNull
  @DomainOrIP
  private String networkManagerAddress;

  @JsonProperty
  @ApiModelProperty(value = "The username for accessing the network manager", required = true)
  @NotNull
  private String networkManagerUsername;

  @JsonProperty
  @ApiModelProperty(value = "The password for accessing the network manager", required = true)
  @NotNull
  private String networkManagerPassword;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the network zone which inter-connects all hosts", required = true)
  @NotNull
  private String networkZoneId;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the router for accessing outside network (i.e. Internet)", required = true)
  @NotNull
  private String networkTopRouterId;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the edge cluster that connects virtual network to physical network",
      required = true)
  @NotNull
  private String edgeClusterId;

  @JsonProperty
  @ApiModelProperty(value = "The global IP range for allocating private IP range to virtual network", required = true)
  @NotNull
  @Cidr
  private String ipRange;

  @JsonProperty
  @ApiModelProperty(value = "The global floating IP range (It could be used as floating IP, SNAT IP, gateway IP, etc.",
      required = true)
  @NotNull
  @Valid
  private IpRange floatingIpRange;

  @JsonProperty
  @ApiModelProperty(value = "IP addresses of DHCP servers", required = false)
  private List<String> dhcpServers;

  public boolean getSdnEnabled() {
    return sdnEnabled;
  }

  public void setSdnEnabled(boolean sdnEnabled) {
    this.sdnEnabled = sdnEnabled;
  }

  public String getNetworkManagerAddress() {
    return networkManagerAddress;
  }

  public void setNetworkManagerAddress(String networkManagerAddress) {
    this.networkManagerAddress = networkManagerAddress;
  }

  public String getNetworkManagerUsername() {
    return networkManagerUsername;
  }

  public void setNetworkManagerUsername(String networkManagerUsername) {
    this.networkManagerUsername = networkManagerUsername;
  }

  public String getNetworkManagerPassword() {
    return networkManagerPassword;
  }

  public void setNetworkManagerPassword(String networkManagerPassword) {
    this.networkManagerPassword = networkManagerPassword;
  }

  public String getNetworkZoneId() {
    return networkZoneId;
  }

  public void setNetworkZoneId(String networkZoneId) {
    this.networkZoneId = networkZoneId;
  }

  public String getNetworkTopRouterId() {
    return networkTopRouterId;
  }

  public String getIpRange() {
    return ipRange;
  }

  public void setIpRange(String ipRange) {
    this.ipRange = ipRange;
  }

  public IpRange getFloatingIpRange() {
    return floatingIpRange;
  }

  public void setFloatingIpRange(IpRange floatingIpRange) {
    this.floatingIpRange = floatingIpRange;
  }

  public void setNetworkTopRouterId(String networkTopRouterId) {
    this.networkTopRouterId = networkTopRouterId;
  }

  public String getEdgeClusterId() {
    return edgeClusterId;
  }

  public void setEdgeClusterId(String edgeClusterId) {
    this.edgeClusterId = edgeClusterId;
  }

  public List<String> getDhcpServers() {
    return dhcpServers;
  }

  public void setDhcpServers(List<String> dhcpServers) {
    this.dhcpServers = dhcpServers;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NetworkConfiguration other = (NetworkConfiguration) o;

    return Objects.equals(this.getSdnEnabled(), other.getSdnEnabled())
        && Objects.equals(this.getNetworkManagerAddress(), other.getNetworkManagerAddress())
        && Objects.equals(this.getNetworkManagerUsername(), other.getNetworkManagerUsername())
        && Objects.equals(this.getNetworkManagerPassword(), other.getNetworkManagerPassword())
        && Objects.equals(this.getNetworkZoneId(), other.getNetworkZoneId())
        && Objects.equals(this.getNetworkTopRouterId(), other.getNetworkTopRouterId())
        && Objects.equals(this.getEdgeClusterId(), other.getEdgeClusterId())
        && Objects.equals(this.getIpRange(), other.getIpRange())
        && Objects.equals(this.getFloatingIpRange(), other.getFloatingIpRange())
        && Objects.deepEquals(this.getDhcpServers(), other.getDhcpServers());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getSdnEnabled(),
        this.getNetworkManagerAddress(),
        this.getNetworkManagerUsername(),
        this.getNetworkManagerPassword(),
        this.getNetworkZoneId(),
        this.getNetworkTopRouterId(),
        this.getEdgeClusterId(),
        this.getFloatingIpRange(),
        this.getDhcpServers());
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    // NOTE: Do not include networkManagerUsername or networkManagerPassword,
    // to avoid having usernames or passwords in log files
    return com.google.common.base.Objects.toStringHelper(this)
        .add("sdnEnabled", this.getSdnEnabled())
        .add("networkManagerAddress", this.getNetworkManagerAddress())
        .add("networkZoneId", this.getNetworkZoneId())
        .add("networkTopRouterId", this.getNetworkTopRouterId())
        .add("ipRange", this.getIpRange())
        .add("floatingIpRange", this.getFloatingIpRange())
        .add("edgeClusterId", this.edgeClusterId)
        .add("dhcpServers", StringUtils.join(this.getDhcpServers(), ','));
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }
}
