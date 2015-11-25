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

import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.apife.backends.ClusterBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.clients.ClusterManagerClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.xenon.common.TaskState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
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
 * Tests {@link MesosClusterCreateStepCmd}.
 */
public class MesosClusterCreateStepCmdTest extends PowerMockTestCase {

  MesosClusterCreateStepCmd command;

  private StepBackend stepBackend;
  private TaskCommand taskCommand;
  private ClusterBackend clusterBackend;
  private ClusterManagerClient clusterManagerClient;

  private TaskEntity taskEntity;
  private StepEntity currentStep;
  private StepEntity nextStep;
  private ClusterCreateSpec createSpec;
  private MesosClusterCreateTask serviceDocument;
  private String remoteTaskLink;

  private void setUpCommon() {
    stepBackend = mock(StepBackend.class);
    taskCommand = mock(TaskCommand.class);
    clusterBackend = mock(ClusterBackend.class);
    clusterManagerClient = mock(ClusterManagerClient.class);

    createSpec = new ClusterCreateSpec();
    createSpec.setName("clusterName");
    createSpec.setType(ClusterType.MESOS);
    createSpec.setVmFlavor("vmFlavor1");
    createSpec.setDiskFlavor("diskFlavor1");
    createSpec.setSlaveCount(50);
    createSpec.setExtendedProperties(ImmutableMap.<String, String>builder()
        .put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "10.1.0.1")
        .put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "10.1.0.2")
        .put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "255.255.255.128")
        .put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP1, "10.1.0.3")
        .put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP2, "10.1.0.4")
        .put(ClusterManagerClient.EXTENDED_PROPERTY_ZOOKEEPER_IP3, "10.1.0.5")
        .build());

    currentStep = new StepEntity();
    currentStep.setId("id");
    currentStep.createOrUpdateTransientResource(MesosClusterCreateStepCmd.PROJECT_ID_RESOURCE_KEY, "projectId");
    currentStep.createOrUpdateTransientResource(MesosClusterCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, createSpec);
    nextStep = new StepEntity();

    taskEntity = new TaskEntity();
    taskEntity.setSteps(ImmutableList.of(currentStep, nextStep));
    when(taskCommand.getTask()).thenReturn(taskEntity);

    command = spy(new MesosClusterCreateStepCmd(taskCommand, stepBackend, currentStep, clusterBackend));
    when(clusterBackend.getClusterManagerClient()).thenReturn(clusterManagerClient);

    serviceDocument = new MesosClusterCreateTask();
    serviceDocument.taskState = new MesosClusterCreateTask.TaskState();
    serviceDocument.taskState.stage = TaskState.TaskStage.STARTED;
    serviceDocument.taskState.subStage = MesosClusterCreateTask.TaskState.SubStage.ALLOCATE_RESOURCES;
    remoteTaskLink = "http://clustermanager" + ServiceUriPaths.MESOS_CLUSTER_CREATE_TASK_SERVICE
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
      when(clusterManagerClient.createMesosCluster(any(String.class), any(ClusterCreateSpec.class)))
          .thenReturn(serviceDocument);

      command.execute();
      verify(clusterManagerClient, times(1)).createMesosCluster(any(String.class), any(ClusterCreateSpec.class));
      assertEquals(nextStep.getTransientResource(ClusterTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY),
          remoteTaskLink);
    }

    @Test
    public void testFailure() throws Throwable {
      when(clusterManagerClient.createMesosCluster(any(String.class), any(ClusterCreateSpec.class)))
          .thenThrow(new RuntimeException("testFailure"));

      try {
        command.execute();
        fail("should have failed with RuntimeException.");
      } catch (Throwable e) {
        assertTrue(e.getMessage().contains("testFailure"));
      }
      verify(clusterManagerClient, times(1)).createMesosCluster(any(String.class), any(ClusterCreateSpec.class));
    }
  }
}
