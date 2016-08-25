/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.api.model.constraints.IPv4;
import com.vmware.photon.controller.api.model.constraints.SoftwareDefinedNetworkingEnabled;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.groups.Default;

import java.util.Objects;

/**
 * Represents an IP range.
 */
@ApiModel(value = "Contains an IP range")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IpRange {
  @JsonProperty
  @ApiModelProperty(value = "The starting IP address (inclusive)")
  @IPv4(groups = {SoftwareDefinedNetworkingEnabled.class, Default.class})
  private String start;

  @JsonProperty
  @ApiModelProperty(value = "The ending IP address (inclusive)")
  @IPv4(groups = {SoftwareDefinedNetworkingEnabled.class, Default.class})
  private String end;

  public String getStart() {
    return start;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IpRange)) {
      return false;
    }

    IpRange other = (IpRange) o;

    return Objects.equals(this.start, other.start)
        && Objects.equals(this.end, other.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        this.start,
        this.end
    );
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("start", start)
        .add("end", end)
        .toString();
  }
}
