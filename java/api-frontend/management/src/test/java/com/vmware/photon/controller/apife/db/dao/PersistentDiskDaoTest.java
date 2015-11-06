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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.apife.backends.BackendTestModule;
import com.vmware.photon.controller.apife.backends.EntityFactory;
import com.vmware.photon.controller.apife.backends.FlavorSqlBackend;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.NonUniqueResultException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the Vm entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class PersistentDiskDaoTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private PersistentDiskDao persistentDiskDao;

  @Inject
  private TagDao tagDao;

  @Inject
  private FlavorSqlBackend flavorSqlBackend;

  @Inject
  private EntityFactory entityFactory;

  private TenantEntity tenant;

  private ProjectEntity project;

  private FlavorEntity flavor;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();

    tenant = new TenantEntity();
    tenant.setName("mhlsoft");
    tenantDao.create(tenant);

    project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    projectDao.create(project);

    entityFactory.loadFlavors();
    flavor = flavorSqlBackend.getEntityByNameAndKind("core-100", PersistentDisk.KIND);
  }

  @Test
  public void testCreate() throws Exception {
    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setProjectId(project.getId());
    disk.setName("pdisk-1");
    disk.setCapacityGb(2);
    disk.setFlavorId(flavor.getId());
    List<QuotaLineItemEntity> diskCost = new ArrayList<>(flavor.getCost());
    diskCost.add(new QuotaLineItemEntity("persistent-disk.capacity", 2, QuotaUnit.GB));
    disk.setCost(diskCost);

    persistentDiskDao.create(disk);

    String id = disk.getId();

    flushSession();

    // assert that the disk was found and that it contains the right project object
    Optional<PersistentDiskEntity> found = persistentDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
    assertThat(found.get().getKind(), equalTo(PersistentDisk.KIND));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getName(), equalTo("core-100"));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getKind(), equalTo(PersistentDisk.KIND));
    assertThat(found.get().getCapacityGb(), is(2));
    assertThat(found.get().getDatastore(), nullValue());

    // read cost and assert that it maps to a correct object...
    QuotaCost cost = new QuotaCost(found.get().getCost());
    assertThat("persistent-disk, persistent-disk.flavor, persistent-disk.cost, persistent-disk.capacity",
        cost.getCostKeys().contains("persistent-disk"),
        is(true));
    assertThat("value is correct",
        cost.getCost("persistent-disk").getValue(),
        is(1.0));
    assertThat("flavor key",
        cost.getCostKeys().contains("persistent-disk.flavor.core-100"),
        is(true));
    assertThat("flavor value",
        cost.getCost("persistent-disk.flavor.core-100").getValue(),
        is(1.0));
    assertThat("cost key",
        cost.getCostKeys().contains("persistent-disk.cost"),
        is(true));
    assertThat("cost value",
        cost.getCost("persistent-disk.cost").getValue(),
        is(1.0));
    assertThat("capacity key",
        cost.getCostKeys().contains("persistent-disk.capacity"),
        is(true));
    assertThat("capacity value",
        cost.getCost("persistent-disk.capacity").getValue(),
        is(2.0));
    assertThat("only 4 items are in the set",
        cost.getCostKeys().size(),
        is(4));
  }

  @Test
  public void testDatastorePersistence() throws Exception {
    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setDatastore("datastore-id-0");
    disk.setProjectId(project.getId());
    disk.setName("pdisk-name-2");
    disk.setCapacityGb(2);
    disk.setFlavorId(flavor.getId());
    disk.setCost(flavor.getCost());

    persistentDiskDao.create(disk);

    String id = disk.getId();

    flushSession();

    // assert that the disk was found and that it contains the right project object
    Optional<PersistentDiskEntity> found = persistentDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getDatastore(), equalTo("datastore-id-0"));
  }

  @Test
  public void testCreateNameCollision() {
    PersistentDiskEntity persistentDisk1 = new PersistentDiskEntity();
    persistentDisk1.setProjectId(project.getId());
    persistentDisk1.setName("persistent-diskt-1");
    persistentDiskDao.create(persistentDisk1);

    // identical
    PersistentDiskEntity persistentDisk2 = new PersistentDiskEntity();
    persistentDisk2.setProjectId(project.getId());
    persistentDisk2.setName("persistent-diskt-1");
    persistentDiskDao.create(persistentDisk2);

    // when trimmed identical
    PersistentDiskEntity persistentDisk3 = new PersistentDiskEntity();
    persistentDisk3.setProjectId(project.getId());
    persistentDisk3.setName(" persistent-diskt-1");
    persistentDiskDao.create(persistentDisk3);

    PersistentDiskEntity persistentDisk4 = new PersistentDiskEntity();
    persistentDisk4.setProjectId(project.getId());
    persistentDisk4.setName(" persistent-diskt-1 ");
    persistentDiskDao.create(persistentDisk4);

    flushSession();

    List<PersistentDiskEntity> persistentDisks = persistentDiskDao.findAll(project);
    assertThat(persistentDisks.size(), is(4));

    for (PersistentDiskEntity persistentDisk : persistentDisks) {
      assertThat(persistentDisk.getProjectId(), is(project.getId()));
    }
  }

  @DataProvider(name = "getFindDiskByNameParams")
  public Object[][] getFindDiskByNameParams() {
    Object[][] states = new Object[ALL_DISK_STATES.length][];

    for (int i = 0; i < ALL_DISK_STATES.length; i++) {
      states[i] = new Object[]{ALL_DISK_STATES[i]};
    }

    return states;
  }

  @Test(dataProvider = "getFindDiskByNameParams")
  public void testFindByName(DiskState state) {
    PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
    persistentDisk.setProjectId(project.getId());
    persistentDisk.setName("persistentDisk-1");
    persistentDisk.setState(state);
    persistentDiskDao.create(persistentDisk);

    flushSession();

    Optional<PersistentDiskEntity> found = persistentDiskDao.findByName("persistentDisk-1", project);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
  }

  @Test
  public void testListByNameWithMultipleDiskEntities() {
    for (DiskState state : ALL_DISK_STATES) {
      PersistentDiskEntity disk = new PersistentDiskEntity();
      disk.setProjectId(project.getId());
      disk.setName("disk-1");
      disk.setState(state);
      persistentDiskDao.create(disk);
    }

    flushSession();

    try {
      persistentDiskDao.findByName("disk-1", project);
    } catch (NonUniqueResultException ex) {
      assertThat(ex.getMessage(), is(
          String.format("query did not return a unique result: %d", ALL_DISK_STATES.length)));
    }

    List<PersistentDiskEntity> disks = persistentDiskDao.listByName("disk-1", project);
    assertThat(disks.size(), is(ALL_DISK_STATES.length));
  }

  @Test
  public void testFindAll() {
    assertThat(persistentDiskDao.findAll(project).isEmpty(), is(true));

    String persistentDiskNames[] = {"persistentDisk1", "persistentDisk2", "persistentDisk3", "persistentDisk4"};
    for (String name : persistentDiskNames) {
      PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
      persistentDisk.setProjectId(project.getId());
      persistentDisk.setName(name);
      persistentDiskDao.create(persistentDisk);
    }

    flushSession();

    List<PersistentDiskEntity> persistentDisks = persistentDiskDao.findAll(project);
    assertThat(persistentDisks.size(), is(4));
  }

  @Test
  public void testFindByTag() {

    // two projects, each with 3 persistentDisks, tagged similarly
    // validate tag lookups work as expected and project isolation is working
    ProjectEntity project2 = new ProjectEntity();
    project2.setTenantId(tenant.getId());
    project2.setName("production");
    project2 = projectDao.create(project2);

    TagEntity tag1 = tagDao.findOrCreate("frontend");
    TagEntity tag2 = tagDao.findOrCreate("cesspool");
    TagEntity tag3 = tagDao.findOrCreate("gold");

    PersistentDiskEntity[] persistentDisks = new PersistentDiskEntity[5];
    for (int i = 0; i < persistentDisks.length; i++) {
      PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
      persistentDisk.setProjectId(project.getId());
      persistentDisk.setName("persistentDisk-p0-" + i);
      persistentDisks[i] = persistentDiskDao.create(persistentDisk);
    }

    // add tag1 to persistentDisk 0 to 2
    persistentDisks[0].getTags().add(tag1);
    persistentDisks[1].getTags().add(tag1);
    persistentDisks[2].getTags().add(tag1);

    // add tag2 to persistentDisk 0 and 3
    persistentDisks[0].getTags().add(tag2);
    persistentDisks[3].getTags().add(tag2);

    // add tag3 to persistentDisk 4
    persistentDisks[4].getTags().add(tag3);

    flushSession();

    List<PersistentDiskEntity> result = persistentDiskDao.findByTag(tag1.getValue(), project);
    assertThat(result, hasSize(3));

    result = persistentDiskDao.findByTag(tag2.getValue(), project);
    assertThat(result, hasSize(2));

    result = persistentDiskDao.findByTag(tag3.getValue(), project);
    assertThat(result, hasSize(1));

    result = persistentDiskDao.findByTag(tag1.getValue(), project2);
    assertThat(result, hasSize(0));
  }

  @Test
  public void testFindByFlavor() {
    PersistentDiskEntity disk1 = new PersistentDiskEntity();
    disk1.setProjectId(project.getId());
    disk1.setName("pdisk-1");
    disk1.setCapacityGb(2);
    disk1.setFlavorId(flavor.getId());
    disk1.setCost(flavor.getCost());
    persistentDiskDao.create(disk1);

    PersistentDiskEntity disk2 = new PersistentDiskEntity();
    disk2.setProjectId(project.getId());
    disk2.setName("pdisk-2");
    disk2.setCapacityGb(2);
    disk2.setFlavorId(flavor.getId());
    disk2.setCost(flavor.getCost());
    persistentDiskDao.create(disk2);

    flushSession();

    List<PersistentDiskEntity> result = persistentDiskDao.findByFlavor(flavor.getId());
    assertThat(result.size(), is(2));
    assertThat(result.get(0).getId(), is(disk1.getId()));
    assertThat(result.get(0).getName(), is("pdisk-1"));
    assertThat(result.get(1).getId(), is(disk2.getId()));
    assertThat(result.get(1).getName(), is("pdisk-2"));
  }
}
