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
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.TenantCreateSpec;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.testng.FileAssert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link AttachedDiskXenonBackend}.
 */
public class AttachedDiskXenonBackendTest {

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
  @Guice(modules = { XenonBackendTestModule.class, TestModule.class })
  public static class CreateTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private AttachedDiskBackend attachedDiskBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    private String tenantId;
    private AttachedDiskCreateSpec spec;
    private String projectId;

    private VmEntity vmEntity;
    private PersistentDiskEntity persistentDiskEntity;

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
      flavorCreateSpec.setKind(EphemeralDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      spec = new AttachedDiskCreateSpec();
      spec.setName("disk");
      spec.setKind(EphemeralDisk.KIND);
      spec.setCapacityGb(2);
      spec.setFlavor("test-flavor");

      vmEntity = new VmEntity();
      vmEntity.setId("vm-id");
      vmEntity.setProjectId(projectId);

      persistentDiskEntity = new PersistentDiskEntity();
      persistentDiskEntity.setId("disk-1");
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
    public void testCreateAttachedDisk() throws Exception {
      List<AttachedDiskEntity> attachedDisks = attachedDiskBackend.createAttachedDisks(vmEntity,
          ImmutableList.of(spec));

      assertThat(attachedDisks.size(), is(1));
      assertThat(attachedDisks.get(0).getVmId(), is(vmEntity.getId()));
      assertThat(attachedDisks.get(0).getKind(), is(EphemeralDisk.KIND));
      assertThat(attachedDisks.get(0).getDiskId(), is(attachedDisks.get(0).getEphemeralDiskId()));
      assertThat(attachedDisks.get(0).getEphemeralDiskId(), notNullValue());
      assertThat(attachedDisks.get(0).getPersistentDiskId(), nullValue());
      assertThat(attachedDisks.get(0).getUnderlyingTransientDisk().getId(), is(attachedDisks.get(0).getDiskId()));
      assertThat(attachedDisks.get(0).getUnderlyingTransientDisk().getState(), is(DiskState.CREATING));


      attachedDiskBackend.attachDisks(vmEntity, ImmutableList.of(persistentDiskEntity));
      assertThat(vmEntity.getAttachedDisks().size(), is(1));
      assertThat(vmEntity.getAttachedDisks().get(0).getPersistentDiskId(), notNullValue());
      assertThat(vmEntity.getAttachedDisks().get(0).getKind(), is(PersistentDisk.KIND));
      assertThat(vmEntity.getAttachedDisks().get(0).getVmId(), is(vmEntity.getId()));
      assertThat(vmEntity.getAttachedDisks().get(0).getDiskId(),
          is(vmEntity.getAttachedDisks().get(0).getPersistentDiskId()));

      AttachedDiskEntity attachedDiskEntity = attachedDiskBackend.findAttachedDisk(persistentDiskEntity);
      assertThat(attachedDiskEntity.getPersistentDiskId(), is(persistentDiskEntity.getId()));
      assertThat(attachedDiskEntity.getVmId(), is(vmEntity.getId()));
      assertThat(attachedDiskEntity.getDiskId(), is(persistentDiskEntity.getId()));
      assertThat(attachedDiskEntity.getUnderlyingDiskId(), is(persistentDiskEntity.getId()));
      assertThat(attachedDiskEntity.getKind(), is(PersistentDisk.KIND));
      assertThat(attachedDiskEntity.getPersistentDiskId(), is(persistentDiskEntity.getId()));

      List<AttachedDiskEntity> attachedDiskEntities = attachedDiskBackend.findByVmId(vmEntity.getId());
      assertThat(attachedDiskEntities.size(), is(2));
    }

    @Test
    public void testCreateInvalidAttachedDisk() throws Exception {
      spec.setKind(PersistentDisk.KIND);

      try {
        attachedDiskBackend.createAttachedDisks(vmEntity, ImmutableList.of(spec));
        fail("should have failed with IllegalStateException");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), is("Attached disk can only be ephemeral disk, but got persistent-disk"));
      }

    }
  }

  /**
   * Tests for deleting disk.
   */
  @Guice(modules = { XenonBackendTestModule.class, TestModule.class })
  public static class DeleteTest {

    @Inject
    private BasicServiceHost basicServiceHost;

    @Inject
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    private AttachedDiskBackend attachedDiskBackend;

    @Inject
    private DiskBackend diskBackend;

    @Inject
    private ProjectBackend projectBackend;

    @Inject
    private TenantBackend tenantBackend;

    @Inject
    private FlavorBackend flavorBackend;

    @Inject
    private ResourceTicketBackend resourceTicketBackend;

    private String tenantId;
    private DiskCreateSpec spec;
    private String projectId;

    private VmEntity vmEntity;
    private String diskId;
    private String diskId2;


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
      flavorCreateSpec.setKind(EphemeralDisk.KIND);
      flavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(), 2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(flavorCreateSpec);

      vmEntity = new VmEntity();
      vmEntity.setId("vm-id");
      vmEntity.setProjectId(projectId);

      FlavorCreateSpec diskFlavorCreateSpec = new FlavorCreateSpec();
      diskFlavorCreateSpec.setName("disk-flavor");
      diskFlavorCreateSpec.setKind(PersistentDisk.KIND);
      diskFlavorCreateSpec.setCost(ImmutableList.of(new QuotaLineItem(UUID.randomUUID().toString(),
          2.0, QuotaUnit.COUNT)));
      flavorBackend.createFlavor(diskFlavorCreateSpec);

      spec = new DiskCreateSpec();
      spec.setName("disk");
      spec.setKind(PersistentDisk.KIND);
      spec.setCapacityGb(2);
      spec.setFlavor("disk-flavor");

      diskId = diskBackend.prepareDiskCreate(projectId, spec).getEntityId();
      diskId2 = diskBackend.prepareDiskCreate(projectId, spec).getEntityId();
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
    public void testDeleteAttachedDisk() throws Exception {
      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId);
      attachedDiskBackend.attachDisks(vmEntity, ImmutableList.of(persistentDiskEntity));

      assertThat(vmEntity.getAttachedDisks().size(), is(1));
      assertThat(vmEntity.getAttachedDisks().get(0).getPersistentDiskId(), notNullValue());
      assertThat(vmEntity.getAttachedDisks().get(0).getKind(), is(PersistentDisk.KIND));
      assertThat(vmEntity.getAttachedDisks().get(0).getDiskId(),
          is(vmEntity.getAttachedDisks().get(0).getPersistentDiskId()));
      assertThat(attachedDiskBackend.findAttachedDisk(persistentDiskEntity), notNullValue());

      List<AttachedDiskEntity> attachedDiskEntities = attachedDiskBackend.findByVmId(vmEntity.getId());
      assertThat(attachedDiskEntities.size(), is(1));
      assertThat(attachedDiskEntities.get(0).getDiskId(), is(persistentDiskEntity.getId()));

      attachedDiskBackend.deleteAttachedDisk(PersistentDisk.KIND, diskId);
      assertThat(attachedDiskBackend.findAttachedDisk(persistentDiskEntity), nullValue());
      attachedDiskEntities = attachedDiskBackend.findByVmId(vmEntity.getId());
      assertThat(attachedDiskEntities.size(), is(0));
    }

    @Test
    public void testDeleteAttachedDisks() throws Exception {
      PersistentDiskEntity diskEntity1 = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId);
      PersistentDiskEntity diskEntity2 = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId2);
      attachedDiskBackend.attachDisks(vmEntity, ImmutableList.of(diskEntity1, diskEntity2));

      assertThat(vmEntity.getAttachedDisks().size(), is(2));
      assertThat(attachedDiskBackend.findAttachedDisk(diskEntity1), notNullValue());
      assertThat(attachedDiskBackend.findAttachedDisk(diskEntity2), notNullValue());

      attachedDiskBackend.deleteAttachedDisks(vmEntity, ImmutableList.of(diskEntity1, diskEntity2));
      assertThat(attachedDiskBackend.findAttachedDisk(diskEntity1), nullValue());
      assertThat(attachedDiskBackend.findAttachedDisk(diskEntity2), nullValue());
    }

    @Test
    public void testDeleteAttachedDiskById() throws Exception {
      PersistentDiskEntity persistentDiskEntity = (PersistentDiskEntity) diskBackend.find(PersistentDisk.KIND, diskId);
      attachedDiskBackend.attachDisks(vmEntity, ImmutableList.of(persistentDiskEntity));

      List<AttachedDiskEntity> attachedDiskEntities = attachedDiskBackend.findByVmId(vmEntity.getId());
      assertThat(attachedDiskEntities.size(), is(1));
      assertThat(attachedDiskEntities.get(0).getDiskId(), is(persistentDiskEntity.getId()));

      attachedDiskBackend.deleteAttachedDiskById(attachedDiskEntities.get(0).getId());
      assertThat(attachedDiskBackend.findAttachedDisk(persistentDiskEntity), nullValue());
    }
  }
}
