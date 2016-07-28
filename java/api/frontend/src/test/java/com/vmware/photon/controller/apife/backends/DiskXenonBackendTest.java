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

import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.LocalitySpec;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.base.TagEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.ProjectNotFoundException;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link DiskXenonBackend}.
 */
public class DiskXenonBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static void commonHostAndClientSetup(
      BasicServiceHost basicServiceHost, ApiFeXenonRestClient apiFeXenonRestClient) {
    host = basicServiceHost;
    xenonClient = apiFeXenonRestClient;

    if (host == null) {
      throw new IllegalStateException(
          "host is not expected to be null in this test setup");
    }

    if (xenonClient == null) {
      throw new IllegalStateException(
          "xenonClient is not expected to be null in this test setup");
    }

    if (!host.isReady()) {
      throw new IllegalStateException(
          "host is expected to be in started state, current state=" + host.getState());
    }
  }

  private static void commonHostDocumentsCleanup() throws Throwable {
    if (host != null) {
      ServiceHostUtils.deleteAllDocuments(host, "test-host");
    }
  }

  private static void commonHostAndClientTeardown() throws Throwable {
    if (xenonClient != null) {
      xenonClient.stop();
      xenonClient = null;
    }

    if (host != null) {
      host.destroy();
      host = null;
    }
  }


  @Test
  private void dummy() {
  }

  /**
   * Tests for creating disk.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class CreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DiskXenonBackend diskXenonBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;
    private DiskCreateSpec spec;
    private String projectId;

    @BeforeMethod
    public void setUp() throws Throwable {
      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      ProjectCreateSpec projectCreateSpec = new ProjectCreateSpec();
      projectCreateSpec.setName("p1");
      projectCreateSpec.setResourceTicket(reservation);
      TaskEntity taskEntity = projectBackend.createProject(tenantId, projectCreateSpec);
      projectId = taskEntity.getEntityId();

      FlavorCreateSpec flavorCreateSpec = new FlavorCreateSpec();
      flavorCreateSpec.setName("test-flavor");
      flavorCreateSpec.setKind(PersistentDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      flavorCreateSpec.setName("test-flavor-ephemeral-disk");
      flavorCreateSpec.setKind(EphemeralDisk.KIND);
      flavorBackend.createFlavor(flavorCreateSpec);

      spec = new DiskCreateSpec();
      spec.setName("disk");
      spec.setKind(PersistentDisk.KIND);
      spec.setCapacityGb(2);
      spec.setFlavor("test-flavor");
      spec.setTags(new HashSet<>(Arrays.asList("tag1")));

      List<LocalitySpec> localitySpecList = new ArrayList<>();
      localitySpecList.add(new LocalitySpec("vm-1", "vm"));
      localitySpecList.add(new LocalitySpec("vm-2", "vm"));
      spec.setAffinities(localitySpecList);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testCreateDisk() throws Exception {
      TaskEntity taskEntity = diskXenonBackend.prepareDiskCreate(projectId, spec);
      assertThat(taskEntity.getEntityId(), notNullValue());
      assertThat(taskEntity.getEntityKind(), is(PersistentDisk.KIND));
      assertThat(taskEntity.getSteps().size(), is(2));

      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskXenonBackend.find(PersistentDisk.KIND,
          taskEntity.getEntityId());
      Flavor flavor = flavorBackend.filter(Optional.of(spec.getFlavor()),
              Optional.of(PersistentDisk.KIND), Optional.absent()).getItems().get(0);

      assertThat(persistentDiskEntity.getName(), is(spec.getName()));
      assertThat(persistentDiskEntity.getCapacityGb(), is(spec.getCapacityGb()));
      assertThat(persistentDiskEntity.getProjectId(), is(projectId));
      assertThat(persistentDiskEntity.getState(), is(DiskState.CREATING));
      assertThat(persistentDiskEntity.getFlavorId(), is(flavor.getId()));
      assertThat(persistentDiskEntity.getCost().size(), is(2));
      for (TagEntity tag : persistentDiskEntity.getTags()) {
        assertThat(tag.getValue(), is("tag1"));
      }
      assertThat(persistentDiskEntity.getAffinities().size(), is(2));
      assertThat(persistentDiskEntity.getAffinities().get(0).getKind(), is(Vm.KIND));
      assertThat(persistentDiskEntity.getAffinities().get(0).getResourceId(), is("vm-1"));
      assertThat(persistentDiskEntity.getAffinities().get(1).getResourceId(), is("vm-2"));
    }

    @Test
    public void testCreateDiskWithAttachedDiskCreateSpec() throws Exception {
      AttachedDiskCreateSpec createSpec = new AttachedDiskCreateSpec();
      createSpec.setKind(EphemeralDisk.KIND);
      createSpec.setName("attached-disk");
      createSpec.setCapacityGb(2);
      createSpec.setFlavor("test-flavor-ephemeral-disk");

      BaseDiskEntity diskEntity = diskXenonBackend.create(projectId, createSpec);
      Flavor flavor = flavorBackend.filter(Optional.of(createSpec.getFlavor()),
              Optional.of(EphemeralDisk.KIND), Optional.absent()).getItems().get(0);

      assertThat(diskEntity.getName(), is(createSpec.getName()));
      assertThat(diskEntity.getCapacityGb(), is(createSpec.getCapacityGb()));
      assertThat(diskEntity.getProjectId(), is(projectId));
      assertThat(diskEntity.getState(), is(DiskState.CREATING));
      assertThat(diskEntity.getFlavorId(), is(flavor.getId()));
    }

    @Test
    public void testCreateDiskInvalidProjectId() throws Exception {
      try {
        diskXenonBackend.prepareDiskCreate("invalid-project", spec);
        fail("should have failed with TenantNotFoundException.");
      } catch (ProjectNotFoundException e) {
        assertThat(e.getMessage(), is("Project invalid-project not found"));
      }
    }
  }

  /**
   * Tests for updating disk.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class UpdateTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DiskBackend diskBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;
    private DiskCreateSpec spec;
    private String projectId;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      ProjectCreateSpec projectCreateSpec = new ProjectCreateSpec();
      projectCreateSpec.setName("p1");
      projectCreateSpec.setResourceTicket(reservation);
      TaskEntity taskEntity = projectBackend.createProject(tenantId, projectCreateSpec);
      projectId = taskEntity.getEntityId();

      FlavorCreateSpec flavorCreateSpec = new FlavorCreateSpec();
      flavorCreateSpec.setName("test-flavor");
      flavorCreateSpec.setKind(PersistentDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      spec = new DiskCreateSpec();
      spec.setName("disk");
      spec.setKind(PersistentDisk.KIND);
      spec.setCapacityGb(2);
      spec.setFlavor("test-flavor");

      List<LocalitySpec> localitySpecList = new ArrayList<>();
      localitySpecList.add(new LocalitySpec("vm-1", "vm"));
      localitySpecList.add(new LocalitySpec("vm-2", "vm"));
      spec.setAffinities(localitySpecList);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testUpdateDisk() throws Exception {
      TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);

      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND,
          taskEntity.getEntityId());
      assertThat(persistentDiskEntity.getName(), is(spec.getName()));

      diskBackend.updateState(persistentDiskEntity, DiskState.ERROR);
      assertThat(diskBackend.find(PersistentDisk.KIND, persistentDiskEntity.getId()).getState(), is(DiskState.ERROR));
    }

    @Test
    public void testQueryDiskInvalidProjectId() throws Exception {
      try {
        diskBackend.find(PersistentDisk.KIND, "invalid-disk");
        fail("should have failed with DiskNotFoundException.");
      } catch (DiskNotFoundException e) {
        assertThat(e.getMessage(), is("Disk #invalid-disk not found"));
      }
    }
  }

  /**
   * Tests for querying disk.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class QueryTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DiskBackend diskBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    private String tenantId;
    private DiskCreateSpec spec;
    private String projectId;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB), // present in tenant ticket
          new QuotaLineItem("foo.bar", 25, QuotaUnit.MB))); // not present in tenant ticket (which is OK)

      ProjectCreateSpec projectCreateSpec = new ProjectCreateSpec();
      projectCreateSpec.setName("p1");
      projectCreateSpec.setResourceTicket(reservation);
      TaskEntity taskEntity = projectBackend.createProject(tenantId, projectCreateSpec);
      projectId = taskEntity.getEntityId();

      FlavorCreateSpec flavorCreateSpec = new FlavorCreateSpec();
      flavorCreateSpec.setName("test-flavor");
      flavorCreateSpec.setKind(PersistentDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      spec = new DiskCreateSpec();
      spec.setName("disk-1");
      spec.setKind(PersistentDisk.KIND);
      spec.setCapacityGb(2);
      spec.setFlavor("test-flavor");
      spec.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testQueryDisk() throws Exception {
      TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);

      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND,
          taskEntity.getEntityId());
      assertThat(persistentDiskEntity.getName(), is(spec.getName()));
      assertThat(persistentDiskEntity.getAffinities().isEmpty(), is(true));

      PersistentDisk persistentDisk = diskBackend.toApiRepresentation(taskEntity.getEntityId());
      assertThat(persistentDisk.getName(), is(spec.getName()));
      assertThat(persistentDisk.getCapacityGb(), is(spec.getCapacityGb()));
      assertThat(persistentDisk.getState(), is(DiskState.CREATING));
      assertThat(persistentDisk.getFlavor(), is("test-flavor"));
      assertThat(persistentDisk.getTags().containsAll(spec.getTags()), is(true));
    }

    @Test
    public void testFilterDisk() throws Exception {
      diskBackend.prepareDiskCreate(projectId, spec);
      spec.setName("disk-2");
      diskBackend.prepareDiskCreate(projectId, spec);

      ResourceList<PersistentDisk> persistentDiskList = diskBackend.filter(projectId, Optional.<String>absent(),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(persistentDiskList.getItems().size(), is(2));

      persistentDiskList = diskBackend.filter(projectId, Optional.of("disk-1"),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(persistentDiskList.getItems().size(), is(1));
      assertThat(persistentDiskList.getItems().get(0).getName(), is("disk-1"));
    }

    @Test
    public void testDiskExistsUsingFlavor() throws Exception {
      TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);

      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND,
          taskEntity.getEntityId());
      boolean flavorInUse = diskBackend.existsUsingFlavor(persistentDiskEntity.getFlavorId());
      assertThat(flavorInUse, is(true));
    }

    @Test
    public void testQueryDiskInvalidProjectId() throws Exception {
      try {
        diskBackend.find(PersistentDisk.KIND, "invalid-disk");
        fail("should have failed with DiskNotFoundException.");
      } catch (DiskNotFoundException e) {
        assertThat(e.getMessage(), is("Disk #invalid-disk not found"));
      }
    }
  }

  /**
   * Tests for deleting disk.
   */
  @Guice(modules = {XenonBackendTestModule.class, TestModule.class})
  public static class DeleteTest {
    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private DiskBackend diskBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private String tenantId;
    private DiskCreateSpec spec;
    private String projectId;

    @BeforeMethod
    public void setUp() throws Throwable {

      commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);

      QuotaLineItem[] tenantTicketLimits = {
          new QuotaLineItem("vm", 100, QuotaUnit.COUNT),
          new QuotaLineItem("disk", 2000, QuotaUnit.GB)
      };

      TenantCreateSpec tenantCreateSpec = new TenantCreateSpec();
      tenantCreateSpec.setName("t1");
      tenantId = tenantBackend.createTenant(tenantCreateSpec).getEntityId();

      ResourceTicketCreateSpec resourceTicketCreateSpec = new ResourceTicketCreateSpec();
      resourceTicketCreateSpec.setName("rt1");
      resourceTicketCreateSpec.setLimits(Arrays.asList(tenantTicketLimits));
      resourceTicketBackend.create(tenantId, resourceTicketCreateSpec).getId();

      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName("rt1");
      reservation.setLimits(ImmutableList.of(
          new QuotaLineItem("vm", 10, QuotaUnit.COUNT), // present in tenant ticket
          new QuotaLineItem("disk", 250, QuotaUnit.GB),
          new QuotaLineItem("persistent-disk.capacity", 250, QuotaUnit.GB))); // present in tenant ticket

      ProjectCreateSpec projectCreateSpec = new ProjectCreateSpec();
      projectCreateSpec.setName("p1");
      projectCreateSpec.setResourceTicket(reservation);
      TaskEntity taskEntity = projectBackend.createProject(tenantId, projectCreateSpec);
      projectId = taskEntity.getEntityId();

      FlavorCreateSpec flavorCreateSpec = new FlavorCreateSpec();
      flavorCreateSpec.setName("test-flavor");
      flavorCreateSpec.setKind(PersistentDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem("disk", 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      spec = new DiskCreateSpec();
      spec.setName("disk");
      spec.setFlavor("test-flavor");
      spec.setKind(PersistentDisk.KIND);
      spec.setCapacityGb(2);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      commonHostDocumentsCleanup();
    }

    @AfterClass
    public static void afterClassCleanup() throws Throwable {
      commonHostAndClientTeardown();
    }

    @Test
    public void testTombstoneDisk() throws Exception {
      TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);

      String diskId = taskEntity.getEntityId();
      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId);
      assertThat(persistentDiskEntity.getName(), is(spec.getName()));

      diskBackend.tombstone(PersistentDisk.KIND, diskId);
      assertThat(tombstoneBackend.getByEntityId(taskEntity.getEntityId()).getEntityKind(), is(PersistentDisk.KIND));
      try {
        diskBackend.find(PersistentDisk.KIND, diskId);
        fail("should have failed with DiskNotFoundException");
      } catch (DiskNotFoundException e) {
        assertThat(e.getMessage(), is("Disk #" + diskId + " not found"));
      }
    }

    @Test
    public void testTombstoneInvalidDisk() throws Exception {
      try {
        diskBackend.tombstone(PersistentDisk.KIND, "invalid-disk");
        fail("should have failed with DiskNotFoundException.");
      } catch (DiskNotFoundException e) {
        assertThat(e.getMessage(), is("Disk #invalid-disk not found"));
      }
    }

    @Test
    public void testDeleteDisk() throws Exception {
      TaskEntity taskEntity = diskBackend.prepareDiskCreate(projectId, spec);

      TaskEntity deleteTaskEntity = diskBackend.prepareDiskDelete(taskEntity.getEntityId());
      assertThat(deleteTaskEntity.getEntityId(), notNullValue());
      assertThat(deleteTaskEntity.getEntityKind(), is(PersistentDisk.KIND));
      assertThat(deleteTaskEntity.getSteps().size(), is(1));
      assertThat(deleteTaskEntity.getSteps().get(0).getOperation(), is(Operation.DELETE_DISK));
    }

    @Test
    public void testDeleteInvalidDisk() throws Exception {
      try {
        diskBackend.prepareDiskDelete("invalid-disk");
        fail("should have failed with DiskNotFoundException.");
      } catch (DiskNotFoundException e) {
        assertThat(e.getMessage(), is("Disk #invalid-disk not found"));
      }
    }
  }
}
