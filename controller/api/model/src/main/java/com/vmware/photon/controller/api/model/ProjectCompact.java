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
import javax.validation.constraints.NotNull;

/**
 * Project list representation is more compact than individual project representation (doesn't contain a list of VMs).
 */
@ApiModel(value = "Project representation in a list of project")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectCompact extends VisibleModel {

  @JsonProperty
  @ApiModelProperty(value = "kind=\"project\"", required = true)
  private String kind = Project.KIND;

  @Valid
  @NotNull
  @JsonProperty
  private ProjectTicket resourceTicket;

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
}
