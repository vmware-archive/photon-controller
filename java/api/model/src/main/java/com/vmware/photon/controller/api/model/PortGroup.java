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

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Port group API class.
 */
@ApiModel(value = "This class is the full fidelity representation of a PortGroup object.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortGroup extends Base {

  public static final String KIND = "portGroup";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"portGroup\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Port group name", required = true)
  @NotNull
  @Size(min = 1, max = 265)
  private String name;

  @JsonProperty
  @ApiModelProperty(value = "Usage tags", allowableValues = UsageTag.PORTGROUP_USAGES, required = true)
  @NotNull
  @Size(min = 1)
  private List<UsageTag> usageTags;

  public PortGroup() {
  }

  public PortGroup(String name, List<UsageTag> usageTags) {
    this.name = name;
    this.usageTags = usageTags;
  }

  @Override
  public String getKind() {
    return kind;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<UsageTag> getUsageTags() {
    return usageTags;
  }

  public void setUsageTags(List<UsageTag> usageTags) {
    this.usageTags = usageTags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PortGroup)) {
      return false;
    }
    if (o == null || !super.equals(o)) {
      return false;
    }

    PortGroup that = (PortGroup) o;

    return Objects.equals(name, that.name)
        && Objects.equals(usageTags, that.usageTags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        name,
        usageTags);
  }

  @Override
  protected ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("name", name)
        .add("usageTags", usageTags);
  }
}
