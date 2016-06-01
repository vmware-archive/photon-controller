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

import com.vmware.photon.controller.api.base.VisibleModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Network is used for interaction between APIFE and users.
 * VMs within the same 'network' can communicate with each other.
 * VMs in different 'networks' may or may not be able to communicate with each other.
 */
@ApiModel(value = "This class represents a network.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Network extends VisibleModel {

  public static final String IPV4_PATTERN =
      "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$";

  public static final String KIND = "network";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"network\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Description of network", required = false)
  private String description;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the network",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  private NetworkState state;

  @JsonProperty
  @ApiModelProperty(value = "PortGroups associated with network", required = true)
  @Size(min = 1)
  @NotNull
  private List<String> portGroups;

  @JsonProperty
  @ApiModelProperty(value = "Indicates whether the network is default for VM creation", required = false)
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

  public List<String> getPortGroups() {
    return portGroups;
  }

  public void setPortGroups(List<String> portGroups) {
    this.portGroups = portGroups;
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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Network other = (Network) o;

    return super.equals(other) &&
        Objects.equals(this.getDescription(), other.getDescription()) &&
        Objects.equals(this.getState(), other.getState()) &&
        Objects.equals(this.getPortGroups(), other.getPortGroups()) &&
        Objects.equals(this.getIsDefault(), other.getIsDefault());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getState(), getDescription(), getPortGroups());
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("state", getState())
        .add("description", getDescription())
        .add("portGroups", getPortGroups())
        .add("isDefault", getIsDefault());
  }
}
