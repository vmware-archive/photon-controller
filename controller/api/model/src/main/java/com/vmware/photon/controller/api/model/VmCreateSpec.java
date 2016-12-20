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

import com.vmware.photon.controller.api.model.base.Flavorful;
import com.vmware.photon.controller.api.model.base.Named;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A VM is created using a JSON payload that maps to this class. From a JSON
 * perspective, this looks like a subset of the {@link Vm} class.
 */
@ApiModel(value = "A class used as the payload when creating a VM.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmCreateSpec implements Flavorful, Named {

  public static final String KIND = "vm";
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name of the VM.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified vm name does not match pattern: " + Named.PATTERN)
  private String name;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired flavor of the VM.",
      required = true)
  private String flavor;
  @JsonProperty
  @ApiModelProperty(value = "This property specifies the set of tags to attach to the VM on creation.",
      required = false)
  private Set<String> tags = new HashSet<>();
  @JsonProperty
  @ApiModelProperty(value = "The id of the source image to be used to create VM from.",
      required = true)
  private String sourceImageId;
  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Supplies this list of disks attached to the VM. During VM creation, this list contains " +
      "the list of disks that should be created and attached to the VM.",
      required = true)
  private List<AttachedDiskCreateSpec> attachedDisks = new ArrayList<>();
  @JsonProperty
  @ApiModelProperty(value = "VM environment", required = false)
  private Map<String, String> environment = new HashMap<>();
  @JsonProperty
  @ApiModelProperty(value = "Locality parameters provide a hint that may help the placement engine " +
      "optimize placement of a VM with respect to an independent disk.", required = false)
  private List<LocalitySpec> affinities = new ArrayList<>();
  @JsonProperty
  @ApiModelProperty(value = "ids of subnets to place vm on", required = false)
  private List<String> subnets;

  @JsonProperty
  public String getKind() {
    return KIND;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFlavor() {
    return flavor;
  }

  public void setFlavor(String flavor) {
    this.flavor = flavor;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public String getSourceImageId() {
    return this.sourceImageId;
  }

  public void setSourceImageId(String sourceImageId) {
    this.sourceImageId = sourceImageId;
  }

  public List<AttachedDiskCreateSpec> getAttachedDisks() {
    return attachedDisks;
  }

  public void setAttachedDisks(List<AttachedDiskCreateSpec> attachedDisks) {
    this.attachedDisks = attachedDisks;
  }

  public void addDisk(AttachedDiskCreateSpec disk) {
    attachedDisks.add(disk);
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public List<LocalitySpec> getAffinities() {
    return affinities;
  }

  public void setAffinities(List<LocalitySpec> affinities) {
    this.affinities = affinities;
  }

  public List<String> getSubnets() {
    return subnets;
  }

  public void setSubnets(List<String> subnets) {
    this.subnets = subnets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VmCreateSpec other = (VmCreateSpec) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(flavor, other.flavor) &&
        Objects.equals(sourceImageId, other.sourceImageId) &&
        Objects.equals(tags, other.tags) &&
        Objects.equals(attachedDisks, other.attachedDisks) &&
        Objects.equals(subnets, other.subnets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, flavor, sourceImageId, tags, attachedDisks, subnets);
  }

  @Override
  public String toString() {
    return String.format("%s, %s", name, flavor);
  }

}
