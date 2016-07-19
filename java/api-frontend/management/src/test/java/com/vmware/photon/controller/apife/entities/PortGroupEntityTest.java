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

import com.vmware.photon.controller.api.model.PortGroupCreateSpec;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Test {@link PortGroupEntity}.
 */
public class PortGroupEntityTest {
  private String portGroupName;
  private String startAddress;
  private String endAddress;
  private String subnetMask;
  private String gateway;
  private List<UsageTag> usageTags;

  @BeforeMethod
  public void setup() {
    portGroupName = "network1";
    startAddress = "192.168.1.1";
    endAddress = "192.168.1.10";
    subnetMask = "192.168.1.0";
    gateway = "192.168.1.100";
    usageTags = Arrays.asList(UsageTag.MGMT);
  }

  @Test
  public void testPortGroupEntityGetterSetters() {
    PortGroupEntity portGroupEntity = new PortGroupEntity();
    portGroupEntity.setPortGroupName(portGroupName);
    portGroupEntity.setStartAddress(startAddress);
    portGroupEntity.setEndAddress(endAddress);
    portGroupEntity.setSubnetMask(subnetMask);
    portGroupEntity.setGateway(gateway);
    portGroupEntity.setUsageTags(UsageTagHelper.serialize(usageTags));

    Assert.assertTrue(validatePortGroupEntity(portGroupEntity));
  }

  @Test
  public void testCreatePortGroupEntity() {
    PortGroupCreateSpec portGroupCreateSpec = new PortGroupCreateSpec(portGroupName, startAddress, endAddress,
        subnetMask, gateway, usageTags);
    PortGroupEntity portGroupEntity = PortGroupEntity.create(portGroupCreateSpec);

    Assert.assertTrue(validatePortGroupEntity(portGroupEntity));
  }

  private boolean validatePortGroupEntity(PortGroupEntity portGroupEntity) {
    return portGroupEntity.getPortGroupName().equals(portGroupName)
        && portGroupEntity.getStartAddress().equals(startAddress)
        && portGroupEntity.getEndAddress().equals(endAddress)
        && portGroupEntity.getSubnetMask().equals(subnetMask)
        && portGroupEntity.getGateway().equals(gateway)
        && portGroupEntity.getUsageTags().equals(UsageTagHelper.serialize(usageTags));
  }
}
