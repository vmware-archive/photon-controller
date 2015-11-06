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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.db.dao.ResourceTicketDao;
import com.vmware.photon.controller.apife.db.dao.TenantDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.ResourceTicketEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.codahale.dropwizard.util.Duration;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

/**
 * Tests {@link TombstoneSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class TombstoneSqlBackendTest extends BaseDaoTest {

  @Inject
  private TaskBackend taskBackend;

  @Inject
  private TombstoneSqlBackend tombstoneBackend;

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private TenantDao tenantDao;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private VmDao vmDao;

  @Inject
  private ResourceTicketDao resourceTicketDao;

  private ProjectEntity project;

  @BeforeMethod
  public void setUp() throws Throwable {
    super.setUp();
    entityFactory.loadFlavors();

    TenantEntity tenant = new TenantEntity();
    tenant.setName("bakkensoft");
    tenantDao.create(tenant);

    ResourceTicketEntity resourceTicketEntity = new ResourceTicketEntity();
    resourceTicketEntity.setTenantId(tenant.getId());
    resourceTicketEntity = resourceTicketDao.create(resourceTicketEntity);

    project = new ProjectEntity();
    project.setTenantId(tenant.getId());
    project.setName("staging");
    project.setResourceTicketId(resourceTicketEntity.getId());
    projectDao.create(project);
  }

  @Test
  public void testGetStaleTombstonesAndDelete() throws Exception {
    VmEntity vm1 = entityFactory.createVm(project.getId(), "core-100", "vm-1", VmState.CREATING, null);
    taskBackend.createQueuedTask(vm1, Operation.CREATE_VM);

    VmEntity vm2 = entityFactory.createVm(project.getId(), "core-100", "vm-2", VmState.CREATING, null);
    taskBackend.createQueuedTask(vm2, Operation.CREATE_VM);

    flushSession();

    tombstoneBackend.create(Vm.KIND, vm1.getId());
    vmDao.delete(vm1);

    TombstoneEntity tombstone = tombstoneBackend.create(Vm.KIND, vm2.getId());
    vmDao.delete(vm2);
    tombstone.setTombstoneTime(System.currentTimeMillis() - Duration.hours(6).toMilliseconds());

    flushSession();

    List<TombstoneEntity> tombstones = tombstoneBackend.getStaleTombstones();
    assertThat(tombstones.size(), is(1));
    TombstoneEntity staleTombstone = tombstones.get(0);
    assertThat(staleTombstone.getEntityId(), is(vm2.getId()));
    assertThat(staleTombstone.getId(), is(tombstone.getId()));

    tombstoneBackend.delete(tombstoneBackend.getByEntityId(vm2.getId()));
    flushSession();

    assertThat(tombstoneBackend.getStaleTombstones().isEmpty(), is(true));
  }
}
