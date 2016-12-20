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

import com.vmware.photon.controller.status.gen.StatusType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Overall System Status Structure.
 */
@ApiModel(value = "This class represents the status of overall system")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemStatus {

  @JsonProperty
  @ApiModelProperty(value = "Statues of components in system", required = true)
  @Valid
  @NotNull
  private List<ComponentStatus> components = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty(value = "Status of system", required = true)
  @NotNull
  private StatusType status;

  public SystemStatus() {
  }

  public List<ComponentStatus> getComponents() {
    return components;
  }

  public void setComponents(List<ComponentStatus> components) {
    this.components = components;
  }

  public StatusType getStatus() {
    return status;
  }

  public void setStatus(StatusType status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SystemStatus other = (SystemStatus) o;

    return Objects.equals(status, other.status) && Objects.equals(components, other.components);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, components);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("status", status)
        .add("components", components)
        .toString();
  }
}
