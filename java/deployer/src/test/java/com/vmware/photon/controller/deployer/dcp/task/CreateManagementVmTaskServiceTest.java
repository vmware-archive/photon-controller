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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.FlavorFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.FlavorService;
import com.vmware.photon.controller.deployer.dcp.entity.ImageFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ImageService;
import com.vmware.photon.controller.deployer.dcp.entity.ProjectFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ProjectService;
import com.vmware.photon.controller.deployer.dcp.entity.VmFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

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
import java.util.Collections;
import java.util.EnumSet;

/**
 * This class implements tests for {@link CreateManagementVmTaskService} class.
 */
public class CreateManagementVmTaskServiceTest {

  private TestHost host;
  private CreateManagementVmTaskService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateManagementVmTaskService.State buildValidStartupState() {
    return buildValidStartupState(
        TaskState.TaskStage.CREATED);
  }

  private CreateManagementVmTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {

    CreateManagementVmTaskService.State state = new CreateManagementVmTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.vmServiceLink = "vmServiceLink";
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private CreateManagementVmTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private CreateManagementVmTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    CreateManagementVmTaskService.State state = new CreateManagementVmTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      DeployerContext deployerContext,
      ApiClientFactory apiClientFactory,
      ServerSet cloudServerSet,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .apiClientFactory(apiClientFactory)
        .cloudServerSet(cloudServerSet)
        .hostCount(hostCount)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateManagementVmTaskService();
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
      service = new CreateManagementVmTaskService();
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
     * @param stage    Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage)
        throws Throwable {

      CreateManagementVmTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmTaskService.State savedState = host.getServiceState(
          CreateManagementVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
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

      CreateManagementVmTaskService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmTaskService.State savedState = host.getServiceState(CreateManagementVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
      assertThat(savedState.queryCreateVmTaskInterval, notNullValue());
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

      CreateManagementVmTaskService.State startState = buildValidStartupState(stage);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmTaskService.State savedState = host.getServiceState(CreateManagementVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
      assertThat(savedState.queryCreateVmTaskInterval, notNullValue());
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
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateManagementVmTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][] {
          {"vmServiceLink"},
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
      CreateManagementVmTaskService.State startState = buildValidStartupState();
      startState.queryCreateVmTaskInterval = 12345;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmTaskService.State savedState = host.getServiceState(
          CreateManagementVmTaskService.State.class);
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
      service = new CreateManagementVmTaskService();
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
     * @param startStage     Supplies the stage of the start state.
     * @param targetStage    Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateManagementVmTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateManagementVmTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateManagementVmTaskService.State savedState = host.getServiceState(CreateManagementVmTaskService.State.class);
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
     * @param startStage     Supplies the stage of the start state.
     * @param targetStage    Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateManagementVmTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateManagementVmTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (IllegalStateException e) {
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
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateManagementVmTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateManagementVmTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, declaredField.getType().newInstance());

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return new Object[][] {
          {"vmServiceLink"},
      };
    }
  }

  /**
   * End-to-end tests for the create VM task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private DeployerContext deployerContext;
    private ApiClientFactory apiClientFactory;
    private ImageService.State imageServiceStartState;
    private ProjectService.State projectServiceStartState;
    private FlavorService.State flavorServiceStartState;
    private VmService.State vmServiceStartState;
    private ContainerTemplateService.State containerTemplateStartState;
    private ContainerService.State containerStartState;
    private CreateManagementVmTaskService.State startState;
    private ApiClient apiClient;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private Task taskReturnedByCreateVm;
    private Task taskReturnedBySetMetadata;
    private Task taskReturnedByGetTask;

    @BeforeMethod
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();

      imageServiceStartState = new ImageService.State();
      imageServiceStartState.imageId = "imageId";
      imageServiceStartState.imageName = "imageName";
      imageServiceStartState.imageFile = "imageFile";

      projectServiceStartState = new ProjectService.State();
      projectServiceStartState.projectName = "projectName";
      projectServiceStartState.resourceTicketServiceLink = "resourceTicketServiceLink";
      projectServiceStartState.projectId = "projectId";
      projectServiceStartState.documentSelfLink = "/projectId";

      vmServiceStartState = new VmService.State();
      vmServiceStartState.name = "vmName";

      flavorServiceStartState = new FlavorService.State();
      flavorServiceStartState.diskFlavorName = "diskFlavor";
      flavorServiceStartState.vmFlavorName = "vmFlavor";
      flavorServiceStartState.diskGb = 1;
      flavorServiceStartState.cpuCount = 1;
      flavorServiceStartState.memoryGb = 1;

      containerTemplateStartState = TestHelper.getContainerTemplateServiceStartState();

      containerStartState = new ContainerService.State();

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

      taskReturnedBySetMetadata = new Task();
      taskReturnedBySetMetadata.setId("taskId");
      taskReturnedBySetMetadata.setState("STARTED");

      taskReturnedByGetTask = new Task();
      taskReturnedByGetTask.setId("taskId");
      taskReturnedByGetTask.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByCreateVm.setEntity(entity);
      taskReturnedBySetMetadata.setEntity(entity);
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
      taskReturnedBySetMetadata = null;
      taskReturnedByGetTask = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
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
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedBySetMetadata);
          return null;
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during createVm"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the setMetadata call throws exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureSetMetadataThrowsException() throws Throwable {

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

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          throw new RuntimeException("Exception during setMetadata");
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during setMetadata"));
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by setMetadata call has ERROR state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureSetMetadataReturnsErrorTaskState(final Task task) throws Throwable {
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
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(task);
          return null;
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    /**
     * This test verifies that the service instance handles failure in
     * STARTED state where the task returned by setMetadata call has unknown state.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndFailureSetMetadataReturnsInvalidTaskState() throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      taskReturnedBySetMetadata.setState("unknown");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedBySetMetadata);
          return null;
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
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

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments();

      CreateManagementVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmTaskFactoryService.SELF_LINK,
              startState,
              CreateManagementVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    /**
     * This method sets up valid service documents which are needed for test.
     * Note that this function does NOT set up the VmService document.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void setupValidOtherServiceDocuments() throws Throwable {

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()));

      ImageService.State imageServiceState =
          machine.callServiceSynchronously(
              ImageFactoryService.SELF_LINK,
              imageServiceStartState,
              ImageService.State.class);

      ProjectService.State projectServiceState =
          machine.callServiceSynchronously(
              ProjectFactoryService.SELF_LINK,
              projectServiceStartState,
              ProjectService.State.class);

      FlavorService.State flavorServiceState =
          machine.callServiceSynchronously(
              FlavorFactoryService.SELF_LINK,
              flavorServiceStartState,
              FlavorService.State.class);

      vmServiceStartState.hostServiceLink = hostServiceState.documentSelfLink;
      vmServiceStartState.imageServiceLink = imageServiceState.documentSelfLink;
      vmServiceStartState.projectServiceLink = projectServiceState.documentSelfLink;
      vmServiceStartState.flavorServiceLink = flavorServiceState.documentSelfLink;
    }

    /**
     * This method sets up valid service documents which are needed for test.
     * Note that this function sets up ONLY VmService document.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void setupValidVmServiceDocument() throws Throwable {

      VmService.State vmServiceState =
          machine.callServiceSynchronously(
              VmFactoryService.SELF_LINK,
              vmServiceStartState,
              VmService.State.class);

      startState.vmServiceLink = vmServiceState.documentSelfLink;

      ContainerTemplateService.State containerTemplateServiceState =
          machine.callServiceSynchronously(
              ContainerTemplateFactoryService.SELF_LINK,
              containerTemplateStartState,
              ContainerTemplateService.State.class);

      containerStartState.vmServiceLink = vmServiceState.documentSelfLink;
      containerStartState.containerTemplateServiceLink = containerTemplateServiceState.documentSelfLink;

      machine.callServiceSynchronously(
          ContainerFactoryService.SELF_LINK,
          containerStartState,
          ContainerService.State.class);
    }

    /**
     * This method sets up valid service documents which are needed for test.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void setupValidServiceDocuments() throws Throwable {

      setupValidOtherServiceDocuments();
      setupValidVmServiceDocument();
    }
  }
}
