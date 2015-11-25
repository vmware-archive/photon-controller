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

import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
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
import static org.hamcrest.core.IsNull.notNullValue;
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

/**
 * This class implements tests for the {@link CreateResourceTicketTaskService}
 * class.
 */
public class CreateResourceTicketTaskServiceTest {

  private CreateResourceTicketTaskService createResourceTicketTaskService;
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
   * CreateResourceTicketTaskService instance.
   */
  private CreateResourceTicketTaskService.State buildValidStartState(TaskState.TaskStage stage) {
    CreateResourceTicketTaskService.State startState = new CreateResourceTicketTaskService.State();
    startState.taskState = new TaskState();
    startState.taskState.stage = stage;
    startState.tenantServiceLink = "TENANT_SERVICE_LINK";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (stage == TaskState.TaskStage.FINISHED) {
      startState.resourceTicketServiceLink = "RESOURCE_TICKET_SERVICE_LINK";
    }

    return startState;
  }

  private CreateResourceTicketTaskService.State buildValidPatchState(TaskState.TaskStage stage) {
    CreateResourceTicketTaskService.State patchState = new CreateResourceTicketTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    if (stage == TaskState.TaskStage.FINISHED) {
      patchState.resourceTicketServiceLink = "RESOURCE_TICKET_SERVICE_LINK";
    }

    return patchState;
  }

  /**
   * This class implements tests for service initialization.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      createResourceTicketTaskService = new CreateResourceTicketTaskService();
    }

    @AfterMethod
    public void tearDownTest() {
      createResourceTicketTaskService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(createResourceTicketTaskService.getOptions(), is(expected));
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
      createResourceTicketTaskService = new CreateResourceTicketTaskService();
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
      CreateResourceTicketTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
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
      CreateResourceTicketTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State savedState =
          testHost.getServiceState(CreateResourceTicketTaskService.State.class);

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

    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      CreateResourceTicketTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateResourceTicketTaskService.State.class,
              NotNull.class));
    }

    @Test(dataProvider = "TaskPollDelayValues")
    public void testTaskPollDelayValues(Integer taskPollDelay, Integer expectedValue) throws Throwable {
      CreateResourceTicketTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State savedState =
          testHost.getServiceState(CreateResourceTicketTaskService.State.class);

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
      CreateResourceTicketTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = taskPollDelay;
      try {
        testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
        fail("Service start should throw in response to illegal taskPollDelay values");
      } catch (DcpRuntimeException e) {
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
      createResourceTicketTaskService = new CreateResourceTicketTaskService();
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
      CreateResourceTicketTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State savedState =
          testHost.getServiceState(CreateResourceTicketTaskService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{

          // N.B. Services created in the CREATED state will transition to
          //      STARTED as part of service creation, but any listeners will
          //      subsequently get a STARTED patch too, so cover this case.

          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},

          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "InvalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      CreateResourceTicketTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State patchState = buildValidPatchState(patchStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOperation);
        fail("Stage transition from " + startStage.toString() + " to " + patchStage.toString() + " should fail");
      } catch (DcpRuntimeException e) {
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

    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "fieldNamesWithInvalidValue")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      CreateResourceTicketTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOperation = testHost.startServiceSynchronously(createResourceTicketTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateResourceTicketTaskService.State patchState =
          createResourceTicketTaskService.buildPatch(TaskState.TaskStage.STARTED, null);
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
              CreateResourceTicketTaskService.State.class,
              Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";

    private DeployerContext deployerContext;
    private ApiClient apiClient;
    private ApiClientFactory apiClientFactory;
    private CreateResourceTicketTaskService.State startState;
    private TasksApi tasksApi;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreTestEnvironment;
    private Task.Entity resourceTicketEntity;

    private TestEnvironment createTestEnvironment(ApiClientFactory apiClientFactory) throws Throwable {
      cloudStoreTestEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(cloudStoreTestEnvironment.getServerSet())
          .hostCount(1)
          .build();

      return testEnvironment;
    }

    @BeforeClass
    public void setUpClass() throws Throwable {

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateResourceTicketTaskService.class.getResource(configFilePath).getPath()).getDeployerContext();

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.taskPollDelay = 10;
      startState.controlFlags = 0;

    }

    @AfterClass
    public void tearDownClass() {
      deployerContext = null;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      testEnvironment = createTestEnvironment(apiClientFactory);

      apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();

      TenantService.State tenantState = TestHelper.createTenant(cloudStoreTestEnvironment);
      String tenantId = ServiceUtils.getIDFromDocumentSelfLink(tenantState.documentSelfLink);
      startState.tenantServiceLink = tenantState.documentSelfLink;

      ResourceTicketService.State resourceTicketState = TestHelper.createResourceTicket(tenantId,
          cloudStoreTestEnvironment);
      resourceTicketEntity = new Task.Entity();
      resourceTicketEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(resourceTicketState.documentSelfLink));
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
      }

      if (null != cloudStoreTestEnvironment) {
        cloudStoreTestEnvironment.stop();
        cloudStoreTestEnvironment = null;
      }

      apiClient = null;
      apiClientFactory = null;
      tasksApi = null;
      tenantsApi = null;
      testEnvironment = null;
    }

    @Test
    public void testEndToEndSuccessCreateResourceTicketReturnsCompletedTask() throws Throwable {
      final Task returnValue = new Task();
      returnValue.setState("COMPLETED");
      returnValue.setEntity(resourceTicketEntity);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(returnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.resourceTicketServiceLink, notNullValue());

      ResourceTicketService.State resourceTicketState =
          cloudStoreTestEnvironment.getServiceState(finalState.resourceTicketServiceLink, ResourceTicketService.State
              .class);
      assertThat(resourceTicketState.name, is(Constants.RESOURCE_TICKET_NAME));
    }

    @Test
    public void testEndToEndSuccessGetTaskReturnsCompletedTask() throws Throwable {
      final Task createResourceTicketReturnValue = new Task();
      createResourceTicketReturnValue.setState("QUEUED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(createResourceTicketReturnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      final Task getTaskReturnValue = new Task();
      getTaskReturnValue.setState("COMPLETED");
      getTaskReturnValue.setEntity(resourceTicketEntity);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(getTaskReturnValue);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.resourceTicketServiceLink, notNullValue());

      ResourceTicketService.State resourceTicketState =
          cloudStoreTestEnvironment.getServiceState(finalState.resourceTicketServiceLink,
              ResourceTicketService.State.class);
      assertThat(resourceTicketState.name, is(Constants.RESOURCE_TICKET_NAME));
    }

    @Test
    public void testEndToEndSuccessGetTaskReturnsSuccessiveStages() throws Throwable {
      final Task createResourceTicketReturnValue = new Task();
      createResourceTicketReturnValue.setState("QUEUED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(createResourceTicketReturnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      final Task getTaskReturnValue1 = new Task();
      getTaskReturnValue1.setState("QUEUED");
      final Task getTaskReturnValue2 = new Task();
      getTaskReturnValue2.setState("STARTED");
      final Task getTaskReturnValue3 = new Task();
      getTaskReturnValue3.setState("COMPLETED");
      getTaskReturnValue3.setEntity(resourceTicketEntity);

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(getTaskReturnValue1);
          return null;
        }
      }).
          doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
              ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(getTaskReturnValue2);
              return null;
            }
          }).
          doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
              ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(getTaskReturnValue3);
              return null;
            }
          }).
          when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.resourceTicketServiceLink, notNullValue());

      ResourceTicketService.State resourceTicketState =
          cloudStoreTestEnvironment.getServiceState(finalState.resourceTicketServiceLink,
              ResourceTicketService.State.class);
      assertThat(resourceTicketState.name, is(Constants.RESOURCE_TICKET_NAME));
    }

    @Test(dataProvider = "CreateResourceTicketExceptions")
    public void testEndToEndFailureCreateResourceTicketThrows(final Exception e) throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          throw e;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "CreateResourceTicketExceptions")
    public Object[][] getCreateResourceTicketExceptions() {
      return new Object[][]{
          {new RuntimeException()},
          {new IOException()},
      };
    }

    @Test(dataProvider = "CreateResourceTicketFailureResponses")
    public void testEndToEndFailureCreateResourceTicketReturnsFailedTask(final Task task) throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(task);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    @DataProvider(name = "CreateResourceTicketFailureResponses")
    public Object[][] getCreateResourceTicketFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
      };
    }

    @Test
    public void testEndToEndFailureCreateResourceTicketReturnsUnknownStatus() throws Throwable {
      final Task task = new Task();
      task.setState("UNKNOWN_STATE");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(task);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: UNKNOWN_STATE"));
    }

    @Test(dataProvider = "GetTaskExceptions")
    public void testEndToEndFailureGetTaskThrows(Exception e) throws Throwable {
      final Task createResourceTicketReturnValue = new Task();
      createResourceTicketReturnValue.setState("QUEUED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(createResourceTicketReturnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      doThrow(e).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "GetTaskExceptions")
    public Object[][] getGetTaskExceptions() {
      return new Object[][]{
          {new RuntimeException()},
          {new IOException()},
      };
    }

    @Test(dataProvider = "GetTaskFailureResponses")
    public void testEndToEndFailureGetTaskReturnsFailedTask(final Task task) throws Throwable {
      final Task createResourceTicketReturnValue = new Task();
      createResourceTicketReturnValue.setState("QUEUED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(createResourceTicketReturnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    @DataProvider(name = "GetTaskFailureResponses")
    public Object[][] getGetTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 2, "errorCode", "errorMessage")},
      };
    }

    @Test
    public void testEndToEndFailureGetTaskReturnsUnknownStatus() throws Throwable {
      final Task createResourceTicketReturnValue = new Task();
      createResourceTicketReturnValue.setState("QUEUED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(createResourceTicketReturnValue);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(anyString(), any(ResourceTicketCreateSpec.class),
          any(FutureCallback.class));

      final Task task = new Task();
      task.setState("UNKNOWN_STATE");
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));

      CreateResourceTicketTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          CreateResourceTicketTaskFactoryService.SELF_LINK,
          startState,
          CreateResourceTicketTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: UNKNOWN_STATE"));
    }
  }
}
