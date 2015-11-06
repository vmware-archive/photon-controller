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
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.apife.backends.BackendTestModule;
import com.vmware.photon.controller.apife.backends.EntityFactory;
import com.vmware.photon.controller.apife.backends.FlavorSqlBackend;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
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
public class EphemeralDiskDaoTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private EphemeralDiskDao ephemeralDiskDao;

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
    flavor = flavorSqlBackend.getEntityByNameAndKind("core-100", EphemeralDisk.KIND);
  }

  @Test
  public void testCreate() throws Exception {
    EphemeralDiskEntity disk = new EphemeralDiskEntity();
    disk.setProjectId(project.getId());
    disk.setName("edisk-name-1");
    disk.setCapacityGb(2);
    disk.setFlavorId(flavor.getId());
    List<QuotaLineItemEntity> diskCost = new ArrayList<>(flavor.getCost());
    diskCost.add(new QuotaLineItemEntity("ephemeral-disk.capacity", 2, QuotaUnit.GB));
    disk.setCost(diskCost);

    ephemeralDiskDao.create(disk);

    String id = disk.getId();

    flushSession();

    // assert that the disk was found and that it contains the right project object
    Optional<EphemeralDiskEntity> found = ephemeralDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
    assertThat(found.get().getKind(), equalTo(EphemeralDisk.KIND));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getName(), equalTo("core-100"));
    assertThat(flavorSqlBackend.getEntityById(found.get().getFlavorId()).getKind(), equalTo(EphemeralDisk.KIND));
    assertThat(found.get().getCapacityGb(), is(2));
    assertThat(found.get().getDatastore(), nullValue());

    // read cost and assert that it maps to a correct object...
    QuotaCost cost = new QuotaCost(found.get().getCost());
    assertThat("ephemeral-disk, ephemeral-disk.flavor, ephemeral-disk.cost, ephemeral-disk.capacity",
        cost.getCostKeys().contains("ephemeral-disk"),
        is(true));
    assertThat("value is correct",
        cost.getCost("ephemeral-disk").getValue(),
        is(1.0));
    assertThat("flavor key",
        cost.getCostKeys().contains("ephemeral-disk.flavor.core-100"),
        is(true));
    assertThat("flavor value",
        cost.getCost("ephemeral-disk.flavor.core-100").getValue(),
        is(1.0));
    assertThat("cost key",
        cost.getCostKeys().contains("ephemeral-disk.cost"),
        is(true));
    assertThat("cost value",
        cost.getCost("ephemeral-disk.cost").getValue(),
        is(1.0));
    assertThat("capacity key",
        cost.getCostKeys().contains("ephemeral-disk.capacity"),
        is(true));
    assertThat("capacity value",
        cost.getCost("ephemeral-disk.capacity").getValue(),
        is(2.0));
    assertThat("only 4 items are in the set",
        cost.getCostKeys().size(),
        is(4));
  }

  @Test
  public void testDatastorePersistence() throws Exception {
    EphemeralDiskEntity disk = new EphemeralDiskEntity();
    disk.setDatastore("datastore-id-0");
    disk.setProjectId(project.getId());
    disk.setName("edisk-name-2");
    disk.setCapacityGb(2);
    disk.setFlavorId(flavor.getId());
    disk.setCost(flavor.getCost());

    ephemeralDiskDao.create(disk);

    String id = disk.getId();

    flushSession();

    // assert that the disk was found and that it contains the right project object
    Optional<EphemeralDiskEntity> found = ephemeralDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getDatastore(), equalTo("datastore-id-0"));
  }

  @Test
  public void testCreateNameCollision() {
    EphemeralDiskEntity ephemeralDisk1 = new EphemeralDiskEntity();
    ephemeralDisk1.setProjectId(project.getId());
    ephemeralDisk1.setName("ephemeral-diskt-1");
    ephemeralDiskDao.create(ephemeralDisk1);

    // identical
    EphemeralDiskEntity ephemeralDisk2 = new EphemeralDiskEntity();
    ephemeralDisk2.setProjectId(project.getId());
    ephemeralDisk2.setName("ephemeral-diskt-1");
    ephemeralDiskDao.create(ephemeralDisk2);

    // when trimmed identical
    EphemeralDiskEntity ephemeralDisk3 = new EphemeralDiskEntity();
    ephemeralDisk3.setProjectId(project.getId());
    ephemeralDisk3.setName(" ephemeral-diskt-1");
    ephemeralDiskDao.create(ephemeralDisk3);

    EphemeralDiskEntity ephemeralDisk4 = new EphemeralDiskEntity();
    ephemeralDisk4.setProjectId(project.getId());
    ephemeralDisk4.setName(" ephemeral-diskt-1 ");
    ephemeralDiskDao.create(ephemeralDisk4);

    flushSession();

    List<EphemeralDiskEntity> ephemeralDisks = ephemeralDiskDao.findAll(project);
    assertThat(ephemeralDisks.size(), is(4));

    for (EphemeralDiskEntity ephemeralDisk : ephemeralDisks) {
      assertThat(ephemeralDisk.getProjectId(), is(project.getId()));
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
    EphemeralDiskEntity ephemeralDisk = new EphemeralDiskEntity();
    ephemeralDisk.setProjectId(project.getId());
    ephemeralDisk.setName("ephemeralDisk-1");
    ephemeralDisk.setState(state);
    ephemeralDiskDao.create(ephemeralDisk);

    flushSession();

    Optional<EphemeralDiskEntity> found = ephemeralDiskDao.findByName("ephemeralDisk-1", project);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getProjectId(), equalTo(project.getId()));
  }

  @Test
  public void testListByNameWithMultipleDiskEntities() {
    for (DiskState state : ALL_DISK_STATES) {
      EphemeralDiskEntity disk = new EphemeralDiskEntity();
      disk.setProjectId(project.getId());
      disk.setName("disk-1");
      disk.setState(state);
      ephemeralDiskDao.create(disk);
    }

    flushSession();

    try {
      ephemeralDiskDao.findByName("disk-1", project);
    } catch (NonUniqueResultException ex) {
      assertThat(ex.getMessage(), is(
          String.format("query did not return a unique result: %d", ALL_DISK_STATES.length)));
    }

    List<EphemeralDiskEntity> disks = ephemeralDiskDao.listByName("disk-1", project);
    assertThat(disks.size(), is(ALL_DISK_STATES.length));
  }

  @Test
  public void testFindAll() {
    assertThat(ephemeralDiskDao.findAll(project).isEmpty(), is(true));

    String ephemeralDiskNames[] = {"ephemeralDisk1", "ephemeralDisk2", "ephemeralDisk3", "ephemeralDisk4"};
    for (String name : ephemeralDiskNames) {
      EphemeralDiskEntity ephemeralDisk = new EphemeralDiskEntity();
      ephemeralDisk.setProjectId(project.getId());
      ephemeralDisk.setName(name);
      ephemeralDiskDao.create(ephemeralDisk);
    }

    flushSession();

    List<EphemeralDiskEntity> ephemeralDisks = ephemeralDiskDao.findAll(project);
    assertThat(ephemeralDisks.size(), is(4));
  }

  @Test
  public void testFindByTag() {

    // two projects, each with 3 ephemeralDisks, tagged similarly
    // validate tag lookups work as expected and project isolation is working
    ProjectEntity project2 = new ProjectEntity();
    project2.setTenantId(tenant.getId());
    project2.setName("production");
    project2 = projectDao.create(project2);

    TagEntity tag1 = tagDao.findOrCreate("frontend");
    TagEntity tag2 = tagDao.findOrCreate("cesspool");
    TagEntity tag3 = tagDao.findOrCreate("gold");

    EphemeralDiskEntity[] ephemeralDisks = new EphemeralDiskEntity[5];
    for (int i = 0; i < ephemeralDisks.length; i++) {
      EphemeralDiskEntity ephemeralDisk = new EphemeralDiskEntity();
      ephemeralDisk.setProjectId(project.getId());
      ephemeralDisk.setName("ephemeralDisk-p0-" + i);
      ephemeralDisks[i] = ephemeralDiskDao.create(ephemeralDisk);
    }

    // add tag1 to ephemeralDisk 0 to 2
    ephemeralDisks[0].getTags().add(tag1);
    ephemeralDisks[1].getTags().add(tag1);
    ephemeralDisks[2].getTags().add(tag1);

    // add tag2 to ephemeralDisk 0 and 3
    ephemeralDisks[0].getTags().add(tag2);
    ephemeralDisks[3].getTags().add(tag2);

    // add tag3 to ephemeralDisk 4
    ephemeralDisks[4].getTags().add(tag3);

    flushSession();

    List<EphemeralDiskEntity> result = ephemeralDiskDao.findByTag(tag1.getValue(), project);
    assertThat(result, hasSize(3));

    result = ephemeralDiskDao.findByTag(tag2.getValue(), project);
    assertThat(result, hasSize(2));

    result = ephemeralDiskDao.findByTag(tag3.getValue(), project);
    assertThat(result, hasSize(1));

    result = ephemeralDiskDao.findByTag(tag1.getValue(), project2);
    assertThat(result, hasSize(0));
  }

  @Test
  public void testFindByFlavor() {
    EphemeralDiskEntity disk1 = new EphemeralDiskEntity();
    disk1.setProjectId(project.getId());
    disk1.setName("pdisk-1");
    disk1.setCapacityGb(2);
    disk1.setFlavorId(flavor.getId());
    disk1.setCost(flavor.getCost());
    ephemeralDiskDao.create(disk1);

    EphemeralDiskEntity disk2 = new EphemeralDiskEntity();
    disk2.setProjectId(project.getId());
    disk2.setName("pdisk-2");
    disk2.setCapacityGb(2);
    disk2.setFlavorId(flavor.getId());
    disk2.setCost(flavor.getCost());
    ephemeralDiskDao.create(disk2);

    flushSession();

    List<EphemeralDiskEntity> result = ephemeralDiskDao.findByFlavor(flavor.getId());
    assertThat(result.size(), is(2));
    assertThat(result.get(0).getId(), is(disk1.getId()));
    assertThat(result.get(0).getName(), is("pdisk-1"));
    assertThat(result.get(1).getId(), is(disk2.getId()));
    assertThat(result.get(1).getName(), is("pdisk-2"));
  }
}
