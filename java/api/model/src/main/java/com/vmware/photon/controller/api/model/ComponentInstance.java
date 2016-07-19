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

import com.vmware.photon.controller.api.model.constraints.DomainOrIP;
import com.vmware.photon.controller.status.gen.StatusType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Information of an instance of a single component.
 */
@ApiModel(value = "Information of an instance of a single component")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentInstance {

  @JsonProperty
  @ApiModelProperty(value = "IP Address of instance", required = true)
  @DomainOrIP
  private String address;

  @JsonProperty
  @ApiModelProperty(value = "Status of component instance", required = true)
  @NotNull
  private StatusType status;

  @JsonProperty
  @ApiModelProperty(value = "Detailed message regarding a component instance", required = false)
  private String message;

  @JsonProperty
  @ApiModelProperty(value = "Detailed stats of a component instance", required = false)
  private Map<String, String> stats;

  @JsonProperty
  @ApiModelProperty(value = "Detailed build information of a component instance", required = false)
  private String buildInfo;

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public StatusType getStatus() {
    return status;
  }

  public void setStatus(StatusType status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, String> getStats() {
    return stats;
  }

  public void setStats(Map<String, String> stats) {
    this.stats = stats;
  }

  public String getBuildInfo() {
    return buildInfo;
  }

  public void setBuildInfo(String buildInfo) {
    this.buildInfo = buildInfo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ComponentInstance other = (ComponentInstance) o;

    return Objects.equals(status, other.status) &&
        Objects.equals(message, other.message) &&
        Objects.equals(address, other.address) &&
        Objects.equals(stats, other.stats) &&
        Objects.equals(buildInfo, other.buildInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, message, address, stats);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("status", status)
        .add("message", message)
        .add("stats", stats)
        .add("address", address)
        .add("buildInfo", buildInfo)
        .toString();
  }
}
