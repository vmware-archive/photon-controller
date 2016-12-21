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
 * This is the representation of a cluster for a GET request.
 *
 * See ClusterCreateSpec to see how a cluster is created with a POST request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cluster extends Base implements Named {

  public static final String KIND = "cluster";
  @JsonProperty
  @ApiModelProperty(value = "The name of the cluster",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified cluster name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "The cluster type",
      allowableValues = ClusterType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private ClusterType type;

  @JsonProperty
  @ApiModelProperty(value = "The cluster state",
      required = true)
  @NotNull
  private ClusterState state;

  @JsonProperty
  @ApiModelProperty(value = "The id of the project that this cluster belongs to.")
  @NotNull
  @Size(min = 1)
  private String projectId;

  @JsonProperty
  @ApiModelProperty(value = "Deprecated, use workerCount instead ", required = false)
  @Min(0)
  private int slaveCount;

  @JsonProperty
  @ApiModelProperty(value = "The number of worker VMs in the cluster.", required = false)
  @Min(1)
  private int workerCount;

  @JsonProperty
  @ApiModelProperty(value = "This is the name of the flavor used to make the master VM(s).")
  private String masterVmFlavorName;

  @JsonProperty
  @ApiModelProperty(value = "This is the name of the flavor used to make all the VMs other "
      + "than the master (e.g. the workers).")
  private String otherVmFlavorName;

  @JsonProperty
  @ApiModelProperty(value = "When the cluster state is either FATAL_ERROR or RECOVERABLE_ERROR, "
      + "this contains the reason for the error.")
  private String errorReason;

  @JsonProperty
  @ApiModelProperty(value = "Id of the image used to create the cluster", required = false)
  private String imageId;

  @JsonProperty
  @ApiModelProperty(value = "A table of extra properties describing the cluster: "
      + "please see the documentation for the complete list.", required = true)
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
    return workerCount;
  }

  public void setSlaveCount(int slaveCount) {
    this.workerCount = slaveCount;
  }

  public int getWorkerCount() {
    return workerCount;
  }

  public void setWorkerCount(int workerCount) {
    this.workerCount = workerCount;
  }

  public void setMasterVmFlavorName(String masterVmFlavor) {
    this.masterVmFlavorName = masterVmFlavor;
  }

  public String getMasterVmFlavorName() {
    return this.masterVmFlavorName;
  }

  public void setOtherVmFlavorName(String otherVmFlavorName) {
    this.otherVmFlavorName = otherVmFlavorName;
  }

  public String getOtherVmFlavorName() {
    return this.otherVmFlavorName;
  }

  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  public String getImageId() {
    return this.imageId;
  }

  public void setErrorReason(String errorReason) {
    this.errorReason = errorReason;
  }

  public String getErrorReason() {
    return this.errorReason;
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
        Objects.equals(workerCount, other.workerCount) &&
        Objects.equals(masterVmFlavorName, other.masterVmFlavorName) &&
        Objects.equals(otherVmFlavorName, other.otherVmFlavorName) &&
        Objects.equals(imageId, other.imageId) &&
        Objects.equals(extendedProperties, other.extendedProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        name, type, projectId, workerCount, masterVmFlavorName, otherVmFlavorName, imageId, extendedProperties);
  }

  @Override
  public String toString() {
    return super.toStringHelper()
        .add("name", name)
        .add("type", type)
        .add("state", state)
        .add("projectId", projectId)
        .add("workerCount", workerCount)
        .add("masterVmFlavorName", masterVmFlavorName)
        .add("otherVmFlavorName", otherVmFlavorName)
        .add("imageId", imageId)
        .add("errorReason", errorReason)
        .add("extendedProperties", extendedProperties)
        .toString();
  }
}
