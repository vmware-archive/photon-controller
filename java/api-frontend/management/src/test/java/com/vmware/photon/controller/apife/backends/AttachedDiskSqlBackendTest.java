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

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.AttachedDiskDao;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TenantEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link AttachedDiskSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class AttachedDiskSqlBackendTest extends BaseDaoTest {

  @Inject
  private AttachedDiskBackend attachedDiskBackend;
  @Inject
  private AttachedDiskDao attachedDiskDao;
  @Inject
  private EntityFactory entityFactory;
  @Inject
  private FlavorBackend flavorBackend;
  @Inject
  private DiskBackend diskBackend;

  private String projectId;
  private VmEntity vm;

  @BeforeMethod()
  public void setUp() throws Throwable {
    super.setUp();

    QuotaLineItemEntity ticketLimit = new QuotaLineItemEntity("vm.cost", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm.cost", 10, QuotaUnit.COUNT);

    TenantEntity tenantEntity = entityFactory.createTenant("t1");
    String tenantId = tenantEntity.getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", ticketLimit);
    entityFactory.loadFlavors();

    projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);
    vm = entityFactory.createVm(projectId, "core-100", "vm1name");
  }

  @Test
  public void testCreateAttachedNewDisk() throws Exception {
    final String vmId = vm.getId();

    AttachedDiskCreateSpec spec = new AttachedDiskCreateSpec();
    spec.setName("ed1name");
    spec.setKind(EphemeralDisk.KIND);
    spec.setCapacityGb(20);
    spec.setBootDisk(true);
    spec.setFlavor("core-100");
    List<AttachedDiskCreateSpec> specList = ImmutableList.of(spec);

    final String attachedDiskId = attachedDiskBackend.createAttachedDisks(vm, specList).get(0).getId();

    AttachedDiskEntity attachedDisk = attachedDiskDao.findById(attachedDiskId).get();
    assertThat(attachedDisk.isBootDisk(), is(true));
    assertThat(attachedDisk.getVmId(), is(vmId));

    BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
    assertThat(disk.getProjectId(), is(projectId));
    assertThat(disk.getKind(), is(EphemeralDisk.KIND));
    assertThat(disk.getName(), is("ed1name"));
    assertThat(disk.getCapacityGb(), is(20));
    assertThat(flavorBackend.getEntityById(disk.getFlavorId()).getName(), is("core-100"));
  }

  @Test
  public void testAttachPersistentDisk() throws Exception {
    final String vmId = vm.getId();
    List<PersistentDiskEntity> disks = new ArrayList<>();
    disks.add(entityFactory.createPersistentDisk(projectId, "core-100", "pd1name", 20));
    disks.add(entityFactory.createPersistentDisk(projectId, "core-100", "pd2name", 20));

    attachedDiskBackend.attachDisks(vm, disks);

    AttachedDiskEntity attachedDisk1 = vm.getAttachedDisks().get(0);
    assertThat(attachedDisk1.getVmId(), is(vmId));
    assertThat(
        diskBackend.find(attachedDisk1.getKind(), attachedDisk1.getUnderlyingDiskId()).getName(), is("pd1name"));
    AttachedDiskEntity attachedDisk2 = vm.getAttachedDisks().get(1);
    assertThat(attachedDisk2.getVmId(), is(vmId));
    assertThat(
        diskBackend.find(attachedDisk2.getKind(), attachedDisk2.getUnderlyingDiskId()).getName(), is("pd2name"));
  }

  @Test
  public void testDetachAndAttachPersistentDisks() throws Throwable {
    PersistentDiskEntity disk1 = entityFactory.createPersistentDisk(projectId, "core-100", "test-vm-disk-1", 64);
    PersistentDiskEntity disk2 = entityFactory.createPersistentDisk(projectId, "core-100", "test-vm-disk-2", 64);

    assertThat(vm.getAttachedDisks().size(), is(0));

    attachedDiskBackend.attachDisks(vm, ImmutableList.of(disk1));
    assertThat(vm.getAttachedDisks().size(), is(1));

    attachedDiskBackend.attachDisks(vm, ImmutableList.of(disk2));
    assertThat(vm.getAttachedDisks().size(), is(2));

    attachedDiskBackend.deleteAttachedDisks(vm, ImmutableList.of(disk1));
    assertThat(vm.getAttachedDisks().size(), is(1));

    attachedDiskBackend.deleteAttachedDisks(vm, ImmutableList.of(disk2));
    assertThat(vm.getAttachedDisks().size(), is(0));
  }
}
