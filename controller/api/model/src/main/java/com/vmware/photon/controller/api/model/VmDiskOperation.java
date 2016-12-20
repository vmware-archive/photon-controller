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
import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * VM operations are requested via POST to /vms/{id}/disk_attach /vms/{id}/disk_detach. The payload of the POST is the
 * VmDiskOperation object. This extensible structure contains the disk id to be attached/detached
 * along with optional, operation code specific arguments stored as a hash.
 */
@ApiModel(value = "Requested VM operation")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmDiskOperation {

  public static final Set<Operation> VALID_OPERATIONS = ImmutableSet.of(
      Operation.ATTACH_DISK,
      Operation.DETACH_DISK
  );

  @JsonProperty
  @ApiModelProperty(value = "Supplies disk id that is to attach/detach to the VM. " +
      "This is the disk " +
      "that should be attached/detached to/from the VM (only persistent disk is allowed).",
      required = true)
  @NotNull
  @Size(min = 1)
  private String diskId;

  /**
   * The arguments map is a simple string/string map used to pass simple arguments along with an operation. E.g,
   * to snapshot a VM you need to specify in the arguments, the disk that you want to land the snapshot on.
   */
  @JsonProperty
  @ApiModelProperty(value = "Operation arguments")
  private Map<String, String> arguments = new HashMap<>();

  public VmDiskOperation() {
  }

  public Map<String, String> getArguments() {
    return arguments;
  }

  public void setArguments(Map<String, String> arguments) {
    this.arguments = arguments;
  }

  public String getDiskId() {
    return diskId;
  }

  public void setDiskId(String diskId) {
    this.diskId = diskId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VmDiskOperation other = (VmDiskOperation) o;

    return Objects.equals(diskId, other.diskId) &&
        Objects.equals(arguments, other.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(diskId, arguments);
  }
}
