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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link UploadImageTaskService} class.
 */
public class UploadImageTaskServiceTest {

  private static final String configFilePath = "/config.yml";

  private TestHost host;
  private UploadImageTaskService service;

  @Test
  private void dummy() {
  }

  private UploadImageTaskService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED);
  }

  private UploadImageTaskService.State buildValidStartupState(TaskState.TaskStage stage) {
    UploadImageTaskService.State state = new UploadImageTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.imageFile = "imageFile";
    state.imageName = "imageName";
    state.imageReplicationType = ImageReplicationType.ON_DEMAND;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private UploadImageTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private UploadImageTaskService.State buildValidPatchState(TaskState.TaskStage stage) {
    UploadImageTaskService.State state = new UploadImageTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    return state;
  }

  public TestEnvironment createTestEnvironment(
          DeployerContext deployerContext,
          ListeningExecutorService listeningExecutorService,
          ApiClientFactory apiClientFactory,
          int hostCount)
          throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .apiClientFactory(apiClientFactory)
        .listeningExecutorService(listeningExecutorService)
        .hostCount(hostCount)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new UploadImageTaskService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

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
      service = new UploadImageTaskService();
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
     * start states.
     *
     * @param stage    Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(
          UploadImageTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.uniqueId, notNullValue());
      assertThat(savedState.imageFile, notNullValue());
      assertThat(savedState.imageName, notNullValue());
      assertThat(savedState.queryUploadImageTaskInterval, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStatesWithoutUploadImageId() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state
     * are transitioned to STARTED:UNTAR_IMAGE state as part of start operation handling.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(UploadImageTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.imageFile, is("imageFile"));
      assertThat(savedState.imageName, is("imageName"));
      assertThat(savedState.uniqueId, notNullValue());
      assertThat(savedState.queryUploadImageTaskInterval, notNullValue());
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage    Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState(stage);
      startState.controlFlags = 0;
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(UploadImageTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.imageFile, is("imageFile"));
      assertThat(savedState.imageName, is("imageName"));
      assertThat(savedState.uniqueId, notNullValue());
      assertThat(savedState.queryUploadImageTaskInterval, notNullValue());
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
    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      UploadImageTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][] {
          {"imageFile"},
      };
    }

    /**
     * This test verifies that the service instance handles the change of uniqueId
     * in the start state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testChangeUniqueId() throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState();
      startState.uniqueId = "uniqueId";

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(UploadImageTaskService.State.class);
      assertThat(savedState.uniqueId, is("uniqueId"));
    }

    /**
     * This test verifies that the service instance handles the change of queryUploadImageTaskInterval
     * in the start state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testChangeQueryUploadImageTaskInterval() throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState();
      startState.queryUploadImageTaskInterval = 12345;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(UploadImageTaskService.State.class);
      assertThat(savedState.queryUploadImageTaskInterval, is(12345));
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
      service = new UploadImageTaskService();
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
     * This test verifies that legal stage and substage transitions succeed.
     *
     * @param startStage     Supplies the stage of the start state.
     * @param targetStage    Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage targetStage)
        throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      UploadImageTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      UploadImageTaskService.State savedState = host.getServiceState(UploadImageTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that illegal stage and substage transitions fail.
     *
     * @param startStage     Supplies the stage of the start state.
     * @param targetStage    Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      UploadImageTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      UploadImageTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (DcpRuntimeException e) {
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
     * This test verifies that the service instance fails when uniqueId is supplied
     * in the patch state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testInvalidPatchUniqueId() throws Throwable {
      UploadImageTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      UploadImageTaskService.State patchState = buildValidPatchState();
      patchState.uniqueId = "uniqueId";

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to a non-null uniqueId");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("uniqueId is immutable"));
      }
    }

    /**
     * This test verifies that the service instance fails when queryUploadImageTaskInterval
     * is supplied in the patch state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testInvalidPatchQueryUploadImageTaskInterval() throws Throwable {
      UploadImageTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      UploadImageTaskService.State patchState = buildValidPatchState();
      patchState.queryUploadImageTaskInterval = 12345;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to a non-null queryUploadImageTaskInterval");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("queryUploadImageTaskInterval is immutable"));
      }
    }

    /**
     * This test verifies that the service instance fails when isOperationProcessingDisabled
     * is supplied in the patch state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testInvalidPatchIsOperationProcessingDisabled() throws Throwable {
      UploadImageTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      UploadImageTaskService.State patchState = buildValidPatchState();
      patchState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to a non-null controlFlags");
      } catch (DcpRuntimeException e) {
        assertThat(e.getMessage(), is("controlFlags is immutable"));
      }
    }
  }

  /**
   * End-to-end tests for the image upload task.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private DeployerContext deployerContext;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;
    private UploadImageTaskService.State startState;
    private ApiClient apiClient;
    private ImagesApi imagesApi;
    private TasksApi tasksApi;
    private Task taskReturnedByUploadImage;
    private Task taskReturnedByGetTask;

    @BeforeClass
    public void setUpClass() throws Exception {

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          UploadImageTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      apiClient = mock(ApiClient.class);
      imagesApi = mock(ImagesApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(imagesApi).when(apiClient).getImagesApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      apiClientFactory = mock(ApiClientFactory.class);
      doReturn(apiClient).when(apiClientFactory).create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      startState = buildValidStartupState();
      startState.controlFlags = 0;
      startState.queryUploadImageTaskInterval = 10;

      taskReturnedByUploadImage = new Task();
      taskReturnedByUploadImage.setId("taskId");
      taskReturnedByUploadImage.setState("STARTED");

      taskReturnedByGetTask = new Task();
      taskReturnedByGetTask.setId("taskId");
      taskReturnedByGetTask.setState("COMPLETED");

      Task.Entity taskEntity = new Task.Entity();
      taskEntity.setId("taskEntityId");
      taskReturnedByUploadImage.setEntity(taskEntity);
      taskReturnedByGetTask.setEntity(taskEntity);

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory, 1);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
      taskReturnedByUploadImage = null;
      taskReturnedByGetTask = null;
    }

    @AfterClass
    public void tearDownClass() throws Exception {
      listeningExecutorService.shutdown();
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(anyString(), anyString());

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the uploadImage call
     * throws exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureUploadImageThrowsException() throws Throwable {
      doThrow(new RuntimeException("Exception during uploadImage"))
          .when(imagesApi).uploadImage(anyString(), anyString());

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during uploadImage"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the getTask call
     * throws exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */

    @Test
    public void testEndToEndFailureGetTaskThrowsException() throws Throwable {
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(anyString(), anyString());
      doThrow(new RuntimeException("Exception during getTask"))
          .when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      doThrow(new RuntimeException("Exception during getTask"))
          .when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during getTask"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the task returned by UploadImage call
     * has error state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureUploadImageReturnsErrorTaskState(final Task task) throws Throwable {
      doReturn(task).when(imagesApi).uploadImage(anyString(), anyString());

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the task returned by GetTask call
     * has error state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureGetTaskReturnsErrorTaskState(final Task task) throws Throwable {
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(anyString(), anyString());

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
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
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the task returned by UploadImage call
     * has unknown state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureUploadImageReturnsInvalidTaskState() throws Throwable {
      taskReturnedByUploadImage.setState("unknown");
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(anyString(), anyString());
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED:UPLOAD_KUBERNETES_IMAGE state where the task state returned by GetTask call
     * has unknown state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureGetTaskReturnsInvalidTaskState() throws Throwable {
      taskReturnedByGetTask.setState("unknown");
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(anyString(), anyString());

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      UploadImageTaskService.State finalState =
          machine.callServiceAndWaitForState(
              UploadImageTaskFactoryService.SELF_LINK,
              startState,
              UploadImageTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }
  }
}
