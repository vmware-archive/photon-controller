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

import com.vmware.photon.controller.api.model.base.Base;
import com.vmware.photon.controller.api.model.base.Named;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Map;
import java.util.Objects;

/**
 * This class represents a cluster.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cluster extends Base implements Named {

  public static final String KIND = "cluster";
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the cluster name.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified cluster name does not match pattern: " + Named.PATTERN)
  private String name;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the cluster type.",
      allowableValues = ClusterType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private ClusterType type;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the cluster state.",
      required = true)
  @NotNull
  private ClusterState state;
  @JsonProperty
  @ApiModelProperty(value = "The id of the project that this cluster belongs to.")
  @NotNull
  @Size(min = 1)
  private String projectId;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the number of slave VMs " +
      "in the cluster.", required = true)
  @Min(1)
  private int slaveCount;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies extended properties needed by " +
      "various cluster types.", required = true)
  @NotNull
  @Size(min = 1)
  private Map<String, String> extendedProperties;

  @JsonProperty
  @ApiModelProperty(value = "kind=\"" + KIND + "\"", required = true)
  @Override
  public String getKind() {
    return KIND;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ClusterType getType() {
    return type;
  }

  public void setType(ClusterType type) {
    this.type = type;
  }

  public ClusterState getState() {
    return state;
  }

  public void setState(ClusterState state) {
    this.state = state;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public int getSlaveCount() {
    return slaveCount;
  }

  public void setSlaveCount(int slaveCount) {
    this.slaveCount = slaveCount;
  }

  public Map<String, String> getExtendedProperties() {
    return extendedProperties;
  }

  public void setExtendedProperties(Map<String, String> extendedProperties) {
    this.extendedProperties = extendedProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Cluster other = (Cluster) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(type, other.type) &&
        Objects.equals(state, other.state) &&
        Objects.equals(projectId, other.projectId) &&
        Objects.equals(slaveCount, other.slaveCount) &&
        Objects.equals(extendedProperties, other.extendedProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, type, projectId, slaveCount, extendedProperties);
  }

  @Override
  public String toString() {
    return super.toStringHelper()
        .add("name", name)
        .add("type", type)
        .add("state", state)
        .add("projectId", projectId)
        .add("slaveCount", slaveCount)
        .add("extendedProperties", extendedProperties)
        .toString();
  }
}
