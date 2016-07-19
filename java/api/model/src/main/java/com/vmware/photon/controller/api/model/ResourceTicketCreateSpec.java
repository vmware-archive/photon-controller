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

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resource ticket API representation.
 */
@ApiModel(value = "Tenant resource ticket creation spec")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTicketCreateSpec implements Named {

  @ApiModelProperty(value = "Ticket name", required = true)
  @JsonProperty
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN,
      message = ": The specified resource ticket name does not match pattern: " + Named.PATTERN)
  private String name;

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Ticket limits", required = true)
  @Size(min = 1)
  private List<QuotaLineItem> limits = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<QuotaLineItem> getLimits() {
    return limits;
  }

  public void setLimits(List<QuotaLineItem> quotaLineItems) {
    limits = new ArrayList<>(quotaLineItems);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ResourceTicketCreateSpec other = (ResourceTicketCreateSpec) o;

    return Objects.equals(name, other.getName()) && Objects.equals(limits, other.getLimits());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, limits);
  }
}
