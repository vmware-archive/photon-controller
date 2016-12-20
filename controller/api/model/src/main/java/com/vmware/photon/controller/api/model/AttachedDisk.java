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

import com.vmware.photon.controller.api.model.base.FlavoredCompact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Min;

import java.util.Objects;

/**
 * Attached disk API representation.
 * <p/>
 * When creating a VM, the caller passes in an array of AttachedDisk objects.
 * These objects provide enough structure to create and attach a disk to the
 * VM, or to attach an existing disk. In general, passing null for id and just
 * specifying a name will result in creation of a disk. Passing an id means
 * to attach the specified disk to the VM.
 */
@ApiModel(value = "This class is used during VM creation and VM disk attach to specify a collection of disks " +
    "that are to be attached to the VM.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttachedDisk extends FlavoredCompact {

  /**
   * This property is required when a disk is being created.
   */
  @JsonProperty
  @ApiModelProperty(value = "This property is the capacity of the disk in GB units. When used in the context of " +
      "attaching an existing disk, this property, if specified, must be valid, but is otherwise ignored.",
      required = true)
  @Min(1)
  private int capacityGb;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies that the disk is the boot disk for the VM.",
      required = true)
  private boolean bootDisk = false;

  public static AttachedDisk create(String id, String name, String kind, String flavor,
                                    int capacityGb, boolean isBootDisk) {
    AttachedDisk result = new AttachedDisk();
    result.setId(id);
    result.setName(name);
    result.setKind(kind);
    result.setFlavor(flavor);
    result.setCapacityGb(capacityGb);
    result.setBootDisk(isBootDisk);
    result.setState(DiskState.CREATING.toString());
    return result;
  }

  public int getCapacityGb() {
    return capacityGb;
  }

  public void setCapacityGb(int capacity) {
    this.capacityGb = capacity;
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
    if (!super.equals(o)) {
      return false;
    }

    AttachedDisk other = (AttachedDisk) o;

    return Objects.equals(bootDisk, other.bootDisk) &&
        Objects.equals(capacityGb, other.capacityGb);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), capacityGb, bootDisk);
  }
}
