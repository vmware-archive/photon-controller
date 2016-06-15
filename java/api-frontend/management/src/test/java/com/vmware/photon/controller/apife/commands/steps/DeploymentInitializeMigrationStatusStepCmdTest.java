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

import com.vmware.photon.controller.apife.backends.DeploymentXenonBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentMigrationFailedException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowService;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

/**
 * Tests {@link DeploymentInitializeMigrationStatusStepCmd}.
 */
public class DeploymentInitializeMigrationStatusStepCmdTest {

  DeploymentInitializeMigrationStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private TaskBackend taskBackend;
  private DeploymentXenonBackend deploymentBackend;

  private DeployerClient deployerClient;
  private DeploymentInitializeMigrationStatusStepCmd.DeploymentInitializeMigrationStatusStepPoller poller;

  private InitializeDeploymentMigrationWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    taskBackend = mock(TaskBackend.class);
    deploymentBackend = mock(DeploymentXenonBackend.class);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);
    when(deploymentBackend.getDeployerClient()).thenReturn(deployerClient);
    stepBackend = mock(StepBackend.class);

    entity = new DeploymentEntity();

    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.addResource(entity);

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    poller = new DeploymentInitializeMigrationStatusStepCmd.DeploymentInitializeMigrationStatusStepPoller(taskCommand,
        taskBackend, deploymentBackend);
    command = spy(new DeploymentInitializeMigrationStatusStepCmd(taskCommand, stepBackend, currentStep, poller));

    serviceDocument = new InitializeDeploymentMigrationWorkflowService.State();
    serviceDocument.taskState = new InitializeDeploymentMigrationWorkflowService.TaskState();
    serviceDocument.taskState.stage = InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK
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
    public void testSuccessfulInitializeMigrationDeployment() throws Throwable {
      configureClient(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.FINISHED);

      command.execute();
      verify(deployerClient).getInitializeMigrateDeploymentStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = DeploymentMigrationFailedException.class)
    public void testFailedInitializeMigrationDeployment() throws Throwable {
      configureClient(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.FAILED);

      command.execute();
      verify(deployerClient).getInitializeMigrateDeploymentStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testTimeout() throws Throwable {
      command.setOperationTimeout(10);
      configureClient(TaskState.TaskStage.STARTED);

      command.execute();
      verify(deployerClient).getInitializeMigrateDeploymentStatus(entity.getOperationId());
    }

    private void configureClient(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage stage)
        throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State state =
          new InitializeDeploymentMigrationWorkflowService.State();
      state.taskState = new InitializeDeploymentMigrationWorkflowService.TaskState();
      state.taskState.stage = stage;
      state.destinationDeploymentId = DeploymentServiceFactory.SELF_LINK + "/" + UUID.randomUUID();
      if (stage == TaskState.TaskStage.FAILED) {
        state.taskState.failure = new ServiceErrorResponse();
        state.taskState.failure.message = "";
      }

      when(deployerClient.getInitializeMigrateDeploymentStatus(any(String.class))).thenReturn(state);
    }
  }
}
