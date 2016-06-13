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

import com.vmware.photon.controller.apife.backends.DeploymentDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentMigrationFailedException;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowService;
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
 * Tests {@link DeploymentFinalizeMigrationStatusStepCmd}.
 */
public class DeploymentFinalizeMigrationStatusStepCmdTest {

  DeploymentFinalizeMigrationStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentDcpBackend deploymentBackend;
  private TaskBackend taskBackend;
  private DeployerClient deployerClient;
  private DeploymentFinalizeMigrationStatusStepCmd.DeploymentFinalizeMigrationStatusStepPoller poller;

  private FinalizeDeploymentMigrationWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    stepBackend = mock(StepBackend.class);
    deploymentBackend = mock(DeploymentDcpBackend.class);
    taskBackend = mock(TaskBackend.class);

    when(deploymentBackend.getDeployerClient()).thenReturn(deployerClient);

    entity = new DeploymentEntity();
    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.addResource(entity);

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    poller = new DeploymentFinalizeMigrationStatusStepCmd.DeploymentFinalizeMigrationStatusStepPoller(taskCommand,
        taskBackend, deploymentBackend);
    command = spy(new DeploymentFinalizeMigrationStatusStepCmd(taskCommand, stepBackend, currentStep, poller));

    serviceDocument = new FinalizeDeploymentMigrationWorkflowService.State();
    serviceDocument.taskState = new FinalizeDeploymentMigrationWorkflowService.TaskState();
    serviceDocument.taskState.stage = FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK
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
    public void testSuccessfulFinalizeMigrationDeployment() throws Throwable {
      configureClient(FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage.FINISHED);

      command.execute();
      verify(deployerClient).getFinalizeMigrateDeploymentStatus(remoteTaskLink);
    }

    @Test(expectedExceptions = DeploymentMigrationFailedException.class)
    public void testFailedFinalizeMigrationDeployment() throws Throwable {
      configureClient(FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage.FAILED);

      command.execute();
      verify(deployerClient).getFinalizeMigrateDeploymentStatus(remoteTaskLink);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testTimeout() throws Throwable {
      command.setOperationTimeout(10);
      configureClient(TaskState.TaskStage.STARTED);

      command.execute();
      verify(deployerClient).getFinalizeMigrateDeploymentStatus(remoteTaskLink);
    }


    private void configureClient(FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage stage)
        throws Throwable {
      FinalizeDeploymentMigrationWorkflowService.State state = new FinalizeDeploymentMigrationWorkflowService.State();
      state.taskState = new FinalizeDeploymentMigrationWorkflowService.TaskState();
      state.taskState.stage = stage;
      state.destinationDeploymentId = DeploymentServiceFactory.SELF_LINK + "/" + UUID.randomUUID();
      if (stage == TaskState.TaskStage.FAILED) {
        state.taskState.failure = new ServiceErrorResponse();
        state.taskState.failure.message = "";
      }

      when(deployerClient.getFinalizeMigrateDeploymentStatus(any(String.class))).thenReturn(state);
    }
  }
}
