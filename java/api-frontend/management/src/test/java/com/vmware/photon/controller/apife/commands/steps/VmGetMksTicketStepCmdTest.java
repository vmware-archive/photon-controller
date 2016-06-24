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
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperClient;
import com.vmware.photon.controller.apife.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.host.gen.MksTicketResponse;
import com.vmware.photon.controller.host.gen.MksTicketResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.MksTicket;

import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
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
  private ApiFeXenonRestClient xenonClient;

  @Mock
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;

  @Mock
  private TaskEntity task;

  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private com.vmware.photon.controller.apife.backends.clients.DeployerClient deployerXenonClient;

  @Mock
  private com.vmware.photon.controller.apife.backends.clients.HousekeeperClient housekeeperXenonClient;

  @Mock
  private EntityLockBackend entityLockBackend;

  @Mock
  private com.vmware.xenon.common.Operation hostServiceOp;

  private TaskCommand taskCommand;

  private VmEntity vm;

  private MksTicketResponse mksTicketResponse;

  private StepEntity step;
  private String stepId = "step-1";
  private String vmId = "vm-1";

  @BeforeMethod
  public void setUp() throws Exception, DocumentNotFoundException {
    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setId(vmId);

    MksTicket ticket = new MksTicket("/vmfs/config_file", "ticket-1234");


    mksTicketResponse = new MksTicketResponse(MksTicketResultCode.OK);
    mksTicketResponse.setTicket(ticket);

    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");

    taskCommand = spy(new TaskCommand(xenonClient, photonControllerXenonRestClient, hostClient,
        housekeeperClient, deployerClient, deployerXenonClient, housekeeperXenonClient,
        entityLockBackend,
        task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getPhotonControllerXenonRestClient()).thenReturn(photonControllerXenonRestClient);
    HostService.State hostServiceState = new HostService.State();
    hostServiceState.hostAddress = "host-ip";
    when(hostServiceOp.getBody(Matchers.any())).thenReturn(hostServiceState);
    when(xenonClient.get(Matchers.startsWith(HostServiceFactory.SELF_LINK))).thenReturn(hostServiceOp);

    when(taskCommand.getTask()).thenReturn(task);
  }

  @Test
  public void testSuccessfulGetMksTicket() throws Exception {
    when(hostClient.getVmMksTicket(vmId))
        .thenReturn(mksTicketResponse);

    vm.setHost("0.0.0.0");
    VmGetMksTicketStepCmd command = getCommand();
    command.execute();

    InOrder inOrder = inOrder(hostClient, taskBackend);
    inOrder.verify(hostClient).getVmMksTicket(vmId);
    inOrder.verify(taskBackend).setTaskResourceProperties(any(TaskEntity.class), any(String.class));

    verifyNoMoreInteractions(taskBackend);
  }

  @Test
  public void testVmNotFoundException() throws Exception {
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
    vm.setHost("0.0.0.0");
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
