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
import com.vmware.photon.controller.api.model.base.VisibleModel;

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
@ApiModel(value = "This class represents a flavor, the type used to represent portions of the configuration of VMs, " +
    "disks, etc. The flavor indicates a name for the flavor as well as it's quota cost. ")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Flavor extends VisibleModel {
  public static final String KIND = "flavor";

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the kind for the flavor. Acceptable value is "
      + "persistent-disk, ephemeral-disk or vm.", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified kind name does not match pattern: " + Named.PATTERN)
  private String kind;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Flavor",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  private FlavorState state;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the base cost, in terms of various quota keys, for objects of "
      + "this flavor.", required = true)
  @NotNull
  @Size(min = 1)
  private List<QuotaLineItem> cost;

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public FlavorState getState() {
    return state;
  }

  public void setState(FlavorState state) {
    this.state = state;
  }

  public List<QuotaLineItem> getCost() {
    return cost;
  }

  public void setCost(List<QuotaLineItem> cost) {
    this.cost = cost;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    Flavor other = (Flavor) o;

    return super.equals(other)
        && Objects.equals(this.getKind(), other.getKind())
        && Objects.equals(this.getState(), other.getState())
        && Objects.equals(this.getCost(), other.getCost());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getKind(),
        this.getState(),
        this.getCost());
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("state", getState())
        .add("cost", getCost());
  }
}
