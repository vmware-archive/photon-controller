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
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.apife.exceptions.external.NoManagementHostException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.DeployResult;
import com.vmware.photon.controller.deployer.gen.DeployResultCode;
import com.vmware.photon.controller.deployer.gen.Deployment;

import org.mockito.ArgumentCaptor;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentCreateStepCmd}.
 */
public class DeploymentCreateStepCmdTest extends PowerMockTestCase {
  private static String desiredState = "PAUSED";

  DeploymentCreateStepCmd command;

  private ArgumentCaptor<Deployment> deploymentArgumentCaptor;
  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeploymentBackend deploymentBackend;
  private DeploymentEntity deploymentEntity;

  public void setUpCommon() {
    deploymentArgumentCaptor = ArgumentCaptor.forClass(Deployment.class);
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    deploymentBackend = mock(DeploymentBackend.class);

    deploymentEntity = new DeploymentEntity();
    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(deploymentEntity);
    step.createOrUpdateTransientResource(DeploymentCreateStepCmd.DEPLOYMENT_DESIRED_STATE_RESOURCE_KEY, desiredState);

    command = spy(new DeploymentCreateStepCmd(taskCommand, stepBackend, step, deploymentBackend));
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
    public void testSuccessfulDeploy() throws Exception {
      DeployResponse response = new DeployResponse(new DeployResult(DeployResultCode.OK));
      response.setOperation_id("operation-id");
      when(deployerClient.deploy(any(Deployment.class), any(String.class))).thenReturn(response);

      command.execute();
      assertThat(deploymentEntity.getOperationId(), is(response.getOperation_id()));

      verify(deployerClient).deploy(deploymentArgumentCaptor.capture(), eq(desiredState));
      Deployment deployment = deploymentArgumentCaptor.getValue();
      assertThat(deployment.getId(), is(deploymentEntity.getId()));
      if (deploymentEntity.getImageDatastores() != null) {
        assertTrue(deploymentEntity.getImageDatastores().contains(deployment.getImageDatastore()));
      }
      assertThat(deployment.getNtpEndpoint(), is(deploymentEntity.getNtpEndpoint()));
      assertThat(deployment.getSyslogEndpoint(), is(deploymentEntity.getSyslogEndpoint()));
      assertThat(deployment.isAuthEnabled(), is(deploymentEntity.getAuthEnabled()));
      assertThat(deployment.getOauthEndpoint(), is(deploymentEntity.getOauthEndpoint()));
      assertThat(deployment.getOauthTenant(), is(deploymentEntity.getOauthTenant()));
      assertThat(deployment.getOauthUsername(), is(deploymentEntity.getOauthUsername()));
      assertThat(deployment.getOauthPassword(), is(deploymentEntity.getOauthPassword()));
      verifyNoMoreInteractions(deployerClient);
    }

    @Test
    public void testFailedDeploy() throws Exception {
      when(deployerClient.deploy(any(Deployment.class), any(String.class))).thenThrow(new RpcException());

      try {
        command.execute();
        fail("should have failed with RpcException.");
      } catch (RpcException e) {
      }
    }

    @Test
    public void testFailedDeployOnManagementHostNotCreated() throws Exception {
      when(deployerClient.deploy(any(Deployment.class), any(String.class)))
          .thenThrow(new com.vmware.photon.controller.common.clients.exceptions.NoManagementHostException("error"));

      try {
        command.execute();
        fail("should have failed with NoManagementHostException.");
      } catch (NoManagementHostException e) {
      }
    }

    @Test
    public void testFailedDeployOnInvalidAuthConfig() throws Exception {
      when(deployerClient.deploy(any(Deployment.class), any(String.class)))
          .thenThrow(new com.vmware.photon.controller.common.clients.exceptions.InvalidAuthConfigException("error"));

      try {
        command.execute();
        fail("should have failed with NoManagementHostException.");
      } catch (InvalidAuthConfigException e) {
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
