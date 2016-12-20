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

import com.vmware.photon.controller.api.model.base.VisibleModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Tenant API representation.
 */
@ApiModel(value = "The tenancy model of Photon Controller is designed to provide tenants with a sub-dividable " +
    "virtual resource pool expressed in terms of quota limits, capabilities, and SLA. " +
    "A tenant contains a collection of projects and resource tickets. Projects are created by carving off " +
    "quota limits from one of the tenant's resource tickets.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tenant extends VisibleModel {

  public static final String KIND = "tenant";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"tenant\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Full ResourceTicket API representation of tenant resource tickets", required = true)
  private List<ResourceTicket> resourceTickets = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty(value = "This property is the list of security groups of this tenant")
  private List<SecurityGroup> securityGroups;

  @Override
  public String getKind() {
    return kind;
  }

  public List<ResourceTicket> getResourceTickets() {
    return resourceTickets;
  }

  public void setResourceTickets(List<ResourceTicket> resourceTickets) {
    this.resourceTickets = resourceTickets;
  }

  public List<SecurityGroup> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<SecurityGroup> securityGroups) {
    this.securityGroups = securityGroups;
  }
}
