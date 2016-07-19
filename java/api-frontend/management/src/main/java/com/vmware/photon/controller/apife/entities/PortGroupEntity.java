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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.model.PortGroupCreateSpec;
import com.vmware.photon.controller.api.model.constraints.IPv4;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.base.Objects.ToStringHelper;

import javax.validation.constraints.NotNull;

import java.util.Objects;

/**
 * PortGroup entity.
 */
public class PortGroupEntity extends BaseEntity {

  public static final String KIND = "portGroupEntity";

  private String portGroupName;

  @IPv4
  private String startAddress;

  @IPv4
  private String endAddress;

  @IPv4
  private String subnetMask;

  @IPv4
  private String gateway;

  @NotNull
  private String usageTags;

  public static PortGroupEntity create(PortGroupCreateSpec apiRepresentation) {
    PortGroupEntity portGroupEntity = new PortGroupEntity();
    portGroupEntity.setPortGroupName(apiRepresentation.getPortGroupName());
    portGroupEntity.setStartAddress(apiRepresentation.getStartAddress());
    portGroupEntity.setEndAddress(apiRepresentation.getEndAddress());
    portGroupEntity.setSubnetMask(apiRepresentation.getSubnetMask());
    portGroupEntity.setGateway(apiRepresentation.getGateway());
    portGroupEntity.setUsageTags(UsageTagHelper.serialize(apiRepresentation.getUsageTags()));

    return portGroupEntity;
  }

  @Override
  public String getKind() {
    return KIND;
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

  public String getUsageTags() {
    return usageTags;
  }

  public void setUsageTags(String usageTags) {
    this.usageTags = usageTags;
  }

  @Override
  protected ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("portGroupName", portGroupName)
        .add("startAddress", startAddress)
        .add("endAddress", endAddress)
        .add("subnetMask", subnetMask)
        .add("gateway", gateway)
        .add("usageTags", usageTags);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof PortGroupEntity)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    PortGroupEntity that = (PortGroupEntity) o;

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
}
