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
  @ApiModelProperty(value = "The mapping between the priavet IP and public IP of the DHCP servers")
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
        && Objects.equals(floatingIpRootRange, other.floatingIpRootRange);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        nsxAddress,
        nsxUsername,
        nsxPassword,
        dhcpServerAddresses,
        privateIpRootCidr,
        floatingIpRootRange);
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
        .toString();
  }
}
