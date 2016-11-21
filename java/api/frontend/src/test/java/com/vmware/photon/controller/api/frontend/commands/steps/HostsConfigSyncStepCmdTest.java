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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.HousekeeperClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.housekeeper.xenon.HostsConfigSyncService;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link HostsConfigSyncStepCmd}.
 */
public class HostsConfigSyncStepCmdTest {

  HostsConfigSyncStepCmd command;

  private HousekeeperClient housekeeperClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private TaskBackend taskBackend;
  private DeploymentEntity deployment;

  private HostsConfigSyncService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity nextStep;
  private StepEntity stepEntityMock;
  private HostsConfigSyncTaskStatusPoller poller;
  private XenonTaskStatusStepCmd commandStatus;

  public void setUpCommon() {
    housekeeperClient = mock(HousekeeperClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    poller = mock(HostsConfigSyncTaskStatusPoller.class);
    taskBackend = mock(TaskBackend.class);
    stepEntityMock = mock(StepEntity.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    deployment = new DeploymentEntity();
    deployment.setId("test-deployment");
    step.addResource(deployment);

    command = spy(new HostsConfigSyncStepCmd(taskCommand, stepBackend, step));
    when(taskCommand.getHousekeeperXenonClient()).thenReturn(housekeeperClient);

    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(step, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new HostsConfigSyncService.State();
    serviceDocument.taskState = new TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + HostsConfigSyncService.FACTORY_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;

    when(stepEntityMock.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY)).thenReturn
        (remoteTaskLink);

    commandStatus =  new XenonTaskStatusStepCmd(taskCommand, stepBackend, stepEntityMock,
        new HostsConfigSyncTaskStatusPoller(taskCommand, taskBackend));
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the execute method.
   */
  public class ExecuteTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Exception {
      when(housekeeperClient.syncHostsConfig()).thenReturn(serviceDocument);
      command.execute();
      verify(housekeeperClient, times(1)).syncHostsConfig();
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);

    }

    @Test
    public void testFailure() throws Throwable {
      when(housekeeperClient.syncHostsConfig()).thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(housekeeperClient, times(1)).syncHostsConfig();
    }
  }
}
