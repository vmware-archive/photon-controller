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

import com.vmware.photon.controller.api.frontend.backends.DiskBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.LocalityEntity;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.StepNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.frontend.utils.NetworkHelper;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.VmState;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.InvalidReservationException;
import com.vmware.photon.controller.host.gen.CreateVmResponse;
import com.vmware.photon.controller.host.gen.VmNetworkInfo;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Vm;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
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
  private NetworkHelper networkHelper;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private TaskCommand taskCommand;

  @Mock
  private TaskEntity task;

  private VmEntity vm;
  private StepEntity step;
  private String stepId = "step-1";
  private String agentId = "agent-1";
  private String reservationId = "r-100";
  private String agentIp = "1.1.1.1";
  private String vmLocationId = "vm-location";
  private CreateVmResponse createVmResponse = new CreateVmResponse();

  @BeforeMethod
  public void setUp() throws InternalException, InterruptedException, StepNotFoundException, ExternalException {
    vm = new VmEntity();
    vm.setId("vm-1");
    createVmResponse.setVm(createThriftVm("vm-1", "vm-100", "datastore-1", "datastore-name"));

    when(taskCommand.getReservation()).thenReturn(reservationId);
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.lookupAgentId(agentIp)).thenReturn(agentId);
    when(hostClient.getHostIp()).thenReturn(agentIp);
    when(networkHelper.convertAgentNetworkToVmNetwork(any(VmNetworkInfo.class))).thenReturn(null);

    doReturn(task).when(taskCommand).getTask();
  }

  @Test
  public void testSuccessfulVmCreate() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();
    command.createVm();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).createVm(reservationId, new HashMap<>());
    inOrder.verify(vmBackend).updateState(vm, VmState.STOPPED, agentId, agentIp, "datastore-1", "datastore-name", null);

    verifyNoMoreInteractions(vmBackend);
  }

  @Test
  public void testSuccessfulVmCreateWithPhysicalNetwork() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

    LocalityEntity locality = new LocalityEntity();
    locality.setKind(VmCreateStepCmd.PORT_GROUP_KIND);
    locality.setResourceId("VM VLAN");
    vm.setAffinities(ImmutableList.of(locality));

    when(hostClient.createVm(reservationId, new HashMap<>())).thenReturn(createVmResponse);
    when(networkHelper.isSdnEnabled()).thenReturn(false);

    command.createVm();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).createVm(reservationId, new HashMap<>());
    inOrder.verify(vmBackend).updateState(vm, VmState.STOPPED, agentId, agentIp, "datastore-1", "datastore-name", null);

    verifyNoMoreInteractions(vmBackend);
  }

  @Test
  public void testSuccessfulVmCreateWithVirtualNetwork() throws Throwable {
    vm.setNetworks(ImmutableList.of("network-1"));
    when(hostClient.createVm(reservationId, new HashMap<>())).thenReturn(createVmResponse);
    when(networkHelper.isSdnEnabled()).thenReturn(true);

    VmCreateStepCmd command = getVmCreateStepCmd();
    command.createVm();

    InOrder inOrder = inOrder(hostClient, vmBackend);
    inOrder.verify(hostClient).createVm(reservationId, new HashMap<>());
    inOrder.verify(vmBackend).updateState(vm, VmState.STOPPED, agentId, agentIp, "datastore-1", "datastore-name", null);

    verifyNoMoreInteractions(vmBackend);
  }

  @Test
  public void testFailedVmCreate() throws Throwable {
    VmCreateStepCmd command = getVmCreateStepCmd();

    when(hostClient.createVm(reservationId, new HashMap<>()))
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
        stepBackend, step, vmBackend, diskBackend, networkHelper);
    return spy(cmd);
  }

  private Vm createThriftVm(String id, String flavor, String datastoreId, String datastoreName) {
    Vm vm = new Vm();
    vm.setId(id);
    vm.setFlavor(flavor);
    Datastore datestore = new Datastore(datastoreId);
    datestore.setName(datastoreName);
    vm.setDatastore(datestore);
    vm.setLocation_id(vmLocationId);
    return vm;
  }

}
