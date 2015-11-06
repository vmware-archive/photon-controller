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
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentFailedException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.DeployResult;
import com.vmware.photon.controller.deployer.gen.DeployResultCode;
import com.vmware.photon.controller.deployer.gen.DeployStageStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatusCode;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;

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

/**
 * Tests {@link DeploymentStatusStepCmdTest}.
 */
public class DeploymentStatusStepCmdTest {

  DeploymentStatusStepCmd command;
  DeploymentEntity entity;
  StepEntity step;

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
    step = new StepEntity();
    step.setId("id");
    step.setOperation(Operation.PROVISION_CLOUD_HOSTS);
    step.addResource(entity);
    command = spy(new DeploymentStatusStepCmd(taskCommand, stepBackend, step, deploymentBackend));

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
      configureClient(DeployStatusCode.FINISHED, DeployStatusCode.FINISHED);

      command.execute();
      verify(deployerClient).deployStatus(entity.getOperationId());
      verify(deploymentBackend).updateState(entity, DeploymentState.READY);
      assertThat(step.getWarnings().size(), is(0));
    }

    @Test
    public void testSuccessfulDeploymentAndStepNotCompleted() throws Throwable {
      configureClient(DeployStatusCode.FINISHED);

      command.execute();
      verify(deployerClient).deployStatus(entity.getOperationId());
      verify(deploymentBackend).updateState(entity, DeploymentState.READY);
      assertThat(step.getWarnings().size(), is(1));
      assertThat(step.getWarnings().get(0).getCode(), is(ErrorCode.STEP_NOT_COMPLETED.getCode()));
    }

    @Test
    public void testRunningDeploymentAndStepCompleted() throws Throwable {
      configureClient(DeployStatusCode.IN_PROGRESS, DeployStatusCode.FINISHED);

      command.execute();
      verify(deployerClient).deployStatus(entity.getOperationId());
      verify(deploymentBackend, never()).updateState(any(DeploymentEntity.class), any(DeploymentState.class));
      assertThat(step.getWarnings().size(), is(0));
    }

    @Test(expectedExceptions = DeploymentFailedException.class,
        expectedExceptionsMessageRegExp = "^Deployment #opid failed: deployment failed$")
    public void testFailedDeployment() throws Throwable {
      configureClient(DeployStatusCode.FAILED);

      try {
        command.execute();
      } catch (Throwable e) {
        verify(deployerClient).deployStatus(entity.getOperationId());
        throw e;
      }
    }

    @Test(expectedExceptions = DeploymentFailedException.class,
        expectedExceptionsMessageRegExp = "^Deployment #opid failed: null$")
    public void testFailedStep() throws Throwable {
      configureClient(DeployStatusCode.IN_PROGRESS, DeployStatusCode.FAILED);

      try {
        command.execute();
        fail("deploy should fail");
      } catch (Throwable e) {
        verify(deployerClient).deployStatus(entity.getOperationId());
        throw e;
      }
    }

    @Test(expectedExceptions = DeploymentFailedException.class,
        expectedExceptionsMessageRegExp = "^Deployment #opid failed: deployment failed$")
    public void testFailedDeploymentAndStep() throws Throwable {
      configureClient(DeployStatusCode.FAILED, DeployStatusCode.FAILED);

      try {
        command.execute();
        fail("deploy should fail");
      } catch (Throwable e) {
        verify(deployerClient).deployStatus(entity.getOperationId());
        throw e;
      }
    }

    @Test(expectedExceptions = RuntimeException.class,
        expectedExceptionsMessageRegExp = "^Timeout waiting for deployment to complete.$")
    public void testTimeoutDeployment() throws Throwable {
      command.setDeploymentTimeout(10);
      configureClient(DeployStatusCode.IN_PROGRESS);

      command.execute();
    }

    @Test(expectedExceptions = ServiceUnavailableException.class,
        expectedExceptionsMessageRegExp = "^Service sue is unavailable$")
    public void testServiceUnavailableError() throws Throwable {
      command.setMaxServiceUnavailableCount(5);
      when(deployerClient.deployStatus(any(String.class))).thenThrow(new ServiceUnavailableException("sue"));

      command.execute();
    }

    @Test(expectedExceptions = RpcException.class, expectedExceptionsMessageRegExp = "^failed to get status$")
    public void testErrorGettingStatus() throws Exception {
      when(deployerClient.deployStatus(any(String.class))).thenThrow(new RpcException("failed to get status"));

      try {
        command.execute();
        fail("calling deployStatus shoudl fail");
      } catch (Throwable e) {
        verify(deployerClient).deployStatus(entity.getOperationId());
        throw e;
      }
    }

    private void configureClient(DeployStatusCode code) throws Throwable {
      configureClient(code, null);
    }

    private void configureClient(DeployStatusCode code, DeployStatusCode stageCode) throws Throwable {
      DeployStatus status = new DeployStatus(code);
      if (code == DeployStatusCode.FAILED) {
        status.setError("deployment failed");
      }

      DeployStageStatus stage = new DeployStageStatus("PROVISION_CLOUD_HOSTS");
      stage.setCode(stageCode);
      status.setStages(ImmutableList.of(stage));

      DeployStatusResponse response = new DeployStatusResponse(new DeployResult(DeployResultCode.OK));
      response.setStatus(status);

      when(deployerClient.deployStatus(any(String.class))).thenReturn(response);
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
