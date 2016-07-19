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

import javax.validation.constraints.Pattern;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * VM operations are requested via POST to /vms/{id}/operations. The payload of the POST is the
 * VmOperation object. This extensible structure contains a simple/flat operation code (Start, Restart,
 * Suspend, etc.) along with optional, operation code specific arguments stored as a hash.
 */
@ApiModel(value = "Requested VM operation")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmOperation {

  public static final Set<Operation> VALID_OPERATIONS = ImmutableSet.of(
      Operation.START_VM,
      Operation.STOP_VM,
      Operation.RESTART_VM,
      Operation.SUSPEND_VM,
      Operation.RESUME_VM
  );

  @JsonProperty
  @ApiModelProperty(value = "VM operation to be performed", required = true,
      allowableValues = "START_VM,STOP_VM,RESTART_VM,SUSPEND_VM,RESUME_VM")
  @Pattern(regexp = "START_VM|STOP_VM|RESTART_VM|SUSPEND_VM|RESUME_VM")
  private String operation;

  /**
   * The arguments map is a simple string/string map used to pass simple arguments along with an operation. E.g,
   * to snapshot a VM you need to specify in the arguments, the disk that you want to land the snapshot on.
   */
  @JsonProperty
  @ApiModelProperty(value = "Operation arguments")
  private Map<String, String> arguments = new HashMap<>();

  public VmOperation() {
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public Map<String, String> getArguments() {
    return arguments;
  }

  public void setArguments(Map<String, String> arguments) {
    this.arguments = arguments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VmOperation other = (VmOperation) o;

    return Objects.equals(operation, other.operation) &&
        Objects.equals(arguments, other.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operation, arguments);
  }
}
