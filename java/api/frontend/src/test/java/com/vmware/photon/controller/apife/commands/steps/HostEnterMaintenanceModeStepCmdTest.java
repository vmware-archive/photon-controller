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

import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.backends.HostXenonBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostStateChangeException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.assertj.core.api.Fail;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

/**
 * Test {@link HostEnterMaintenanceModeStepCmd}.
 */
public class HostEnterMaintenanceModeStepCmdTest {

  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private HostXenonBackend hostBackend;
  private VmBackend vmBackend;
  private DeployerClient deployerClient;

  private StepEntity step;
  private HostEntity hostEntity;
  private HostEnterMaintenanceModeStepCmd command;
  private XenonTaskStatusStepCmd commandStatus;

  private ChangeHostModeTaskService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity nextStep;

  private StepEntity stepEntityMock;
  private TaskBackend taskBackend;

  @BeforeMethod
  public void setUp() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    hostBackend = mock(HostXenonBackend.class);
    vmBackend = mock(VmBackend.class);
    stepEntityMock = mock(StepEntity.class);
    taskBackend = mock(TaskBackend.class);
    deployerClient = mock(DeployerClient.class);

    hostEntity = new HostEntity();
    hostEntity.setId("host-1");
    hostEntity.setUsername("username");
    hostEntity.setPassword("password");
    hostEntity.setAddress("192.168.0.1");
    hostEntity.setUsageTags(UsageTag.MGMT.toString());

    step = new StepEntity();
    step.setId("step-1");
    step.addResource(hostEntity);

    command = new HostEnterMaintenanceModeStepCmd(taskCommand, stepBackend, step, hostBackend, vmBackend);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(step, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new ChangeHostModeTaskService.State();
    serviceDocument.taskState = new TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + ChangeHostModeTaskFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;

    when(stepEntityMock.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY)).thenReturn
        (remoteTaskLink);

    stepEntityMock.addResource(hostEntity);
    commandStatus =  new XenonTaskStatusStepCmd(taskCommand, stepBackend, stepEntityMock,
        new HostChangeModeTaskStatusPoller(taskCommand, hostBackend, taskBackend));
  }


  @Test
  public void testSuccess() throws Throwable {
    when(deployerClient.enterMaintenanceMode(hostEntity.getId())).thenReturn(serviceDocument);
    command.run();
    verify(deployerClient, times(1)).enterMaintenanceMode(hostEntity.getId());
    assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
        remoteTaskLink);
    InOrder inOrder = inOrder(deployerClient, hostBackend);
    inOrder.verify(deployerClient).enterMaintenanceMode(hostEntity.getId());
  }

  @Test(expectedExceptions = HostStateChangeException.class)
  public void testFailure() throws Throwable {
    when(deployerClient.enterMaintenanceMode(anyString())).thenReturn(serviceDocument);
    when(deployerClient.getHostChangeModeStatus(any(String.class))).thenThrow(HostStateChangeException.class);
    command.execute();
    deployerClient.getHostChangeModeStatus(serviceDocument.documentSelfLink);
    fail("Should have failed");
  }

  @Test
  public void testHostStatusChangeReady() throws Throwable{
    ChangeHostModeTaskService.State readyState = new ChangeHostModeTaskService.State();
    readyState.taskState = new TaskState();
    readyState.hostMode = HostMode.MAINTENANCE;
    readyState.taskState.stage = TaskState.TaskStage.FINISHED;
    when(deployerClient.getHostChangeModeStatus(anyString())).thenReturn(readyState);

    commandStatus.run();
    verify(hostBackend).updateState(hostEntity, HostState.MAINTENANCE);
  }

  @Test(expectedExceptions = HostStateChangeException.class)
  public void testHostStatusChangeFailed() throws Throwable{
    ChangeHostModeTaskService.State failedState = new ChangeHostModeTaskService.State();
    failedState.hostMode = HostMode.ENTERING_MAINTENANCE;
    failedState.hostServiceLink = this.hostEntity.getId();
    failedState.taskState = new TaskState();
    failedState.taskState.stage = TaskState.TaskStage.FAILED;
    failedState.taskState.failure = new ServiceErrorResponse();
    failedState.taskState.failure.message = "failed";
    when(deployerClient.getHostChangeModeStatus(anyString())).thenReturn(failedState);

    commandStatus.execute();
    Fail.fail("should have failed");
  }
}
