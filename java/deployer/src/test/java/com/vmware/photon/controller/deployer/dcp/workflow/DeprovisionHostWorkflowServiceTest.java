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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskService;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import javax.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link DeprovisionHostWorkflowService} class.
 */
public class DeprovisionHostWorkflowServiceTest {

  public static DeprovisionHostWorkflowService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable DeprovisionHostWorkflowService.TaskState.SubStage startSubStage) {
    DeprovisionHostWorkflowService.State startState = new DeprovisionHostWorkflowService.State();
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new DeprovisionHostWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;
    }

    return startState;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private DeprovisionHostWorkflowService deprovisionHostWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      deprovisionHostWorkflowService = new DeprovisionHostWorkflowService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(deprovisionHostWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private DeprovisionHostWorkflowService deprovisionHostWorkflowService;
    private boolean serviceCreated = false;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      deprovisionHostWorkflowService = new DeprovisionHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
        serviceCreated = false;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      DeprovisionHostWorkflowService.State serviceState =
          testHost.getServiceState(DeprovisionHostWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
      assertThat(serviceState.taskState.stage, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.PUT_HOST_TO_DEPROVISION_MODE},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "AutoProgressedStartStages")
    public void testAutoProgressedStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      DeprovisionHostWorkflowService.State serviceState =
          testHost.getServiceState(DeprovisionHostWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(DeprovisionHostWorkflowService.TaskState.SubStage.PUT_HOST_TO_DEPROVISION_MODE));
    }

    @DataProvider(name = "AutoProgressedStartStages")
    public Object[][] getAutoProgressedStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.PUT_HOST_TO_DEPROVISION_MODE},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      DeprovisionHostWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = null;
      startService(startState);

      DeprovisionHostWorkflowService.State serviceState =
          testHost.getServiceState(DeprovisionHostWorkflowService.State.class);

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

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testFailureRequiredFieldMissing(String fieldName) throws Throwable {
      DeprovisionHostWorkflowService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeprovisionHostWorkflowService.State.class, NotNull.class));
    }

    private void startService(DeprovisionHostWorkflowService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(deprovisionHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private DeprovisionHostWorkflowService deprovisionHostWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      deprovisionHostWorkflowService = new DeprovisionHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(
        TaskState.TaskStage startStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      DeprovisionHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(deprovisionHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeprovisionHostWorkflowService.State patchState =
          DeprovisionHostWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      DeprovisionHostWorkflowService.State serviceState =
          testHost.getServiceState(DeprovisionHostWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable DeprovisionHostWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      DeprovisionHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(deprovisionHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeprovisionHostWorkflowService.State patchState =
          DeprovisionHostWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeprovisionHostWorkflowService.TaskState.SubStage.DELETE_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }
  }

  /**
   * This class implements end-to-end tests for the workflow.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");

    private DeployerContext deployerContext;
    private DeprovisionHostWorkflowService.State startState;
    private ListeningExecutorService listeningExecutorService;
    private HostClientFactory hostClientFactory;
    private ApiClientFactory apiClientFactory;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private DeploymentService.State deploymentServiceState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      FileUtils.deleteDirectory(storageDirectory);

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      apiClientFactory = mock(ApiClientFactory.class);
      deploymentServiceState = TestHelper.createDeploymentService(cloudStoreMachine);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
      hostClientFactory = mock(HostClientFactory.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);

      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {
      MockHelper.mockCreateScriptFile(deployerContext, DeleteAgentTaskService.SCRIPT_NAME, true);
      startTestEnvironment(hostCount, HostState.READY);

      DeprovisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              DeprovisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              DeprovisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndFailureDeleteAgentFailure(Integer hostCount) throws Throwable {
      MockHelper.mockCreateScriptFile(deployerContext, DeleteAgentTaskService.SCRIPT_NAME, false);
      startTestEnvironment(hostCount, HostState.READY);

      DeprovisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              DeprovisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              DeprovisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Deleting the agent on host hostAddress failed with exit code 1"));
    }

    @Test(dataProvider = "HostCounts")
    public void testSuccessForUnprovisonedHost(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount, HostState.CREATING);

      DeprovisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              DeprovisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              DeprovisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @DataProvider(name = "HostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }

    private void startTestEnvironment(Integer hostCount, HostState hostState) throws Throwable {
      ZookeeperClientFactory zkFactory = mock(ZookeeperClientFactory.class);
      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .listeningExecutorService(listeningExecutorService)
          .apiClientFactory(apiClientFactory)
          .hostClientFactory(hostClientFactory)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .zookeeperServersetBuilderFactory(zkFactory)
          .hostCount(hostCount)
          .build();

      HostService.State hostService = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()), hostState);
      startState.hostServiceLink = hostService.documentSelfLink;

      ApiClient apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();

      MockHelper.mockHostClient(hostClientFactory, true);

      ZookeeperClient zkBuilder = mock(ZookeeperClient.class);
      doReturn(zkBuilder).when(zkFactory).create();
      doAnswer(new Answer<Object>() {
                 public Object answer(InvocationOnMock invocation) {
                   ((FutureCallback) invocation.getArguments()[2]).onSuccess(null);
                   return null;
                 }
               }
      ).when(zkBuilder).removeServer(anyString(), anyInt(), anyObject());

      // Update deploymentService with the map
      HashMap<Integer, String> zkMap = new HashMap<>();
      zkMap.put(1, hostService.hostAddress);
      DeploymentService.State deploymentState = new DeploymentService.State();
      deploymentState.zookeeperIdToIpMap = zkMap;
      cloudStoreMachine.sendPatchAndWait(deploymentServiceState.documentSelfLink, deploymentState);
    }
  }
}
