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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NetworkConfigurationCreateSpec other = (NetworkConfigurationCreateSpec) o;

    return Objects.equals(this.getNetworkManagerAddress(), other.getNetworkManagerAddress())
        && Objects.equals(this.getNetworkManagerUsername(), other.getNetworkManagerUsername())
        && Objects.equals(this.getNetworkManagerPassword(), other.getNetworkManagerPassword());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getNetworkManagerAddress(),
        this.getNetworkManagerUsername(),
        this.getNetworkManagerPassword());
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("networkManagerAddress", this.getNetworkManagerAddress())
        .add("networkManagerUsername", this.getNetworkManagerUsername())
        .add("networkManagerPassword", this.getNetworkManagerPassword());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }
}
