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
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Visible model API representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class VisibleModel extends Model implements Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the project-scoped name of the entity.",
      required = true)
  @Size(min = 1, max = 63)
  @NotNull
  @Pattern(regexp = Named.PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the set of machine-tags attached to the entity",
      required = false)
  private Set<String> tags = new HashSet<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    this.tags = tags;
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

    VisibleModel other = (VisibleModel) o;

    return super.equals(other) &&
        Objects.equals(name, other.name) &&
        Objects.equals(tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, tags);
  }
}
