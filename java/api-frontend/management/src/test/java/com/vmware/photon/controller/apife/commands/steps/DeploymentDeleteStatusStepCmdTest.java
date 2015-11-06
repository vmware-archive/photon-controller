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
import com.vmware.photon.controller.apife.exceptions.external.DeleteDeploymentFailedException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResult;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentResultCode;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusCode;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusResponse;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentDeleteStatusStepCmd}.
 */
public class DeploymentDeleteStatusStepCmdTest {

  DeploymentDeleteStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentBackend deploymentBackend;
  private DeployerClient deployerClient;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);

    stepBackend = mock(StepBackend.class);
    deploymentBackend = mock(DeploymentBackend.class);

    entity = new DeploymentEntity();
    entity.setOperationId("opid");
    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(entity);
    command = spy(new DeploymentDeleteStatusStepCmd(taskCommand, stepBackend, step, deploymentBackend));
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
      configureClient(RemoveDeploymentStatusCode.FINISHED);

      command.execute();
      verify(deployerClient).removeDeploymentStatus(entity.getOperationId());
      verify(deploymentBackend).updateState(entity, DeploymentState.NOT_DEPLOYED);
    }

    @Test(expectedExceptions = DeleteDeploymentFailedException.class,
        expectedExceptionsMessageRegExp = "^Delete deployment #opid failed: Remove deployment failed$")
    public void testFailedDeleteDeployment() throws Throwable {
      configureClient(RemoveDeploymentStatusCode.FAILED);

      command.execute();
      verify(deployerClient).removeDeploymentStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = RuntimeException.class,
        expectedExceptionsMessageRegExp = "^Timeout waiting for deleting deployment to complete.$")
    public void testTimeoutDeleteDeployment() throws Throwable {
      command.setDeleteDeploymentTimeout(10);
      configureClient(RemoveDeploymentStatusCode.IN_PROGRESS);

      command.execute();
      verify(deployerClient).removeDeploymentStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = ServiceUnavailableException.class,
        expectedExceptionsMessageRegExp = "^Service sue is unavailable$")
    public void testServiceUnavailableError() throws Throwable {
      command.setMaxServiceUnavailableCount(5);
      when(deployerClient.removeDeploymentStatus(any(String.class))).thenThrow(new ServiceUnavailableException("sue"));

      command.execute();
      verify(deployerClient).removeDeploymentStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = RpcException.class, expectedExceptionsMessageRegExp = "^failed to get status$")
    public void testErrorGettingStatus() throws Exception {
      when(deployerClient.removeDeploymentStatus(any(String.class)))
          .thenThrow(new RpcException("failed to get status"));

      command.execute();
      verify(deployerClient).removeDeploymentStatus(entity.getOperationId());
    }

    private void configureClient(RemoveDeploymentStatusCode code) throws Throwable {
      RemoveDeploymentStatus status = new RemoveDeploymentStatus(code);
      if (code == RemoveDeploymentStatusCode.FAILED) {
        status.setError("Remove deployment failed");
      }

      RemoveDeploymentStatusResponse response = new RemoveDeploymentStatusResponse(
          new RemoveDeploymentResult(RemoveDeploymentResultCode.OK));
      response.setStatus(status);

      when(deployerClient.removeDeploymentStatus(any(String.class))).thenReturn(response);
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
