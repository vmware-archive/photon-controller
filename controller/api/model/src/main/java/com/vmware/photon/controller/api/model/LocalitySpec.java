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
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * This class is for specifying the affinity field during creating vm and creating disk.
 */

@ApiModel(value = "A class used to specify affinity field when creating vm and creating disk.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalitySpec {

  public static final String VALID_KINDS = "vm|disk|host|datastore|portGroup|availabilityZone";

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the id or the resource part of the locality relationship.",
      required = true)
  @NotNull
  @Size(min = 1)
  private String id;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the kind of the resource part of the locality relationship." +
      " Supported values are: vm, disk.",
      required = true)
  @NotNull
  @Size(min = 1)
  @Pattern(regexp = VALID_KINDS, message = ": The specified kind does not match pattern: " + VALID_KINDS)
  private String kind;

  public LocalitySpec(String name, String kind) {
    this.id = name;
    this.kind = kind;
  }

  public LocalitySpec() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKind() {
    return kind;
  }

  public String setKind(String kind) {
    return this.kind = kind;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LocalitySpec that = (LocalitySpec) o;

    if (!id.equals(that.id)) {
      return false;
    }
    if (!kind.equals(that.kind)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, kind);
  }

  @Override
  public String toString() {
    return String.format("%s, %s", id, kind);
  }
}
