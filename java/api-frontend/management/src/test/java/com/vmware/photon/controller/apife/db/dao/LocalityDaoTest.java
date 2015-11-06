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

import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.hibernate.SessionFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * This class implements the Vm entity DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class})
public class LocalityDaoTest extends BaseDaoTest {

  @Inject
  private LocalityDao localityDao;

  @Inject
  private SessionFactory sessionFactory;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private PersistentDiskDao persistentDiskDao;

  private TenantEntity tenant;

  private ProjectEntity project;

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
  }

  @Test
  public void testCreateVmLocality() throws Exception {
    VmEntity vmEntity = new VmEntity();
    vmEntity.setId("vm-id1");
    vmEntity.setProjectId(project.getId());

    vmDao.create(vmEntity);

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("vm-id1");
    localityEntity.setKind("vm");
    localityEntity.setVm(vmEntity);

    localityDao.create(localityEntity);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the LocalitySpec was found and that it contains the right project object
    String id = localityEntity.getId();

    Optional<LocalityEntity> found = localityDao.findById(id);
    assertThat(found.get().getId(), equalTo(id));
    assertThat(found.get().getKind(), equalTo(Vm.KIND));
    assertThat(found.get().getVm(), equalTo(vmEntity));
  }

  @Test
  public void testCreateDiskLocality() throws Exception {
    PersistentDiskEntity diskEntity = new PersistentDiskEntity();
    diskEntity.setId("disk-id");
    diskEntity.setProjectId(project.getId());

    persistentDiskDao.create(diskEntity);

    LocalityEntity localityEntity = new LocalityEntity();
    localityEntity.setResourceId("disk-id1");
    localityEntity.setKind(PersistentDisk.KIND);
    localityEntity.setDisk(diskEntity);

    localityDao.create(localityEntity);

    String id = localityEntity.getId();

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the LocalitySpec was found and that it contains the right project object
    Optional<LocalityEntity> found = localityDao.findById(id);
    assertThat(found.get().getId(), equalTo(id));
    assertThat(found.get().getKind(), equalTo(PersistentDisk.KIND));
    assertThat(found.get().getDisk(), equalTo(diskEntity));
  }

}
