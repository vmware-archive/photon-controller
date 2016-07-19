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
import com.wordnik.swagger.annotations.ApiModelProperty;
import static com.google.common.base.Objects.toStringHelper;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Map;
import java.util.Objects;

/**
 * This class represents the creation spec of a cluster.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterCreateSpec implements Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the cluster name.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified name does not match pattern: " + Named.PATTERN)
  private String name;

  @Enumerated(EnumType.STRING)
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the cluster type.",
      allowableValues = ClusterType.ALLOWABLE_VALUES, required = true)
  @NotNull
  private ClusterType type;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired VM flavor of " +
      "the component. If omitted, default flavor will be used.", required = false)
  @Size(min = 0, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified vmFlavor name does not match pattern: " + Named.PATTERN)
  private String vmFlavor;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired disk flavor " +
      "of the component. Each VM is attached one disk.  If omitted, default flavor " +
      "will be used.", required = false)
  @Size(min = 0, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified diskFlavor name does not match pattern: " + Named.PATTERN)
  private String diskFlavor;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired network id " +
      "of the component. If omitted, default network will be used.", required = false)
  private String vmNetworkId;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired number of slave VMs " +
      "in the cluster.", required = true)
  @Min(1)
  @Max(1000)
  private int slaveCount;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the size of batch expansion for slave VMs.",
  required = false)
  private int slaveBatchExpansionSize;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies extended properties needed by " +
      "various cluster types.", required = true)
  @NotNull
  @Size(min = 1)
  private Map<String, String> extendedProperties;

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

  public String getVmFlavor() {
    return vmFlavor;
  }

  public void setVmFlavor(String vmFlavor) {
    this.vmFlavor = vmFlavor;
  }

  public String getDiskFlavor() {
    return diskFlavor;
  }

  public void setDiskFlavor(String diskFlavor) {
    this.diskFlavor = diskFlavor;
  }

  public String getVmNetworkId() {
    return vmNetworkId;
  }

  public void setVmNetworkId(String vmNetworkId) {
    this.vmNetworkId = vmNetworkId;
  }

  public int getSlaveCount() {
    return slaveCount;
  }

  public void setSlaveCount(int slaveCount) {
    this.slaveCount = slaveCount;
  }

  public int getSlaveBatchExpansionSize() {
    return slaveBatchExpansionSize;
  }

  public void setSlaveBatchExpansionSize(int slaveBatchExpansionSize) {
    this.slaveBatchExpansionSize = slaveBatchExpansionSize;
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

    ClusterCreateSpec other = (ClusterCreateSpec) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(type, other.type) &&
        Objects.equals(vmFlavor, other.vmFlavor) &&
        Objects.equals(diskFlavor, other.diskFlavor) &&
        Objects.equals(vmNetworkId, other.vmNetworkId) &&
        Objects.equals(slaveCount, other.slaveCount) &&
        Objects.equals(slaveBatchExpansionSize, other.slaveBatchExpansionSize) &&
        Objects.equals(extendedProperties, other.extendedProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, vmFlavor, diskFlavor, vmNetworkId, slaveCount,
        extendedProperties);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("name", name)
        .add("type", type)
        .add("vmFlavor", vmFlavor)
        .add("diskFlavor", diskFlavor)
        .add("vmNetworkId", vmNetworkId)
        .add("slaveCount", slaveCount)
        .add("slaveBatchExpansionSize", slaveBatchExpansionSize)
        .add("extendedProperties", extendedProperties)
        .toString();
  }
}
