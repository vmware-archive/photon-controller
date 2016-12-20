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

import java.util.List;
import java.util.Objects;

/**
 * A project is created using a JSON payload that maps to this class.
 * From a JSON perspective, this looks like a small subset of a project,
 * e.g., the project's name, resource request, etc.
 */
@ApiModel(value = "A class used as the payload when creating a project.", description = "A project is created by " +
    "POST to /v1/tenants/{id}/projects with this entity as the payload.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectCreateSpec implements Named {

  @ApiModelProperty(value = "This property specifies the name of the project. Project names are scoped to a tenant, " +
      "and duplicate project names within the same tenant are not permitted.",
      required = true)
  @JsonProperty
  @NotNull
  @Size(min = 1, max = 63)
  @Pattern(regexp = Named.PATTERN, message = ": The specified project name does not match pattern: " + Named.PATTERN)
  private String name;

  @ApiModelProperty(value = "This property is used to specify which tenant resource ticket the project will draw" +
      "from, and how much it will consume.",
      required = true)
  @Valid
  @NotNull
  @JsonProperty
  private ResourceTicketReservation resourceTicket;

  @JsonProperty
  @ApiModelProperty(value = "SecurityGroups associated with project")
  @Size(min = 0)
  private List<String> securityGroups;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ResourceTicketReservation getResourceTicket() {
    return resourceTicket;
  }

  public void setResourceTicket(ResourceTicketReservation resourceTicket) {
    this.resourceTicket = resourceTicket;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProjectCreateSpec other = (ProjectCreateSpec) o;

    return Objects.equals(name, other.getName()) &&
        Objects.equals(resourceTicket, other.getResourceTicket());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, resourceTicket);
  }

}
