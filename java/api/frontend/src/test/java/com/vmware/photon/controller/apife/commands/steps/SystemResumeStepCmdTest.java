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

import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.XenonBackendTestModule;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;

import com.google.inject.Inject;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;

import java.util.Collections;

/**
 * Tests {@link SystemResumeStepCmd}.
 */
@Guice(modules = {XenonBackendTestModule.class})
public class SystemResumeStepCmdTest extends PowerMockTestCase {

  @Mock
  private TaskCommand taskCommand;
  @Mock
  private StepBackend stepBackend;

  private StepEntity step;

  private SystemResumeStepCmd command;

  @Inject
  private ApiFeXenonRestClient apiFeXenonRestClient;

  @Inject
  private DeploymentBackend deploymentBackend;

  private DeploymentEntity initialDeploymentEntity;

  @BeforeMethod
  public void setUp() throws Exception {
    step = new StepEntity();
    step.setId("step-1");

    DeploymentCreateSpec deploymentCreateSpec = new DeploymentCreateSpec();
    deploymentCreateSpec.setImageDatastores(Collections.singleton("imageDatastore"));

    TaskEntity task = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
    initialDeploymentEntity = deploymentBackend.findById(task.getEntityId());
    step.createOrUpdateTransientResource(SystemResumeStepCmd.DEPLOYMENT_ID_RESOURCE_KEY,
        initialDeploymentEntity.getId());
    Mockito.when(taskCommand.getApiFeXenonRestClient()).thenReturn(apiFeXenonRestClient);
    command = spy(new SystemResumeStepCmd(taskCommand, stepBackend, step));
  }

  @AfterMethod
  public void cleanUp() throws Throwable {
    // We need to change the state so that it can be deleted
    deploymentBackend.updateState(initialDeploymentEntity, DeploymentState.NOT_DEPLOYED);
    deploymentBackend.prepareDeleteDeployment(initialDeploymentEntity.getId());
  }

  @Test
  public void testSuccessAlreadyReadyState() throws Throwable {
    command.execute();
    DeploymentEntity deploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
    assertEquals(deploymentEntity.getState(), DeploymentState.READY);
  }

  @Test
  public void testSuccessPausedState() throws Throwable {
    SystemPauseStepCmd pCommand =
        spy(new SystemPauseStepCmd(taskCommand, stepBackend, step));
    pCommand.execute();
    DeploymentEntity deploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
    assertEquals(deploymentEntity.getState(), DeploymentState.PAUSED);

    command.execute();
    deploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
    assertEquals(deploymentEntity.getState(), DeploymentState.READY);
  }

  @Test
  public void testSuccessBackgroudPausedState() throws Throwable {
    SystemPauseBackgroundTasksStepCmd bpCommand =
        spy(new SystemPauseBackgroundTasksStepCmd(taskCommand, stepBackend, step));
    bpCommand.execute();
    DeploymentEntity deploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
    assertEquals(deploymentEntity.getState(), DeploymentState.BACKGROUND_PAUSED);

    command.execute();
    deploymentEntity = deploymentBackend.findById(initialDeploymentEntity.getId());
    assertEquals(deploymentEntity.getState(), DeploymentState.READY);
  }

  @Test(expectedExceptions = InternalException.class)
  public void testError() throws Throwable {
    step = new StepEntity();
    step.setId("step-1");

    command = spy(new SystemResumeStepCmd(taskCommand, stepBackend, step));
    command.execute();
  }
}
