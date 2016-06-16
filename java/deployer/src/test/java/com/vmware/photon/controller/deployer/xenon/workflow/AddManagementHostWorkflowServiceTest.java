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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CreateVmSpecLayoutTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link AddManagementHostWorkflowService} class.
 */
public class AddManagementHostWorkflowServiceTest {

  private AddManagementHostWorkflowService addManagementHostWorkflowService;
  private TestHost testHost;

  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object which is sufficient to create a new
   * AddManagementHostTaskService instance.
   */
  private AddManagementHostWorkflowService.State buildValidStartState(
      TaskState.TaskStage stage,
      @Nullable AddManagementHostWorkflowService.TaskState.SubStage startSubStage) {
    AddManagementHostWorkflowService.State startState = new AddManagementHostWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.hostServiceLink = "hostServiceLink1";
    startState.isNewDeployment = false;
    startState.deploymentServiceLink = "deploymentServiceLink";

    if (null != stage) {
      startState.taskState = new AddManagementHostWorkflowService.TaskState();
      startState.taskState.stage = stage;
      startState.taskState.subStage = startSubStage;

      if (TaskState.TaskStage.CREATED != stage) {
        startState.taskSubStates = new ArrayList<>(AddManagementHostWorkflowService.TaskState.SubStage.values().length);
        for (AddManagementHostWorkflowService.TaskState.SubStage s :
            AddManagementHostWorkflowService.TaskState.SubStage.values()) {
          if (null == startSubStage || startSubStage.ordinal() > s.ordinal()) {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.FINISHED);
          } else if (startSubStage.ordinal() == s.ordinal()) {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.STARTED);
          } else {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.CREATED);
          }
        }
      }
    }
    return startState;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      addManagementHostWorkflowService = new AddManagementHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      addManagementHostWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);

      assertThat(addManagementHostWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      addManagementHostWorkflowService = new AddManagementHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStagestestValidStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "AutoProgressedStartStages")
    public void testAutoProgressedStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      AddManagementHostWorkflowService.State serviceState =
          testHost.getServiceState(AddManagementHostWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT));
    }

    @DataProvider(name = "AutoProgressedStartStages")
    public Object[][] getAutoProgressedStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = null;
      testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);

      AddManagementHostWorkflowService.State serviceState =
          testHost.getServiceState(AddManagementHostWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AddManagementHostWorkflowService.State.class,
              NotNull.class));
    }

    @Test(dataProvider = "TaskPollDelayValues")
    public void testTaskPollDelayValues(Integer taskPollDelay, Integer expectedValue) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(null, null);
      startState.taskPollDelay = taskPollDelay;
      Operation startOperation = testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      AddManagementHostWorkflowService.State savedState = testHost.getServiceState(
          AddManagementHostWorkflowService.State.class);
      assertThat(savedState.taskPollDelay, is(expectedValue));
    }

    @DataProvider(name = "TaskPollDelayValues")
    public Object[][] getTaskPollDelayValues() {
      DeployerServiceGroup deployerServiceGroup =
          (DeployerServiceGroup) (((PhotonControllerXenonHost) testHost).getDeployer());
      return new Object[][]{
          {null, new Integer(deployerServiceGroup.getDeployerContext().getTaskPollDelay())},
          {new Integer(500), new Integer(500)},
      };
    }

    @Test(dataProvider = "InvalidTaskPollDelayValues")
    public void testFailureInvalidTaskPollDelayValues(int taskPollDelay) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(null, null);
      startState.taskPollDelay = taskPollDelay;
      try {
        testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
        fail("Service start should throw in response to illegal taskPollDelay values");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("taskPollDelay must be greater than zero"));
      }
    }

    @DataProvider(name = "InvalidTaskPollDelayValues")
    public Object[][] getInvalidTaskPollDelayValues() {
      return new Object[][]{
          {0},
          {-10},
      };
    }

    @Test(dataProvider = "InvalidTaskSubStates", expectedExceptions = XenonRuntimeException.class)
    public void testFailureInvalidSubStateList(List<TaskState.TaskStage> taskSubStates) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.STARTED,
          AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT);
      startState.taskSubStates = taskSubStates;
      testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
    }

    @DataProvider(name = "InvalidTaskSubStates")
    public Object[][] getInvalidTaskSubStates() {
      return new Object[][]{
          {Collections.<TaskState.TaskStage>emptyList()},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.STARTED))},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED,
              TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED))},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED,
              TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED))},
      };
    }

  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      addManagementHostWorkflowService = new AddManagementHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddManagementHostWorkflowService.State patchState = AddManagementHostWorkflowService.buildPatch(patchStage,
          patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));
      AddManagementHostWorkflowService.State savedState = testHost.getServiceState(
          AddManagementHostWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.CANCELLED, null},

      };
    }

    @Test(dataProvider = "InvalidStageUpdates", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageUpdates(
        TaskState.TaskStage startStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable AddManagementHostWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddManagementHostWorkflowService.State patchState = AddManagementHostWorkflowService.buildPatch(patchStage,
          patchSubStage, null);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER},

          {TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},

          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.RECONFIGURE_ZOOKEEPER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AddManagementHostWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithInvalidValue")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      AddManagementHostWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(addManagementHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddManagementHostWorkflowService.State patchState = AddManagementHostWorkflowService.buildPatch(
          TaskState.TaskStage.STARTED,
          AddManagementHostWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "fieldNamesWithInvalidValue")
    public Object[][] getFieldNamesWithInvalidValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AddManagementHostWorkflowService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the add management host task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private DeployerConfig deployerConfig;
    private ContainersConfig containersConfig;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private AuthHelperFactory authHelperFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;

    private AddManagementHostWorkflowService.State startState;
    private TestEnvironment localDeployer;
    private TestEnvironment remoteDeployer;
    private AuthClientHandler.ImplicitClient implicitClient;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment localStore;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment remoteStore;
    private SystemConfig systemConfig;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      vibDirectory.mkdirs();
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      TestHelper.createSourceFile("esxcloud-" + UUID.randomUUID().toString() + ".vib", vibDirectory);
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "user-data.template"));
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "meta-data.template"));

      deployerConfig = spy(ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()));
      TestHelper.setContainersConfig(deployerConfig);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
      startState.childPollInterval = 10;
      implicitClient = new AuthClientHandler.ImplicitClient("client_id", "http://login", "http://logout");
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      containersConfig = deployerConfig.getContainersConfig();
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
    }

    private void createCloudStore() throws Throwable {

      localStore = new com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .build();

      remoteStore = new com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .build();

      this.systemConfig = spy(SystemConfig.createInstance(localStore.getHosts()[0]));
    }

    private void createTestEnvironment(int remoteNodeCount) throws Throwable {
      String quorum = deployerConfig.getZookeeper().getQuorum();
      deployerConfig.getDeployerContext().setZookeeperQuorum(quorum);

      DeployerContext context = spy(deployerConfig.getDeployerContext());
      ZookeeperClientFactory zkFactory = mock(ZookeeperClientFactory.class);

      localDeployer = new TestEnvironment.Builder()
          .authHelperFactory(authHelperFactory)
          .containersConfig(containersConfig)
          .deployerContext(context)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(localStore.getServerSet())
          .zookeeperServersetBuilderFactory(zkFactory)
          .hostCount(1)
          .build();

      remoteDeployer = new TestEnvironment.Builder()
          .authHelperFactory(authHelperFactory)
          .containersConfig(containersConfig)
          .deployerContext(context)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(remoteStore.getServerSet())
          .zookeeperServersetBuilderFactory(zkFactory)
          .hostCount(remoteNodeCount)
          .build();

      ZookeeperClient zkBuilder = mock(ZookeeperClient.class);
      doReturn(zkBuilder).when(zkFactory).create();
      doReturn(Collections.singleton(
          new InetSocketAddress("127.0.0.1", localDeployer.getHosts()[0].getState().httpPort)))
          .when(zkBuilder).getServers(eq(quorum), eq("deployer"));
      doReturn(Collections.singleton(
          new InetSocketAddress("127.0.0.1", remoteDeployer.getHosts()[0].getState().httpPort)))
          .when(zkBuilder)
          .getServers(Matchers.startsWith("0.0.0.0:2181"), eq("deployer"));
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1", localStore.getHosts()[0].getState().httpPort)))
          .when(zkBuilder).getServers(eq(quorum), eq("cloudstore"));
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1", remoteStore.getHosts()[0].getState().httpPort)))
          .when(zkBuilder)
          .getServers(Matchers.startsWith("0.0.0.0:2181"), eq("cloudstore"));
      doAnswer(new Answer<Object>() {
                 @Override
                 public Object answer(InvocationOnMock invocation) {
                   ((FutureCallback) invocation.getArguments()[4]).onSuccess(null);
                   return null;
                 }
               }
      ).when(zkBuilder).addServer(anyString(), anyString(), anyString(), anyInt(), anyObject());

      InetSocketAddress address = remoteStore.getServerSet().getServers().iterator().next();
      doReturn(Collections.singleton(address))
          .when(zkBuilder).getServers(anyString(), eq(Constants.HOUSEKEEPER_SERVICE_NAME));
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != localDeployer) {
        localDeployer.stop();
        localDeployer = null;
      }

      if (null != remoteDeployer) {
        remoteDeployer.stop();
        remoteDeployer = null;
      }

      if (null != localStore) {
        localStore.stop();
        localStore = null;
      }

      if (null != remoteStore) {
        remoteStore.stop();
        remoteStore = null;
      }

      authHelperFactory = null;
      containersConfig = null;
      dockerProvisionerFactory = null;
      apiClientFactory = null;
      healthCheckHelperFactory = null;
      agentControlClientFactory = null;
      hostClientFactory = null;
      httpFileServiceClientFactory = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);
    }

    @DataProvider(name = "HostWithTagWithAuthInfo")
    public Object[][] getHostsWithAuthInfo() {
      return new Object[][]{
          {true, true, 4},
          {true, false, 5},
          {false, true, 4},
          {false, false, 5},
      };
    }

    @Test(dataProvider = "HostWithTagWithAuthInfo")
    public void testSuccess(Boolean isOnlyMgmtHost, Boolean isAuthEnabled, int hostCount) throws Throwable {
      int initialHostNum = isAuthEnabled ? 2 : 1;
      createCloudStore();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateManagementVmTaskService.SCRIPT_NAME,
          true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, isAuthEnabled);
      for (int i = 0; i < initialHostNum; ++i) {
        createHostService(Collections.singleton(UsageTag.MGMT.name()), localStore, null);
      }

      DeploymentWorkflowService.State deploymentState = DeploymentWorkflowServiceTest.buildValidStartState(null, null);
      deploymentState.deploymentServiceLink = startState.deploymentServiceLink;
      deploymentState.controlFlags = null;
      deploymentState.childPollInterval = 10;
      DeploymentWorkflowService.State deploymentWorkflowState =
          localDeployer.callServiceAndWaitForState(
              DeploymentWorkflowFactoryService.SELF_LINK,
              deploymentState,
              DeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(deploymentWorkflowState.taskState);

      DeploymentService.State deploymentServiceRemoteOriginal = (queryForServiceStates(DeploymentService.State.class,
          remoteStore)).get(0);

      assertThat(deploymentServiceRemoteOriginal.zookeeperIdToIpMap.size() == 1, is(true));
      for (int i = initialHostNum + 1; i <= hostCount; i++) {
        if (isOnlyMgmtHost) {
          HostService.State mgmtHost = createHostService(Collections.singleton(UsageTag.MGMT.name()), remoteStore,
              "0.0.0." + i);
          startState.hostServiceLink = mgmtHost.documentSelfLink;
        } else {
          HostService.State mgmtHost = createHostService(new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag
              .MGMT.name())), remoteStore, "0.0.0." + i);
          startState.hostServiceLink = mgmtHost.documentSelfLink;
        }

        AddManagementHostWorkflowService.State finalState =
            remoteDeployer.callServiceAndWaitForState(
                AddManagementHostWorkflowFactoryService.SELF_LINK,
                startState,
                AddManagementHostWorkflowService.State.class,
                (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

        TestHelper.assertTaskStateFinished(finalState.taskState);

        DeploymentService.State deploymentServiceRemote = (queryForServiceStates(DeploymentService.State.class,
            remoteStore)).get(0);

        assertThat(deploymentServiceRemote.zookeeperIdToIpMap.size(), is(i - (initialHostNum - 1)));
        verifyZookeeperQuorumChange(deploymentServiceRemoteOriginal, deploymentServiceRemote, i - initialHostNum);
        verifyVmServiceStates(i);
      }

      verifyContainerTemplateServiceStates(isAuthEnabled);
      verifyContainerServiceStates(startState.hostServiceLink);
    }

    private HostService.State createHostService(Set<String> usageTags, MultiHostEnvironment<?> machine, String
        bindAddress) throws Throwable {
      HostService.State hostStartState = TestHelper.getHostServiceStartState(usageTags, HostState.CREATING);
      if (usageTags.contains(UsageTag.MGMT.name())) {
        PhotonControllerXenonHost remoteHost = remoteDeployer.getHosts()[0];
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP,
            bindAddress != null ? bindAddress : remoteHost.getState().bindAddress);
        hostStartState.hostAddress = bindAddress != null ? bindAddress : remoteHost.getState().bindAddress;
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_DEPLOYER_XENON_PORT,
            Integer.toString(remoteHost.getPort()));
      }
      return TestHelper.createHostService(machine, hostStartState);
    }

    private String createDeploymentServiceLink(
        com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStore,
        boolean isAuthEnabled)
        throws Throwable {
      DeploymentService.State deploymentService = TestHelper.createDeploymentService(cloudStore,
          isAuthEnabled, false);
      return deploymentService.documentSelfLink;
    }

    @DataProvider(name = "AuthEnabled")
    public Object[][] getAuthEnabled() {
      return new Object[][]{
          {Boolean.TRUE},
          {Boolean.FALSE},
      };
    }

    @Test(dataProvider = "AuthEnabled")
    public void testProvisionManagementHostFailure(Boolean authEnabled) throws Throwable {
      createCloudStore();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, false);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, false);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateManagementVmTaskService.SCRIPT_NAME,
          true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, authEnabled);

      AddManagementHostWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              AddManagementHostWorkflowFactoryService.SELF_LINK,
              startState,
              AddManagementHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "AuthEnabled")
    public void testCreateManagementPlaneFailure(Boolean authEnabled) throws Throwable {
      createCloudStore();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, false);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateManagementVmTaskService.SCRIPT_NAME,
          true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, authEnabled);

      AddManagementHostWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              AddManagementHostWorkflowFactoryService.SELF_LINK,
              startState,
              AddManagementHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testAuthClientRegistrationFailure() throws Throwable {
      createCloudStore();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateManagementVmTaskService.SCRIPT_NAME,
          true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, false);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, true);

      AddManagementHostWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              AddManagementHostWorkflowFactoryService.SELF_LINK,
              startState,
              AddManagementHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void verifyVmServiceStates(int expectedVmEntityNumber) throws Throwable {
      List<VmService.State> states = queryForServiceStates(VmService.State.class, remoteDeployer);

      // The number of VmService entities that the workflow creates should match
      // the sum of the number of MGMT only hosts and the number of MIXED host.
      assertThat(states.size(), is(expectedVmEntityNumber));

      Set<String> hostServiceLinks = new HashSet<>();
      for (VmService.State state : states) {
        assertThat(state.vmId, is("CREATE_VM_ENTITY_ID"));
        assertThat(state.name, startsWith(CreateVmSpecLayoutTaskService.DOCKER_VM_PREFIX));

        HostService.State hostState = remoteStore.getServiceState(state.hostServiceLink, HostService.State.class);
        assertThat(state.ipAddress,
            is(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP)));

        hostServiceLinks.add(state.hostServiceLink);
      }

      // It should be a one-to-one mapping between VmService entity and HostService entity.
      assertThat(hostServiceLinks.size(), is(expectedVmEntityNumber));
    }

    private void verifyContainerTemplateServiceStates(Boolean isAuthEnabled) throws Throwable {
      List<ContainerTemplateService.State> states = queryForServiceStates(ContainerTemplateService.State.class,
          remoteDeployer);

      // The number of ContainerTemplateService entities that the workflow creates should match
      // the number of the container specs in the config.
      int expectedContainerTemplateEntitynumber = containersConfig.getContainerSpecs().size();
      if (!isAuthEnabled) {
        // if auth is not enabled we will not deploy LightWave
        expectedContainerTemplateEntitynumber -= 1;
      }
      assertThat(states.size(), is(expectedContainerTemplateEntitynumber));

      Map<String, ContainerTemplateService.State> containerTemplateMap = new HashMap<>();
      for (ContainerTemplateService.State state : states) {
        containerTemplateMap.put(state.name, state);
      }

      List<ContainerTemplateService.State> containerTemplateStates = new ArrayList<>();
      for (Map.Entry<String, ContainersConfig.Spec> entry : containersConfig
          .getContainerSpecs().entrySet()) {
        // For each spec, we create one and only one container template entity.
        if (!isAuthEnabled &&
            entry.getValue().getType().equals(ContainersConfig.ContainerType.Lightwave.name())) {
          // if auth is disabled we do not generate the Lightwave container template.
          assertThat(containerTemplateMap.containsKey(entry.getKey()), is(false));
          continue;
        } else {
          assertThat(containerTemplateMap.containsKey(entry.getKey()), is(true));
        }

        ContainersConfig.Spec spec = entry.getValue();
        ContainerTemplateService.State finalState = containerTemplateMap.get(entry.getKey());
        containerTemplateStates.add(finalState);

        assertThat(finalState.name, is(spec.getType()));
        assertThat(finalState.cpuCount, is(spec.getCpuCount()));
        assertThat(finalState.memoryMb, is(spec.getMemoryMb()));
        assertThat(finalState.diskGb, is(spec.getDiskGb()));
        assertThat(finalState.isReplicated, is(spec.getIsReplicated()));
        assertThat(finalState.containerImage, is(spec.getContainerImage()));
      }
    }

    private void verifyContainerServiceStates(String hostServiceLink) throws Throwable {
      List<VmService.State> vmStates = queryForServiceStates(VmService.State.class, remoteDeployer);
      List<ContainerTemplateService.State> containerTemplateStates =
          queryForServiceStates(ContainerTemplateService.State.class, remoteDeployer);

      for (ContainerTemplateService.State containerTemplateState : containerTemplateStates) {
        int containerStateNumberIfReplicated = 0;

        for (VmService.State vmState : vmStates) {
          QueryTask.Query kindClause = new QueryTask.Query()
              .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
              .setTermMatchValue(Utils.buildKind(ContainerService.State.class));
          QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
              .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
              .setTermMatchValue(vmState.documentSelfLink);
          QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
              .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
              .setTermMatchValue(containerTemplateState.documentSelfLink);

          QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
          querySpecification.query.addBooleanClause(kindClause);
          querySpecification.query.addBooleanClause(vmServiceLinkClause);
          querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);
          QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

          NodeGroupBroadcastResponse queryResponse = remoteDeployer.sendBroadcastQueryAndWait(queryTask);
          Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

          // For each VmService entity and each ContainerTemplate entity, there is one and only one
          // ContainerService entity to link them together, if the container is not replicated.
          if (containerTemplateState.isReplicated) {
            assertThat(documentLinks.size(), is(1));
          } else {
            containerStateNumberIfReplicated += documentLinks.size();
          }

          if (documentLinks.size() > 0) {
            for (String documentLink : documentLinks) {
              ContainerService.State containerState = remoteDeployer.getServiceState(
                  documentLink, ContainerService.State.class);
              if (vmState.hostServiceLink.equals(hostServiceLink)) {
                assertNotNull(containerState.memoryMb);
                assertNotNull(containerState.cpuShares);
              }
              // TODO(ysheng): verify container ID
            }
          }
        }

        // If the container is not replicated, only one VmService entity could be linked to the
        // ContainerTemplateService entity.
        if (!containerTemplateState.isReplicated) {
          assertThat(containerStateNumberIfReplicated, is(1));
        }
      }
    }

    private void verifyZookeeperQuorumChange(DeploymentService.State deploymentServiceOriginal,
                                             DeploymentService.State deploymentServiceAfterAddHost, int hostCount) {
      String[] originalZookeeperQuorum = deploymentServiceOriginal.zookeeperQuorum.split(",");
      String[] newZookeeperQuorum = deploymentServiceAfterAddHost.zookeeperQuorum.split(",");
      assertThat(newZookeeperQuorum.length == originalZookeeperQuorum.length + hostCount, is(true));
    }

    private <T extends ServiceDocument> List<T> queryForServiceStates(Class<T> classType,
                                                                      MultiHostEnvironment<?> multiHostEnvironment)
        throws Throwable {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(classType));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = multiHostEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
      List<T> states = new ArrayList<>();
      for (String documentLink : documentLinks) {
        states.add(multiHostEnvironment.getServiceState(documentLink, classType));
      }

      return states;
    }
  }
}
