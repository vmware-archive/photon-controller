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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.PortGroupEntity;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests {@link PortGroupDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class PortGroupDaoTest extends BaseDaoTest {
  @Inject
  private PortGroupDao portGroupDao;

  private PortGroupEntity portGroupEntity;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("id", "ManagementVLAN");

    portGroupEntity = new PortGroupEntity();
    portGroupEntity.setPortGroupName("VM VLAN");
    portGroupEntity.setStartAddress("10.146.56.24");
    portGroupEntity.setEndAddress("10.146.56.25");
    portGroupEntity.setSubnetMask("255.255.248.0");
    portGroupEntity.setGateway("10.146.63.253");
    portGroupEntity.setUsageTags(UsageTagHelper.serialize(Arrays.asList(UsageTag.MGMT)));
  }

  @Test
  public void testCreate() {
    portGroupDao.create(portGroupEntity);

    String portGroupId = portGroupEntity.getId();
    Optional<PortGroupEntity> retrievedPortGroupEntity = portGroupDao.findById(portGroupId);
    Assert.assertTrue(retrievedPortGroupEntity.isPresent());
    Assert.assertEquals(retrievedPortGroupEntity.get(), portGroupEntity);
    Assert.assertEquals(portGroupDao.listAll().size(), 1);
  }

  @Test
  public void testDelete() {
    portGroupDao.create(portGroupEntity);

    String portGroupId = portGroupEntity.getId();
    Optional<PortGroupEntity> retrievedPortGroupEntity = portGroupDao.findById(portGroupId);
    Assert.assertTrue(retrievedPortGroupEntity.isPresent());
    Assert.assertEquals(retrievedPortGroupEntity.get(), portGroupEntity);
    Assert.assertEquals(portGroupDao.listAll().size(), 1);

    portGroupDao.delete(portGroupEntity);

    Assert.assertEquals(portGroupDao.listAll().size(), 0);
  }

  @Test
  public void testEmptyHostList() {
    Assert.assertEquals(portGroupDao.listAll().size(), 0);
  }

  @Test
  public void testListAll() {
    portGroupDao.create(portGroupEntity);

    List<PortGroupEntity> retrievedPortGroupEntities = portGroupDao.listAll();
    Assert.assertEquals(retrievedPortGroupEntities.size(), 1);
    Assert.assertEquals(retrievedPortGroupEntities.get(0), portGroupEntity);
  }

  @Test
  public void testListAllByUsageTag() {
    portGroupDao.create(portGroupEntity);

    List<PortGroupEntity> retrievedPortGroupEntities = portGroupDao.listAllByUsage(UsageTag.MGMT);
    Assert.assertEquals(retrievedPortGroupEntities.size(), 1);
    Assert.assertEquals(retrievedPortGroupEntities.get(0), portGroupEntity);
  }

  @Test
  public void testListAllByInvalidUsageTag() {
    portGroupDao.create(portGroupEntity);

    List<PortGroupEntity> retrievedPortGroupEntities = portGroupDao.listAllByUsage(UsageTag.CLOUD);
    Assert.assertEquals(retrievedPortGroupEntities.size(), 0);
  }

  @Test
  public void testFindNonExistingHost() {
    Optional<PortGroupEntity> retrievedPortGroupEntity = portGroupDao.findById("id1");
    Assert.assertFalse(retrievedPortGroupEntity.isPresent());
  }

  @Test
  public void testFindById() {
    portGroupDao.create(portGroupEntity);

    String portGroupId = portGroupEntity.getId();
    Optional<PortGroupEntity> retrievedPortGroupEntity = portGroupDao.findById(portGroupId);
    Assert.assertTrue(retrievedPortGroupEntity.isPresent());
    Assert.assertEquals(retrievedPortGroupEntity.get(), portGroupEntity);
  }
}
