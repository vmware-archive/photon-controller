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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.util.List;

/**
 * Project API representation.
 */
@ApiModel(value = "When a project is fetched by ID, this full representation of the project is returned.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project extends VisibleModel {

  public static final String KIND = "project";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"project\"", required = true)
  private String kind = KIND;

  @Valid
  @NotNull
  @JsonProperty
  @ApiModelProperty(value = "This property is the list of VMs in the project. Note, the list of VMs is in a " +
      "compact representation form which contains only the id, name, flavor, and state of the VM.",
      required = true)
  private ProjectTicket resourceTicket;

  @JsonProperty
  @ApiModelProperty(value = "This property is the list of security groups of this project.")
  private List<SecurityGroup> securityGroups;

  @JsonIgnore
  private String tenantId;

  @Override
  public String getKind() {
    return kind;
  }

  public ProjectTicket getResourceTicket() {
    return resourceTicket;
  }

  public void setResourceTicket(ProjectTicket resourceTicket) {
    this.resourceTicket = resourceTicket;
  }

  public List<SecurityGroup> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<SecurityGroup> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
