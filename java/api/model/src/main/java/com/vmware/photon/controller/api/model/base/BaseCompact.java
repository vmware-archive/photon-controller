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
 * Many API representation objects contain lists of sub-objects. E.g., a tenant has a list of projects,
 * a project has a list of vms, disks, etc. Instead of accepting/returning full fidelity objects through
 * the API for these lists, the APIs typically accept/return a compact representation including name, id, and
 * depending on the object kind, and flavor.
 * <p>
 * This class is the base form of the compact representation which is simply name and id.
 */
@ApiModel(value = "Compact API representation (id and name only)")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseCompact implements Named {
  @JsonProperty
  @ApiModelProperty(value = "id", required = true)
  protected String id;

  @JsonProperty
  @ApiModelProperty(value = "name", required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN)
  protected String name;

  public static BaseCompact create(String id, String name) {
    BaseCompact result = new BaseCompact();
    result.setId(id);
    result.setName(name);
    return result;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BaseCompact other = (BaseCompact) o;

    return Objects.equals(id, other.id) && Objects.equals(name, other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }
}
