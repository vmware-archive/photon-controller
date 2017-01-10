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

import com.vmware.photon.controller.nsxclient.datatypes.RoutingAdvertisementResourceType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Create spec for routing advertisement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutingAdvertisement {
  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "advertise_nat_routes", required = true)
  private Boolean advertiseNatRoutes;

  @JsonProperty(value = "advertise_nsx_connected_routes", required = true)
  private Boolean advertiseNsxConnectedRoutes;

  @JsonProperty(value = "advertise_static_routes", required = true)
  private Boolean advertiseStaticRoutes;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "display_name", required = true)
  private String displayName;

  @JsonProperty(value = "enabled", required = true)
  private Boolean enabled;

  @JsonProperty(value = "resource_type", required = true)
  private RoutingAdvertisementResourceType resourceType;

  @JsonProperty(value = "tags", required = false)
  private List<Tag> tags;

  @JsonProperty(value = "_revision", required = true)
  private Integer revision;

  public Boolean getAdvertiseNatRoutes() {
    return advertiseNatRoutes;
  }

  public void setAdvertiseNatRoutes(Boolean advertiseNatRoutes) {
    this.advertiseNatRoutes = advertiseNatRoutes;
  }

  public Boolean getAdvertiseNsxConnectedRoutes() {
    return advertiseNsxConnectedRoutes;
  }

  public void setAdvertiseNsxConnectedRoutes(Boolean advertiseNsxConnectedRoutes) {
    this.advertiseNsxConnectedRoutes = advertiseNsxConnectedRoutes;
  }

  public Boolean getAdvertiseStaticRoutes() {
    return advertiseStaticRoutes;
  }

  public void setAdvertiseStaticRoutes(Boolean advertiseStaticRoutes) {
    this.advertiseStaticRoutes = advertiseStaticRoutes;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public RoutingAdvertisementResourceType getResourceType() {
    return resourceType;
  }

  public void setResourceType(RoutingAdvertisementResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public List<Tag> getTags() {
    return tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Integer getRevision() {
    return revision;
  }

  public void setRevision(Integer revision) {
    this.revision = revision;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RoutingAdvertisement other = (RoutingAdvertisement) o;
    return Objects.equals(id, other.id)
        && Objects.equals(advertiseNatRoutes, other.advertiseNatRoutes)
        && Objects.equals(advertiseNsxConnectedRoutes, other.advertiseNsxConnectedRoutes)
        && Objects.equals(advertiseStaticRoutes, other.advertiseStaticRoutes)
        && Objects.equals(description, other.description)
        && Objects.equals(displayName, other.displayName)
        && Objects.equals(enabled, other.enabled)
        && Objects.equals(resourceType, other.resourceType)
        && Objects.equals(tags, other.tags)
        && Objects.equals(revision, other.revision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        id,
        advertiseNatRoutes,
        advertiseNsxConnectedRoutes,
        advertiseStaticRoutes,
        description,
        displayName,
        enabled,
        resourceType,
        tags,
        revision);
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
