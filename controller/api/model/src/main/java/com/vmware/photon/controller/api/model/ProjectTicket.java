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
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;

import java.util.ArrayList;
import java.util.List;

/**
 * The ProjectTicket API representation is a subset of the tenant level
 * resource ticket. More specifically, these tickets do not expose id's,
 * names, etc. Just usage and limits.
 */
@ApiModel(value = "Project resource ticket")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectTicket {

  @JsonProperty
  @ApiModelProperty(value = "Tenant ticket id", required = true)
  private String tenantTicketId;

  @JsonProperty
  @ApiModelProperty(value = "Tenant ticket name", required = true)
  private String tenantTicketName;

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Project limits", required = true)
  private List<QuotaLineItem> limits = new ArrayList<>();

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Project resource usage", required = true)
  private List<QuotaLineItem> usage = new ArrayList<>();

  public String getTenantTicketId() {
    return tenantTicketId;
  }

  public void setTenantTicketId(String tenantTicketId) {
    this.tenantTicketId = tenantTicketId;
  }

  public String getTenantTicketName() {
    return tenantTicketName;
  }

  public void setTenantTicketName(String tenantTicketName) {
    this.tenantTicketName = tenantTicketName;
  }

  public List<QuotaLineItem> getUsage() {
    return usage;
  }

  public void setUsage(List<QuotaLineItem> usage) {
    this.usage = new ArrayList<>(usage);
  }

  public List<QuotaLineItem> getLimits() {
    return limits;
  }

  public void setLimits(List<QuotaLineItem> limits) {
    this.limits = new ArrayList<>(limits);
  }
}
