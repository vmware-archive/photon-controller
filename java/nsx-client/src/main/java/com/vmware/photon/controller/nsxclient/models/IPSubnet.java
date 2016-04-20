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

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * IP subnet.
 */
public class IPSubnet {
  @JsonProperty(value = "ip_addresses", required = true)
  @Size(min = 1, max = 1)
  private List<String> ipAddresses;

  @JsonProperty(value = "prefix_length", required = true)
  @Min(1)
  @Max(31)
  private Integer prefixLength;

  public List<String> getIpAddresses() {
    return ipAddresses;
  }

  public void setIpAddresses(List<String> ipAddresses) {
    this.ipAddresses = ipAddresses;
  }

  public Integer getPrefixLength() {
    return prefixLength;
  }

  public void setPrefixLength(Integer prefixLength) {
    this.prefixLength = prefixLength;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    IPSubnet other = (IPSubnet) o;
    return Objects.equals(this.ipAddresses, other.ipAddresses)
        && Objects.equals(this.prefixLength, other.prefixLength);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), ipAddresses, prefixLength);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
