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

import com.vmware.photon.controller.api.model.base.Base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects.ToStringHelper;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;

/**
 * Datastore API representation.
 */
@ApiModel(value = "The datastore model of Photon Controller defines " +
    "datastores of Photon Controller data-centers.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Datastore extends Base {
  public static final String KIND = "datastore";
  @JsonProperty
  @ApiModelProperty(value = "kind=\"datastore\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "A list of tags", required = true)
  @NotNull
  @Size(min = 1)
  private Set<String> tags;

  @JsonProperty
  @ApiModelProperty(value = "Type of datastore", required = true)
  @NotNull
  @Size(min = 1)
  private String type;

  @Override
  public String getKind() {
    return kind;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> tags) {
    if (tags != null) {
      this.tags = tags;
    }
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    if (type != null) {
      this.type = type;
    }
  }

  @Override
  protected ToStringHelper toStringHelper() {
    ToStringHelper result = super.toStringHelper();
    if (StringUtils.isNotBlank(tags.toString())) {
      result.add("tags", tags.toString());
    }
    if (StringUtils.isNotBlank(type)) {
      result.add("type", type);
    }
    return result;
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

    Datastore other = (Datastore) o;

    return Objects.equals(tags, other.tags) &&
        Objects.equals(type, other.type);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        super.hashCode(),
        kind,
        tags,
        type);
  }
}
