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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link BuildContainersConfigurationWorkflowService} class.
 */
public class BuildContainersConfigurationWorkflowServiceTest {

  private BuildContainersConfigurationWorkflowService buildConfigurationWorkflowService;
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
   * BuildContainersConfigurationWorkflowService instance.
   */
  private BuildContainersConfigurationWorkflowService.State buildValidStartState(TaskState.TaskStage stage) {
    BuildContainersConfigurationWorkflowService.State startState = new BuildContainersConfigurationWorkflowService
        .State();
    startState.taskState = new TaskState();
    startState.taskState.stage = stage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "deploymentServiceLink";

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * BuildContainersConfigurationtTaskService instance.
   */
  private BuildContainersConfigurationWorkflowService.State buildValidPatchState(TaskState.TaskStage stage) {
    BuildContainersConfigurationWorkflowService.State patchState = new BuildContainersConfigurationWorkflowService
        .State();
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
      buildConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      buildConfigurationWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(buildConfigurationWorkflowService.getOptions(), is(expected));
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
      buildConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
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
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
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
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      BuildContainersConfigurationWorkflowService.State savedState = testHost.getServiceState
          (BuildContainersConfigurationWorkflowService.State.class);
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

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildContainersConfigurationWorkflowService.State.class,
              NotNull.class));
    }

    @Test(dataProvider = "TaskPollDelayValues")
    public void testTaskPollDelayValues(Integer taskPollDelay, Integer expectedValue) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      BuildContainersConfigurationWorkflowService.State savedState = testHost.getServiceState
          (BuildContainersConfigurationWorkflowService.State.class);
      assertThat(savedState.taskPollDelay, is(expectedValue));
    }

    @DataProvider(name = "TaskPollDelayValues")
    public Object[][] getTaskPollDelayValues() {
      return new Object[][]{
          {null, new Integer(testHost.getDeployerContext().getTaskPollDelay())},
          {new Integer(500), new Integer(500)},
      };
    }

    @Test(dataProvider = "InvalidTaskPollDelayValues")
    public void testFailureInvalidTaskPollDelayValues(int taskPollDelay) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      try {
        testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
        fail("Service start should throw in response to illegal taskPollDelay values");
      } catch (IllegalStateException e) {
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
      buildConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
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
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));
      BuildContainersConfigurationWorkflowService.State savedState = testHost.getServiceState
          (BuildContainersConfigurationWorkflowService.State.class);
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
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOperation);
        fail("Stage transition from " + startStage.toString() + " to " + patchStage.toString() + " should fail");
      } catch (IllegalStateException e) {
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

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "fieldNamesWithInvalidValue")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOperation = testHost.startServiceSynchronously(buildConfigurationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED);
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
              BuildContainersConfigurationWorkflowService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the build containers configuration workflow.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private BuildContainersConfigurationWorkflowService.State startState;
    private DeployerConfig deployerConfig;
    private ListeningExecutorService listeningExecutorService;
    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
    }

    private void createTestEnvironment() throws Throwable {
      machine = new TestEnvironment.Builder()
          .deployerContext(deployerConfig.getDeployerContext())
          .containersConfig(deployerConfig.getContainersConfig())
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    /**
     * This test verifies the success scenario for build containers configuration workflow.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testSuccess() throws Throwable {
      createTestEnvironment();
      createHostEntitiesAndAllocateVmsAndContainers(3, 7);
      createDeploymentServiceDocuments();

      BuildContainersConfigurationWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              BuildContainersConfigurationWorkflowFactoryService.SELF_LINK,
              startState,
              BuildContainersConfigurationWorkflowService.State.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }


    /**
     * This test verifies the failure scenario when the workflow fails to retrieve containers.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testFailureToRetrieveContainers() throws Throwable {
      createTestEnvironment();
      createDeploymentServiceDocuments();

      BuildContainersConfigurationWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              BuildContainersConfigurationWorkflowFactoryService.SELF_LINK,
              startState,
              BuildContainersConfigurationWorkflowService.State.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    /**
     * This method sets up valid service documents which are needed for test.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void createHostEntitiesAndAllocateVmsAndContainers(
        int mgmtCount,
        int cloudCount) throws Throwable {

      for (int i = 0; i < mgmtCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));
      }

      for (int i = 0; i < cloudCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()));
      }

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      workflowStartState.taskPollDelay = 10;

      CreateManagementPlaneLayoutWorkflowService.State serviceState =
          machine.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }

    private void createDeploymentServiceDocuments() throws Throwable {
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
    }
  }
}
