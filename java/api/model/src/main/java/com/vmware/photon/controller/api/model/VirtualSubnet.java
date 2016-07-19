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

import com.vmware.photon.controller.api.model.base.VisibleModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * VirtualNetwork represents an NSX subnet returned from api-fe to users.
 */
@ApiModel(value = "This class represents an NSX subnet")
public class VirtualSubnet extends VisibleModel {

  public static final String KIND = "virtualSubnet";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"virtualSubnet\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Description to the virtual subnet", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the subnet",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  @NotNull
  private SubnetState state;

  @JsonProperty
  @ApiModelProperty(value = "Whether allow the VMs on this subnet to access Internet",
      allowableValues = RoutingType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private RoutingType routingType;

  @JsonProperty
  @ApiModelProperty(value = "Indicates whether the subnet is default", required = false)
  private Boolean isDefault;

  @JsonProperty
  @ApiModelProperty(value = "CIDR of the virtual network", required = false)
  private String cidr;

  @JsonProperty
  @ApiModelProperty(value = "Smallest dynamic IP of the available dynamic IP range", required = false)
  private String lowIpDynamic;

  @JsonProperty
  @ApiModelProperty(value = "Biggest dynamic IP of the available dynamic IP range", required = false)
  private String highIpDynamic;

  @JsonProperty
  @ApiModelProperty(value = "Smallest static IP of the available static IP range", required = false)
  private String lowIpStatic;

  @JsonProperty
  @ApiModelProperty(value = "Biggest static IP of the available static IP range", required = false)
  private String highIpStatic;

  @JsonProperty
  @ApiModelProperty(value = "List of IPs reserved for infrastructure use.", required = false)
  private List<String> reservedIpList;

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

  public SubnetState getState() {
    return state;
  }

  public void setState(SubnetState state) {
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

  public String getCidr() {
    return cidr;
  }

  public void setCidr(String cidr) {
    this.cidr = cidr;
  }

  public String getLowIpDynamic() {
    return lowIpDynamic;
  }

  public void setLowIpDynamic(String lowIpDynamic) {
    this.lowIpDynamic = lowIpDynamic;
  }

  public String getHighIpDynamic() {
    return highIpDynamic;
  }

  public void setHighIpDynamic(String highIpDynamic) {
    this.highIpDynamic = highIpDynamic;
  }

  public String getLowIpStatic() {
    return lowIpStatic;
  }

  public void setLowIpStatic(String lowIpStatic) {
    this.lowIpStatic = lowIpStatic;
  }

  public String getHighIpStatic() {
    return highIpStatic;
  }

  public void setHighIpStatic(String highIpStatic) {
    this.highIpStatic = highIpStatic;
  }

  public List<String> getReservedIpList() {
    return reservedIpList;
  }

  public void setReservedIpList(List<String> reservedIpList) {
    this.reservedIpList = reservedIpList;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    VirtualSubnet other = (VirtualSubnet) o;

    return Objects.equals(this.getName(), other.getName())
        && Objects.equals(this.description, other.description)
        && Objects.equals(this.state, other.state)
        && Objects.equals(this.routingType, other.routingType)
        && Objects.equals(this.isDefault, other.isDefault)
        && Objects.equals(this.cidr, other.cidr)
        && Objects.equals(this.lowIpDynamic, other.lowIpDynamic)
        && Objects.equals(this.highIpDynamic, other.highIpDynamic)
        && Objects.equals(this.lowIpStatic, other.lowIpStatic)
        && Objects.equals(this.highIpStatic, other.highIpStatic);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.description,
        this.state,
        this.routingType,
        this.isDefault,
        this.cidr,
        this.lowIpDynamic,
        this.highIpDynamic,
        this.lowIpStatic,
        this.highIpStatic);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", getName())
        .add("description", description)
        .add("state", state)
        .add("routingType", routingType)
        .add("isDefault", isDefault)
        .add("cide", cidr)
        .add("lowIpDynamic", lowIpDynamic)
        .add("highIpDynamic", highIpDynamic)
        .add("lowIpStatic", lowIpStatic)
        .add("highIpStatic", highIpStatic)
        .toString();
  }
}
