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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A Disk is created using a JSON payload that maps to this class. From a JSON
 * perspective, this looks like a subset of the {@link BaseDisk} class.
 */
@ApiModel(value = "A class used as the payload when creating a Disk.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiskCreateSpec implements Flavorful, Named {

  public static final int MIN_NAME_LENGTH = 1;
  public static final int MAX_NAME_LENGTH = 63;

  public static final int MIN_CAPACITY_IN_GB = 1;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name of the Disk. Disk names must be unique within their " +
      "project.",
      required = true)
  @NotNull
  @Size(min = MIN_NAME_LENGTH, max = MAX_NAME_LENGTH)
  @Pattern(regexp = Named.PATTERN, message = ": The specified disk name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired kind of the Disk: persistent is the only one " +
      "currently supported",
      required = true)
  @Pattern(regexp = PersistentDisk.KIND + "|" + PersistentDisk.KIND_SHORT_FORM,
      message = ": The specified kind does not match : " + PersistentDisk.KIND + "|" + PersistentDisk.KIND_SHORT_FORM)
  private String kind;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired flavor of the Disk.",
      required = true)
  @NotNull
  private String flavor;

  @JsonProperty
  @ApiModelProperty(value = "This property is the capacity of the disk in GB units. When used in the context of " +
      "attaching an existing disk, this property, if specified, must be valid, but is otherwise ignored.",
      required = true)
  @Min(MIN_CAPACITY_IN_GB)
  private int capacityGb;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the set of machine-tags to attach to the Disk on creation",
      required = false)
  private Set<String> tags = new HashSet<>();

  @JsonProperty
  @ApiModelProperty(value = "Locality parameters provide information that will guide the scheduler in placement" +
      " of the disk with respect to an existing VM. The only supported value for the 'kind' in the " +
      "LocalitySpec is 'vm'. ", required = false)
  private List<LocalitySpec> affinities = new ArrayList<>();

  @JsonProperty
  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    switch (kind) {
      case PersistentDisk.KIND_SHORT_FORM:
        this.kind = PersistentDisk.KIND;
        break;
      case EphemeralDisk.KIND_SHORT_FORM:
        this.kind = EphemeralDisk.KIND;
        break;
      default:
        this.kind = kind;
    }
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

  public int getCapacityGb() {
    return capacityGb;
  }

  public void setCapacityGb(int capacityGb) {
    this.capacityGb = capacityGb;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
  }

  public List<LocalitySpec> getAffinities() {
    return affinities;
  }

  public void setAffinities(List<LocalitySpec> affinities) {
    this.affinities = affinities;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DiskCreateSpec other = (DiskCreateSpec) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(kind, other.kind) &&
        Objects.equals(flavor, other.flavor) &&
        Objects.equals(capacityGb, other.capacityGb) &&
        Objects.equals(tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, kind, flavor, capacityGb, tags);
  }

  @Override
  public String toString() {
    return String.format("%s, %s, %s", name, kind, flavor, capacityGb);
  }

}
