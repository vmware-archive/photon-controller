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
import com.vmware.photon.controller.apife.entities.IsoEntity;
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
 * This class implements the ISO DAO Tests.
 */
@Guice(modules = {HibernateTestModule.class})
public class IsoDaoTest extends BaseDaoTest {

  @Inject
  private IsoDao isoDao;

  @Inject
  private SessionFactory sessionFactory;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

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
  public void testCreateIso() throws Exception {
    VmEntity vmEntity = new VmEntity();
    vmEntity.setId("vm-id1");
    vmEntity.setProjectId(project.getId());

    vmDao.create(vmEntity);

    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName("iso-name");
    isoEntity.setSize(new Long(1000000));
    isoEntity.setVm(vmEntity);
    isoDao.create(isoEntity);

    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    // assert that the Iso entity was found and that it contains the right project vm
    String id = isoEntity.getId();

    Optional<IsoEntity> found = isoDao.findById(id);
    assertThat(found.get().getId(), equalTo(id));
    assertThat(found.get().getName(), equalTo("iso-name"));
    assertThat(found.get().getSize(), equalTo(new Long(1000000)));
    assertThat(found.get().getVm(), equalTo(vmEntity));
  }
}
