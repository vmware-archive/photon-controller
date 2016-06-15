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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.DeploymentXenonBackend;
import com.vmware.photon.controller.apife.backends.HostXenonBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.xenon.workflow.DeploymentWorkflowService;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentCreateStepCmd}.
 */
public class DeploymentCreateStepCmdTest extends PowerMockTestCase {
  private static String desiredState = "PAUSED";

  DeploymentCreateStepCmd command;

  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentXenonBackend deploymentBackend;
  private DeploymentEntity deploymentEntity;
  private HostXenonBackend hostXenonBackend;

  private DeploymentWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    deploymentBackend = mock(DeploymentXenonBackend.class);
    hostXenonBackend = mock(HostXenonBackend.class);

    deploymentEntity = new DeploymentEntity();
    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(deploymentEntity);
    step.createOrUpdateTransientResource(DeploymentCreateStepCmd.DEPLOYMENT_DESIRED_STATE_RESOURCE_KEY, desiredState);

    command = spy(new DeploymentCreateStepCmd(taskCommand, stepBackend, step, deploymentBackend));
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    currentStep = new StepEntity();
    currentStep.setId("id");
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new DeploymentWorkflowService.State();
    serviceDocument.taskState = new DeploymentWorkflowService.TaskState();
    serviceDocument.taskState.stage = DeploymentWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = DeploymentWorkflowFactoryService.SELF_LINK
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
    public void testSuccessfulDeploy() throws Throwable {
      when(deployerClient.deploy(deploymentEntity, desiredState)).thenReturn(serviceDocument);
      command.execute();

      deployerClient.getDeploymentStatus(remoteTaskLink);
      verify(deployerClient, times(1)).deploy(deploymentEntity, desiredState);
      assertEquals(deploymentEntity.getOperationId(),
          remoteTaskLink);
      InOrder inOrder = inOrder(deployerClient, deploymentBackend);
      inOrder.verify(deployerClient).deploy(deploymentEntity, desiredState);
    }

    @Test
    public void testFailedDeployOnAlreadyRunning() throws Throwable {
      when(deployerClient.deploy(any(), anyString()))
          .thenThrow(new ExternalException("running"));

      try {
        command.execute();
        fail("should have failed with Already running exception.");
      } catch (ExternalException e) {
      }
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

    @Test
    public void testEntitySet() throws Throwable {
      DeploymentEntity entity = new DeploymentEntity();
      command.setEntity(entity);

      command.markAsFailed(new Throwable());
      verify(deploymentBackend).updateState(entity, DeploymentState.ERROR);
    }
  }
}
