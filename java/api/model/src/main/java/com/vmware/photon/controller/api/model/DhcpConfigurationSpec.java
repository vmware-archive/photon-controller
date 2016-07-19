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
import com.wordnik.swagger.annotations.ApiModelProperty;

import static com.google.common.base.Objects.toStringHelper;

import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * This class represents the configuration spec of DHCP service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DhcpConfigurationSpec {

  @JsonProperty
  @ApiModelProperty(value = "This property specifies the DHCP server addresses.", required = true)
  @NotNull
  private List<String> serverAddresses;

  public List<String> getServerAddresses() {
    return this.serverAddresses;
  }

  public void setServerAddresses(List<String> serverAddresses) {
    this.serverAddresses = serverAddresses;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DhcpConfigurationSpec other = (DhcpConfigurationSpec) o;
    return Objects.equals(serverAddresses, other.serverAddresses);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(),
        serverAddresses);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("serverAddresses", serverAddresses.toString())
        .toString();
  }
}
