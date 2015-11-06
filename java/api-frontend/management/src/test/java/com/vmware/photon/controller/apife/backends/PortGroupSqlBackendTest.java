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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.PortGroupDao;
import com.vmware.photon.controller.apife.entities.PortGroupEntity;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.inject.Inject;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link PortGroupSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class PortGroupSqlBackendTest extends BaseDaoTest {

  @Inject
  PortGroupBackend portGroupBackend;
  @Inject
  private EntityFactory entityFactory;
  @Inject
  private PortGroupDao portGroupDao;

  @Test
  public void testListZeroPortGroups() {
    PortGroupSqlBackend portGroupSqlBackend = new PortGroupSqlBackend(portGroupDao);
    List<PortGroup> retrievedPortGroupList = portGroupSqlBackend.listAll();

    Assert.assertNotNull(retrievedPortGroupList);
    Assert.assertEquals(retrievedPortGroupList.size(), 0);
  }

  @Test
  public void testListAllPortGroups() throws ExternalException {
    PortGroupEntity portGroupEntity = entityFactory.createPortGroup("network1", "192.168.1.1", "192.168.1.10",
        "192.168.1.254", "192.168.1.253", Arrays.asList(UsageTag.MGMT));
    Assert.assertNotNull(portGroupEntity);

    PortGroupSqlBackend portGroupSqlBackend = new PortGroupSqlBackend(portGroupDao);
    List<PortGroup> retrievedPortGroupList = portGroupSqlBackend.listAll();

    Assert.assertNotNull(retrievedPortGroupList);
    Assert.assertEquals(retrievedPortGroupList.size(), 1);

    PortGroup retrievedPortGroup = retrievedPortGroupList.get(0);

    Assert.assertEquals(retrievedPortGroup.getId(), portGroupEntity.getId());
  }

  @Test(dataProvider = "usageTags")
  public void testListPortGroupsByUsageTag(UsageTag usageTag, int expectedCount) throws ExternalException {
    PortGroupEntity portGroupEntity = entityFactory.createPortGroup("network1", "192.168.1.1", "192.168.1.10",
        "192.168.1.254", "192.168.1.253", Arrays.asList(UsageTag.MGMT));
    Assert.assertNotNull(portGroupEntity);
    PortGroupSqlBackend portGroupSqlBackend = new PortGroupSqlBackend(portGroupDao);

    List<PortGroup> retrievedPortGroupList = portGroupSqlBackend.listByUsageTag(usageTag);
    Assert.assertNotNull(retrievedPortGroupList);
    Assert.assertEquals(retrievedPortGroupList.size(), expectedCount);
  }

  @DataProvider(name = "usageTags")
  public Object[][] getUsageTags() {
    return new Object[][]{
        {UsageTag.MGMT, 1},
        {UsageTag.CLOUD, 0}
    };
  }

  @Test
  public void testGetPortGroup() throws ExternalException {
    PortGroupEntity portGroupEntity = entityFactory.createPortGroup("network1", "192.168.1.1", "192.168.1.10",
        "192.168.1.254", "192.168.1.253", Arrays.asList(UsageTag.MGMT));
    Assert.assertNotNull(portGroupEntity);

    PortGroup retrievedPortGroup = portGroupBackend.toApiRepresentation(portGroupEntity.getId());
    Assert.assertNotNull(retrievedPortGroup);
    Assert.assertEquals(retrievedPortGroup.getId(), portGroupEntity.getId());
    Assert.assertTrue(verifyCreatedPortGroup(retrievedPortGroup, portGroupEntity));
  }


  @Test(expectedExceptions = PortGroupNotFoundException.class,
      expectedExceptionsMessageRegExp = "^Port Group #id1 not found$")
  public void testGetNonExistingPortGroup() throws PortGroupNotFoundException {
    portGroupBackend.toApiRepresentation("id1");
  }

  private boolean verifyCreatedPortGroup(PortGroup portGroup, PortGroupEntity portGroupEntity) {
    return portGroup.getName().equals(portGroupEntity.getPortGroupName())
        && UsageTagHelper.serialize(portGroup.getUsageTags()).equals(portGroupEntity.getUsageTags());
  }
}
