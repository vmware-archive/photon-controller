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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

import java.util.Objects;

/**
 * A floating IP is assigned to or released from a VM using a JSON payload that
 * maps to this class.
 */
@ApiModel(value = "A request to assign or remove a floating IP on a specific network.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmFloatingIpSpec {

  @JsonProperty
  @ApiModelProperty(value = "This value specifies the ID of the network on which" +
      "the floating IP is allocated.")
  @NotNull
  private String networkId;

  public String getNetworkId() {
    return networkId;
  }

  public void setNetworkId(String networkId) {
    this.networkId = networkId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    VmFloatingIpSpec other = (VmFloatingIpSpec) o;

    return Objects.equals(networkId, other.networkId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(networkId);
  }
}
