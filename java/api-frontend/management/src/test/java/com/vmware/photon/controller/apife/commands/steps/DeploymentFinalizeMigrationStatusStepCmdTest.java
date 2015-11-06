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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentMigrationFailedException;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResult;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusCode;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusResponse;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link DeploymentFinalizeMigrationStatusStepCmd}.
 */
public class DeploymentFinalizeMigrationStatusStepCmdTest {

  DeploymentFinalizeMigrationStatusStepCmd command;
  DeploymentEntity entity;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private DeployerClient deployerClient;

  public void setUpCommon() {
    deployerClient = mock(DeployerClient.class);
    taskCommand = mock(TaskCommand.class);
    when(taskCommand.getDeployerClient()).thenReturn(deployerClient);

    stepBackend = mock(StepBackend.class);

    entity = new DeploymentEntity();
    entity.setOperationId("opid");
    StepEntity step = new StepEntity();
    step.setId("id");
    step.addResource(entity);
    command = spy(new DeploymentFinalizeMigrationStatusStepCmd(taskCommand, stepBackend, step));
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
      configureClient(FinalizeMigrateDeploymentStatusCode.FINISHED);

      command.execute();
      verify(deployerClient).finalizeMigrateStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = DeploymentMigrationFailedException.class)
    public void testFailedFinalizeMigrationDeployment() throws Throwable {
      configureClient(FinalizeMigrateDeploymentStatusCode.FAILED);

      command.execute();
      verify(deployerClient).finalizeMigrateStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testTimeout() throws Throwable {
      command.setOperationTimeout(10);
      configureClient(FinalizeMigrateDeploymentStatusCode.IN_PROGRESS);

      command.execute();
      verify(deployerClient).finalizeMigrateStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = ServiceUnavailableException.class)
    public void testServiceUnavailableError() throws Throwable {
      command.setMaxServiceUnavailableCount(5);
      when(deployerClient.finalizeMigrateStatus(any(String.class))).thenThrow(new
          ServiceUnavailableException
          ("exception"));

      command.execute();
      verify(deployerClient).finalizeMigrateStatus(entity.getOperationId());
    }

    @Test(expectedExceptions = RpcException.class)
    public void testErrorGettingStatus() throws Exception {
      when(deployerClient.finalizeMigrateStatus(any(String.class)))
          .thenThrow(new RpcException("failed to get status"));

      command.execute();
      verify(deployerClient).finalizeMigrateStatus(entity.getOperationId());
    }

    private void configureClient(FinalizeMigrateDeploymentStatusCode code) throws Throwable {
      FinalizeMigrateDeploymentStatus status = new FinalizeMigrateDeploymentStatus(code);
      if (code == FinalizeMigrateDeploymentStatusCode.FAILED) {
        status.setError("Migrate deployment failed");
      }

      FinalizeMigrateDeploymentStatusResponse response = new FinalizeMigrateDeploymentStatusResponse(
          new FinalizeMigrateDeploymentResult(
              com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentResultCode.OK));
      response.setStatus(status);

      when(deployerClient.finalizeMigrateStatus(any(String.class))).thenReturn(response);
    }
  }
}
