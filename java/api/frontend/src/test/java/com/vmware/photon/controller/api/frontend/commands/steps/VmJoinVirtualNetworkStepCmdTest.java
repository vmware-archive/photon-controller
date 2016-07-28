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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.tasks.ConnectVmToSwitchTaskService;
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

import java.util.ArrayList;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.commands.steps.VmJoinVirtualNetworkStepCmd}.
 */
public class VmJoinVirtualNetworkStepCmdTest {

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
  public void testVmLocationIdMissing() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();

    try {
      command.execute();
      fail("Should have failed due to missing VM location ID");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("VM location id is not available"));
    }
  }

  @Test
  public void testLogicalSwitchMissing() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");

    try {
      command.execute();
      fail("Should have failed due to missing logical switches");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("Logical switch to connect VM to is not available"));
    }
  }

  @Test
  public void testVmIdMissing() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");

    try {
      command.execute();
      fail("Should have failed due to missing vm id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("VM id is not available"));
    }
  }

  @Test
  public void testNetworkIdMissing() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");

    try {
      command.execute();
      fail("Should have failed due to missing network id");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("Network id is not available"));
    }
  }

  @Test
  public void testDeploymentServiceMissing() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(new ArrayList<>()).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    try {
      command.execute();
      fail("Should have failed due to missing deployment service");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), is("Found 0 deployment service(s)."));
    }
  }

  @Test
  public void testSuccessfulJoin() throws Throwable{
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    ConnectVmToSwitchTask task = new ConnectVmToSwitchTask();
    task.taskState = new TaskState();
    task.taskState.stage = TaskState.TaskStage.FINISHED;
    task.documentSelfLink = documentSelfLink;
    doReturn(task).when(operation).getBody(ConnectVmToSwitchTask.class);

    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    command.execute();

    verify(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));
    verify(photonControllerXenonRestClient).get(eq(documentSelfLink));
    verify(operation, times(2)).getBody(ConnectVmToSwitchTask.class);
  }

  @Test
  public void testFailedToJoinWithException() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    String errorMsg = "Failed with error code 500";
    doThrow(new XenonRuntimeException(errorMsg))
        .when(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    try {
      command.execute();
      fail("should have failed to connect vm to switch");
    } catch (XenonRuntimeException e) {
      assertThat(e.getMessage(), is(errorMsg));
    }
  }

  @Test
  public void testJoinTaskFailed() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    ConnectVmToSwitchTask task1 = new ConnectVmToSwitchTask();
    task1.taskState = new TaskState();
    task1.taskState.stage = TaskState.TaskStage.STARTED;
    task1.documentSelfLink = documentSelfLink;

    ConnectVmToSwitchTask task2 = new ConnectVmToSwitchTask();
    task2.taskState = new TaskState();
    task2.taskState.stage = TaskState.TaskStage.STARTED;

    ConnectVmToSwitchTask task3 = new ConnectVmToSwitchTask();
    task3.taskState = new TaskState();
    task3.taskState.stage = TaskState.TaskStage.FAILED;

    doReturn(task1).doReturn(task2).doReturn(task3).when(operation).getBody(ConnectVmToSwitchTask.class);
    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    try {
      command.execute();
    } catch (RuntimeException e) {
      verify(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
          any(ConnectVmToSwitchTask.class));
      verify(photonControllerXenonRestClient, times(2)).get(eq(documentSelfLink));
      verify(operation, times(3)).getBody(ConnectVmToSwitchTask.class);

      assertThat(e.getMessage(),
          is("Connecting VM at vm-location-id to logical switch logical-switch1 failed with a state of FAILED"));
    }
  }

  @Test
  public void testJoinTaskTimeout() throws Throwable {
    VmJoinVirtualNetworkStepCmd command = getVmJoinVirtualNetworkStepCmd();
    step.createOrUpdateTransientResource(VmCreateStepCmd.VM_LOCATION_ID, "vm-location-id");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.LOGICAL_SWITCH_ID, "logical-switch1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VM_ID, "vm1");
    step.createOrUpdateTransientResource(ResourceReserveStepCmd.VIRTUAL_NETWORK_ID, "network1");

    doReturn(ImmutableList.of(new DeploymentService.State())).when(apiFeXenonRestClient)
        .queryDocuments(eq(DeploymentService.State.class), any(ImmutableMap.class));

    Operation operation = mock(Operation.class);
    doReturn(operation).when(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
        any(ConnectVmToSwitchTask.class));

    String documentSelfLink = UUID.randomUUID().toString();
    ConnectVmToSwitchTask task = new ConnectVmToSwitchTask();
    task.taskState = new TaskState();
    task.taskState.stage = TaskState.TaskStage.STARTED;
    task.documentSelfLink = documentSelfLink;
    doReturn(task).when(operation).getBody(ConnectVmToSwitchTask.class);

    doReturn(operation).when(photonControllerXenonRestClient).get(documentSelfLink);

    try {
      command.execute();
    } catch (RuntimeException e) {
      verify(photonControllerXenonRestClient).post(eq(ConnectVmToSwitchTaskService.FACTORY_LINK),
          any(ConnectVmToSwitchTask.class));
      verify(photonControllerXenonRestClient, times(5)).get(eq(documentSelfLink));
      verify(operation, times(6)).getBody(ConnectVmToSwitchTask.class);

      assertThat(e.getMessage(), is("Timeout when waiting for ConnectVmToSwitchTask"));
    }
  }

  private VmJoinVirtualNetworkStepCmd getVmJoinVirtualNetworkStepCmd() {
    step = new StepEntity();
    step.setId(UUID.randomUUID().toString());

    StepBackend stepBackend = mock(StepBackend.class);

    VmJoinVirtualNetworkStepCmd cmd = new VmJoinVirtualNetworkStepCmd(taskCommand, stepBackend, step);

    return spy(cmd);
  }
}
