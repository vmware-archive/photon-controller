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

import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.apife.backends.BackendTestModule;
import com.vmware.photon.controller.apife.backends.EntityFactory;
import com.vmware.photon.controller.apife.backends.FlavorSqlBackend;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * This class implements the Vm entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class AttachedDiskDaoTest extends BaseDaoTest {

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private PersistentDiskDao persistentDiskDao;

  @Inject
  private EphemeralDiskDao ephemeralDiskDao;

  @Inject
  private AttachedDiskDao attachedDiskDao;

  @Inject
  private TagDao tagDao;

  @Inject
  private SessionFactory sessionFactory;

  @Inject
  private FlavorSqlBackend flavorSqlBackend;

  @Inject
  private EntityFactory entityFactory;

  private TenantEntity tenant;

  private ProjectEntity project;

  private FlavorEntity flavorEntity;

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
  }

  @Test
  public void testEmptyTransientColumns() {
    AttachedDiskEntity attachedDiskEntity = new AttachedDiskEntity();

    attachedDiskEntity.setKind(PersistentDisk.KIND);
    attachedDiskEntity.setBootDisk(true);

    attachedDiskDao.create(attachedDiskEntity);
    String id = attachedDiskEntity.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the disk was found and that it contains the right project object
    Optional<AttachedDiskEntity> found = attachedDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getKind(), equalTo(PersistentDisk.KIND));
    assertThat(found.get().isBootDisk(), is(true));
    assertThat(found.get().getUnderlyingDiskId(), is(nullValue()));
  }

  @Test
  public void testPersistentDiskAttachedTransientColumns() throws Exception {
    flavorEntity = flavorSqlBackend.getEntityByNameAndKind("core-100", PersistentDisk.KIND);

    // create persistent disk and then assign this disk
    // to the attached disk entity, then validate that the
    // transient properties fetch from the underlying disk
    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setProjectId(project.getId());
    disk.setName("pdisk-name-0");
    disk.setFlavorId(flavorEntity.getId());
    disk.setCapacityGb(2);
    disk.setCost(flavorEntity.getCost());
    persistentDiskDao.create(disk);

    String diskId = disk.getId();

    AttachedDiskEntity attachedDiskEntity = new AttachedDiskEntity();
    attachedDiskEntity.setKind(PersistentDisk.KIND);
    attachedDiskEntity.setPersistentDiskId(diskId);

    attachedDiskDao.create(attachedDiskEntity);
    String id = attachedDiskEntity.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the disk was found and that it contains the right project object
    Optional<AttachedDiskEntity> found = attachedDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getKind(), equalTo(PersistentDisk.KIND));
    assertThat(found.get().getUnderlyingDiskId(), is(diskId));
  }

  @Test
  public void testEphemeralDiskAttachedTransientColumns() throws Exception {
    flavorEntity = flavorSqlBackend.getEntityByNameAndKind("core-100", EphemeralDisk.KIND);

    // create ephemeral disk and then assign this disk
    // to the attached disk entity, then validate that the
    // transient properties fetch from the underlying disk
    EphemeralDiskEntity disk = new EphemeralDiskEntity();
    disk.setProjectId(project.getId());
    disk.setName("edisk-name-0");
    disk.setFlavorId(flavorEntity.getId());
    disk.setCapacityGb(2);
    disk.setCost(flavorEntity.getCost());
    ephemeralDiskDao.create(disk);

    String diskId = disk.getId();

    AttachedDiskEntity attachedDiskEntity = new AttachedDiskEntity();
    attachedDiskEntity.setKind(EphemeralDisk.KIND);
    attachedDiskEntity.setBootDisk(true);
    attachedDiskEntity.setEphemeralDiskId(diskId);

    attachedDiskDao.create(attachedDiskEntity);
    String id = attachedDiskEntity.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the disk was found and that it contains the right project object
    Optional<AttachedDiskEntity> found = attachedDiskDao.findById(id);
    assertThat(found.isPresent(), is(true));
    assertThat(found.get().getKind(), equalTo(EphemeralDisk.KIND));
    assertThat(found.get().isBootDisk(), is(true));
    assertThat(found.get().getUnderlyingDiskId(), is(diskId));
  }
}
