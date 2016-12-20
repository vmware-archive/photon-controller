/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.constraints.StatsDisabled;
import com.vmware.photon.controller.api.model.constraints.StatsEnabled;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

import java.util.Objects;

/**
 * Contains Stats information.
 */
@ApiModel(value = "Contains Stats information")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatsInfo {

  @JsonProperty
  @ApiModelProperty(value = "Flag that indicates if stats is enabled or not", required = true)
  private boolean enabled = false;

  @JsonProperty
  @ApiModelProperty(value = "Url/IP of the Stats Store Endpoint", required = false)
  @Null(groups = {StatsDisabled.class})
  @NotNull(groups = {StatsEnabled.class})
  private String storeEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "The Stats Server Port", required = false)
  @Null(groups = {StatsDisabled.class})
  @NotNull(groups = {StatsEnabled.class})
  private Integer storePort;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the stats store type.",
      allowableValues = StatsStoreType.ALLOWABLE_VALUES, required = false)
  private StatsStoreType storeType;

  public boolean getEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getStoreEndpoint() {
    return storeEndpoint;
  }

  public void setStoreEndpoint(String storeEndpoint) {
    this.storeEndpoint = storeEndpoint;
  }

  public Integer getStorePort() {
    return storePort;
  }

  public void setStorePort(Integer port) {
    this.storePort = port;
  }

  public StatsStoreType getStoreType() {
    return storeType;
  }

  public void setStoreType(StatsStoreType type) {
    this.storeType = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StatsInfo other = (StatsInfo) o;

    return Objects.equals(this.getEnabled(), other.getEnabled())
        && Objects.equals(this.getStoreEndpoint(), other.getStoreEndpoint())
        && Objects.equals(this.getStorePort(), other.getStorePort())
        && Objects.equals(this.getStoreType(), other.getStoreType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getEnabled(),
        this.getStoreEndpoint(),
        this.getStorePort(),
        this.getStoreType());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("enabled", this.getEnabled())
        .add("storeEndpoint", this.getStoreEndpoint())
        .add("storePort", this.getStorePort())
        .add("storeType", this.getStoreType());
  }
}
