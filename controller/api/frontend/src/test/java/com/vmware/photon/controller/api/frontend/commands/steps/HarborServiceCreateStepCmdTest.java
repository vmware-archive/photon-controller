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

import com.vmware.photon.controller.api.frontend.backends.ServiceBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ServicesManagerClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.servicesmanager.servicedocuments.HarborServiceCreateTaskState;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link HarborServiceCreateStepCmd}.
 */
public class HarborServiceCreateStepCmdTest extends PowerMockTestCase {

  HarborServiceCreateStepCmd command;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private ServiceBackend serviceBackend;
  private ServicesManagerClient servicesManagerClient;

  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;
  private ServiceCreateSpec createSpec;
  private HarborServiceCreateTaskState serviceDocument;
  private String remoteTaskLink;

  private void setUpCommon() {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    serviceBackend = mock(ServiceBackend.class);
    servicesManagerClient = mock(ServicesManagerClient.class);

    createSpec = new ServiceCreateSpec();
    createSpec.setName("serviceName");
    createSpec.setType(ServiceType.HARBOR);
    createSpec.setVmFlavor("vmFlavor1");
    createSpec.setDiskFlavor("diskFlavor1");
    createSpec.setWorkerCount(0);

    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, "projectId");
    currentStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, createSpec);
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = spy(new HarborServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend));
    when(serviceBackend.getServicesManagerClient()).thenReturn(servicesManagerClient);

    serviceDocument = new HarborServiceCreateTaskState();
    serviceDocument.taskState = new HarborServiceCreateTaskState.TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    serviceDocument.taskState.subStage = HarborServiceCreateTaskState.TaskState.SubStage.SETUP_HARBOR;
    remoteTaskLink = "http://servicesmanager" + ServiceUriPaths.HARBOR_SERVICE_CREATE_TASK
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
    public void testSuccess() throws Throwable {
      when(servicesManagerClient.createHarborService(any(String.class), any(ServiceCreateSpec.class)))
          .thenReturn(serviceDocument);

      command.execute();
      verify(servicesManagerClient, times(1)).createHarborService(any(String.class), any(ServiceCreateSpec.class));
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
    }

    @Test
    public void testFailure() throws Throwable {
      when(servicesManagerClient.createHarborService(any(String.class), any(ServiceCreateSpec.class)))
          .thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(servicesManagerClient, times(1)).createHarborService(any(String.class), any(ServiceCreateSpec.class));
    }

    @Test
    public void testMissingProjectId() throws Throwable {
      currentStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, null);
      command = new HarborServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend);

      try {
        command.execute();
        fail("should have failed with IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("project-id is not defined in TransientResource"));
      }
    }

    @Test
    public void testMissingCreateSpec() throws Throwable {
      currentStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, null);
      command = new HarborServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend);

      try {
        command.execute();
        fail("should have failed with IllegalArgumentException");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("create-spec is not defined in TransientResource"));
      }
    }
  }
}
