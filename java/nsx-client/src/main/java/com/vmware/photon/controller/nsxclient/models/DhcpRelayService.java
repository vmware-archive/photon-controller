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

import com.vmware.photon.controller.nsxclient.datatypes.LogicalServiceResourceType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a DhcpRelayService JSON structure.
 */
public class DhcpRelayService {

  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "resource_type", required = true)
  private LogicalServiceResourceType resourceType;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "dhcp_relay_profile_id", required = true)
  private String profileId;

  @JsonProperty(value = "tags", required = false)
  private List<Tag> tags;

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LogicalServiceResourceType getResourceType() {
    return this.resourceType;
  }

  public void setResourceType(LogicalServiceResourceType resourceType) {
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

  public String getProfileId() {
    return this.profileId;
  }

  public void setProfileId(String profileId) {
    this.profileId = profileId;
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

    DhcpRelayService other = (DhcpRelayService) o;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getResourceType(), other.getResourceType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.equals(getProfileId(), other.getProfileId())
        && Objects.deepEquals(getTags(), other.getTags());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(),
        getResourceType(),
        getDisplayName(),
        getDescription(),
        getProfileId(),
        getTags().toString());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
