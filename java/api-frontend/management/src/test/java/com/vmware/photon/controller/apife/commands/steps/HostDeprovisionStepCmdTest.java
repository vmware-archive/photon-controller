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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.apife.backends.HostDcpBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowService;

import jersey.repackaged.com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
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
 * Tests {@link HostDeprovisionStepCmd}.
 */
public class HostDeprovisionStepCmdTest {

  HostDeprovisionStepCmd command;
  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private HostDcpBackend hostBackend;
  private TaskCommand taskCommand;
  private HostEntity host;
  private String hostId = "host1";
  private String operationId = "operation-id";

  private DeprovisionHostWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  private void setUpCommon() throws Throwable {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostDcpBackend.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId(hostId);
    host.setState(HostState.NOT_PROVISIONED);
    step.addResource(host);

    command = spy(new HostDeprovisionStepCmd(taskCommand, stepBackend, step, hostBackend));
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    currentStep = new StepEntity();
    currentStep.setId("id");
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);
    when(hostBackend.getDeployerClient()).thenReturn(deployerClient);

    serviceDocument = new DeprovisionHostWorkflowService.State();
    serviceDocument.taskState = new DeprovisionHostWorkflowService.TaskState();
    serviceDocument.taskState.stage = DeprovisionHostWorkflowService.TaskState.TaskStage.STARTED;
    remoteTaskLink = "http://deployer" + DeprovisionHostWorkflowFactoryService.SELF_LINK
        + "/00000000-0000-0000-0000-000000000001";
    serviceDocument.documentSelfLink = remoteTaskLink;
  }

  /**
   * Dummy test to for IntelliJ.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests {@link HostDeprovisionStepCmd#execute()}.
   */
  public class ExecuteTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      when(deployerClient.deprovisionHost(anyString())).thenReturn(serviceDocument);
      command.execute();
      deployerClient.getHostDeprovisionStatus(serviceDocument.documentSelfLink);
      verify(deployerClient, times(1)).deprovisionHost(anyString());
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
      InOrder inOrder = inOrder(deployerClient, hostBackend);
      inOrder.verify(deployerClient).deprovisionHost(HostServiceFactory.SELF_LINK + "/" + hostId);
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testFailure() throws Throwable {
      when(deployerClient.deprovisionHost(anyString())).thenReturn(serviceDocument);
      when(deployerClient.getHostDeprovisionStatus(any(String.class))).thenThrow(DocumentNotFoundException.class);
      command.execute();
      deployerClient.getHostDeprovisionStatus(serviceDocument.documentSelfLink);
      fail("should have failed with RuntimeException.");
    }

    @Test
    public void testRuntimeExceptionWithErrorState() throws Throwable {
      when(deployerClient.deprovisionHost(anyString())).thenReturn(serviceDocument);
      host.setState(HostState.ERROR);
      when(deployerClient.getHostDeprovisionStatus(any(String.class))).thenThrow(DocumentNotFoundException.class);
      command.execute();
      try {
        deployerClient.getHostDeprovisionStatus(serviceDocument.documentSelfLink);
        fail("should have failed.");
      } catch (Throwable t) {

      }
      verify(hostBackend, never()).updateState(any(HostEntity.class), any(HostState.class));
    }
  }
}
