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
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResponse;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResult;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResultCode;

import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentDeleteStepCmd}.
 */
public class DeploymentDeleteStepCmdTest {

  DeploymentDeleteStepCmd command;

  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentBackend deploymentBackend;
  private DeploymentEntity deploymentEntity;

  public void setUpCommon() {
    deploymentEntity = new DeploymentEntity();
    deploymentEntity.setId("deployment");

    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    deploymentBackend = mock(DeploymentBackend.class);

    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(deploymentEntity);

    command = spy(new DeploymentDeleteStepCmd(taskCommand, stepBackend, step, deploymentBackend));
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);
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
    public void testSuccessfulDeleteDeployment() throws Exception {
      RemoveDeploymentResponse response = new RemoveDeploymentResponse(
          new RemoveDeploymentResult(RemoveDeploymentResultCode.OK));
      when(deployerClient.removeDeployment(deploymentEntity.getId())).thenReturn(response);

      command.execute();
      verify(deployerClient).removeDeployment(any(String.class));
      InOrder inOrder = inOrder(deployerClient, deploymentBackend);
      inOrder.verify(deployerClient).removeDeployment(deploymentEntity.getId());
      verifyNoMoreInteractions(deployerClient, deploymentBackend);
    }

    @Test
    public void testFailedDeleteDeployment() throws Exception {
      when(deployerClient.removeDeployment(any(String.class))).thenThrow(new RpcException());

      try {
        command.execute();
        fail("should have failed with RpcException.");
      } catch (RpcException e) {
        assertThat(e, isA(RpcException.class));
      }

      InOrder inOrder = inOrder(deployerClient, deploymentBackend);
      inOrder.verify(deployerClient).removeDeployment(deploymentEntity.getId());
      verifyNoMoreInteractions(deployerClient, deploymentBackend);
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
      command.setDeploymentEntity(entity);

      command.markAsFailed(new Throwable());
      verify(deploymentBackend).updateState(entity, DeploymentState.ERROR);
    }
  }
}
