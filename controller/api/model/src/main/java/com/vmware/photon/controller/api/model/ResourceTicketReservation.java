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

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * This class is used by project creation. It's purpose is to allow
 * the caller specify the name of a resource ticket within it's tenant
 * and the amount it would like to draw from the ticket.
 */
@ApiModel(value = "During project creation, the API caller must specify a tenant level resource ticket and " +
    "a list a quota line item's it to carve out of the resource ticket. The carve out must contain one line item " +
    "for each limit set in the tenant ticket.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTicketReservation implements Named {

  @JsonProperty
  @ApiModelProperty(value = "Supplies the tenant level resource ticket.",
      required = true)
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN,
      message = ": The specified resource ticket name does not match pattern: " + Named.PATTERN)
  private String name;

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Supplies the list of quota line items to carve from the tenant level ticket. Note, the " +
      "key value of 'subdivide.percent' is used to carve out a percentage of the tenant level ticket's original " +
      "limits. In this mode, the value is the percentage of the ticket that should be used (e.g., 10.0 means 10%).",
      required = true)
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

  public void setLimits(List<QuotaLineItem> limits) {
    if (limits.size() == 0) {
      this.limits = new ArrayList<>();
    } else {
      for (QuotaLineItem qli : limits) {
        this.limits.add(qli);
      }
    }
  }

  /**
   * Ticket can be in the special form saying "carve xx% of the parent resource ticket.
   *
   * @return Percentage of parent ticket that should be given to child ticket, or null if it's not in the proper
   * format for subdivision
   */
  @JsonIgnore
  public Double getSubdividePercentage() {
    if (limits.size() != 1) {
      return null;
    }

    QuotaLineItem limit = limits.get(0);

    if (limit.getKey().equals("subdivide.percent") && QuotaUnit.COUNT.equals(limit.getUnit())) {
      return limit.getValue();
    }

    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ResourceTicketReservation other = (ResourceTicketReservation) o;
    return name.equals(other.name) && limits.equals(other.limits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, limits);
  }
}
