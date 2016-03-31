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

import com.vmware.photon.controller.nsxclient.datatypes.RouterType;
import com.vmware.photon.controller.nsxclient.utils.ToStringHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * This class represents a LogicalRouterCreateSpec JSON structure.
 */
@JsonIgnoreProperties(ignoreUnknown =  true)
public class LogicalRouterCreateSpec {

  @JsonProperty(value = "router_type", required = true)
  private RouterType routerType;

  @JsonProperty(value = "display_name", required = false)
  private String displayName;

  @JsonProperty(value = "description", required = false)
  private String description;

  @JsonProperty(value = "config", required = false)
  private LogicalRouterConfig logicalRouterConfig;

  public RouterType getRouterType() {
    return routerType;
  }

  public void setRouterType(RouterType routerType) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LogicalRouterCreateSpec other = (LogicalRouterCreateSpec) o;
    return Objects.equals(getRouterType(), other.getRouterType())
        && Objects.equals(getDisplayName(), other.getDisplayName())
        && Objects.equals(getDescription(), other.getDescription())
        && Objects.deepEquals(getLogicalRouterConfig(), other.getLogicalRouterConfig());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getRouterType(),
        getDescription(),
        getDisplayName(),
        getLogicalRouterConfig());
  }

  @Override
  public String toString() {
    return ToStringHelper.jsonObjectToString(this);
  }
}
