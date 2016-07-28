/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.tasks.DisconnectVmFromSwitchTaskService;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.util.UUID;

/**
 * Tests {@link VmUnjoinVirtualNetworkStepCmd}.
 */
public class VmUnjoinVirtualNetworkStepCmdTest {

  private TaskCommand taskCommand;
  private PhotonControllerXenonRestClient photonControllerXenonRestClient;
  private ApiFeXenonRestClient apiFeXenonRestClient;
  private StepEntity step;

  @BeforeMethod
  public void setup() {
    taskCommand = mock(TaskCommand.class);
    photonControllerXenonRestClient = mock(PhotonControllerXenonRestClient.class);
    apiFeXenonRestClient = mock(ApiFeXenonRestClient.class);

    doReturn(photonControllerXenonRestClient).when(taskCommand).getPhotonControllerXenonRestClient();
    doReturn(apiFeXenonRestClient).when(taskCommand).getApiFeXenonRestClient();
  }

  @Test
  public void testNetworkIdMissing() throws Throwable {
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();

    try {
      command.execute();
      fail("Should have failed due to missing network id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("Virtual network id is not available"));
    }
  }

  @Test
  public void testVmIdMissing() throws Throwable {
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "virtual-network-id");

    try {
      command.execute();
      fail("Should have failed due to missing vm id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("VM id is not available"));
    }
  }

  @Test
  public void testSuccessfulUnJoin() throws Throwable{
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
        any(DisconnectVmFromSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    DisconnectVmFromSwitchTask task = new DisconnectVmFromSwitchTask();
    task.taskState = new TaskState();
    task.taskState.stage = TaskState.TaskStage.FINISHED;
    task.documentSelfLink = documentSelfLink;
    doReturn(task).when(operation).getBody(DisconnectVmFromSwitchTask.class);

    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    command.execute();

    verify(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
        any(DisconnectVmFromSwitchTask.class));
    verify(photonControllerXenonRestClient).get(eq(documentSelfLink));
    verify(operation, times(2)).getBody(DisconnectVmFromSwitchTask.class);
  }

  @Test
  public void testFailedToUnjoinWithException() throws Throwable {
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    String errorMsg = "Failed with error code 500";
    doThrow(new XenonRuntimeException(errorMsg))
        .when(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
        any(DisconnectVmFromSwitchTask.class));

    try {
      command.execute();
      fail("should have failed to disconnect vm from switch");
    } catch (XenonRuntimeException e) {
      assertThat(e.getMessage(), is(errorMsg));
    }
  }

  @Test
  public void testJoinTaskFailed() throws Throwable {
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
        any(DisconnectVmFromSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    DisconnectVmFromSwitchTask task1 = new DisconnectVmFromSwitchTask();
    task1.taskState = new TaskState();
    task1.taskState.stage = TaskState.TaskStage.STARTED;
    task1.documentSelfLink = documentSelfLink;

    DisconnectVmFromSwitchTask task2 = new DisconnectVmFromSwitchTask();
    task2.taskState = new TaskState();
    task2.taskState.stage = TaskState.TaskStage.STARTED;

    DisconnectVmFromSwitchTask task3 = new DisconnectVmFromSwitchTask();
    task3.taskState = new TaskState();
    task3.taskState.stage = TaskState.TaskStage.FAILED;

    doReturn(task1).doReturn(task2).doReturn(task3).when(operation).getBody(DisconnectVmFromSwitchTask.class);
    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    try {
      command.execute();
    } catch (ExternalException e) {
      verify(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
          any(DisconnectVmFromSwitchTask.class));
      verify(photonControllerXenonRestClient, times(2)).get(eq(documentSelfLink));
      verify(operation, times(3)).getBody(DisconnectVmFromSwitchTask.class);

      assertThat(e.getMessage(),
          is("Disconnecting VM vm1 from virtual network network1 failed with a state of FAILED"));
    }
  }

  @Test
  public void testJoinTaskTimeout() throws Throwable {
    VmUnjoinVirtualNetworkStepCmd command = getVmUnjoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
        any(DisconnectVmFromSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    DisconnectVmFromSwitchTask task = new DisconnectVmFromSwitchTask();
    task.taskState = new TaskState();
    task.taskState.stage = TaskState.TaskStage.STARTED;
    task.documentSelfLink = documentSelfLink;
    doReturn(task).when(operation).getBody(DisconnectVmFromSwitchTask.class);

    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    try {
      command.execute();
    } catch (RuntimeException e) {
      verify(photonControllerXenonRestClient).post(eq(DisconnectVmFromSwitchTaskService.FACTORY_LINK),
          any(DisconnectVmFromSwitchTask.class));
      verify(photonControllerXenonRestClient, times(5)).get(eq(documentSelfLink));
      verify(operation, times(6)).getBody(DisconnectVmFromSwitchTask.class);

      assertThat(e.getMessage(), is("Timeout when waiting for DisconnectVmFromSwitchTask"));
    }
  }

  private VmUnjoinVirtualNetworkStepCmd getVmUnjoinVirtualNetworkStepCmd() {
    step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmUnjoinVirtualNetworkStepCmd cmd = new VmUnjoinVirtualNetworkStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }
}
