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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import java.util.Objects;

/**
 * Packet address classifier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PacketAddressClassifier {
  /**
   * In the format of x.x.x.x or x.x.x.x/y.
   */
  @JsonProperty(value = "ip_address", required = false)
  private String ipAddress;

  @JsonProperty(value = "mac_address", required = false)
  private String macAddress;

  @JsonProperty(value = "vlan", required = false)
  @Min(0)
  @Max(4094)
  private Integer vlanId;

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getMacAddress() {
    return macAddress;
  }

  public void setMacAddress(String macAddress) {
    this.macAddress = macAddress;
  }

  public Integer getVlanId() {
    return vlanId;
  }

  public void setVlanId(Integer vlanId) {
    this.vlanId = vlanId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    PacketAddressClassifier other = (PacketAddressClassifier) o;
    return Objects.equals(this.ipAddress, other.ipAddress)
        && Objects.equals(this.macAddress, other.macAddress)
        && Objects.equals(this.vlanId, other.vlanId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), ipAddress, macAddress, vlanId);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
