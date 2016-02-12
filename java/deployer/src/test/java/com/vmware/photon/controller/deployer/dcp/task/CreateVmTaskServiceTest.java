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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for {@link CreateVmTaskService} class.
 */
public class CreateVmTaskServiceTest {

  private TestHost host;
  private CreateVmTaskService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateVmTaskService.State buildValidStartupState() {
    return buildValidStartupState(
        TaskState.TaskStage.CREATED);
  }

  private CreateVmTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName("vm");
    spec.setFlavor("flavor");

    CreateVmTaskService.State state = new CreateVmTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    state.vmCreateSpec = spec;
    state.projectId = UUID.randomUUID().toString();
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  private CreateVmTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private CreateVmTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    CreateVmTaskService.State state = new CreateVmTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      DeployerContext deployerContext,
      ApiClientFactory apiClientFactory,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .apiClientFactory(apiClientFactory)
        .hostCount(hostCount)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateVmTaskService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CreateVmTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that service instances can be created with specific
     * start states, where the createVmTaskId is not set.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage)
        throws Throwable {

      CreateVmTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVmTaskService.State savedState = host.getServiceState(
          CreateVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.projectId, notNullValue());
      assertThat(savedState.vmCreateSpec, notNullValue());
      assertThat(savedState.queryCreateVmTaskInterval, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state
     * is transitioned to STARTED state as part of start operation handling.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      CreateVmTaskService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVmTaskService.State savedState = host.getServiceState(CreateVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    /**
     * This test verifies that a service instance which is started in the final states
     * is not transitioned to another state as part of start operation handling.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      CreateVmTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVmTaskService.State savedState = host.getServiceState(CreateVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateVmTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][]{
          {"vmCreateSpec"},
          {"projectId"},
      };
    }

    /**
     * This test verifies that the service instance handles the change of queryUploadImageTaskInterval
     * in the start state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testChangeQueryUploadImageTaskInterval() throws Throwable {
      CreateVmTaskService.State startState = buildValidStartupState();
      startState.queryCreateVmTaskInterval = 12345;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVmTaskService.State savedState = host.getServiceState(
          CreateVmTaskService.State.class);
      assertThat(savedState.queryCreateVmTaskInterval, is(12345));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CreateVmTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateVmTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateVmTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateVmTaskService.State savedState = host.getServiceState(CreateVmTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

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

    /**
     * This test verifies that illegal stage transitions fail.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateVmTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateVmTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (XenonRuntimeException e) {
      }
    }

    @DataProvider(name = "illegalStageUpdatesInvalidPatch")
    public Object[][] getIllegalStageUpdatesInvalidPatch() {

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

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateVmTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateVmTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, declaredField.getType().newInstance());

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][]{
          {"vmCreateSpec"},
          {"projectId"}
      };
    }
  }

  /**
   * End-to-end tests for the create VM task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private CreateVmTaskService.State startState;
    private TestEnvironment machine;
    private DeployerContext deployerContext;
    private ApiClientFactory apiClientFactory;
    private ApiClient apiClient;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private Task taskReturnedByCreateVm;
    private Task taskReturnedByGetTask;

    @BeforeMethod
    public void setUpClass() throws Exception {

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();

      apiClient = mock(ApiClient.class);
      projectApi = mock(ProjectApi.class);
      vmApi = mock(VmApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      apiClientFactory = mock(ApiClientFactory.class);
      doReturn(apiClient).when(apiClientFactory).create();
    }

    @BeforeMethod
    public void setUpTest() throws Exception {

      startState = buildValidStartupState();
      startState.controlFlags = 0;
      startState.queryCreateVmTaskInterval = 10;

      taskReturnedByCreateVm = new Task();
      taskReturnedByCreateVm.setId("taskId");
      taskReturnedByCreateVm.setState("STARTED");

      taskReturnedByGetTask = new Task();
      taskReturnedByGetTask.setId("taskId");
      taskReturnedByGetTask.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByCreateVm.setEntity(entity);
      taskReturnedByGetTask.setEntity(entity);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
      taskReturnedByCreateVm = null;
      taskReturnedByGetTask = null;
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the createVm call throws exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureCreateVmThrowsException() throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          throw new RuntimeException("Exception during createVm");
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during createVm"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the getTask call throws exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureGetTaskThrowsException() throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doThrow(new RuntimeException("Exception during getTask"))
          .when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during getTask"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by CreateVm call has ERROR state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureCreateVmReturnsErrorTaskState(final Task task) throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(task);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by GetTask call has ERROR state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureGetTaskReturnsErrorTaskState(final Task task) throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    @DataProvider(name = "errorTasksResponses")
    public Object[][] getErrorTasksResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
      };
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by CreateVm call has unknown state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureCreateVmReturnsInvalidTaskState() throws Throwable {

      taskReturnedByCreateVm.setState("unknown");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by GetTask call has unknown state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureGetTaskReturnsInvalidTaskState() throws Throwable {

      taskReturnedByGetTask.setState("unknown");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, 1);

      CreateVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateVmTaskFactoryService.SELF_LINK,
              startState,
              CreateVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }
  }
}
