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

import java.util.List;
import java.util.Objects;

/**
 * Flavor describes the cost and possibly other properties of an instance of a
 * particular kind. Every time an infrastructure object (VM, disk, network etc.)
 * needs to be created, the end user provides its flavor, so API can look up the
 * details of the flavor.
 */
@ApiModel(value = "A class used as the payload when creating a Flavor.", description = "A flavor is created by "
    + "POST to /v1/flavors with this entity as the payload.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlavorCreateSpec implements Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name for the flavor. This value is used as the flavor "
      + "property when creating VMs, specifying attached disks, etc.", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified flavor name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the kind for the flavor. Acceptable value is "
      + "persistent-disk, ephemeral-disk or vm.", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = "(persistent\\-disk|ephemeral\\-disk|vm)",
      message = ": The specified kind name does not match pattern: (persistent\\-disk|ephemeral\\-disk|vm)")
  private String kind;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the base cost, in terms of various quota keys, for objects of "
      + "this flavor.", required = true)
  @NotNull
  @Size(min = 1)
  private List<QuotaLineItem> cost;

  public FlavorCreateSpec() {
    // Needed for object mapper and JSON deserialization
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public List<QuotaLineItem> getCost() {
    return cost;
  }

  public void setCost(List<QuotaLineItem> cost) {
    this.cost = cost;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FlavorCreateSpec other = (FlavorCreateSpec) o;

    return Objects.equals(this.name, other.name)
        && Objects.equals(this.kind, other.kind)
        && Objects.equals(this.cost, other.cost);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, kind, cost);
  }
}
