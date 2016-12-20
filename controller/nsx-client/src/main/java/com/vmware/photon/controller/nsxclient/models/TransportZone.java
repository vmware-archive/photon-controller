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

import com.vmware.photon.controller.nsxclient.datatypes.TransportType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * This class represents a TransportZone JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class TransportZone {

  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "host_switch_name", required = true)
  private String hostSwitchName;

  @JsonProperty(value = "transport_type", required = true)
  private TransportType transportType;

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getHostSwitchName() {
    return this.hostSwitchName;
  }

  public void setHostSwitchName(String hostSwitchName) {
    this.hostSwitchName = hostSwitchName;
  }

  public TransportType getTransportType() {
    return this.transportType;
  }

  public void setTransportType(TransportType transportType) {
    this.transportType = transportType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TransportZone other = (TransportZone) o;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.equals(getHostSwitchName(), other.getHostSwitchName())
        && Objects.equals(getTransportType(), other.getTransportType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(),
        getDisplayName(),
        getDescription(),
        getHostSwitchName(),
        getTransportType());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
