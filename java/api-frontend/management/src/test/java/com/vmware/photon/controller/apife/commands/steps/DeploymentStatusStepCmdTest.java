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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.apife.backends.DeploymentDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentFailedException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowService;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

/**
 * Tests {@link DeploymentStatusStepCmdTest}.
 */
public class DeploymentStatusStepCmdTest {

  DeploymentStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentDcpBackend deploymentBackend;
  private TaskBackend taskBackend;
  private DeployerClient deployerClient;
  private DeploymentStatusStepCmd.DeploymentStatusStepPoller poller;

  private DeploymentWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    stepBackend = mock(StepBackend.class);
    deploymentBackend = mock(DeploymentDcpBackend.class);
    when(deploymentBackend.getDeployerClient()).thenReturn(deployerClient);
    taskBackend = mock(TaskBackend.class);
    poller = new DeploymentStatusStepCmd.DeploymentStatusStepPoller(taskCommand, taskBackend, deploymentBackend);

    entity = new DeploymentEntity();
    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.setOperation(Operation.PROVISION_CLOUD_HOSTS);
    currentStep.addResource(entity);
    command = spy(new DeploymentStatusStepCmd(taskCommand, stepBackend, currentStep, poller));

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new DeploymentWorkflowService.State();
    serviceDocument.taskState = new DeploymentWorkflowService.TaskState();
    serviceDocument.taskState.stage = DeploymentWorkflowService.TaskState.TaskStage.STARTED;
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
    public void testSuccessfulDeploymentAndStep() throws Throwable {
      configureClient(TaskState.TaskStage.FINISHED);

      command.execute();
      verify(deployerClient).getDeploymentStatus(remoteTaskLink);
      verify(deploymentBackend).updateState(entity, DeploymentState.READY);
      assertThat(currentStep.getWarnings().size(), is(0));
    }

    @Test
    public void testRunningDeployment() throws Throwable {
      command.setDefaultDeploymentTimeout(10);
      configureClient(TaskState.TaskStage.STARTED);

      try {
        command.execute();
        fail("Should have timed out");
      } catch (RuntimeException ex) {

      }
      verify(deploymentBackend, never()).updateState(any(DeploymentEntity.class), any(DeploymentState.class));
    }

    @Test
    public void testFailedDeploymentAndStep() throws Throwable {
      configureClient(DeploymentWorkflowService.TaskState.TaskStage.FAILED);

      try {
        command.execute();
        fail("deploy should fail");
      } catch (DeploymentFailedException e) {

      }
    }

    private void configureClient(DeploymentWorkflowService.TaskState.TaskStage stage)
        throws Throwable {
      DeploymentWorkflowService.State state = new DeploymentWorkflowService.State();
      state.taskState = new DeploymentWorkflowService.TaskState();
      state.taskState.stage = stage;
      state.taskState.subStage = DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE;
      state.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + UUID.randomUUID();
      if (stage == DeploymentWorkflowService.TaskState.TaskStage.FAILED) {
        state.taskState.failure = new ServiceErrorResponse();
        state.taskState.failure.message = "deployment failed";
      }
      state.taskState.subStage = DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS;
      when(deployerClient.getDeploymentStatus(any(String.class))).thenReturn(state);
    }
  }

  /**
   * Tests for markAsFailed method.
   */
  public class MarkAsFailedTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testEntityNotSet() throws Throwable {
      command.markAsFailed(new Throwable());
      verify(deploymentBackend, never()).updateState(any(DeploymentEntity.class), any(DeploymentState.class));
    }
  }
}
