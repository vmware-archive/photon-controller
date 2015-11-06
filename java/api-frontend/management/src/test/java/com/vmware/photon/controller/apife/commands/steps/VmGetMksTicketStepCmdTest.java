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

import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.RootSchedulerClient;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.MksTicketResponse;
import com.vmware.photon.controller.host.gen.MksTicketResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.MksTicket;
import com.vmware.photon.controller.scheduler.gen.FindResponse;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.testng.AssertJUnit.fail;

/**
 * Tests {@link VmGetNetworksStepCmd}.
 */
public class VmGetMksTicketStepCmdTest extends PowerMockTestCase {
  @Mock
  private StepBackend stepBackend;

  @Mock
  private TaskBackend taskBackend;

  @Mock
  private HostClient hostClient;

  @Mock
  private RootSchedulerClient rootSchedulerClient;

  @Mock
  private TaskEntity task;

  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private EntityLockBackend entityLockBackend;

  private TaskCommand taskCommand;

  private VmEntity vm;

  private MksTicketResponse mksTicketResponse;

  private StepEntity step;
  private FindResponse findResponse;
  private String stepId = "step-1";
  private String vmId = "vm-1";

  @BeforeMethod
  public void setUp() throws Exception {
    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setId(vmId);

    MksTicket ticket = new MksTicket("/vmfs/config_file", "ticket-1234");


    mksTicketResponse = new MksTicketResponse(MksTicketResultCode.OK);
    mksTicketResponse.setTicket(ticket);

    findResponse = new FindResponse();
    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");
    findResponse.setDatastore(datastore);
    ServerAddress serverAddress = new ServerAddress();
    serverAddress.setHost("0.0.0.0");
    serverAddress.setPort(0);
    findResponse.setAddress(serverAddress);

    taskCommand = spy(new TaskCommand(
        rootSchedulerClient, hostClient, housekeeperClient, deployerClient, entityLockBackend, task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getRootSchedulerClient()).thenReturn(rootSchedulerClient);
    when(rootSchedulerClient.findVm(vmId)).thenReturn(findResponse);

    when(taskCommand.getTask()).thenReturn(task);
  }

  @Test
  public void testSuccessfulGetMksTicket() throws Exception {
    when(hostClient.getVmMksTicket(vmId))
        .thenReturn(mksTicketResponse);

    VmGetMksTicketStepCmd command = getCommand();
    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend);
    inOrder.verify(hostClient).getVmMksTicket(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(any(TaskEntity.class), any(String.class));

    verifyNoMoreInteractions(taskBackend);
  }

  @Test
  public void testStaleAgent() throws Exception {
    vm.setAgent("staled-agent");
    VmGetMksTicketStepCmd command = getCommand();

    when(rootSchedulerClient.findVm("vm-1")).thenReturn(findResponse);
    when(hostClient.getVmMksTicket(anyString())).thenThrow(
        new VmNotFoundException("Error")).thenReturn(mksTicketResponse);

    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend, rootSchedulerClient);
    inOrder.verify(hostClient).setAgentId("staled-agent");
    inOrder.verify(hostClient).getVmMksTicket(vmId);
    inOrder.verify(rootSchedulerClient).findVm(vmId);
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    inOrder.verify(hostClient).getVmMksTicket(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(any(TaskEntity.class), any(String.class));
    verifyNoMoreInteractions(hostClient, taskBackend, rootSchedulerClient);
  }

  @Test
  public void testVmNotFoundException() throws Exception {
    when(rootSchedulerClient.findVm(vmId)).thenThrow(new VmNotFoundException("Error"));

    VmGetMksTicketStepCmd command = getCommand();
    try {
      command.execute();
      fail("should have failed due to VmNotFoundException exception");
    } catch (com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException ex) {
    }
  }

  @Test
  public void testFailedGetMksTicket() throws Throwable {
    when(hostClient.getVmMksTicket(vmId)).thenThrow(new SystemErrorException("e"));

    VmGetMksTicketStepCmd command = getCommand();
    try {
      command.execute();
      fail("should have failed due to SystemErrorException exception");
    } catch (SystemErrorException e) {
    }
  }

  private VmGetMksTicketStepCmd getCommand() {
    step = new StepEntity();
    step.setId(stepId);
    step.addResource(vm);

    return spy(new VmGetMksTicketStepCmd(taskCommand, stepBackend, step, taskBackend));
  }
}
