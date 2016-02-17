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

package com.vmware.photon.controller.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

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
  private String storeEndpoint;

  @JsonProperty
  @ApiModelProperty(value = "The Stats Server Port", required = false)
  private Integer port;

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

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
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
        && Objects.equals(this.getPort(), other.getPort());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getEnabled(),
        this.getStoreEndpoint(),
        this.getPort());
  }

  @Override
  public String toString() {
    return toStringHelper().toString();
  }

  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("enabled", this.getEnabled())
        .add("storeEndpoint", this.getStoreEndpoint())
        .add("port", this.getPort());
  }
}
