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

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.DiskType;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.TestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.junit.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link DiskBackend}.
 */
@Guice(modules = {XenonBackendTestModule.class, TestModule.class})
public class VmDiskBackendTest {

  private static ApiFeXenonRestClient xenonClient;
  private static BasicServiceHost host;

  private static String projectId;

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

  private static void commonDataSetup(
      TenantXenonBackend tenantXenonBackend,
      ResourceTicketXenonBackend resourceTicketXenonBackend,
      ProjectXenonBackend projectXenonBackend,
      FlavorXenonBackend flavorXenonBackend,
      FlavorLoader flavorLoader) throws Throwable {
    String tenantId = XenonBackendTestHelper.createTenant(tenantXenonBackend, "vmware");

    QuotaLineItem ticketLimit = new QuotaLineItem("vm.cost", 100, QuotaUnit.COUNT);
    XenonBackendTestHelper.createTenantResourceTicket(resourceTicketXenonBackend,
        tenantId, "rt1", ImmutableList.of(ticketLimit));

    QuotaLineItem projectLimit = new QuotaLineItem("vm.cost", 10, QuotaUnit.COUNT);
    projectId = XenonBackendTestHelper.createProject(projectXenonBackend,
        "staging", tenantId, "rt1", ImmutableList.of(projectLimit));

    XenonBackendTestHelper.createFlavors(flavorXenonBackend, flavorLoader.getAllFlavors());
  }

  @Inject
  private VmBackend vmBackend;

  @Inject
  private EntityLockBackend entityLockBackend;

  private String vmId;

  @Inject
  private BasicServiceHost basicServiceHost;

  @Inject
  private ApiFeXenonRestClient apiFeXenonRestClient;

  @Inject
  private TenantXenonBackend tenantXenonBackend;

  @Inject
  private ResourceTicketXenonBackend resourceTicketXenonBackend;

  @Inject
  private ProjectXenonBackend projectXenonBackend;

  @Inject
  private FlavorXenonBackend flavorXenonBackend;

  @Inject
  private FlavorLoader flavorLoader;

  @BeforeMethod()
  public void setUp() throws Throwable {
    commonHostAndClientSetup(basicServiceHost, apiFeXenonRestClient);
    commonDataSetup(
        tenantXenonBackend,
        resourceTicketXenonBackend,
        projectXenonBackend,
        flavorXenonBackend,
        flavorLoader);

    VmService.State vm = new VmService.State();
    vm.name = "test-vm";
    FlavorEntity flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-100", Vm.KIND);
    vm.flavorId = flavorEntity.getId();
    vm.imageId = UUID.randomUUID().toString();
    vm.projectId = projectId;
    vm.vmState = VmState.CREATING;
    com.vmware.xenon.common.Operation result = xenonClient.post(VmServiceFactory.SELF_LINK, vm);
    VmService.State createdVm = result.getBody(VmService.State.class);
    vmId = ServiceUtils.getIDFromDocumentSelfLink(createdVm.documentSelfLink);
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
  public void testDiskAttachSuccess() throws Exception {
    List<String> diskIdList = new ArrayList<>();
    diskIdList.add(createPersistentDisk("test-disk-name-1", DiskState.DETACHED));
    diskIdList.add(createPersistentDisk("test-disk-name-2", DiskState.DETACHED));

    TaskEntity taskEntity = vmBackend.prepareVmDiskOperation(
        vmId, diskIdList, Operation.ATTACH_DISK);
    // Look up steps
    List<StepEntity> steps = taskEntity.getSteps();
    assertThat(steps.size(), is(1));
    StepEntity stepEntity = steps.get(0);
    assertThat(stepEntity.getOperation(), is(Operation.ATTACH_DISK));
    assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
    List<BaseEntity> resourceEntities = stepEntity.getTransientResourceEntities();
    assertThat(resourceEntities.size(), is(3));
  }

  @Test(expectedExceptions = DiskNotFoundException.class)
  public void testDiskAttachDiskNotFound() throws Exception {
    List<String> diskIdList = new ArrayList<>();
    diskIdList.add(createPersistentDisk("test-disk-name-1", DiskState.DETACHED));
    diskIdList.add("non-existent-disk-id");
    vmBackend.prepareVmDiskOperation(vmId, diskIdList, Operation.ATTACH_DISK);
  }

  @Test(expectedExceptions = InvalidOperationStateException.class)
  public void testDiskAttachAlreadyAttached() throws Exception {
    List<String> diskIdList = new ArrayList<>();
    diskIdList.add(createPersistentDisk("test-disk-name-1", DiskState.DETACHED));
    diskIdList.add(createPersistentDisk("test-disk-name-2", DiskState.ATTACHED));
    vmBackend.prepareVmDiskOperation(vmId, diskIdList, Operation.ATTACH_DISK);
  }

  @Test(dataProvider = "DiskAttachSuccessForAllVmStates")
  public void testDiskAttachSuccessForAllVmStates(VmState vmState) throws Exception {
    List<String> diskIdList = new ArrayList<>();
    diskIdList.add(createPersistentDisk("test-disk-name-1", DiskState.DETACHED));
    diskIdList.add(createPersistentDisk("test-disk-name-2", DiskState.DETACHED));
    VmEntity vmEntity = vmBackend.findById(vmId);
    if (vmEntity.getState() != vmState) {
      vmBackend.updateState(vmEntity, vmState, "agent-007", "1.1.1.1", vmEntity.getDatastore(),
          vmEntity.getDatastoreName());
    }
    TaskEntity taskEntity = vmBackend.prepareVmDiskOperation(vmId, diskIdList, Operation.ATTACH_DISK);
    List<StepEntity> steps = taskEntity.getSteps();
    assertThat(steps.size(), is(1));
    StepEntity stepEntity = steps.get(0);
    assertThat(stepEntity.getOperation(), is(Operation.ATTACH_DISK));
    assertThat(stepEntity.getState(), is(StepEntity.State.QUEUED));
  }

  @DataProvider(name = "DiskAttachSuccessForAllVmStates")
  public Object[][] getDiskAttachSuccessForAllVmStatesData() {
    return new Object[][]{
        //{VmState.CREATING},
        {VmState.DELETED},
        {VmState.STARTED},
        {VmState.STOPPED},
        //{VmState.SUSPENDED},
        //{VmState.ERROR}
    };
  }

  private String createPersistentDisk(String name, DiskState state)
      throws ExternalException {

    FlavorEntity flavorEntity = flavorXenonBackend.getEntityByNameAndKind("core-200", PersistentDisk.KIND);
    DiskService.State diskState = new DiskService.State();
    diskState.flavorId = flavorEntity.getId();
    diskState.diskType = DiskType.PERSISTENT;
    diskState.state = state;
    diskState.name = name;
    diskState.projectId = projectId;
    diskState.capacityGb = 2;

    com.vmware.xenon.common.Operation result = xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);
    diskState = result.getBody(DiskService.State.class);
    return ServiceUtils.getIDFromDocumentSelfLink(diskState.documentSelfLink);
  }
}
