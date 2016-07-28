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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.AttachedDiskBackend;
import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.backends.EntityLockBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.DeployerClient;
import com.vmware.photon.controller.api.frontend.backends.clients.HousekeeperClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.AttachedDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.FlavorEntity;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.model.AttachedDisk;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.host.gen.VmDiskOpError;
import com.vmware.photon.controller.host.gen.VmDiskOpResultCode;
import com.vmware.photon.controller.host.gen.VmDisksOpResponse;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Disk;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link VmPowerOpStepCmd}.
 */
public class VmDiskOpStepCmdTest extends PowerMockTestCase {
  private static final VmDisksOpResponse SUCCESSFUL_VM_DISKOP_RESPONSE;
  private static final VmDisksOpResponse DISK_NOT_FOUND_VM_DISKOP_RESPONSE;
  private static final VmDisksOpResponse DISK_ATTACHED_VM_DISKOP_RESPONSE;
  private static final VmDisksOpResponse DISK_DETACHED_VM_DISKOP_RESPONSE;

  private static final String flavor = "core-100";
  private static final String dataStoreId = "data-store-id";

  private static final String stepId = "step-1";
  private static final String vmId = "vm-id-1";
  private static final String diskId1 = "disk-id-1";
  private static final String diskId2 = "disk-id-2";
  private static final String diskName1 = "disk-name-1";
  private static final String diskName2 = "disk-name-2";

  private final String attachedDiskId1 = "attached-disk-id-1";
  private final String attachedDiskId2 = "attached-disk-id-2";

  static {
    SUCCESSFUL_VM_DISKOP_RESPONSE = new VmDisksOpResponse(VmDiskOpResultCode.OK);
    Disk disk1 = new Disk(diskId1, flavor, true, true, 1);
    disk1.setDatastore(new Datastore(dataStoreId));
    SUCCESSFUL_VM_DISKOP_RESPONSE.addToDisks(disk1);
    SUCCESSFUL_VM_DISKOP_RESPONSE.putToDisk_errors(diskId1, new VmDiskOpError(VmDiskOpResultCode.OK));
    Disk disk2 = new Disk(diskId2, flavor, true, true, 2);
    disk2.setDatastore(new Datastore(dataStoreId));
    SUCCESSFUL_VM_DISKOP_RESPONSE.addToDisks(disk2);
    SUCCESSFUL_VM_DISKOP_RESPONSE.putToDisk_errors(diskId2, new VmDiskOpError(VmDiskOpResultCode.OK));
  }

  static {
    DISK_NOT_FOUND_VM_DISKOP_RESPONSE = new VmDisksOpResponse(VmDiskOpResultCode.OK);
    Disk disk1 = new Disk(diskId1, flavor, true, true, 1);
    disk1.setDatastore(new Datastore(dataStoreId));
    DISK_NOT_FOUND_VM_DISKOP_RESPONSE.addToDisks(disk1);
    DISK_NOT_FOUND_VM_DISKOP_RESPONSE.putToDisk_errors(diskId1, new VmDiskOpError(VmDiskOpResultCode.DISK_NOT_FOUND));
    Disk disk2 = new Disk(diskId2, flavor, true, true, 2);
    disk2.setDatastore(new Datastore(dataStoreId));
    DISK_NOT_FOUND_VM_DISKOP_RESPONSE.addToDisks(disk2);
    DISK_NOT_FOUND_VM_DISKOP_RESPONSE.putToDisk_errors(diskId2, new VmDiskOpError(VmDiskOpResultCode.OK));
  }

  static {
    DISK_ATTACHED_VM_DISKOP_RESPONSE = new VmDisksOpResponse(VmDiskOpResultCode.OK);
    Disk disk1 = new Disk(diskId1, flavor, true, true, 1);
    disk1.setDatastore(new Datastore(dataStoreId));
    DISK_ATTACHED_VM_DISKOP_RESPONSE.addToDisks(disk1);
    DISK_ATTACHED_VM_DISKOP_RESPONSE.putToDisk_errors(diskId1, new VmDiskOpError(VmDiskOpResultCode.OK));
    Disk disk2 = new Disk(diskId2, flavor, true, true, 2);
    disk2.setDatastore(new Datastore(dataStoreId));
    DISK_ATTACHED_VM_DISKOP_RESPONSE.addToDisks(disk2);
    DISK_ATTACHED_VM_DISKOP_RESPONSE.putToDisk_errors(diskId2, new VmDiskOpError(VmDiskOpResultCode.DISK_ATTACHED));
  }

  static {
    DISK_DETACHED_VM_DISKOP_RESPONSE = new VmDisksOpResponse(VmDiskOpResultCode.OK);
    Disk disk1 = new Disk(diskId1, flavor, true, true, 1);
    disk1.setDatastore(new Datastore(dataStoreId));
    DISK_DETACHED_VM_DISKOP_RESPONSE.addToDisks(disk1);
    DISK_DETACHED_VM_DISKOP_RESPONSE.putToDisk_errors(diskId1, new VmDiskOpError(VmDiskOpResultCode.OK));
    Disk disk2 = new Disk(diskId2, flavor, true, true, 2);
    disk2.setDatastore(new Datastore(dataStoreId));
    DISK_DETACHED_VM_DISKOP_RESPONSE.addToDisks(disk2);
    DISK_DETACHED_VM_DISKOP_RESPONSE.putToDisk_errors(diskId2, new VmDiskOpError(VmDiskOpResultCode.DISK_DETACHED));
  }

  @Mock
  DiskBackend diskBackend;
  @Mock
  AttachedDiskBackend attachedDiskBackend;
  @Mock
  ApiFeXenonRestClient xenonClient;
  @Mock
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;
  @Mock
  private HostClient hostClient;
  @Mock
  private VmBackend vmBackend;
  @Mock
  private StepBackend stepBackend;
  @Mock
  private VmDisksOpResponse response;
  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private EntityLockBackend entityLockBackend;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private com.vmware.photon.controller.api.frontend.backends.clients.DeployerClient deployerXenonClient;

  @Mock
  private com.vmware.photon.controller.api.frontend.backends.clients.HousekeeperClient housekeeperXenonClient;

  @Mock
  private com.vmware.xenon.common.Operation hostServiceOp;

  private TaskCommand taskCommand;
  private StepEntity step;
  private TaskEntity task;
  private VmEntity vm;

  private PersistentDiskEntity disk1;
  private PersistentDiskEntity disk2;

  private AttachedDiskEntity attachedDiskEntity1;
  private AttachedDiskEntity attachedDiskEntity2;

  private AttachedDisk attachedDisk1;
  private AttachedDisk attachedDisk2;

  private List<String> attachedDiskIds;
  private List<AttachedDiskEntity> attachedDiskEntities;
  private List<PersistentDiskEntity> persistentDiskEntities;
  private List<PersistentDiskEntity> baseDiskEntities;

  @BeforeMethod
  public void setUp() throws Exception, DocumentNotFoundException {
    attachedDiskIds = new ArrayList<>();
    attachedDiskEntities = new ArrayList<>();
    persistentDiskEntities = new ArrayList<>();
    baseDiskEntities = new ArrayList<>();

    FlavorEntity vmFlavorEntity = new FlavorEntity();
    vmFlavorEntity.setName("vm-100");
    vmFlavorEntity.setKind(Vm.KIND);
    FlavorEntity diskFlavorEntity = new FlavorEntity();
    diskFlavorEntity.setName("core-100");
    diskFlavorEntity.setKind(PersistentDisk.KIND);

    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setName("vm-name-1");
    vm.setId(vmId);
    vm.setFlavorId(vmFlavorEntity.getId());
    vm.setState(VmState.STOPPED);

    disk1 = new PersistentDiskEntity();
    disk1.setName(diskName1);
    disk1.setId(diskId1);
    disk1.setFlavorId(diskFlavorEntity.getId());
    disk1.setCapacityGb(1);
    persistentDiskEntities.add(disk1);
    baseDiskEntities.add(disk1);

    disk2 = new PersistentDiskEntity();
    disk2.setName(diskName2);
    disk2.setId(diskId2);
    disk2.setFlavorId(diskFlavorEntity.getId());
    disk2.setCapacityGb(2);
    persistentDiskEntities.add(disk2);
    baseDiskEntities.add(disk2);

    // String id, String name, String kind, String flavor, String state
    attachedDisk1 = AttachedDisk.create(disk1.getId(), disk1.getName(), disk1.getKind(), "core-100", 1, false);
    attachedDisk2 = AttachedDisk.create(disk2.getId(), disk2.getName(), disk2.getKind(), "core-100", 2, false);
    attachedDiskIds.add(attachedDisk1.getId());
    attachedDiskIds.add(attachedDisk2.getId());

    attachedDiskEntity1 = new AttachedDiskEntity();
    attachedDiskEntity1.setId(attachedDiskId1);
    attachedDiskEntities.add(attachedDiskEntity1);

    attachedDiskEntity2 = new AttachedDiskEntity();
    attachedDiskEntity2.setId(attachedDiskId2);
    attachedDiskEntities.add(attachedDiskEntity2);

    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");

    when(diskBackend.find(PersistentDisk.KIND, diskId1)).thenReturn(disk1);
    when(diskBackend.find(PersistentDisk.KIND, diskId2)).thenReturn(disk2);

    taskCommand = spy(new TaskCommand(xenonClient, photonControllerXenonRestClient, hostClient,
        housekeeperClient, deployerClient, deployerXenonClient, housekeeperXenonClient,
        entityLockBackend, task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getPhotonControllerXenonRestClient()).thenReturn(photonControllerXenonRestClient);
    when(vmBackend.findById(vmId)).thenReturn(vm);
    when(diskBackend.find(PersistentDisk.KIND, diskId1)).thenReturn(disk1);
    when(diskBackend.find(PersistentDisk.KIND, diskId2)).thenReturn(disk2);

    when(attachedDiskBackend.findAttachedDisk(disk1)).thenReturn(attachedDiskEntity1);
    when(attachedDiskBackend.findAttachedDisk(disk2)).thenReturn(attachedDiskEntity2);
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    HostService.State hostServiceState = new HostService.State();
    hostServiceState.hostAddress = "host-ip";
    when(hostServiceOp.getBody(Matchers.any())).thenReturn(hostServiceState);
    when(xenonClient.get(Matchers.startsWith(HostServiceFactory.SELF_LINK))).thenReturn(hostServiceOp);
  }

  @Test
  public void testSuccessfulAttachOperation() throws Exception {
    when(hostClient.attachDisks(vmId, attachedDiskIds)).thenReturn(SUCCESSFUL_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.ATTACH_DISK);
    vm.setAgent("some-agent");
    command.execute();

    verify(hostClient).attachDisks(vmId, attachedDiskIds);
    verify(diskBackend).updateState(disk1, DiskState.ATTACHED);
    verify(diskBackend).updateState(disk2, DiskState.ATTACHED);
    verify(attachedDiskBackend).attachDisks(vm, persistentDiskEntities);
  }

  @Test(expectedExceptions = com.vmware.photon.controller.api.frontend.exceptions.external.VmNotFoundException.class)
  public void testFailedAttachDiskVmNotFound() throws Exception {
    when(hostClient.attachDisks(vmId, attachedDiskIds)).thenThrow(VmNotFoundException.class);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.ATTACH_DISK);
    vm.setAgent("some-agent");
    vm.setHost("0.0.0.0");
    command.execute();
  }

  @Test(expectedExceptions = RpcException.class)
  public void testFailedAttachOperationDiskNotFound() throws Exception {
    when(hostClient.attachDisks(vmId, attachedDiskIds)).thenReturn(DISK_NOT_FOUND_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.ATTACH_DISK);
    vm.setAgent("some-agent");
    command.execute();
  }

  @Test(expectedExceptions = RpcException.class)
  public void testFailedAttachOperationDiskAttached() throws Exception {
    when(hostClient.attachDisks(vmId, attachedDiskIds)).thenReturn(DISK_ATTACHED_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.ATTACH_DISK);
    vm.setAgent("some-agent");
    command.execute();
  }

  @Test
  public void testSuccessfulDetachOperation() throws Exception {
    when(hostClient.detachDisks(vmId, attachedDiskIds)).thenReturn(SUCCESSFUL_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.DETACH_DISK);
    vm.setAgent("some-agent");
    command.execute();
  }

  @Test
  public void testSuccessfulDetachDiskNotFoundOperation() throws Exception {
    when(hostClient.detachDisks(vmId, attachedDiskIds)).thenReturn(DISK_NOT_FOUND_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.DETACH_DISK);
    vm.setAgent("some-agent");
    command.execute();

    verify(hostClient).detachDisks(vmId, attachedDiskIds);
    verify(diskBackend).updateState(disk1, DiskState.ERROR);
    verify(diskBackend).updateState(disk2, DiskState.DETACHED);
    verify(attachedDiskBackend).deleteAttachedDisks(vm, persistentDiskEntities);
  }

  @Test(expectedExceptions = RpcException.class)
  public void testFailedDetachOperationDiskDetached() throws Exception {
    when(hostClient.detachDisks(vmId, attachedDiskIds)).thenReturn(DISK_DETACHED_VM_DISKOP_RESPONSE);

    VmDiskOpStepCmd command = getVmDiskOpStepCmd(Operation.DETACH_DISK);
    vm.setAgent("some-agent");
    command.execute();
  }

  private VmDiskOpStepCmd getVmDiskOpStepCmd(Operation operation) {
    step = new StepEntity();
    step.setId(stepId);
    step.setTask(task);
    step.setSequence(0);
    step.setState(StepEntity.State.QUEUED);
    step.addResource(vm);
    step.addResource(disk1);
    step.addResource(disk2);
    step.setOperation(operation);
    VmDiskOpStepCmd cmd = new VmDiskOpStepCmd(taskCommand,
        stepBackend, step, diskBackend, attachedDiskBackend);
    return spy(cmd);
  }

}
