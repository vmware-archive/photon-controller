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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.resource.TasksApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.internal.matchers.NotNull;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link DeleteVmTaskService} class.
 */
public class DeleteVmTaskServiceTest {

  private TestHost host;
  private DeleteVmTaskService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private DeleteVmTaskService.State buildValidStartUpState() throws Throwable {
    return buildValidStartUpState(TaskState.TaskStage.CREATED);
  }

  private DeleteVmTaskService.State buildValidStartUpState(TaskState.TaskStage stage) throws Throwable {
    DeleteVmTaskService.State state = ReflectionUtils.buildValidStartState(DeleteVmTaskService.State.class);
    state.taskState.stage = stage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  /**
   * Test for the constructor.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() {
      service = new DeleteVmTaskService();
    }

    /**
     * Test that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
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
      service = new DeleteVmTaskService();
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

    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage taskStage) throws Throwable {

      DeleteVmTaskService.State startState = buildValidStartUpState(taskStage);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test
    public void testMinimalStartStateChanged() throws Throwable {
      DeleteVmTaskService.State startState = buildValidStartUpState();
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteVmTaskService.State savedState = host.getServiceState(DeleteVmTaskService.State.class);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "StartStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      DeleteVmTaskService.State startState = buildValidStartUpState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DeleteVmTaskService.State savedState = host.getServiceState(DeleteVmTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "StartStateNotChanged")
    public Object[][] getStartStateNotChanged() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      DeleteVmTaskService.State startState = buildValidStartUpState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(DeleteVmTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new DeleteVmTaskService();
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

    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStateUpdates(
        TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {

      DeleteVmTaskService.State startState = buildValidStartUpState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteVmTaskService.State patchState = new DeleteVmTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = patchStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      DeleteVmTaskService.State savedState = host.getServiceState(DeleteVmTaskService.State.class);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdatesData() throws Throwable {
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

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "IllegalStageUpdate")
    public void testIllegalStageUpdate(
        TaskState.TaskStage startStage, TaskState.TaskStage patchStage) throws Throwable {
      DeleteVmTaskService.State startState = buildValidStartUpState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteVmTaskService.State patchState = new DeleteVmTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = patchStage;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "IllegalStageUpdate")
    public Object[][] getIllegalStageUpdatesData() throws Throwable {
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

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      DeleteVmTaskService.State startState = buildValidStartUpState();
      host.startServiceSynchronously(service, startState);

      DeleteVmTaskService.State patchState = new DeleteVmTaskService.State();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(DeleteVmTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * This class implements end-to-end tests for the deploy agent task.
   */
  public class EndToEndTest {
    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private ApiClientFactory apiClientFactory;
    private ApiClient apiClient;
    private Task apifeTask;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private DeployerContext deployerContext;

    private DeleteVmTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      apiClient = mock(ApiClient.class);
      vmApi = mock(VmApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(apiClient).when(apiClientFactory).create();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();

      startState = buildValidStartUpState();
      startState.controlFlags = 0;
      startState.taskPollDelay = 10;

      apifeTask = new Task();
      apifeTask.setId("taskId");
      apifeTask.setState("COMPLETED");
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
    }

    @AfterClass
    public void tearDownClass() throws Exception {
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(apifeTask);
          return null;
        }
      }).when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(apifeTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment();

      DeleteVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteVmTaskFactoryService.SELF_LINK,
              startState,
              DeleteVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndEsxClientCallDeleteFailure() throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          throw new IOException("Expected exception");
        }
      }).when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));


      machine = createTestEnvironment();

      DeleteVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteVmTaskFactoryService.SELF_LINK,
              startState,
              DeleteVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Expected exception"));
    }

    @Test
    public void testEndToEndEsxClientCallWaitFailure() throws Throwable {
      apifeTask.setState("QUEUED");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(apifeTask);
          return null;
        }
      }).when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));

      doThrow(new IOException("Expected exception")).
          when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      machine = createTestEnvironment();

      DeleteVmTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteVmTaskFactoryService.SELF_LINK,
              startState,
              DeleteVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Expected exception"));
    }

    @Test(enabled = false)
    public void testEndToEndGetVmServiceCall() {
      fail("Not implemented yet, due to Xenon bug");
    }

    private TestEnvironment createTestEnvironment() throws Throwable {
      return new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();
    }
  }
}
