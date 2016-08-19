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

import javax.validation.constraints.Min;

import java.util.Objects;

/**
 * This is the API representation of a quota limit line item, it differs from the internal form of the
 * QuotaLineItemEntity in that the Units are represented as a string instead of an enum.
 */
@ApiModel(value = "This class represents a single quote line item. This structure is used in resource tickets " +
    "at both the project and tenant level, in resource ticket reservation requests, and in infrastructure " +
    "entity costs. The class is a 3-tuple of key, value, and unit.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuotaLineItem {

  // Commonly used QuotaLineItem keys
  public static final String VM = "vm";
  public static final String VM_CPU = "vm.cpu";
  public static final String VM_MEMORY = "vm.memory";
  public static final String VM_COST = "vm.cost";
  public static final String PERSISTENT_DISK_CAPACITY = "persistent-disk.capacity";
  public static final String EPHEMERAL_DISK_CAPACITY = "ephemeral-disk.capacity";

  @JsonProperty
  @ApiModelProperty(value = "Item key (e.g., vm.cost, vm.memory, etc.)", required = true)
  private String key;

  @JsonProperty
  @ApiModelProperty(value = "Item value", required = true)
  @Min(0)
  private double value;

  @JsonProperty
  @ApiModelProperty(value = "Item unit", allowableValues = "GB, MB, KB, B, COUNT", required = true)
  private QuotaUnit unit;

  public QuotaLineItem() {
  }

  public QuotaLineItem(String key, double value, QuotaUnit unit) {
    this.key = key;
    this.value = value;
    this.unit = unit;
  }

  public String toString() {
    return key + ", " + value + ", " + unit;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  public QuotaUnit getUnit() {
    return unit;
  }

  public void setUnit(QuotaUnit unit) {
    this.unit = unit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    QuotaLineItem other = (QuotaLineItem) o;

    return Objects.equals(key, other.key) &&
        Objects.equals(value, other.value) &&
        Objects.equals(unit, other.unit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value, unit);
  }

}
