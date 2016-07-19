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
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Image creation payload.
 */
@ApiModel(value = "A class used as the payload when creating a image.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the name for the image", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified image name does not match pattern: " + Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Image replication type", required = true)
  @NotNull
  private ImageReplicationType replicationType;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ImageReplicationType getReplicationType() {
    return replicationType;
  }

  public void setReplicationType(ImageReplicationType replicationType) {
    this.replicationType = replicationType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ImageCreateSpec other = (ImageCreateSpec) o;

    return Objects.equals(getName(), other.getName()) &&
        Objects.equals(getReplicationType(), other.getReplicationType());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        getName(),
        getReplicationType()
    );
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("name", getName())
        .add("replicationType", getReplicationType())
        .toString();
  }
}
