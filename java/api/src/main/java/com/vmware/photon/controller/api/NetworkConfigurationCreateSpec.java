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

import com.vmware.photon.controller.api.constraints.DomainOrIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Contains network configuration creation information.
 */
@ApiModel(value = "Contains network configuration creation information")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetworkConfigurationCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "Flag that indicates if virtual network support is enabled or not", required = true)
  private boolean virtualNetworkEnabled = false;

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
  @ApiModelProperty(value = "The ID of the router for accessing outside network (i.e. Internet)", required = false)
  private String networkTopRouterId;

  public boolean getVirtualNetworkEnabled() {
    return virtualNetworkEnabled;
  }

  public void setVirtualNetworkEnabled(boolean virtualNetworkEnabled) {
    this.virtualNetworkEnabled = virtualNetworkEnabled;
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

  public String getNetworkTopRouterId() {
    return networkTopRouterId;
  }

  public void setNetworkTopRouterId(String networkTopRouterId) {
    this.networkTopRouterId = networkTopRouterId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NetworkConfigurationCreateSpec other = (NetworkConfigurationCreateSpec) o;

    return Objects.equals(this.getVirtualNetworkEnabled(), other.getVirtualNetworkEnabled())
        && Objects.equals(this.getNetworkManagerAddress(), other.getNetworkManagerAddress())
        && Objects.equals(this.getNetworkManagerUsername(), other.getNetworkManagerUsername())
        && Objects.equals(this.getNetworkManagerPassword(), other.getNetworkManagerPassword())
        && Objects.equals(this.getNetworkTopRouterId(), other.getNetworkTopRouterId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getVirtualNetworkEnabled(),
        this.getNetworkManagerAddress(),
        this.getNetworkManagerUsername(),
        this.getNetworkManagerPassword(),
        this.getNetworkTopRouterId());
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("virtualNetworkEnabled", this.getVirtualNetworkEnabled())
        .add("networkManagerAddress", this.getNetworkManagerAddress())
        .add("networkManagerUsername", this.getNetworkManagerUsername())
        .add("networkManagerPassword", this.getNetworkManagerPassword())
        .add("networkTopRouterId", this.getNetworkTopRouterId());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }
}
