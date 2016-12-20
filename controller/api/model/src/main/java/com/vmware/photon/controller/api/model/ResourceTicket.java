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

import javax.validation.Valid;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource ticket API representation.
 */
@ApiModel(value = "Tenant-level resource ticket")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTicket extends VisibleModel {

  public static final String KIND = "resource-ticket";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"resource-ticket\"", required = true)
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Tenant id", required = true)
  private String tenantId;

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Ticket limits", required = true)
  private List<QuotaLineItem> limits = new ArrayList<>();

  @Valid
  @JsonProperty
  @ApiModelProperty(value = "Ticket usage", required = true)
  private List<QuotaLineItem> usage = new ArrayList<>();

  @Override
  public String getKind() {
    return kind;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public List<QuotaLineItem> getUsage() {
    return usage;
  }

  public void setUsage(List<QuotaLineItem> quotaLineItems) {
    usage = new ArrayList<>(quotaLineItems);
  }

  public List<QuotaLineItem> getLimits() {
    return limits;
  }

  public void setLimits(List<QuotaLineItem> quotaLineItems) {
    limits = new ArrayList<>(quotaLineItems);

    if (usage.isEmpty()) {
      for (QuotaLineItem qli : quotaLineItems) {
        usage.add(new QuotaLineItem(qli.getKey(), 0.0, qli.getUnit()));
      }
    }
  }

}
