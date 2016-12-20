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
import static com.google.common.base.Objects.toStringHelper;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.Objects;

/**
 * Set host's availability zone is requested via POST to /hosts/{id}/set_availability_zone. The payload of the POST
 * is the HostAvailabilityZoneSetOperation object.
 */
@ApiModel(value = "Set Host's availability zone operation")
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostSetAvailabilityZoneOperation {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the availability zone.", required = true)
  @NotNull
  @Size(min = 1)
  private String availabilityZoneId;

  public String getAvailabilityZoneId() {
    return availabilityZoneId;
  }

  public void setAvailabilityZoneId(String availabilityZoneId) {
    this.availabilityZoneId = availabilityZoneId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HostSetAvailabilityZoneOperation other = (HostSetAvailabilityZoneOperation) o;

    return Objects.equals(availabilityZoneId, other.availabilityZoneId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(availabilityZoneId);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("availabilityZoneId", availabilityZoneId)
        .toString();
  }
}
