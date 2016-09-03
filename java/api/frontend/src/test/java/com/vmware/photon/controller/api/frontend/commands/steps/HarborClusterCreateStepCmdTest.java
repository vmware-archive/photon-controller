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

import com.vmware.photon.controller.api.frontend.backends.ClusterBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ClusterManagerClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.clustermanager.servicedocuments.HarborClusterCreateTask;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
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
 * Tests {@link HarborClusterCreateStepCmd}.
 */
public class HarborClusterCreateStepCmdTest extends PowerMockTestCase {

  HarborClusterCreateStepCmd command;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private ClusterBackend clusterBackend;
  private ClusterManagerClient clusterManagerClient;

  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;
  private ClusterCreateSpec createSpec;
  private HarborClusterCreateTask serviceDocument;
  private String remoteTaskLink;

  private void setUpCommon() {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    clusterBackend = mock(ClusterBackend.class);
    clusterManagerClient = mock(ClusterManagerClient.class);

    createSpec = new ClusterCreateSpec();
    createSpec.setName("clusterName");
    createSpec.setType(ClusterType.HARBOR);
    createSpec.setVmFlavor("vmFlavor1");
    createSpec.setDiskFlavor("diskFlavor1");
    createSpec.setWorkerCount(0);

    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.createOrUpdateTransientResource(HarborClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, "projectId");
    currentStep.createOrUpdateTransientResource(HarborClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, createSpec);
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = spy(new HarborClusterCreateStepCmd(taskCommand, stepBackend, currentStep, clusterBackend));
    when(clusterBackend.getClusterManagerClient()).thenReturn(clusterManagerClient);

    serviceDocument = new HarborClusterCreateTask();
    serviceDocument.taskState = new HarborClusterCreateTask.TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    serviceDocument.taskState.subStage = HarborClusterCreateTask.TaskState.SubStage.SETUP_HARBOR;
    remoteTaskLink = "http://clustermanager" + ServiceUriPaths.HARBOR_CLUSTER_CREATE_TASK_SERVICE
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
      when(clusterManagerClient.createHarborCluster(any(String.class), any(ClusterCreateSpec.class)))
          .thenReturn(serviceDocument);

      command.execute();
      verify(clusterManagerClient, times(1)).createHarborCluster(any(String.class), any(ClusterCreateSpec.class));
      assertEquals(nextStep.getTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
    }

    @Test
    public void testFailure() throws Throwable {
      when(clusterManagerClient.createHarborCluster(any(String.class), any(ClusterCreateSpec.class)))
          .thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(clusterManagerClient, times(1)).createHarborCluster(any(String.class), any(ClusterCreateSpec.class));
    }

    @Test
    public void testMissingProjectId() throws Throwable {
      currentStep.createOrUpdateTransientResource(HarborClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, null);
      command = new HarborClusterCreateStepCmd(taskCommand, stepBackend, currentStep, clusterBackend);

      try {
        command.execute();
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("project-id is not defined in TransientResource"));
      }
    }

    @Test
    public void testMissingCreateSpec() throws Throwable {
      currentStep.createOrUpdateTransientResource(HarborClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, null);
      command = new HarborClusterCreateStepCmd(taskCommand, stepBackend, currentStep, clusterBackend);

      try {
        command.execute();
        fail("should have failed with NullPointerException");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("create-spec is not defined in TransientResource"));
      }
    }
  }
}
