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

import java.util.Objects;

/**
 * This class represents a IPv4CIDRBlock JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class IPv4CIDRBlock {

  @JsonProperty(value = "IPv4CIDRBlock", required = true)
  private String ipv4CIDRBlock;

  public String getIPv4CIDRBlock() {
    return this.ipv4CIDRBlock;
  }

  public void setIPv4CIDRBlock(String ipv4CIDRBlock) {
    this.ipv4CIDRBlock = ipv4CIDRBlock;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IPv4CIDRBlock other = (IPv4CIDRBlock) o;
    return Objects.equals(getIPv4CIDRBlock(), other.getIPv4CIDRBlock());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getIPv4CIDRBlock());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
