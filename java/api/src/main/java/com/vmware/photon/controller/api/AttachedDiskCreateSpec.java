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

import com.vmware.photon.controller.api.base.Flavorful;
import com.vmware.photon.controller.api.base.Named;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * When creating a VM, the caller passes in an array of AttachedDiskCreateSpec
 * objects. These objects provide enough structure to create and attach a disk
 * to the VM.
 */
@ApiModel(value = "A class used as the payload when creating a AttachedDisk.")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachedDiskCreateSpec implements Flavorful, Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired flavor of the Disk.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified flavor name does not match pattern: " + Named.PATTERN)
  protected String flavor;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name of the Disk. Disk names must be unique within their " +
      "project.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified disk name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired kind of the Disk: ephemeral is the only one " +
      "currently supported",
      required = true)
  @Pattern(regexp = "ephemeral-disk|ephemeral")
  private String kind;

  @JsonProperty
  @ApiModelProperty(value = "This property is the capacity of the disk in GB units. When used in the context of " +
      "attaching an existing disk, this property, if specified, must be valid, but is otherwise ignored. " +
      "Not required if it is boot disk",
      required = false)
  @Min(1)
  private Integer capacityGb;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies that the disk is the boot disk for the VM.",
      required = true)
  private boolean bootDisk = false;

  @JsonProperty
  public String getKind() {
    return this.kind;
  }

  public void setKind(String kind) {
    switch (kind) {
      case "persistent":
        this.kind = PersistentDisk.KIND;
        break;
      case "ephemeral":
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

  public Integer getCapacityGb() {
    return capacityGb;
  }

  public void setCapacityGb(Integer capacityGb) {
    this.capacityGb = capacityGb;
  }

  public boolean isBootDisk() {
    return bootDisk;
  }

  public void setBootDisk(boolean bootDisk) {
    this.bootDisk = bootDisk;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AttachedDiskCreateSpec other = (AttachedDiskCreateSpec) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(flavor, other.flavor) &&
        Objects.equals(kind, other.kind) &&
        Objects.equals(capacityGb, other.capacityGb) &&
        Objects.equals(bootDisk, other.bootDisk);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, kind, flavor, capacityGb, bootDisk);
  }

  @Override
  public String toString() {
    return String.format("%s, %s, %s, %s, %s", name, kind, flavor, capacityGb, bootDisk);
  }

}
