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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.base.VisibleModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.Objects;

/**
 * VirtualNetwork represents an NSX network returned from api-fe to users.
 */
@ApiModel(value = "This class represents an NSX network")
public class VirtualNetwork extends VisibleModel {

  public static final String KIND = "virtualNetwork";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"virtualNetwork\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Description to the virtual network", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the network",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  @NotNull
  private NetworkState state;

  @JsonProperty
  @ApiModelProperty(value = "Whether allow the VMs on this network to access Internet",
      allowableValues = RoutingType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private RoutingType routingType;

  @JsonProperty
  @ApiModelProperty(value = "Indicates whether the network is default", required = false)
  private Boolean isDefault;

  @Override
  public String getKind() {
    return kind;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public NetworkState getState() {
    return state;
  }

  public void setState(NetworkState state) {
    this.state = state;
  }

  public RoutingType getRoutingType() {
    return routingType;
  }

  public void setRoutingType(RoutingType routingType) {
    this.routingType = routingType;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    VirtualNetwork other = (VirtualNetwork) o;

    return Objects.equals(this.getName(), other.getName())
        && Objects.equals(this.description, other.description)
        && Objects.equals(this.state, other.state)
        && Objects.equals(this.routingType, other.routingType)
        && Objects.equals(this.isDefault, other.isDefault);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.description,
        this.state,
        this.routingType,
        this.isDefault);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", getName())
        .add("description", description)
        .add("state", state)
        .add("routingType", routingType)
        .add("isDefault", isDefault)
        .toString();
  }
}
