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

import com.vmware.photon.controller.api.model.QuotaLineItem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Infrastructure API representation.
 */
@ApiModel(value = "This common class is extended by infrastructure objects (i.e., vms, ephemeral-disks, etc.)")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Infrastructure extends Base implements Flavorful, Named {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the project-scoped name of the entity.",
      required = true)
  @Size(min = 1, max = 63)
  @NotNull
  @Pattern(regexp = PATTERN)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the flavor of the entity.",
      required = true)
  private String flavor;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the base cost of the entity. It's important to note that the " +
      "actual cost may be different due to runtime parameters used in the creation of an entity. For instance, " +
      "the base cost of an ephemeral or persistent disk is augmented with a capacity cost at runtime.",
      required = true)
  private List<QuotaLineItem> cost;

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

  public String getFlavor() {
    return flavor;
  }

  public void setFlavor(String flavor) {
    this.flavor = flavor;
  }

  public List<QuotaLineItem> getCost() {
    return cost;
  }

  public void setCost(List<QuotaLineItem> cost) {
    this.cost = cost;
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

    Infrastructure other = (Infrastructure) o;

    return super.equals(other) &&
        Objects.equals(name, other.name) &&
        Objects.equals(flavor, other.flavor) &&
        Objects.equals(cost, other.cost) &&
        Objects.equals(tags, other.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, flavor, cost, tags);
  }
}
