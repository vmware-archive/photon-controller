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

import com.vmware.photon.controller.api.model.base.Infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Objects;

/**
 * Base Disk API representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseDisk extends Infrastructure {

  @JsonProperty
  @ApiModelProperty(value = "Supplies the state of the Disk",
      allowableValues = "CREATING,DETACHED,ATTACHED,ERROR,DELETED",
      required = true)
  private DiskState state;

  @JsonProperty
  @ApiModelProperty(value = "Supplies the datastore id of the Disk")
  private String datastore;

  @JsonProperty
  private int capacityGb;

  public DiskState getState() {
    return state;
  }

  public void setState(DiskState state) {
    this.state = state;
  }

  public String getDatastore() {
    return datastore;
  }

  public void setDatastore(String datastore) {
    this.datastore = datastore;
  }

  public int getCapacityGb() {
    return capacityGb;
  }

  public void setCapacityGb(int capacityGb) {
    this.capacityGb = capacityGb;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    BaseDisk other = (BaseDisk) o;

    return Objects.equals(state, other.state) &&
        Objects.equals(capacityGb, other.capacityGb) &&
        Objects.equals(datastore, other.datastore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), state, capacityGb, datastore);
  }
}
