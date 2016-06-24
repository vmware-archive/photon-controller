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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperClient;
import com.vmware.photon.controller.apife.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.resource.gen.Datastore;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link VmDeleteStepCmd}.
 */
public class VmDeleteStepCmdTest extends PowerMockTestCase {

  @Mock
  private ApiFeXenonRestClient xenonClient;

  @Mock
  private HostClient hostClient;

  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private DiskBackend diskBackend;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private EntityLockBackend entityLockBackend;

  @Mock
  private com.vmware.xenon.common.Operation hostServiceOp;

  @Mock
  private com.vmware.photon.controller.apife.backends.clients.DeployerClient deployerXenonClient;

  @Mock
  private com.vmware.photon.controller.apife.backends.clients.HousekeeperClient housekeeperXenonClient;

  private TaskCommand taskCommand;
  private String stepId = "step-1";
  private TaskEntity task;
  private VmEntity vm;
  private StepEntity step;
  private EphemeralDiskEntity eDisk1;
  private EphemeralDiskEntity eDisk2;

  @BeforeMethod
  public void setUp() throws Exception, DocumentNotFoundException {
    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setId("vm-1");

    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");

    taskCommand = spy(new TaskCommand(xenonClient, photonControllerXenonRestClient, hostClient,
        housekeeperClient, deployerClient, deployerXenonClient, housekeeperXenonClient,
        entityLockBackend, task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getPhotonControllerXenonRestClient()).thenReturn(photonControllerXenonRestClient);
    HostService.State hostServiceState = new HostService.State();
    hostServiceState.hostAddress = "host-ip";
    when(hostServiceOp.getBody(Matchers.any())).thenReturn(hostServiceState);
    when(xenonClient.get(Matchers.startsWith(HostServiceFactory.SELF_LINK))).thenReturn(hostServiceOp);
  }

  @Test
  public void testSuccessfulDeleteNoDisks() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.STOPPED);
    vm.setHost("0.0.0.0");

    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend);
    inOrder.verify(hostClient).setHostIp("0.0.0.0");
    inOrder.verify(hostClient).deleteVm("vm-1", null);
    inOrder.verify(vmBackend).updateState(vm, VmState.DELETED);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testSuccessfulDeleteWithDisks() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.STOPPED);
    vm.setAttachedDisks(getAttachedDisks());
    vm.setHost("0.0.0.0");
    for (EphemeralDiskEntity disk : getEphemeralDisks()) {
      step.addTransientResourceEntity(disk);
    }
    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
    inOrder.verify(hostClient).setHostIp("0.0.0.0");
    inOrder.verify(hostClient).deleteVm("vm-1", null);
    inOrder.verify(vmBackend).updateState(vm, VmState.DELETED);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);
    inOrder.verify(diskBackend).tombstone(eDisk1.getKind(), eDisk1.getId());
    inOrder.verify(diskBackend).tombstone(eDisk2.getKind(), eDisk2.getId());

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
  }

  @Test
  public void testSuccessfulDeleteWithIso() throws Exception {
    IsoEntity iso = new IsoEntity();
    iso.setName("iso-name");
    iso.setVm(vm);
    when(vmBackend.isosAttached(vm)).thenReturn(ImmutableList.of(iso));

    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.STOPPED);
    vm.setHost("0.0.0.0");
    vm.setAttachedDisks(getAttachedDisks());
    for (EphemeralDiskEntity disk : getEphemeralDisks()) {
      step.addTransientResourceEntity(disk);
    }
    vm.addIso(iso);
    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
    inOrder.verify(hostClient).setHostIp("0.0.0.0");
    inOrder.verify(hostClient).deleteVm("vm-1", null);
    inOrder.verify(vmBackend).updateState(vm, VmState.DELETED);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).detachIso(vm);
    inOrder.verify(vmBackend).tombstone(vm);
    inOrder.verify(diskBackend).tombstone(eDisk1.getKind(), eDisk1.getId());
    inOrder.verify(diskBackend).tombstone(eDisk2.getKind(), eDisk2.getId());

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
  }

  @Test
  public void testSuccessfulDeleteAgentIdPresent() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.STOPPED);
    vm.setAgent("vm-1-agent-id");

    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).deleteVm("vm-1", null);
    inOrder.verify(vmBackend).updateState(vm, VmState.DELETED);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testMissingVmsInCreatingStateAreCleanedUp() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.CREATING);
    vm.setAgent("agent-id");

    when(hostClient.deleteVm(vm.getId(), null)).thenThrow(new VmNotFoundException("Error"));

    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).deleteVm(vm.getId(), null);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testDeleteMissingVm() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();

    vm.setState(VmState.STOPPED);
    vm.setAgent("agent-id");

    when(hostClient.deleteVm("vm-1", null)).thenThrow(new VmNotFoundException("Error"));

    cmd.execute();
  }

  @Test
  public void testDeletingVmInDeletedState() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setState(VmState.DELETED);
    cmd.execute();

    verify(vmBackend).isosAttached(vm);
    verify(vmBackend).tombstone(vm);
    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testDeletingVmInErrorState() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();

    vm.setState(VmState.ERROR);
    vm.setAgent("agent-id");
    vm.setAttachedDisks(getAttachedDisks());
    for (EphemeralDiskEntity disk : getEphemeralDisks()) {
      step.addTransientResourceEntity(disk);
    }

    when(hostClient.deleteVm(vm.getId(), null)).thenThrow(new VmNotFoundException("Error"));

    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).deleteVm(vm.getId(), null);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);
    inOrder.verify(diskBackend).tombstone(eDisk1.getKind(), eDisk1.getId());
    inOrder.verify(diskBackend).tombstone(eDisk2.getKind(), eDisk2.getId());

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testForceDeleteVmNotFound() throws Exception {
    VmDeleteStepCmd cmd = getVmDeleteStepCmd();
    vm.setAttachedDisks(getAttachedDisks());
    for (EphemeralDiskEntity disk : getEphemeralDisks()) {
      step.addTransientResourceEntity(disk);
    }

    vm.setState(VmState.STOPPED);
    vm.setAgent("agent-id");

    when(hostClient.deleteVm(vm.getId(), null)).thenThrow(new VmNotFoundException("Error"));

    cmd.execute();

    InOrder inOrder = inOrder(photonControllerXenonRestClient, hostClient, vmBackend, diskBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).deleteVm(vm.getId(), null);
    inOrder.verify(vmBackend).isosAttached(vm);
    inOrder.verify(vmBackend).tombstone(vm);
    inOrder.verify(diskBackend).tombstone(eDisk1.getKind(), eDisk1.getId());
    inOrder.verify(diskBackend).tombstone(eDisk2.getKind(), eDisk2.getId());

    verifyNoMoreInteractions(photonControllerXenonRestClient, hostClient, vmBackend);
  }

  @Test
  public void testPersistentDisksDetach() throws Throwable {
    VmDeleteStepCmd command = getVmDeleteStepCmd();

    PersistentDiskEntity disk = new PersistentDiskEntity();
    disk.setName("disk-1");
    disk.setId("disk-1");

    step.addResource(disk);

    try {
      command.execute();
      fail("should have failed due to persistent disk");
    } catch (InternalException e) {
    }
  }

  private List<AttachedDiskEntity> getAttachedDisks() {

    List<AttachedDiskEntity> attachedDiskEntityList = new ArrayList<>();
    for (EphemeralDiskEntity ephemeralDiskEntity : getEphemeralDisks()) {
      AttachedDiskEntity disk = new AttachedDiskEntity();
      disk.setUnderlyingDiskIdAndKind(ephemeralDiskEntity);
    }
    return attachedDiskEntityList;
  }

  private List<EphemeralDiskEntity> getEphemeralDisks() {
    eDisk1 = new EphemeralDiskEntity();
    eDisk1.setState(DiskState.ATTACHED);
    eDisk1.setId("e-disk-1");
    eDisk1.setDatastore("ds-1");

    eDisk2 = new EphemeralDiskEntity();
    eDisk2.setState(DiskState.ATTACHED);
    eDisk2.setId("e-disk-2");
    eDisk2.setDatastore("ds-2");

    return ImmutableList.of(eDisk1, eDisk2);
  }

  private VmDeleteStepCmd getVmDeleteStepCmd() {
    step = new StepEntity();
    step.setId(stepId);
    step.setOperation(Operation.DELETE_VM);
    step.setTask(task);
    step.setSequence(0);
    step.setState(StepEntity.State.QUEUED);
    step.addResource(vm);
    VmDeleteStepCmd cmd = new VmDeleteStepCmd(taskCommand,
        stepBackend, step, vmBackend, diskBackend);
    return spy(cmd);
  }

}
