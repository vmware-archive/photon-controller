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

import com.vmware.photon.controller.api.model.base.BaseCompact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.Objects;

/**
 * Image Structure.
 */
@ApiModel(value = "This class represents an ISO template.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Iso extends BaseCompact {

  public static final String KIND = "iso";

  @JsonProperty
  @ApiModelProperty(value = "kind=\"iso\"")
  private String kind = KIND;

  @JsonProperty
  @ApiModelProperty(value = "Iso size (in bytes)", required = false)
  private Long size;


  public String getKind() {
    return kind;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Iso other = (Iso) o;

    return Objects.equals(this.getId(), other.getId()) &&
        Objects.equals(this.getName(), other.getName()) &&
        Objects.equals(this.getSize(), other.getSize());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        getId(), getName(), getSize());
  }
}
