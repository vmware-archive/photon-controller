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

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.StatsStoreType;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link AddCloudHostWorkflowService} class.
 */
public class AddCloudHostWorkflowServiceTest {

  private AddCloudHostWorkflowService addCloudHostWorkflowService;
  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object which is sufficient to create a new
   * AddCloudHostTaskService instance.
   */
  private AddCloudHostWorkflowService.State buildValidStartState(TaskState.TaskStage stage) {
    AddCloudHostWorkflowService.State startState = new AddCloudHostWorkflowService.State();
    startState.taskState = new TaskState();
    startState.taskState.stage = stage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.hostServiceLink = "hostServiceLink1";

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * AddCloudHostTaskService instance.
   */
  private AddCloudHostWorkflowService.State buildValidPatchState(TaskState.TaskStage stage) {
    AddCloudHostWorkflowService.State patchState = new AddCloudHostWorkflowService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    return patchState;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      addCloudHostWorkflowService = new AddCloudHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      addCloudHostWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(addCloudHostWorkflowService.getOptions(), is(expected));
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
      addCloudHostWorkflowService = new AddCloudHostWorkflowService();
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
    public void testValidStartStages(TaskState.TaskStage startStage) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "StartStagesNotChanged")
    public void testStartStagesNotChanged(TaskState.TaskStage startStage) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      AddCloudHostWorkflowService.State savedState = testHost.getServiceState(AddCloudHostWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(startStage));
    }

    @DataProvider(name = "StartStagesNotChanged")
    public Object[][] getStartStagesNotChanged() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AddCloudHostWorkflowService.State.class,
              NotNull.class));
    }

    @Test(dataProvider = "TaskPollDelayValues")
    public void testTaskPollDelayValues(Integer taskPollDelay, Integer expectedValue) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      AddCloudHostWorkflowService.State savedState = testHost.getServiceState(AddCloudHostWorkflowService.State.class);
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
      AddCloudHostWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      try {
        testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
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
      addCloudHostWorkflowService = new AddCloudHostWorkflowService();
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
    public void testValidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddCloudHostWorkflowService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));
      AddCloudHostWorkflowService.State savedState = testHost.getServiceState(AddCloudHostWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "InvalidStageUpdates")
    public void testInvalidStageUpdates(
        TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddCloudHostWorkflowService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOperation);
        fail("Stage transition from " + startStage.toString() + " to " + patchStage.toString() + " should fail");
      } catch (XenonRuntimeException e) {
        // N.B. An assertion can be added here if an error message is added to
        //      the checkState calls in validatePatch.
      }
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithInvalidValue")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      AddCloudHostWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOperation = testHost.startServiceSynchronously(addCloudHostWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AddCloudHostWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED);
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
              AddCloudHostWorkflowService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the add cloud host task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private AddCloudHostWorkflowService.State startState;
    private DeployerContext deployerContext;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private File vibSourceFile;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      vibDirectory.mkdirs();
      vibSourceFile = TestHelper.createSourceFile(null, vibDirectory);

      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
    }

    private void createTestEnvironment() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      agentControlClientFactory = null;
      hostClientFactory = null;
      httpFileServiceClientFactory = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testSuccess() throws Throwable {
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerContext, ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      createTestEnvironment();

      startState.hostServiceLink =
          TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()))
              .documentSelfLink;

      createDeploymentServiceEntity();

      AddCloudHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AddCloudHostWorkflowFactoryService.SELF_LINK,
              startState,
              AddCloudHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testMissingDeploymentFailure() throws Throwable {
      createTestEnvironment();

      AddCloudHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AddCloudHostWorkflowFactoryService.SELF_LINK,
              startState,
              AddCloudHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));

    }

    private void createDeploymentServiceEntity() throws Throwable {
      DeploymentService.State state = new DeploymentService.State();
      state.imageDataStoreNames = Collections.singleton("datastore");
      state.imageDataStoreUsedForVMs = true;
      state.ntpEndpoint = "ntpEndpoint";
      state.oAuthEnabled = false;
      state.oAuthTenantName = "oAuthTenantName";
      state.oAuthUserName = "oAuthUserName";
      state.oAuthPassword = "oAuthPassword";
      state.oAuthServerAddress = "oAuthServerAddress";
      state.oAuthServerPort = 433;
      state.syslogEndpoint = "syslogEndpoint";
      state.statsEnabled = false;
      state.statsStoreEndpoint = "statsStoreEndpoint";
      state.statsStorePort = 8081;
      state.statsStoreType = StatsStoreType.GRAPHITE;
      state.state = DeploymentState.READY;

      cloudStoreMachine.callServiceSynchronously(
          DeploymentServiceFactory.SELF_LINK,
          state,
          DeploymentService.State.class);
    }
  }
}
