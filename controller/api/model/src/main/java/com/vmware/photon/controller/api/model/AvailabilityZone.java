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

import java.util.Objects;

/**
 * This class represents a availability zone.
 */
@ApiModel(value = "This class represents a availability zone")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AvailabilityZone extends VisibleModel {
  public static final String KIND = "availability-zone";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"availabilityZone\"", required = true)
  private String kind;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Availability Zone",
      allowableValues = "CREATING,READY,ERROR,DELETED,PENDING_DELETE",
      required = true)
  private AvailabilityZoneState state;

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public AvailabilityZoneState getState() {
    return state;
  }

  public void setState(AvailabilityZoneState state) {
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    AvailabilityZone other = (AvailabilityZone) o;

    return super.equals(other)
        && Objects.equals(this.getKind(), other.getKind())
        && Objects.equals(this.getState(), other.getState());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.getKind(),
        this.getState());
  }

  @Override
  protected com.google.common.base.Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("state", getState());
  }
}
