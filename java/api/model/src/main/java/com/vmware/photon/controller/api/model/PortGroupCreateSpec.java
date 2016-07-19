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

import com.vmware.photon.controller.api.model.constraints.IPv4;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import static com.google.common.base.Objects.toStringHelper;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Port group creation payload.
 */
@ApiModel(value = "A class used as the payload when creating a Port Group.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortGroupCreateSpec {

  @JsonProperty
  @ApiModelProperty(value = "Port group name", required = true)
  @NotNull
  @Size(min = 1, max = 265)
  private String portGroupName;

  @JsonProperty
  @ApiModelProperty(value = "Start Address for this IP Range", required = true)
  @IPv4
  private String startAddress;

  @JsonProperty
  @ApiModelProperty(value = "End Address for this IP Range", required = true)
  @IPv4
  private String endAddress;

  @JsonProperty
  @ApiModelProperty(value = "Subnet mask to use", required = true)
  @IPv4
  private String subnetMask;

  @JsonProperty
  @ApiModelProperty(value = "Gateway IP for this port group", required = true)
  @IPv4
  private String gateway;

  @JsonProperty
  @ApiModelProperty(value = "Usage tags", allowableValues = UsageTag.PORTGROUP_USAGES, required = true)
  @NotNull
  @Size(min = 1)
  private List<UsageTag> usageTags;

  public PortGroupCreateSpec() {
  }

  public PortGroupCreateSpec(String portGroupName, String startAddress, String endAddress, String subnetMask,
                             String gateway, List<UsageTag> usageTags) {
    this.portGroupName = portGroupName;
    this.startAddress = startAddress;
    this.endAddress = endAddress;
    this.subnetMask = subnetMask;
    this.gateway = gateway;
    this.usageTags = usageTags;
  }

  public String getPortGroupName() {
    return portGroupName;
  }

  public void setPortGroupName(String portGroupName) {
    this.portGroupName = portGroupName;
  }

  public String getStartAddress() {
    return startAddress;
  }

  public void setStartAddress(String startAddress) {
    this.startAddress = startAddress;
  }

  public String getEndAddress() {
    return endAddress;
  }

  public void setEndAddress(String endAddress) {
    this.endAddress = endAddress;
  }

  public String getSubnetMask() {
    return subnetMask;
  }

  public void setSubnetMask(String subnetMask) {
    this.subnetMask = subnetMask;
  }

  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }

  public List<UsageTag> getUsageTags() {
    return usageTags;
  }

  public void setUsageTags(List<UsageTag> usageTags) {
    this.usageTags = usageTags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PortGroupCreateSpec)) {
      return false;
    }

    PortGroupCreateSpec that = (PortGroupCreateSpec) o;

    return Objects.equals(portGroupName, that.portGroupName)
        && Objects.equals(startAddress, that.startAddress)
        && Objects.equals(endAddress, that.endAddress)
        && Objects.equals(subnetMask, that.subnetMask)
        && Objects.equals(gateway, that.gateway)
        && Objects.equals(usageTags, that.usageTags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        portGroupName,
        startAddress,
        endAddress,
        subnetMask,
        gateway,
        usageTags);
  }

  @Override
  public String toString() {
    return toStringHelper(this.getClass())
        .add("portGroupName", portGroupName)
        .add("startAddress", startAddress)
        .add("endAddress", endAddress)
        .add("subnetMask", subnetMask)
        .add("gateway", gateway)
        .add("usageTags", usageTags)
        .toString();
  }
}
