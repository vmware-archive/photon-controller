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

import com.vmware.photon.controller.api.model.base.Named;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Availability zone API representation.
 */
@ApiModel(value = "A class used as the payload when creating availability zone.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AvailabilityZoneCreateSpec implements Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name for the availability zone.", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN,
      message = ": The specified availability zone name does not match pattern: " + Named.PATTERN)
  private String name;

  public AvailabilityZoneCreateSpec() {
    // Needed for object mapper and JSON deserialization
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AvailabilityZoneCreateSpec other = (AvailabilityZoneCreateSpec) o;

    return Objects.equals(this.name, other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", getName())
        .toString();
  }
}
