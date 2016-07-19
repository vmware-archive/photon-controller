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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.base.Named;
import com.vmware.photon.controller.api.model.constraints.PowerOfTwo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import java.util.Objects;

/**
 * VirtualNetworkCreateSpec is used to represent the payload to create an NSX network.
 */
@ApiModel(value = "This class represents the payload to create an nsx network.")
public class VirtualNetworkCreateSpec implements Named {
  public static final int DEFAULT_MIN_NETWORK_SIZE = 8;

  @JsonProperty
  @ApiModelProperty(value = "Name of the virtual network", required = true)
  @NotNull
  @Pattern(regexp = Named.PATTERN,
      message = ": The specific virtual network name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Description to the virtual network", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "Whether allow the VMs on this network to access Internet",
      allowableValues = RoutingType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private RoutingType routingType;

  @JsonProperty
  @ApiModelProperty(value = "This property is the size of the virtual network", required = true)
  @Min(DEFAULT_MIN_NETWORK_SIZE)
  @PowerOfTwo
  private int size;

  @JsonProperty
  @ApiModelProperty(value = "This property is the count of static IPs reserved from the size of the virtual network",
      required = false)
  private int reservedStaticIpSize;

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

  public RoutingType getRoutingType() {
    return routingType;
  }

  public void setRoutingType(RoutingType routingType) {
    this.routingType = routingType;
  }

  public int getReservedStaticIpSize() {
    return reservedStaticIpSize;
  }

  public void setReservedStaticIpSize(int reservedStaticIpSize) {
    this.reservedStaticIpSize = reservedStaticIpSize;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    VirtualNetworkCreateSpec other = (VirtualNetworkCreateSpec) o;
    return Objects.equals(this.name, other.name)
        && Objects.equals(this.description, other.description)
        && Objects.equals(this.routingType, other.routingType)
        && Objects.equals(this.size, other.size)
        && Objects.equals(this.reservedStaticIpSize, other.reservedStaticIpSize);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), this.name, this.description, this.routingType, this.size, this.reservedStaticIpSize);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", name)
        .add("description", description)
        .add("routingType", routingType)
        .add("size", size)
        .add("reservedStaticIpSize", reservedStaticIpSize)
        .toString();
  }
}
