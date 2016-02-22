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

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.apife.backends.EntityLockBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.RootSchedulerClient;
import com.vmware.photon.controller.common.clients.exceptions.InvalidVmPowerStateException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.PowerVmOp;
import com.vmware.photon.controller.host.gen.PowerVmOpResponse;
import com.vmware.photon.controller.host.gen.PowerVmOpResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.scheduler.gen.FindResponse;

import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.testng.AssertJUnit.fail;

/**
 * Tests {@link VmPowerOpStepCmd}.
 */
public class VmPowerOpStepCmdTest extends PowerMockTestCase {

  @Mock
  private ApiFeDcpRestClient dcpClient;

  @Mock
  private RootSchedulerClient rootSchedulerClient;

  @Mock
  private HostClient hostClient;

  @Mock
  private VmBackend vmBackend;

  @Mock
  private StepBackend stepBackend;

  @Mock
  private HousekeeperClient housekeeperClient;

  @Mock
  private DeployerClient deployerClient;

  @Mock
  private EntityLockBackend entityLockBackend;

  @Mock
  private com.vmware.xenon.common.Operation hostServiceOp;

  private TaskCommand taskCommand;

  private String stepId = "step-1";
  private TaskEntity task;
  private VmEntity vm;
  private StepEntity step;
  private FindResponse findResponse;

  @BeforeMethod
  public void setUp() throws Exception, DocumentNotFoundException {
    task = new TaskEntity();
    task.setId("task-1");

    vm = new VmEntity();
    vm.setId("vm-1");

    findResponse = new FindResponse();
    Datastore datastore = new Datastore();
    datastore.setId("datastore-id");
    findResponse.setDatastore(datastore);
    ServerAddress serverAddress = new ServerAddress();
    serverAddress.setHost("0.0.0.0");
    serverAddress.setPort(0);
    findResponse.setAddress(serverAddress);

    taskCommand = spy(new TaskCommand(dcpClient,
        rootSchedulerClient, hostClient, housekeeperClient, deployerClient, entityLockBackend, task));
    when(taskCommand.getHostClient()).thenReturn(hostClient);
    when(taskCommand.getRootSchedulerClient()).thenReturn(rootSchedulerClient);
    HostService.State hostServiceState = new HostService.State();
    hostServiceState.hostAddress = "host-ip";
    when(hostServiceOp.getBody(Matchers.<Class>any())).thenReturn(hostServiceState);
    when(dcpClient.get(Matchers.startsWith(HostServiceFactory.SELF_LINK))).thenReturn(hostServiceOp);
  }

  @Test
  public void testStaleAgent() throws Exception {
    VmPowerOpStepCmd command = getVmPowerOpStepCmd();
    Operation operation = Operation.START_VM;
    vm.setAgent("some-agent");
    step.setOperation(operation);

    when(rootSchedulerClient.findVm("vm-1")).thenReturn(findResponse);
    when(hostClient.powerVmOp(anyString(), any(PowerVmOp.class))).thenThrow(
        new VmNotFoundException("Error")).thenReturn(new PowerVmOpResponse(PowerVmOpResultCode.OK));

    command.execute();

    InOrder inOrder = inOrder(hostClient, rootSchedulerClient, vmBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).powerVmOp("vm-1", PowerVmOp.ON);
    inOrder.verify(rootSchedulerClient).findVm("vm-1");
    inOrder.verify(hostClient).setIpAndPort("0.0.0.0", 0);
    inOrder.verify(hostClient).powerVmOp("vm-1", PowerVmOp.ON);
    inOrder.verify(vmBackend).updateState(vm, VmState.STARTED);

    verifyNoMoreInteractions(hostClient, rootSchedulerClient, vmBackend);
  }

  @Test
  public void testVmNotFoundException() throws Exception {
    VmPowerOpStepCmd command = getVmPowerOpStepCmd();
    Operation operation = Operation.START_VM;
    step.setOperation(operation);
    when(rootSchedulerClient.findVm("vm-1")).thenThrow(new VmNotFoundException("Error"));

    try {
      command.execute();
      fail("should have failed due to VmNotFoundException exception");
    } catch (com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException ex) {
    }
  }

  @Test(expectedExceptions = InvalidVmPowerStateException.class)
  public void testFailedResponse() throws Throwable {
    VmPowerOpStepCmd command = getVmPowerOpStepCmd();
    Operation operation = Operation.START_VM;
    step.setOperation(operation);
    vm.setAgent("some-agent");

    when(hostClient.powerVmOp(anyString(), any(PowerVmOp.class))).thenThrow(
        new InvalidVmPowerStateException("Error"));

    command.execute();
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testInvalidOperation() throws Throwable {
    VmPowerOpStepCmd command = getVmPowerOpStepCmd();
    Operation operation = Operation.DETACH_DISK;
    step.setOperation(operation);
    vm.setAgent("some-agent");

    command.execute();
  }

  @Test(dataProvider = "operations")
  public void testOperation(Operation operation,
                            PowerVmOp expectedPowerOp,
                            VmState previousState,
                            VmState expectedState) throws Exception {
    vm.setState(previousState);

    VmPowerOpStepCmd command = getVmPowerOpStepCmd();

    vm.setAgent("some-agent");
    step.setOperation(operation);

    when(hostClient.findVm("vm-1")).thenReturn(true);
    when(hostClient.powerVmOp(anyString(), any(PowerVmOp.class))).thenReturn(
        new PowerVmOpResponse(PowerVmOpResultCode.OK));

    command.execute();

    InOrder inOrder = inOrder(hostClient, rootSchedulerClient, vmBackend);
    inOrder.verify(hostClient).setHostIp("host-ip");
    inOrder.verify(hostClient).powerVmOp("vm-1", expectedPowerOp);
    inOrder.verify(vmBackend).updateState(vm, expectedState);
    verifyNoMoreInteractions(hostClient, rootSchedulerClient, vmBackend);
  }

  @DataProvider(name = "operations")
  public Object[][] getOperations() {
    return new Object[][]{
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.CREATING, VmState.STOPPED},
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.DELETED, VmState.STOPPED},
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.ERROR, VmState.STOPPED},
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.STARTED, VmState.STOPPED},
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.STOPPED, VmState.STOPPED},
        {Operation.STOP_VM, PowerVmOp.OFF, VmState.SUSPENDED, VmState.STOPPED},
        {Operation.START_VM, PowerVmOp.ON, VmState.CREATING, VmState.STARTED},
        {Operation.START_VM, PowerVmOp.ON, VmState.DELETED, VmState.STARTED},
        {Operation.START_VM, PowerVmOp.ON, VmState.ERROR, VmState.STARTED},
        {Operation.START_VM, PowerVmOp.ON, VmState.STARTED, VmState.STARTED},
        {Operation.START_VM, PowerVmOp.ON, VmState.STOPPED, VmState.STARTED},
        {Operation.START_VM, PowerVmOp.ON, VmState.SUSPENDED, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.CREATING, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.DELETED, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.ERROR, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.STARTED, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.STOPPED, VmState.STARTED},
        {Operation.RESTART_VM, PowerVmOp.RESET, VmState.SUSPENDED, VmState.STARTED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.CREATING, VmState.SUSPENDED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.DELETED, VmState.SUSPENDED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.ERROR, VmState.SUSPENDED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.STARTED, VmState.SUSPENDED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.STOPPED, VmState.SUSPENDED},
        {Operation.SUSPEND_VM, PowerVmOp.SUSPEND, VmState.SUSPENDED, VmState.SUSPENDED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.CREATING, VmState.STARTED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.DELETED, VmState.STARTED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.ERROR, VmState.STARTED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.STARTED, VmState.STARTED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.STOPPED, VmState.STARTED},
        {Operation.RESUME_VM, PowerVmOp.RESUME, VmState.SUSPENDED, VmState.STARTED},
    };
  }

  private VmPowerOpStepCmd getVmPowerOpStepCmd() {
    step = new StepEntity();
    step.setId(stepId);
    step.setTask(task);
    step.setSequence(0);
    step.setState(StepEntity.State.QUEUED);
    step.addResource(vm);
    VmPowerOpStepCmd cmd = new VmPowerOpStepCmd(taskCommand,
        stepBackend, step, vmBackend);
    return spy(cmd);
  }

}
