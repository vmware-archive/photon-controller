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

import com.vmware.photon.controller.api.base.Named;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * A subnet is created using a JSON payload that maps to this class. From a JSON
 * perspective, this looks like a subset of the {@link Subnet} class.
 */
@ApiModel(value = "A class used as the payload when creating a subnet.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubnetCreateSpec implements Named {

  @JsonProperty
  @ApiModelProperty(value = "Name of subnet", required = true)
  @NotNull
  @Pattern(regexp = Named.PATTERN, message = ": The specified subnet name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Description of subnet", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "PortGroups associated with subnet", required = true)
  @Size(min = 1)
  @NotNull
  private List<String> portGroups;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getPortGroups() {
    return portGroups;
  }

  public void setPortGroups(List<String> portGroups) {
    this.portGroups = portGroups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SubnetCreateSpec other = (SubnetCreateSpec) o;

    return Objects.equals(this.getName(), other.getName()) &&
        Objects.equals(this.getDescription(), other.getDescription()) &&
        Objects.equals(this.getPortGroups(), other.getPortGroups());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getDescription(), getPortGroups());
  }
}
