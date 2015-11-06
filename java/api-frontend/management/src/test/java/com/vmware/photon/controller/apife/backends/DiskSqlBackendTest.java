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
import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.db.dao.ProjectDao;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DiskSqlBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class DiskSqlBackendTest extends BaseDaoTest {

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private DiskBackend diskBackend;

  @Inject
  private TaskBackend taskBackend;

  @Inject
  private ProjectDao projectDao;

  @Inject
  private FlavorBackend flavorbackend;

  private String projectId;

  @BeforeMethod()
  public void setUp() throws Throwable {
    super.setUp();

    QuotaLineItemEntity tenantLimit = new QuotaLineItemEntity("vm", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT);

    String tenantId = entityFactory.createTenant("vmware").getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", tenantLimit);
    projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);
    entityFactory.loadFlavors();
  }

  @Test
  public void testCreateWithSameName() throws Exception {
    entityFactory.createPersistentDisk(projectId, "core-100", "test-disk", 100);
    entityFactory.createPersistentDisk(projectId, "core-100", "test-disk", 100);
    assertThat(getUsage("persistent-disk.cost"), is(2.0));

    entityFactory.createEphemeralDisk(projectId, "core-100", "test-disk", 100);
    entityFactory.createEphemeralDisk(projectId, "core-100", "test-disk", 100);
    assertThat(getUsage("ephemeral-disk.cost"), is(2.0));
  }

  @Test
  public void testDiskCreationTask() throws Exception {
    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("vm-id1", "vm"));
    affinities.add(new LocalitySpec("vm-id2", "vm"));

    DiskCreateSpec spec = new DiskCreateSpec();
    spec.setName("test-disk");
    spec.setKind(PersistentDisk.KIND);
    spec.setFlavor("core-200");
    spec.setCapacityGb(4);
    spec.setAffinities(affinities);

    TaskEntity task = diskBackend.prepareDiskCreate(projectId, spec);
    flushSession();

    assertThat(task, is(notNullValue()));
    assertThat(task.getState(), is(TaskEntity.State.QUEUED));
  }

  @Test
  public void testCreatePersistentDisk() throws Exception {
    List<LocalitySpec> affinities = new ArrayList<>();
    affinities.add(new LocalitySpec("vm-id1", "vm"));
    affinities.add(new LocalitySpec("vm-id2", "vm"));

    DiskCreateSpec spec = new DiskCreateSpec();
    spec.setName("test-disk");
    spec.setKind(PersistentDisk.KIND);
    spec.setFlavor("core-200");
    spec.setCapacityGb(4);
    spec.setAffinities(affinities);

    String diskId = diskBackend.prepareDiskCreate(projectId, spec).getEntityId();
    flushSession();

    PersistentDiskEntity disk = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId);
    assertThat(disk, is(notNullValue()));
    assertThat(getUsage("persistent-disk.cost"), is(1.8));

    // now validate cost is correctly accounted for
    assertThat(disk.getAffinities().get(0).getResourceId(), is("vm-id1"));
    assertThat(disk.getAffinities().get(0).getKind(), is("vm"));
    assertThat(disk.getAffinities().get(0).getDisk(), is(disk));
    assertThat(disk.getAffinities().get(1).getResourceId(), is("vm-id2"));
    assertThat(disk.getAffinities().get(1).getKind(), is("vm"));

    assertThat(disk.getName(), is("test-disk"));
    assertThat(flavorbackend.getEntityById(disk.getFlavorId()).getName(), is("core-200"));
  }

  @Test
  public void testFailInvalidAttachedDiskKind() throws Exception {
    AttachedDiskCreateSpec attachedDiskCreateSpec = new AttachedDiskCreateSpec();
    attachedDiskCreateSpec.setKind("persistent");
    attachedDiskCreateSpec.setName("disk-1");
    try {
      diskBackend.create(projectId, attachedDiskCreateSpec);
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), is("Attached disk can only be ephemeral disk, but got persistent"));
    }
  }

  @Test
  public void testToApiRepresentation() throws Exception {
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    PersistentDiskEntity persistentDiskEntity = entityFactory.createPersistentDisk(projectId,
        "core-100", "test-vm-disk-1", 64);
    persistentDiskEntity.setDatastore("datastore-id");
    entityFactory.attachDisk(vm, PersistentDisk.KIND, persistentDiskEntity.getId());

    PersistentDisk disk = diskBackend.toApiRepresentation(persistentDiskEntity.getId());
    assertThat(disk.getKind(), is(PersistentDisk.KIND));
    assertThat(disk.getName(), is("test-vm-disk-1"));
    assertThat(disk.getFlavor(), is("core-100"));
    assertThat(disk.getDatastore(), is("datastore-id"));
    assertThat(disk.getVms().get(0), is(vm.getId()));
  }

  // TODO(olegs): this is not even using the backend?
  @Test
  public void testCreateEphemeralDisk() throws Exception {
    entityFactory.createEphemeralDisk(projectId, "core-100", "disk-name", 2);

    // now validate cost is correctly accounted for
    assertThat(getUsage("ephemeral-disk"), is(1.0));
    assertThat(getUsage("ephemeral-disk.flavor.core-100"), is(1.0));
    assertThat(getUsage("ephemeral-disk.cost"), is(1.0));
    assertThat(getUsage("ephemeral-disk.capacity"), is(2.0));

    assertThat(diskBackend.filter(projectId, Optional.<String>absent()).size(), is(0));
  }

  @Test
  public void testTombstonePersistentDisk() throws Exception {
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);
    assertThat(getUsage("persistent-disk"), is(1.0));
    String diskId = disk.getId();

    diskBackend.tombstone("persistent-disk", disk.getId());

    try {
      diskBackend.find(EphemeralDisk.KIND, diskId);
      fail();
    } catch (DiskNotFoundException ex) {
    }
    assertThat(getUsage("persistent-disk"), is(0.0));
    assertThat(getUsage("persistent-disk.flavor.core-200"), is(0.0));
    assertThat(getUsage("persistent-disk.cost"), is(0.0));
    assertThat(getUsage("persistent-disk.capacity"), is(0.0));
  }

  @Test
  public void testTombstoneEphemeralDisk() throws Exception {
    EphemeralDiskEntity disk = entityFactory.createEphemeralDisk(projectId, "core-100", "disk-name", 4);

    assertThat(getUsage("ephemeral-disk"), is(1.0));

    flushSession();
    diskBackend.tombstone("ephemeral-disk", disk.getId());

    try {
      diskBackend.find(EphemeralDisk.KIND, disk.getId());
      fail();
    } catch (DiskNotFoundException ex) {
    }
    assertThat(getUsage("ephemeral-disk"), is(0.0));
    assertThat(getUsage("ephemeral-disk.flavor.core-100"), is(0.0));
    assertThat(getUsage("ephemeral-disk.cost"), is(0.0));
    assertThat(getUsage("ephemeral-disk.capacity"), is(0.0));
  }

  @Test
  public void testFindDatastoreById() throws ExternalException {
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);
    diskBackend.updateState(disk, DiskState.DETACHED, "agent-id", "datastore-id");
    flushSession();

    assertThat(disk.getDatastore(), is("datastore-id"));
  }

  @Test(expectedExceptions = InvalidOperationStateException.class)
  public void testDeleteWhileAttached() throws Exception {
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);
    diskBackend.updateState(disk, DiskState.ATTACHED);

    diskBackend.prepareDiskDelete(disk.getId());
  }

  @Test(expectedExceptions = FlavorNotFoundException.class)
  public void testInvalidFlavor() throws Exception {
    DiskCreateSpec spec = new DiskCreateSpec();
    spec.setName("test-disk");
    spec.setKind(PersistentDisk.KIND);
    spec.setFlavor("bad-flavor");
    spec.setCapacityGb(4);

    diskBackend.prepareDiskCreate(projectId, spec);
  }

  @Test(expectedExceptions = DiskNotFoundException.class)
  public void testGetTasksWithNoVm() throws Exception {
    diskBackend.getTasks("disk1", Optional.<String>absent());
  }

  @Test
  public void testUpdateState() throws Exception {
    PersistentDiskEntity persistentDisk =
        entityFactory.createPersistentDisk(projectId, "core-200", "disk-name", 4);
    assertThat(persistentDisk.getState(), is(nullValue()));
    diskBackend.updateState(persistentDisk, DiskState.ATTACHED);
    flushSession();

    persistentDisk = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, persistentDisk.getId());
    assertThat(persistentDisk.getState(), is(DiskState.ATTACHED));
    assertThat(persistentDisk.getAgent(), is(nullValue()));
    assertThat(persistentDisk.getDatastore(), is(nullValue()));

    diskBackend.updateState(persistentDisk, DiskState.DETACHED, "agent-id", "datastore-id");
    flushSession();

    persistentDisk = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, persistentDisk.getId());
    assertThat(persistentDisk.getState(), is(DiskState.DETACHED));
    assertThat(persistentDisk.getAgent(), is("agent-id"));
    assertThat(persistentDisk.getDatastore(), is("datastore-id"));

    EphemeralDiskEntity ephemeralDiskEntity =
        entityFactory.createEphemeralDisk(projectId, "core-100", "disk-name", 4);
    assertThat(ephemeralDiskEntity.getState(), is(nullValue()));
    diskBackend.updateState(ephemeralDiskEntity, DiskState.ATTACHED);
    flushSession();

    ephemeralDiskEntity = (EphemeralDiskEntity) diskBackend.find(EphemeralDisk.KIND, ephemeralDiskEntity.getId());
    assertThat(ephemeralDiskEntity.getState(), is(DiskState.ATTACHED));
    assertThat(ephemeralDiskEntity.getDatastore(), is(nullValue()));

    diskBackend.updateState(ephemeralDiskEntity, DiskState.DETACHED, "agent-id", "datastore-id");
    flushSession();

    ephemeralDiskEntity = (EphemeralDiskEntity) diskBackend.find(EphemeralDisk.KIND, ephemeralDiskEntity.getId());
    assertThat(ephemeralDiskEntity.getDatastore(), is("datastore-id"));
    assertThat(ephemeralDiskEntity.getAgent(), is(nullValue()));
  }

  private double getUsage(String key) {
    return entityFactory.getProjectUsage(projectId, key);
  }

  @Transactional
  private ProjectEntity getProject() {
    return projectDao.findById(projectId).get();
  }
}
