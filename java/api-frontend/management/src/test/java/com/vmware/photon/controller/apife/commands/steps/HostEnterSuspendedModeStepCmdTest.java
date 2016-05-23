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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostStateChangeException;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
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
 * Test {@link HostEnterSuspendedModeStepCmd}.
 */
public class HostEnterSuspendedModeStepCmdTest {

  private TaskCommand taskCommand;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private DeployerClient deployerClient;

  private StepEntity step;
  private HostEntity hostEntity;
  private HostEnterSuspendedModeStepCmd command;

  private ChangeHostModeTaskService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  @BeforeMethod
  public void setUp() {
    taskCommand = mock(TaskCommand.class);
    stepBackend = mock(StepBackend.class);
    hostBackend = mock(HostBackend.class);
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

    command = new HostEnterSuspendedModeStepCmd(taskCommand, stepBackend, step, hostBackend);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    currentStep = new StepEntity();
    currentStep.setId("id");
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new ChangeHostModeTaskService.State();
    serviceDocument.taskState = new TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + ChangeHostModeTaskFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;
  }

  @Test
  public void testHappy() throws Throwable {
    when(deployerClient.enterSuspendedMode((hostEntity.getId()))).thenReturn(serviceDocument);
    command.run();
    deployerClient.getHostChangeModeStatus(serviceDocument.documentSelfLink);
    verify(deployerClient, times(1)).enterSuspendedMode(hostEntity.getId());
    assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
        remoteTaskLink);
    InOrder inOrder = inOrder(deployerClient, hostBackend);
    inOrder.verify(deployerClient).enterSuspendedMode(hostEntity.getId());

    verify(hostBackend).updateState(hostEntity, HostState.SUSPENDED);

  }


  @Test(expectedExceptions = HostStateChangeException.class)
  public void testFailure() throws Throwable {
    when(deployerClient.enterSuspendedMode(anyString())).thenReturn(serviceDocument);
    when(deployerClient.getHostChangeModeStatus(any(String.class))).thenThrow(HostStateChangeException.class);
    command.execute();
    deployerClient.getHostChangeModeStatus(serviceDocument.documentSelfLink);
    fail("Should have failed");
  }
}
