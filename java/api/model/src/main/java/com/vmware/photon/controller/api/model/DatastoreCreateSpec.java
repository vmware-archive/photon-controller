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

import java.util.Objects;
import java.util.Set;

/**
 * Datastore API representation.
 */
@ApiModel(value = "A class used as the payload when creating a datastore.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatastoreCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "Name of the Datastore", required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Tags", required = true)
  @NotNull
  @Size(min = 1)
  private Set<String> tags;

  public DatastoreCreateSpec() {
  }

  public DatastoreCreateSpec(String name, Set<String> tags) {
    setName(name);
    setTags(tags);
  }

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
    this.tags = ImmutableSet.copyOf(tags);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DatastoreCreateSpec other = (DatastoreCreateSpec) o;

    return Objects.equals(name, other.name) &&
        Objects.equals(tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, tags);
  }
}
