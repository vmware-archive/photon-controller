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
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test {@link HostDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class HostDaoTest extends BaseDaoTest {
  @Inject
  private HostDao hostDao;

  @Inject
  private HostEntity hostEntity;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    Map<String, String> hostMetadata = new HashMap<>();
    hostMetadata.put("id", "h_146_36_27");

    hostEntity = new HostEntity();
    hostEntity.setAddress("10.146.1.1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAvailabilityZone("availabilityZone");
    hostEntity.setEsxVersion("6.0");
    hostEntity.setMetadata(hostMetadata);
    hostEntity.setUsageTags(UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
      add(UsageTag.MGMT);
    }}));
  }

  @Test
  public void testCreate() {

    hostDao.create(hostEntity);
    String hostId = hostEntity.getId();
    Optional<HostEntity> retrievedHostEntity = hostDao.findById(hostId);
    Assert.assertTrue(retrievedHostEntity.isPresent());
    Assert.assertEquals(retrievedHostEntity.get(), hostEntity);
    Assert.assertEquals(hostDao.listAll().size(), 1);
  }

  @Test
  public void testDelete() {
    hostDao.create(hostEntity);

    String hostId = hostEntity.getId();
    Optional<HostEntity> retrievedHostEntity = hostDao.findById(hostId);
    Assert.assertTrue(retrievedHostEntity.isPresent());
    Assert.assertEquals(retrievedHostEntity.get(), hostEntity);
    Assert.assertEquals(hostDao.listAll().size(), 1);

    hostDao.delete(hostEntity);

    Assert.assertEquals(hostDao.listAll().size(), 0);
  }

  @Test
  public void testFindById() {
    hostDao.create(hostEntity);

    String hostId = hostEntity.getId();
    Optional<HostEntity> retrievedHostEntity = hostDao.findById(hostId);
    Assert.assertTrue(retrievedHostEntity.isPresent());
    Assert.assertEquals(retrievedHostEntity.get(), hostEntity);
  }

  @Test
  public void testListAll() {
    hostDao.create(hostEntity);

    String hostId = hostEntity.getId();
    Optional<HostEntity> retrievedHostEntity = hostDao.findById(hostId);
    Assert.assertTrue(retrievedHostEntity.isPresent());
    Assert.assertEquals(retrievedHostEntity.get(), hostEntity);
    Assert.assertEquals(hostDao.listAll().size(), 1);
  }

  @Test
  public void testListAllByUsage() {
    hostDao.create(hostEntity);

    List<HostEntity> hostEntityList = hostDao.listAllByUsage(UsageTag.MGMT);
    Assert.assertEquals(hostEntityList.size(), 1);
    Assert.assertEquals(hostEntityList.get(0), hostEntity);
  }

  @Test
  public void testListAllByUsageEmptyList() {
    hostDao.create(hostEntity);

    List<HostEntity> hostEntityList = hostDao.listAllByUsage(UsageTag.CLOUD);
    Assert.assertEquals(hostEntityList.size(), 0);
  }
}
