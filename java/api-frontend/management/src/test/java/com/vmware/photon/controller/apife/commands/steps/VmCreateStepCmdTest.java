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
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.InvalidReservationException;
import com.vmware.photon.controller.host.gen.CreateVmResponse;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Vm;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.AssertJUnit.fail;

import java.util.HashMap;

/**
 * Tests {@link VmCreateStepCmd}.
 */
public class VmCreateStepCmdTest extends PowerMockTestCase {

  @Mock
  private HostClient hostClient;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private DiskBackend diskBackend;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private TaskCommand taskCommand;

  private VmEntity vm;
  private StepEntity step;
  private String stepId = "step-1";
  private String agentId = "agent-1";
  private String reservationId = "r-100";
  private String agentIp = "1.1.1.1";
  private CreateVmResponse createVmResponse = new CreateVmResponse();

  @BeforeMethod
  public void setUp() throws InternalException, InterruptedException {
    vm = new VmEntity();
    vm.setId("vm-1");
    when(taskCommand.getReservation()).thenReturn(reservationId);
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.lookupAgentId(agentIp)).thenReturn(agentId);
    when(hostClient.getHostIp()).thenReturn(agentIp);

    createVmResponse.setVm(createThriftVm("vm-1", "vm-100", "datastore-1", "datastore-name"));
  }

  @Test
  public void testSuccessfulVmCreate() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();
    command.createVm();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).createVm(reservationId, new HashMap<>());
    inOrder.verify(vmBackend).updateState(vm, VmState.STOPPED, agentId, agentIp, "datastore-1", "datastore-name");

    verifyNoMoreInteractions(vmBackend);
  }

  @Test
  public void testSuccessfulVmCreateWithPortGroup() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

    LocalityEntity locality = new LocalityEntity();
    locality.setKind(VmCreateStepCmd.PORT_GROUP_KIND);
    locality.setResourceId("VM VLAN");
    vm.setAffinities(ImmutableList.of(locality));

    when(hostClient.createVm(reservationId, new HashMap<>())).thenReturn(createVmResponse);

    command.createVm();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).createVm(reservationId, new HashMap<String, String>());
    inOrder.verify(vmBackend).updateState(vm, VmState.STOPPED, agentId, agentIp, "datastore-1", "datastore-name");

    verifyNoMoreInteractions(vmBackend);
  }

  @Test
  public void testFailedVmCreate() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

    when(hostClient.createVm(reservationId, new HashMap<String, String>()))
        .thenThrow(new InvalidReservationException(null));

    try {
      command.createVm();
      fail("should have failed due to invalid reservation exception");
    } catch (InvalidReservationException e) {
    }

    verify(vmBackend).updateState(vm, VmState.ERROR);
  }

  @Test
  public void testSuccessfulDisksAttach() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

    EphemeralDiskEntity disk = new EphemeralDiskEntity();
    disk.setName("disk-1");
    disk.setId("disk-1");

    step.addResource(disk);
    command.execute();

    InOrder inOrder = inOrder(diskBackend);
    inOrder.verify(diskBackend).updateState(disk, DiskState.ATTACHED, null, null);

    verifyNoMoreInteractions(diskBackend);
  }

  @Test
  public void testPersistentDisksAttach() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

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

  private VmCreateStepCmd getVmCreateStepCmd() throws Throwable {
    when(hostClient.createVm(anyString(), anyMap())).thenReturn(createVmResponse);

    step = new StepEntity();
    step.setId(stepId);
    step.addResource(vm);
    VmCreateStepCmd cmd = new VmCreateStepCmd(taskCommand,
        stepBackend, step, vmBackend, diskBackend);
    return spy(cmd);
  }

  private Vm createThriftVm(String id, String flavor, String datastoreId, String datastoreName) {
    Vm vm = new Vm();
    vm.setId(id);
    Datastore datestore = new Datastore(datastoreId);
    datestore.setName(datastoreName);
    vm.setDatastore(datestore);
    return vm;
  }

}
