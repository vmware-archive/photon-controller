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

import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.DatastoreEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test {@link DatastoreDao}.
 */
@Guice(modules = {HibernateTestModule.class})
public class DatastoreDaoTest extends BaseDaoTest {
  @Inject
  private DatastoreDao datastoreDao;

  private DatastoreEntity datastoreEntity;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    datastoreEntity = new DatastoreEntity();
    datastoreEntity.setName("UNUSED");
    datastoreEntity.setHostIps("127.0.0.1");
    datastoreEntity.setId("test id");
    datastoreEntity.setEsxId("esx id");
    datastoreEntity.setTags("tag1");

    Map<String, DatastoreEntity> datastoreMap = new HashMap<>();
    datastoreMap.put("datastore1", datastoreEntity);
  }

  @Test
  public void testCreate() {
    datastoreDao.create(datastoreEntity);

    String datastoreId = datastoreEntity.getId();
    Optional<DatastoreEntity> retrievedDatastoreEntity = datastoreDao.findById(datastoreId);
    Assert.assertTrue(retrievedDatastoreEntity.isPresent());
    Assert.assertEquals(retrievedDatastoreEntity.get(), datastoreEntity);
    Assert.assertEquals(datastoreDao.listAll().size(), 1);
  }

  @Test
  public void testDelete() {
    datastoreDao.create(datastoreEntity);

    String datastoreId = datastoreEntity.getId();
    Optional<DatastoreEntity> retrievedDatastoreEntity = datastoreDao.findById(datastoreId);
    Assert.assertTrue(retrievedDatastoreEntity.isPresent());
    Assert.assertEquals(retrievedDatastoreEntity.get(), datastoreEntity);
    Assert.assertEquals(datastoreDao.listAll().size(), 1);

    datastoreDao.delete(datastoreEntity);

    Assert.assertEquals(datastoreDao.listAll().size(), 0);
  }

  @Test
  public void testFindById() {
    datastoreDao.create(datastoreEntity);

    String datastoreId = datastoreEntity.getId();
    Optional<DatastoreEntity> retrievedDatastoreEntity = datastoreDao.findById(datastoreId);
    Assert.assertTrue(retrievedDatastoreEntity.isPresent());
    Assert.assertEquals(retrievedDatastoreEntity.get(), datastoreEntity);
  }

  @Test
  public void testListAll() {
    datastoreDao.create(datastoreEntity);

    String datastoreId = datastoreEntity.getId();
    Optional<DatastoreEntity> retrievedDatastoreEntity = datastoreDao.findById(datastoreId);
    Assert.assertTrue(retrievedDatastoreEntity.isPresent());
    Assert.assertEquals(retrievedDatastoreEntity.get(), datastoreEntity);
    Assert.assertEquals(datastoreDao.listAll().size(), 1);
  }

  @Test
  public void testListAllByUsage() {
    datastoreDao.create(datastoreEntity);

    List<DatastoreEntity> datastoreEntityList = datastoreDao.listAllByTag("tag1");
    Assert.assertEquals(datastoreEntityList.size(), 1);
    Assert.assertEquals(datastoreEntityList.get(0), datastoreEntity);
  }

  @Test
  public void testListAllByUsageEmptyList() {
    datastoreDao.create(datastoreEntity);

    List<DatastoreEntity> datastoreEntityList = datastoreDao.listAllByTag("tag2");
    Assert.assertEquals(datastoreEntityList.size(), 0);
  }
}
