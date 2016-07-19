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
import com.vmware.photon.controller.apife.backends.HostXenonBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DuplicateHostException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskService;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link HostCreateStepCmd}.
 */
public class HostCreateStepCmdTest {

  HostCreateStepCmd command;

  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private HostXenonBackend hostBackend;
  private TaskBackend taskBackend;
  private HostEntity host;

  private ValidateHostTaskService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity nextStep;
  private StepEntity stepEntityMock;
  private HostCreateTaskStatusPoller poller;
  private XenonTaskStatusStepCmd commandStatus;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostXenonBackend.class);
    poller = mock(HostCreateTaskStatusPoller.class);
    taskBackend = mock(TaskBackend.class);
    stepEntityMock = mock(StepEntity.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId("host1");
    host.setState(HostState.CREATING);
    host.setAddress("host-addr");
    step.addResource(host);

    command = spy(new HostCreateStepCmd(taskCommand, stepBackend, step, hostBackend));
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(step, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);
    when(hostBackend.getDeployerClient()).thenReturn(deployerClient);

    serviceDocument = new ValidateHostTaskService.State();
    serviceDocument.taskState = new ValidateHostTaskService.TaskState();
    serviceDocument.taskState.stage = ValidateHostTaskService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + ValidateHostTaskFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;

    when(stepEntityMock.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY)).thenReturn
        (remoteTaskLink);

    stepEntityMock.addResource(host);
    commandStatus =  new XenonTaskStatusStepCmd(taskCommand, stepBackend, stepEntityMock,
        new HostCreateTaskStatusPoller(taskCommand, hostBackend, taskBackend));
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
      when(deployerClient.createHost(any(HostEntity.class))).thenReturn(serviceDocument);
      command.execute();
      verify(deployerClient, times(1)).createHost(any(HostEntity.class));
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);

    }

    @Test
    public void testFailure() throws Throwable {
      when(deployerClient.createHost(any(HostEntity.class)))
          .thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(deployerClient, times(1)).createHost(any(HostEntity.class));
    }


    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testCreateHostFailedOnDeployer() throws Throwable {
      when(deployerClient.createHost(any(HostEntity.class))).thenReturn(serviceDocument);
      when(deployerClient.getHostCreationStatus(any(String.class))).thenThrow(DocumentNotFoundException.class);
      deployerClient.createHost(any(HostEntity.class));
      command.execute();

      deployerClient.getHostCreationStatus(serviceDocument.documentSelfLink);
      fail("should have failed with DocumentNotFoundException");
    }

    @Test
    public void testHostStatusChangeReady() throws Throwable{
      ValidateHostTaskService.State readyState = new ValidateHostTaskService.State();
      readyState.taskState = new ValidateHostTaskService.TaskState();
      readyState.taskState.stage = ValidateHostTaskService.TaskState.TaskStage.FINISHED;
      when(deployerClient.getHostCreationStatus(anyString())).thenReturn(readyState);

      commandStatus.run();
      verify(hostBackend).updateState(host, HostState.NOT_PROVISIONED);
    }

    @Test(expectedExceptions = DuplicateHostException.class)
    public void testHostStatusChangeFailed() throws Throwable{
      ValidateHostTaskService.State failedState = new ValidateHostTaskService.State();
      failedState.taskState = new ValidateHostTaskService.TaskState();
      failedState.taskState.stage = TaskState.TaskStage.FAILED;
      failedState.taskState.failure = new ServiceErrorResponse();
      failedState.taskState.failure.message = "failed";
      failedState.taskState.resultCode = ValidateHostTaskService.TaskState.ResultCode.ExistHostWithSameAddress;
      when(deployerClient.getHostCreationStatus(anyString())).thenReturn(failedState);

      commandStatus.execute();
      fail("should have failed");
    }
  }
}
