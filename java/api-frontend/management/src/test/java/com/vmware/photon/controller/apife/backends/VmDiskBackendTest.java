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
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidOperationStateException;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;

import com.google.inject.Inject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link DiskBackend}.
 */
@Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
public class VmDiskBackendTest extends BaseDaoTest {

  @Inject
  private EntityFactory entityFactory;

  @Inject
  private VmBackend vmBackend;

  @Inject
  private DiskBackend diskBackend;

  @Inject
  private EntityLockBackend entityLockBackend;

  private String projectId;
  private String vmId;

  @BeforeMethod()
  public void setUp() throws Throwable {
    super.setUp();
    entityFactory.loadFlavors();

    QuotaLineItemEntity tenantLimit = new QuotaLineItemEntity("vm", 100, QuotaUnit.COUNT);
    QuotaLineItemEntity projectLimit = new QuotaLineItemEntity("vm", 10, QuotaUnit.COUNT);

    String tenantId = entityFactory.createTenant("vmware").getId();
    String ticketId = entityFactory.createTenantResourceTicket(tenantId, "rt1", tenantLimit);
    projectId = entityFactory.createProject(tenantId, ticketId, "staging", projectLimit);

    final VmCreateSpec spec = new VmCreateSpec();
    spec.setName("test-vm");
    spec.setFlavor("core-100");
    VmEntity vm = entityFactory.createVm(projectId, "core-100", "test-vm");
    vmId = vm.getId();

  }

  @Test
  public void testDiskAttachSuccess() throws Exception {
    List<String> diskIdList = new ArrayList<>();
    diskIdList.add(createPersistentDisk("test-disk-name-1", DiskState.DETACHED));
    flushSession();
    diskIdList.add(createPersistentDisk("test-disk-name-2", DiskState.DETACHED));
    flushSession();
    VmEntity vmEntity = vmBackend.findById(vmId);
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

    // Compare the lists
    List<String> resourceEntityList = new ArrayList<>();
    for (BaseEntity baseEntity : resourceEntities) {
      resourceEntityList.add(baseEntity.getId());
    }
    resourceEntityList.removeAll(diskIdList);
    // Only the VM is left
    assertThat(resourceEntityList.size(), is(1));

    // Test the stepLocks are in place
    try {
      // On the main VM
      entityLockBackend.setStepLock(vmEntity, stepEntity);
      fail("no stepLock for VM entity during disk attach");
    } catch (Throwable e) {
      assertThat(e instanceof ConcurrentTaskException, is(true));
    }

    try {
      // On first DISK
      entityLockBackend.setStepLock(resourceEntities.get(0), stepEntity);
      fail("no stepLock for DISK entity during disk attach");
    } catch (Throwable e) {
      assertThat(e instanceof ConcurrentTaskException, is(true));
    }

    try {
      // On second DISK
      entityLockBackend.setStepLock(resourceEntities.get(1), stepEntity);
      fail("no stepLock for DISK entity during disk attach");
    } catch (Throwable e) {
      assertThat(e instanceof ConcurrentTaskException, is(true));
    }
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
    PersistentDiskEntity disk = entityFactory.createPersistentDisk(projectId, "core-200", name, 2);
    diskBackend.updateState(disk, state);
    return disk.getId();
  }

}
