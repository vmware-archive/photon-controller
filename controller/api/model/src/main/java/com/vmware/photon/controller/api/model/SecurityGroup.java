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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Objects;

/**
 * Represent the security groups of a tenant or project.
 */
@ApiModel(value = "This class represents a single security group.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityGroup {

  @JsonProperty
  @ApiModelProperty(value = "Fully qualified name of the security group.", required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Flag indicating if this security group was inherited from parent resource.")
  private boolean inherited;

  public SecurityGroup() {
  }

  public SecurityGroup(String name, boolean inherited) {
    this.name = name;
    this.inherited = inherited;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isInherited() {
    return inherited;
  }

  public void setInherited(boolean inherited) {
    this.inherited = inherited;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SecurityGroup other = (SecurityGroup) o;

    return Objects.equals(this.getName(), other.getName())
        && this.isInherited() == other.isInherited();
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(getClass())
        .add("Name", name)
        .add("Inherited", inherited)
        .toString();
  }
}
