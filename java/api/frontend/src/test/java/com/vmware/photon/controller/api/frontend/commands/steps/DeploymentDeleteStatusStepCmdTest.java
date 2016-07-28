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

import com.vmware.photon.controller.api.frontend.backends.DeploymentXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.DeployerClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeleteDeploymentFailedException;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.RemoveDeploymentWorkflowService;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

/**
 * Tests {@link DeploymentDeleteStatusStepCmd}.
 */
public class DeploymentDeleteStatusStepCmdTest {

  DeploymentDeleteStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentXenonBackend deploymentBackend;
  private DeployerClient deployerClient;
  private DeploymentDeleteStatusStepCmd.DeploymentDeleteStepPoller poller;

  private RemoveDeploymentWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private TaskBackend taskBackend;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    taskBackend = mock(TaskBackend.class);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    stepBackend = mock(StepBackend.class);
    deploymentBackend = mock(DeploymentXenonBackend.class);
    when(deploymentBackend.getDeployerClient()).thenReturn(deployerClient);

    entity = new DeploymentEntity();
    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.setOperation(Operation.DEPROVISION_HOSTS);
    currentStep.addResource(entity);

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    poller = new DeploymentDeleteStatusStepCmd.DeploymentDeleteStepPoller(taskCommand, taskBackend, deploymentBackend);

    command = spy(new DeploymentDeleteStatusStepCmd(taskCommand, stepBackend, currentStep, poller));

    serviceDocument = new RemoveDeploymentWorkflowService.State();
    serviceDocument.taskState = new RemoveDeploymentWorkflowService.TaskState();
    serviceDocument.taskState.stage = RemoveDeploymentWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + DeploymentWorkflowFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;

    entity.setOperationId(remoteTaskLink);
  }

  /**
   * Dummy test to keep IntelliJ happy.
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

      command.setStatusPollInterval(5);
    }

    @Test
    public void testSuccessfulDeleteDeployment() throws Throwable {
      configureClient(RemoveDeploymentWorkflowService.TaskState.TaskStage.FINISHED);

      command.execute();
      verify(deployerClient).getRemoveDeploymentStatus(remoteTaskLink);
      verify(deploymentBackend).updateState(entity, DeploymentState.NOT_DEPLOYED);
    }

    @Test
    public void testFailedDeleteDeployment() throws Throwable {
      configureClient(RemoveDeploymentWorkflowService.TaskState.TaskStage.FAILED);
      try {
        command.execute();
        fail("Should have failed");
      } catch (DeleteDeploymentFailedException ex) {

      }
      verify(deployerClient).getRemoveDeploymentStatus(remoteTaskLink);
      verify(deploymentBackend).updateState(entity, DeploymentState.ERROR);
    }

    @Test
    public void testTimeoutDeleteDeployment() throws Throwable {
      command.setDeleteDeploymentTimeout(10);
      configureClient(TaskState.TaskStage.STARTED);
      try {
        command.execute();
        fail("Should have failed");
      } catch (RuntimeException ex) {

      }
    }

    private void configureClient(RemoveDeploymentWorkflowService.TaskState.TaskStage stage) throws Throwable {
      RemoveDeploymentWorkflowService.State state = new RemoveDeploymentWorkflowService.State();
      state.taskState = new RemoveDeploymentWorkflowService.TaskState();
      state.taskState.stage = stage;
      state.taskState.subStage = RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE;
      state.deploymentId = UUID.randomUUID().toString();
      if (stage == TaskState.TaskStage.FAILED) {
        state.taskState.failure = new ServiceErrorResponse();
        state.taskState.failure.message = "Remove deployment failed";
      }

      when(deployerClient.getRemoveDeploymentStatus(any(String.class))).thenReturn(state);
    }
  }
}
