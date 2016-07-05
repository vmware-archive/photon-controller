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

package com.vmware.photon.controller.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Network Connection.
 */
@ApiModel(value = "This class represents a network connection.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkConnection {

  @JsonProperty
  @ApiModelProperty(value = "Network Name")
  private String network;

  @JsonProperty
  @ApiModelProperty(value = "MAC Address", required = true)
  @NotNull
  @Size(min = 1)
  private String macAddress;

  @JsonProperty
  @ApiModelProperty(value = "IP Address")
  @Pattern(regexp = Subnet.IPV4_PATTERN,
      message = ": The specified ipAddress does not match IPV4 pattern: " + Subnet.IPV4_PATTERN)
  private String ipAddress;

  @JsonProperty
  @ApiModelProperty(value = "Netmask")
  @Pattern(regexp = Subnet.IPV4_PATTERN,
      message = ": The specified netmask does not match IPV4 pattern: " + Subnet.IPV4_PATTERN)
  private String netmask;

  @JsonProperty
  @ApiModelProperty(value = "Whether NIC is up and connected to a valid port group")
  private Connected isConnected = Connected.Unknown;

  public NetworkConnection() {
    // Needed for object mapper and JSON deserialization
  }

  public NetworkConnection(String macAddress) {
    this.macAddress = macAddress;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  public String getMacAddress() {
    return macAddress;
  }

  public void setMacAddress(String macAddress) {
    this.macAddress = macAddress;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getNetmask() {
    return netmask;
  }

  public void setNetmask(String netmask) {
    this.netmask = netmask;
  }

  public Connected getIsConnected() {
    return isConnected;
  }

  public void setIsConnected(Connected isConnected) {
    this.isConnected = isConnected;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NetworkConnection other = (NetworkConnection) o;

    return Objects.equals(this.getMacAddress(), other.getMacAddress()) &&
        Objects.equals(this.getIpAddress(), other.getIpAddress()) &&
        Objects.equals(this.getIsConnected(), other.getIsConnected()) &&
        Objects.equals(this.getNetmask(), other.getNetmask()) &&
        Objects.equals(this.getNetwork(), other.getNetwork());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMacAddress(), getIpAddress(), getIsConnected(),
        getNetmask(), getNetwork());
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("macAddress", getMacAddress())
        .add("ipAddress", getIpAddress())
        .add("isConnected", getIsConnected())
        .add("netmask", getNetmask())
        .add("network", getNetwork())
        .toString();
  }

  /**
   * Whether NIC is up and connected to a valid port group.
   */
  public enum Connected {
    True,
    False,
    Unknown
  }
}
