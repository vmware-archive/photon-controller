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
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.servicedocuments.SwarmServiceCreateTaskState;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
 * Tests {@link SwarmServiceCreateStepCmd}.
 */
public class SwarmServiceCreateStepCmdTest extends PowerMockTestCase {

  SwarmServiceCreateStepCmd command;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private ServiceBackend serviceBackend;
  private ServicesManagerClient servicesManagerClient;

  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;
  private ServiceCreateSpec createSpec;
  private SwarmServiceCreateTaskState serviceDocument;
  private String remoteTaskLink;

  private void setUpCommon() {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    serviceBackend = mock(ServiceBackend.class);
    servicesManagerClient = mock(ServicesManagerClient.class);

    createSpec = new ServiceCreateSpec();
    createSpec.setName("serviceName");
    createSpec.setType(ServiceType.SWARM);
    createSpec.setVmFlavor("vmFlavor1");
    createSpec.setDiskFlavor("diskFlavor1");
    createSpec.setWorkerCount(50);
    createSpec.setExtendedProperties(ImmutableMap.<String, String>builder()
        .put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1")
        .put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2")
        .put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128")
        .put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP1, "10.1.0.3")
        .put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP2, "10.1.0.4")
        .put(ServicesManagerClient.EXTENDED_PROPERTY_ETCD_IP3, "10.1.0.5")
        .build());

    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, "projectId");
    currentStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, createSpec);
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = spy(new SwarmServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend));
    when(serviceBackend.getServicesManagerClient()).thenReturn(servicesManagerClient);

    serviceDocument = new SwarmServiceCreateTaskState();
    serviceDocument.taskState = new SwarmServiceCreateTaskState.TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    serviceDocument.taskState.subStage = SwarmServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD;
    remoteTaskLink = "http://servicesmanager" + ServiceUriPaths.SWARM_SERVICE_CREATE_TASK
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
      when(servicesManagerClient.createSwarmService(any(String.class), any(ServiceCreateSpec.class)))
          .thenReturn(serviceDocument);

      command.execute();
      verify(servicesManagerClient, times(1)).createSwarmService(any(String.class), any(ServiceCreateSpec.class));
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
    }

    @Test
    public void testFailure() throws Throwable {
      when(servicesManagerClient.createSwarmService(any(String.class), any(ServiceCreateSpec.class)))
          .thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(servicesManagerClient, times(1)).createSwarmService(any(String.class), any(ServiceCreateSpec.class));
    }

    @Test
    public void testMissingProjectId() throws Throwable {
      currentStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, null);
      command = new SwarmServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend);

      try {
        command.execute();
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("project-id is not defined in TransientResource"));
      }
    }

    @Test
    public void testMissingCreateSpec() throws Throwable {
      currentStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, null);
      command = new SwarmServiceCreateStepCmd(taskCommand, stepBackend, currentStep, serviceBackend);

      try {
        command.execute();
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("create-spec is not defined in TransientResource"));
      }
    }
  }
}
