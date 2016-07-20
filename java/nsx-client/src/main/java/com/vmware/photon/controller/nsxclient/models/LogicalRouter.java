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

import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * This class represents a LogicalRouter JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class LogicalRouter {

  @JsonProperty(value = "id", required = true)
  private String id;

  @JsonProperty(value = "resource_type", required = false)
  private String resourceType;

  @JsonProperty(value = "config", required = false)
  private LogicalRouterConfig logicalRouterConfig;

  @JsonProperty(value = "router_type", required = true)
  private NsxRouter.RouterType routerType;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "tags")
  private List<Tag> tags;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public NsxRouter.RouterType getRouterType() {
    return routerType;
  }

  public void setRouterType(NsxRouter.RouterType routerType) {
    this.routerType = routerType;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public LogicalRouterConfig getLogicalRouterConfig() {
    return this.logicalRouterConfig;
  }

  public void setLogicalRouterConfig(LogicalRouterConfig logicalRouterConfig) {
    this.logicalRouterConfig = logicalRouterConfig;
  }

  public List<Tag> getTags() {
    return tags;
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

    LogicalRouter other = (LogicalRouter) o;
    return Objects.equals(getId(), other.getId())
        && Objects.equals(getResourceType(), other.getResourceType())
        && Objects.deepEquals(getLogicalRouterConfig(), other.getLogicalRouterConfig())
        && Objects.equals(getRouterType(), other.getRouterType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.deepEquals(this.tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(),
        getResourceType(),
        getLogicalRouterConfig(),
        getRouterType(),
        getDisplayName(),
        getDescription(),
        getTags());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
