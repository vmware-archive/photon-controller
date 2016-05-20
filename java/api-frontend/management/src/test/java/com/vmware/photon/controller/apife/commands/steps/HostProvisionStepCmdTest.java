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
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.AddCloudHostWorkflowService;

import com.google.common.collect.ImmutableList;
import org.mockito.InOrder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

/**
 * Tests {@link HostProvisionStepCmd}.
 */
public class HostProvisionStepCmdTest {

  HostProvisionStepCmd command;
  private DeployerClient deployerClient;
  private StepBackend stepBackend;
  private HostBackend hostBackend;
  private TaskCommand taskCommand;
  private HostEntity host;
  private String hostId = "host1";
  private String operationId = "operation-id";


  private AddCloudHostWorkflowService.State serviceDocument;
  private String remoteTaskLink;
  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;

  private void setUpCommon() throws Throwable {
    deployerClient = mock(DeployerClient.class);
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    hostBackend = mock(HostBackend.class);

    StepEntity step = new StepEntity();
    step.setId("step-1");

    host = new HostEntity();
    host.setId(hostId);
    host.setState(HostState.NOT_PROVISIONED);
    host.setUsageTags(UsageTagHelper.serialize(new ArrayList<UsageTag>() {{
      add(UsageTag.CLOUD);
    }}));
    step.addResource(host);

    command = spy(new HostProvisionStepCmd(taskCommand, stepBackend, step, hostBackend));
    when(taskCommand.getDeployerXenonClient()).thenReturn(deployerClient);

    currentStep = new StepEntity();
    currentStep.setId("id");
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    serviceDocument = new AddCloudHostWorkflowService.State();
    remoteTaskLink = "http://deployer" + AddCloudHostWorkflowFactoryService.SELF_LINK
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
   * Tests {@link HostProvisionStepCmd#execute()}.
   */
  public class ExecuteTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      when(deployerClient.provisionCloudHost(anyString())).thenReturn(serviceDocument);
      command.execute();
      deployerClient.getHostProvisionStatus(serviceDocument.documentSelfLink);
      verify(deployerClient, times(1)).provisionCloudHost(anyString());
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
      InOrder inOrder = inOrder(deployerClient, hostBackend);
      inOrder.verify(deployerClient).provisionCloudHost(HostServiceFactory.SELF_LINK + "/" + hostId);
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testFailedProvision() throws Throwable {
      when(deployerClient.provisionCloudHost(anyString())).thenReturn(serviceDocument);
      when(deployerClient.getHostProvisionStatus(any(String.class))).thenThrow(DocumentNotFoundException.class);
      command.execute();
      deployerClient.getHostProvisionStatus(serviceDocument.documentSelfLink);
      fail("should have failed with RuntimeException.");
    }
  }
}
