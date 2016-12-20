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

import java.util.List;
import java.util.Objects;

/**
 * This class represents a LogicalRouterConfig JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class LogicalRouterConfig {

  @JsonProperty(value = "external_transit_networks", required = false)
  private List<IPv4CIDRBlock> externalTransitNetworks;

  @JsonProperty(value = "internal_transit_networks", required = false)
  private IPv4CIDRBlock internalTransitNetworks;

  public List<IPv4CIDRBlock> getExternalTransitNetworks() {
    return this.externalTransitNetworks;
  }

  public void setExternalTransitNetworks(List<IPv4CIDRBlock> externalTransitNetworks) {
    this.externalTransitNetworks = externalTransitNetworks;
  }

  public IPv4CIDRBlock getInternalTransitNetworks() {
    return this.internalTransitNetworks;
  }

  public void setInternalTransitNetworks(IPv4CIDRBlock internalTransitNetworks) {
    this.internalTransitNetworks = internalTransitNetworks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LogicalRouterConfig other = (LogicalRouterConfig) o;
    return Objects.deepEquals(getExternalTransitNetworks(), other.getExternalTransitNetworks())
        && Objects.deepEquals(getInternalTransitNetworks(), other.getInternalTransitNetworks());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getExternalTransitNetworks(),
        getInternalTransitNetworks());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
