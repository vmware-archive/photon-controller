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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import java.util.Objects;

/**
 * Cluster resize operation is requested via POST to /clusters/{id}/resize. The payload of the POST is the
 * ClusterResizeOperation object.
 */
@ApiModel(value = "Cluster resize operation")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterResizeOperation {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired number of slave VMs.", required = false)
  @Min(0)
  @Max(1000)
  private int newSlaveCount;

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the desired number of worker VMs.", required = false)
  @Min(1)
  @Max(1000)
  private int newWorkerCount;

  public int getNewSlaveCount() {
    return newWorkerCount;
  }

  public void setNewSlaveCount(int newSlaveCount) {
    this.newWorkerCount = newSlaveCount;
  }

  public int getNewWorkerCount() {
    return newWorkerCount;
  }

  public void setNewWorkerCount(int newWorkerCount) {
    this.newWorkerCount = newWorkerCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ClusterResizeOperation other = (ClusterResizeOperation) o;

    return Objects.equals(newWorkerCount, other.newWorkerCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(newWorkerCount);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("newWorkerCount", newWorkerCount)
        .toString();
  }
}
