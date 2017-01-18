/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
import com.wordnik.swagger.annotations.ApiModelProperty;

import static com.google.common.base.Objects.toStringHelper;

import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * This class represents the configuration spec of NSX.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NsxConfigurationSpec {

  @JsonProperty
  @ApiModelProperty(value = "The IP address of NSX setup")
  @NotNull
  private String nsxAddress;

  @JsonProperty
  @ApiModelProperty(value = "The username of the NSX setup")
  @NotNull
  private String nsxUsername;

  @JsonProperty
  @ApiModelProperty(value = "The password of the NSX setup")
  @NotNull
  private String nsxPassword;

  @JsonProperty
  @ApiModelProperty(value = "The mapping between the private IP and public IP of the DHCP servers")
  @NotNull
  private Map<String, String> dhcpServerAddresses;

  @JsonProperty
  @ApiModelProperty(value = "The root CIDR of the private IPs of the system")
  @NotNull
  private String privateIpRootCidr;

  @JsonProperty
  @ApiModelProperty(value = "The root range of the floating IPs of the system")
  @NotNull
  private IpRange floatingIpRootRange;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the T0-Router")
  @NotNull
  private String t0RouterId;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the Edge cluster")
  @NotNull
  private String edgeClusterId;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the OVERLAY transport zone")
  @NotNull
  private String overlayTransportZoneId;

  @JsonProperty
  @ApiModelProperty(value = "The ID of the tunnel IP pool")
  @NotNull
  private String tunnelIpPoolId;

  @JsonProperty
  @ApiModelProperty(value = "The name of the host uplink pnic")
  @NotNull
  private String hostUplinkPnic;

  public String getNsxAddress() {
    return nsxAddress;
  }

  public void setNsxAddress(String nsxAddress) {
    this.nsxAddress = nsxAddress;
  }

  public String getNsxUsername() {
    return nsxUsername;
  }

  public void setNsxUsername(String nsxUsername) {
    this.nsxUsername = nsxUsername;
  }

  public String getNsxPassword() {
    return nsxPassword;
  }

  public void setNsxPassword(String nsxPassword) {
    this.nsxPassword = nsxPassword;
  }

  public Map<String, String> getDhcpServerAddresses() {
    return dhcpServerAddresses;
  }

  public void setDhcpServerAddresses(Map<String, String> dhcpServerAddresses) {
    this.dhcpServerAddresses = dhcpServerAddresses;
  }

  public String getPrivateIpRootCidr() {
    return privateIpRootCidr;
  }

  public void setPrivateIpRootCidr(String privateIpRootCidr) {
    this.privateIpRootCidr = privateIpRootCidr;
  }

  public IpRange getFloatingIpRootRange() {
    return floatingIpRootRange;
  }

  public void setFloatingIpRootRange(IpRange floatingIpRootRange) {
    this.floatingIpRootRange = floatingIpRootRange;
  }

  public String getT0RouterId() {
    return t0RouterId;
  }

  public void setT0RouterId(String t0RouterId) {
    this.t0RouterId = t0RouterId;
  }

  public String getEdgeClusterId() {
    return edgeClusterId;
  }

  public void setEdgeClusterId(String edgeClusterId) {
    this.edgeClusterId = edgeClusterId;
  }

  public String getOverlayTransportZoneId() {
    return overlayTransportZoneId;
  }

  public void setOverlayTransportZoneId(String overlayTransportZoneId) {
    this.overlayTransportZoneId = overlayTransportZoneId;
  }

  public String getTunnelIpPoolId() {
    return tunnelIpPoolId;
  }

  public void setTunnelIpPoolId(String tunnelIpPoolId) {
    this.tunnelIpPoolId = tunnelIpPoolId;
  }

  public String getHostUplinkPnic() {
    return hostUplinkPnic;
  }

  public void setHostUplinkPnic(String hostUplinkPnic) {
    this.hostUplinkPnic = hostUplinkPnic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NsxConfigurationSpec other = (NsxConfigurationSpec) o;

    return Objects.equals(nsxAddress, other.nsxAddress)
        && Objects.equals(nsxUsername, other.nsxUsername)
        && Objects.equals(nsxPassword, other.nsxPassword)
        && Objects.deepEquals(dhcpServerAddresses, other.dhcpServerAddresses)
        && Objects.equals(privateIpRootCidr, other.privateIpRootCidr)
        && Objects.equals(floatingIpRootRange, other.floatingIpRootRange)
        && Objects.equals(t0RouterId, other.t0RouterId)
        && Objects.equals(edgeClusterId, other.edgeClusterId)
        && Objects.equals(overlayTransportZoneId, other.overlayTransportZoneId)
        && Objects.equals(tunnelIpPoolId, other.tunnelIpPoolId)
        && Objects.equals(hostUplinkPnic, other.hostUplinkPnic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        nsxAddress,
        nsxUsername,
        nsxPassword,
        dhcpServerAddresses,
        privateIpRootCidr,
        floatingIpRootRange,
        t0RouterId,
        edgeClusterId,
        overlayTransportZoneId,
        tunnelIpPoolId,
        hostUplinkPnic);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("nsxAddress", nsxAddress)
        .add("nsxUsername", nsxUsername)
        .add("nsxPassword", nsxPassword)
        .add("dhcpServerAddresses", dhcpServerAddresses.toString())
        .add("privateIpRootCidr", privateIpRootCidr)
        .add("floatingIpRootRange", floatingIpRootRange)
        .add("t0RouterId", t0RouterId)
        .add("edgeClusterId", edgeClusterId)
        .add("overlayTransportZoneId", overlayTransportZoneId)
        .add("tunnelIpPoolId", tunnelIpPoolId)
        .add("hostUplinkPnic", hostUplinkPnic)
        .toString();
  }
}
