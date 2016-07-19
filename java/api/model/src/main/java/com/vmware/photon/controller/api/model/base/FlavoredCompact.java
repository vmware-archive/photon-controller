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

package com.vmware.photon.controller.api.model.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * This class extends the BaseCompactRepresentation and is the generic form for
 * flavored items like vms, disks, etc.
 */
@ApiModel(value = "This class is used to represent entities in a more compact form.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlavoredCompact extends BaseCompact {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the kind value (e.g., vm, ephemeral-disk, etc.) for the " +
      "entity being described by an instance of this class.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = PATTERN, message = ": The specified flavored compact name does not match pattern: " + Named.PATTERN)
  protected String kind;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the flavor value (e.g., core-100, etc.) for the " +
      "entity being described by an instance of this class.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = PATTERN)
  protected String flavor;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the state value (e.g., STARTED, STOPPED, etc.) for the " +
      "entity being described by an instance of this class.",
      required = true)
  protected String state;

  public static FlavoredCompact create(String id, String name, String kind, String flavor, String state) {
    FlavoredCompact result = new FlavoredCompact();
    result.setId(id);
    result.setName(name);
    result.setKind(kind);
    result.setFlavor(flavor);
    result.setState(state);
    return result;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getFlavor() {
    return flavor;
  }

  public void setFlavor(String flavor) {
    this.flavor = flavor;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
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

    FlavoredCompact other = (FlavoredCompact) o;

    return Objects.equals(flavor, other.flavor) &&
        Objects.equals(kind, other.kind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), kind, flavor);
  }
}
