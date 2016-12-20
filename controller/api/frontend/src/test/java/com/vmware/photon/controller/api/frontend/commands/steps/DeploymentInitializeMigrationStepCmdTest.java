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

import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.DeployerClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.InitializeDeploymentMigrationWorkflowService;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentInitializeMigrationStepCmd}.
 */
public class DeploymentInitializeMigrationStepCmdTest {

  DeploymentInitializeMigrationStepCmd command;

  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentBackend deploymentBackend;
  private DeploymentEntity deploymentEntity;

  private InitializeDeploymentMigrationWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  public void setUpCommon() {
    deploymentEntity = new DeploymentEntity();
    deploymentEntity.setId("deployment");

    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    deploymentBackend = mock(DeploymentBackend.class);

    StepEntity step = new StepEntity();
    step.setId("id");
    step.createOrUpdateTransientResource(DeploymentInitializeMigrationStepCmd.SOURCE_ADDRESS_RESOURCE_KEY,
        "address");
    step.addResource(deploymentEntity);

    TaskEntity taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(step));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = spy(new DeploymentInitializeMigrationStepCmd(taskCommand, stepBackend, step, deploymentBackend));
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    currentStep = new StepEntity();
    currentStep.setId("id");
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new InitializeDeploymentMigrationWorkflowService.State();
    serviceDocument.taskState = new InitializeDeploymentMigrationWorkflowService.TaskState();
    serviceDocument.taskState.stage = InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;
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
    }

    @Test
    public void testSuccessfulInitializeDeploymentMigration() throws Throwable {
      when(deployerClient.initializeMigrateDeployment(anyString(), anyString())).thenReturn(serviceDocument);
      command.execute();

      deployerClient.getInitializeMigrateDeploymentStatus(remoteTaskLink);
      verify(deployerClient, times(1)).initializeMigrateDeployment(anyString(), anyString());
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
      InOrder inOrder = inOrder(deployerClient, deploymentBackend);
      inOrder.verify(deployerClient).initializeMigrateDeployment(anyString(), anyString());
    }


    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testFailedInitializeDeploymentMigration() throws Throwable {
      when(deployerClient.initializeMigrateDeployment(anyString(), anyString())).thenReturn(serviceDocument);

      command.execute();

      when(deployerClient.getInitializeMigrateDeploymentStatus(remoteTaskLink))
          .thenThrow(new DocumentNotFoundException(null, null));
      deployerClient.getInitializeMigrateDeploymentStatus(remoteTaskLink);
      fail("should have failed with DocumentNotFoundException.");
    }
  }
}
