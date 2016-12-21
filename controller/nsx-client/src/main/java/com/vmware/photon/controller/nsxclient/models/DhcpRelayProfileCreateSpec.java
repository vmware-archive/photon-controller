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

import com.vmware.photon.controller.nsxclient.datatypes.ServiceProfileResourceType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a DhcpRelayProfileCreateSpec JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class DhcpRelayProfileCreateSpec {

  @JsonProperty(value = "resource_type", required = true)
  private ServiceProfileResourceType resourceType;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "server_addresses", required = true)
  private List<String> serverAddresses;

  @JsonProperty(value = "tags", required = false)
  private List<Tag> tags;

  public ServiceProfileResourceType getResourceType() {
    return this.resourceType;
  }

  public void setResourceType(ServiceProfileResourceType resourceType) {
    this.resourceType = resourceType;
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

  public void setDescription(String descrption) {
    this.description = descrption;
  }

  public List<String> getServerAddresses() {
    return this.serverAddresses;
  }

  public void setServerAddresses(List<String> serverAddresses) {
    this.serverAddresses = serverAddresses;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DhcpRelayProfileCreateSpec other = (DhcpRelayProfileCreateSpec) o;
    return Objects.equals(getResourceType(), other.getResourceType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.deepEquals(getServerAddresses(), other.getServerAddresses())
        && Objects.deepEquals(getTags(), other.getTags());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getResourceType(),
        getDisplayName(),
        getDescription(),
        getServerAddresses().toString(),
        getTags().toString());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
