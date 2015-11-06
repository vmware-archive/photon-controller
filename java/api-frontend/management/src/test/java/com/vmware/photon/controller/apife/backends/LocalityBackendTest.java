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

import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.LocalityDao;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link LocalityBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class LocalityBackendTest extends BaseDaoTest {

  @Inject
  private LocalityBackend localityBackend;
  @Inject
  private LocalityDao localityDao;
  @Inject
  private EntityFactory entityFactory;

  private String projectId;

  @BeforeMethod()
  public void setUp() throws Throwable {
    super.setUp();
    entityFactory.loadFlavors();

    QuotaLineItemEntity ticketLimit = new QuotaLineItemEntity("vm.cost", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm.cost", 10, QuotaUnit.COUNT);

    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String tenantId = tenantEntity.getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", ticketLimit);
    projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);

  }

  @Test
  public void testCreateVmLocalityEntity() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "vm1name");

    LocalityEntity localityEntity = localityBackend.create(vm, new LocalitySpec("disk-id1", "disk"));

    assertThat(localityEntity.getVm().getId(), is(vm.getId()));
  }

  @Test
  public void testCreateDiskLocalityEntity() throws Exception {
    PersistentDiskEntity persistentDisk = entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);

    LocalityEntity localityEntity = localityBackend.create(persistentDisk, new LocalitySpec("vm-id1", "vm"));

    assertThat(localityEntity.getDisk().getId(), is(persistentDisk.getId()));
  }

  @Test
  public void testCreateVmLocalityEntities() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "vm1name");

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("disk-id1", "disk"));
    affinities.add(new LocalitySpec("disk-id2", "disk"));

    List<LocalityEntity> localityEntities = localityBackend.create(vm, affinities);

    for (LocalityEntity entity : localityEntities) {
      String id = entity.getId();
      LocalityEntity localityEntity = localityDao.findById(id).get();
      assertThat(localityEntity.getVm().getId(), is(vm.getId()));
    }
  }

  @Test
  public void testCreateDiskLocalityEntities() throws Exception {
    PersistentDiskEntity persistentDisk = entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);

    flushSession();

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("vm-id1", "vm"));
    affinities.add(new LocalitySpec("vm-id2", "vm"));

    List<LocalityEntity> localityEntities = localityBackend
        .create(persistentDisk, affinities);

    for (LocalityEntity entity : localityEntities) {
      String id = entity.getId();
      LocalityEntity localityEntity = localityDao.findById(id).get();
      assertThat(localityEntity.getDisk().getId(), is(persistentDisk.getId()));
    }
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCreateEmptyLocality() throws Exception {
    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("vm-id1", "vm"));
    affinities.add(new LocalitySpec("vm-id2", "vm"));

    localityBackend.create(null, affinities);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testCreateIllegalLocality() throws Exception {
    ImageEntity image = new ImageEntity();

    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("vm-id1", "vm"));
    affinities.add(new LocalitySpec("vm-id2", "vm"));

    localityBackend.create(image, affinities);
  }

}
