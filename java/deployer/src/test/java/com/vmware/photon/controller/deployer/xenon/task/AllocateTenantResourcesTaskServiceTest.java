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
import com.vmware.photon.controller.api.client.resource.TenantsApi;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.ResourceTicketReservation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ApiTestUtils;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.ApiUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.Matchers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link AllocateTenantResourcesTaskService} class.
 */
public class AllocateTenantResourcesTaskServiceTest {

  /**
   * This dummy test case enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private AllocateTenantResourcesTaskService allocateTenantResourcesTaskService;

    @BeforeClass
    public void setUpClass() {
      allocateTenantResourcesTaskService = new AllocateTenantResourcesTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(allocateTenantResourcesTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link AllocateTenantResourcesTaskService#handleStart(Operation)}
   * method.
   */
  public class HandleStartTest {

    private AllocateTenantResourcesTaskService allocateTenantResourcesTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateTenantResourcesTaskService = new AllocateTenantResourcesTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully started.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage,
                                    AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage)
        throws Throwable {

      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);

      if (startStage == null) {
        assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.CREATED));
      } else {
        assertThat(serviceState.taskState.stage, is(startStage));
      }

      assertThat(serviceState.taskState.subStage, is(startSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.quotaLineItems, is(Arrays.asList(
          new QuotaLineItem("vm.count", 1.0, QuotaUnit.COUNT),
          new QuotaLineItem("disk.count", 1.0, QuotaUnit.COUNT))));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage,
                                       AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage)
        throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, is(startSubStage));
      assertThat(serviceState.controlFlags, is(0));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateMissingRequiredFieldName(String fieldName) throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateTenantResourcesTaskService.State.class, NotNull.class));
    }

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldNegative(String fieldName) throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, -1);
      testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
    }

    @DataProvider(name = "PositiveFieldNames")
    public Object[][] getPositiveFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateTenantResourcesTaskService.State.class, Positive.class));
    }
  }

  /**
   * This class implements tests for the {@link AllocateTenantResourcesTaskService#handlePatch(Operation)}
   * method.
   */
  public class HandlePatchTest {

    private AllocateTenantResourcesTaskService allocateTenantResourcesTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateTenantResourcesTaskService = new AllocateTenantResourcesTaskService();
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
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         AllocateTenantResourcesTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {

      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(AllocateTenantResourcesTaskService.buildPatch(patchStage, patchSubStage));

      op = testHost.sendRequestAndWait(patchOp);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(AllocateTenantResourcesTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           AllocateTenantResourcesTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {

      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(AllocateTenantResourcesTaskService.buildPatch(patchStage, patchSubStage));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(AllocateTenantResourcesTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State patchState = AllocateTenantResourcesTaskService.buildPatch(
          TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateTenantResourcesTaskService.State.class, Immutable.class));
    }

    @Test(dataProvider = "WriteOnceFieldNames")
    public void testInvalidPatchOverwriteWriteOnceField(String fieldName) throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State patchState = AllocateTenantResourcesTaskService.buildPatch(
          TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
      assertThat(patchOp.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT));
      assertThat(declaredField.get(serviceState), is(ReflectionUtils.getDefaultAttributeValue(declaredField)));

      try {
        testHost.sendRequestAndWait(Operation
            .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
            .setBody(patchState));
        fail("Overwriting a field with the WriteOnce annotation is expected to fail");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), containsString(fieldName));
      }
    }

    @DataProvider(name = "WriteOnceFieldNames")
    public Object[][] getWriteOnceFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateTenantResourcesTaskService.State.class, WriteOnce.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link AllocateTenantResourcesTaskService}
   * task.
   */
  public class EndToEndTest {

    private final Task failedTask = ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage");

    private ApiClientFactory apiClientFactory;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private String projectId;
    private String resourceTicketId;
    private AllocateTenantResourcesTaskService.State startState;
    private TasksApi tasksApi;
    private String tenantId;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          this.getClass().getResource("/config.yml").getPath()).getDeployerContext();

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostCount(1)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      ApiClient apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();

      projectId = UUID.randomUUID().toString();
      resourceTicketId = UUID.randomUUID().toString();
      tenantId = UUID.randomUUID().toString();

      doAnswer(MockHelper.mockCreateTenantAsync("CREATE_TENANT_TASK_ID", tenantId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_TENANT_TASK_ID", tenantId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_TENANT_TASK_ID", tenantId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_TENANT_TASK_ID", tenantId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_TENANT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_RESOURCE_TICKET_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync("CREATE_PROJECT_TASK_ID", projectId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(anyString(), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_PROJECT_TASK_ID", projectId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_PROJECT_TASK_ID", projectId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_PROJECT_TASK_ID", projectId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_PROJECT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      DeploymentService.State deploymentState = TestHelper.createDeploymentService(cloudStoreEnvironment);

      for (int i = 0; i < 3; i++) {
        TestHelper.createVmService(testEnvironment);
      }

      startState = buildValidStartState(null, null);
      startState.deploymentServiceLink = deploymentState.documentSelfLink;
      startState.controlFlags = null;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, is("CREATE_PROJECT_TASK_ID"));
      assertThat(finalState.createProjectPollCount, is(3));
      assertThat(finalState.projectId, is(projectId));

      verify(tenantsApi).createAsync(
          eq(Constants.TENANT_NAME),
          Matchers.<FutureCallback<Task>>any());

      verify(tasksApi, times(3)).getTaskAsync(
          eq("CREATE_TENANT_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      verify(tenantsApi).createResourceTicketAsync(
          eq(tenantId),
          eq(getExpectedResourceTicketCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      verify(tasksApi, times(3)).getTaskAsync(
          eq("CREATE_RESOURCE_TICKET_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      verify(tenantsApi).createProjectAsync(
          eq(tenantId),
          eq(getExpectedProjectCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(VmService.State.class)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.BROADCAST,
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      QueryTask result = testEnvironment.sendQueryAndWait(queryTask);
      for (Object vmDocument : result.results.documents.values()) {
        VmService.State vmState = Utils.fromJson(vmDocument, VmService.State.class);
        assertThat(vmState.projectId, is(projectId));
      }

      DeploymentService.State deploymentState = cloudStoreEnvironment.getServiceState(finalState.deploymentServiceLink,
          DeploymentService.State.class);
      assertThat(deploymentState.projectId, is(projectId));
    }

    @Test
    public void testSuccessNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync("CREATE_TENANT_TASK_ID", tenantId, "COMPLETED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId,
          "COMPLETED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync("CREATE_PROJECT_TASK_ID", projectId, "COMPLETED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.createTenantTaskId, nullValue());
      assertThat(finalState.createTenantPollCount, is(0));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, nullValue());
      assertThat(finalState.createResourceTicketPollCount, is(0));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, nullValue());
      assertThat(finalState.createProjectPollCount, is(0));
      assertThat(finalState.projectId, is(projectId));

      verify(tenantsApi).createAsync(
          eq(Constants.TENANT_NAME),
          Matchers.<FutureCallback<Task>>any());

      verify(tenantsApi).createResourceTicketAsync(
          eq(tenantId),
          eq(getExpectedResourceTicketCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      verify(tenantsApi).createProjectAsync(
          eq(tenantId),
          eq(getExpectedProjectCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(VmService.State.class)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.BROADCAST,
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      QueryTask result = testEnvironment.sendQueryAndWait(queryTask);
      for (Object vmDocument : result.results.documents.values()) {
        VmService.State vmState = Utils.fromJson(vmDocument, VmService.State.class);
        assertThat(vmState.projectId, is(projectId));
      }

      DeploymentService.State deploymentState = cloudStoreEnvironment.getServiceState(finalState.deploymentServiceLink,
          DeploymentService.State.class);
      assertThat(deploymentState.projectId, is(projectId));
    }

    private ResourceTicketCreateSpec getExpectedResourceTicketCreateSpec() {
      ResourceTicketCreateSpec createSpec = new ResourceTicketCreateSpec();
      createSpec.setName(Constants.RESOURCE_TICKET_NAME);
      createSpec.setLimits(Arrays.asList(
          new QuotaLineItem("vm.count", 1, QuotaUnit.COUNT),
          new QuotaLineItem("disk.count", 1, QuotaUnit.COUNT)));

      return createSpec;
    }

    private ProjectCreateSpec getExpectedProjectCreateSpec() {
      ResourceTicketReservation reservation = new ResourceTicketReservation();
      reservation.setName(Constants.RESOURCE_TICKET_NAME);
      reservation.setLimits(Collections.singletonList(new QuotaLineItem("subdivide.percent", 100.0, QuotaUnit.COUNT)));

      ProjectCreateSpec createSpec = new ProjectCreateSpec();
      createSpec.setName(Constants.PROJECT_NAME);
      createSpec.setResourceTicket(reservation);
      return createSpec;
    }

    @Test
    public void testCreateTenantFailure() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync("CREATE_TENANT_TASK_ID", tenantId, "QUEUED"))
          .doAnswer(MockHelper.mockCreateTenantAsync("CREATE_TENANT_TASK_ID", tenantId, "STARTED"))
          .doAnswer(MockHelper.mockCreateTenantAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_TENANT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
    }

    @Test
    public void testCreateTenantFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(failedTask))
          .when(tenantsApi)
          .createAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, nullValue());
      assertThat(finalState.createTenantPollCount, is(0));
    }

    @Test
    public void testCreateTenantFailureExceptionInCreateTenantCall() throws Throwable {

      doThrow(new IOException("I/O exception during tenants API createAsync call"))
          .when(tenantsApi)
          .createAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("I/O exception during tenants API createAsync call"));
      assertThat(finalState.createTenantTaskId, nullValue());
      assertThat(finalState.createTenantPollCount, is(0));
    }

    @Test
    public void testCreateTenantFailureExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_TENANT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during getTaskAsync call"));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(1));
    }

    @Test
    public void testCreateResourceTicketFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_RESOURCE_TICKET_TASK_ID", resourceTicketId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_RESOURCE_TICKET_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
    }

    @Test
    public void testCreateResourceTicketFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(failedTask))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, nullValue());
      assertThat(finalState.createResourceTicketPollCount, is(0));
    }

    @Test
    public void testCreateResourceTicketFailureExceptionInCreateResourceTicketCall() throws Throwable {

      doThrow(new IOException("I/O exception in createResourceTicketAsync call"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("I/O exception in createResourceTicketAsync call"));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, nullValue());
      assertThat(finalState.createResourceTicketPollCount, is(0));
    }

    @Test
    public void testCreateResourceTicketFailureExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception in getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_RESOURCE_TICKET_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in getTaskAsync call"));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(1));
    }

    @Test
    public void testCreateProjectFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_PROJECT_TASK_ID", projectId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_PROJECT_TASK_ID", projectId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_PROJECT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, is("CREATE_PROJECT_TASK_ID"));
      assertThat(finalState.createProjectPollCount, is(3));
    }

    @Test
    public void testCreateProjectFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(failedTask))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, nullValue());
      assertThat(finalState.createProjectPollCount, is(0));
    }

    @Test
    public void testCreateProjectFailureExceptionInCreateProjectCall() throws Throwable {

      doThrow(new IOException("I/O exception in createProjectAsync call"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in createProjectAsync call"));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, nullValue());
      assertThat(finalState.createProjectPollCount, is(0));
    }

    @Test
    public void testCreateProjectFailureExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception in getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_PROJECT_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception in getTaskAsync call"));
      assertThat(finalState.createTenantTaskId, is("CREATE_TENANT_TASK_ID"));
      assertThat(finalState.createTenantPollCount, is(3));
      assertThat(finalState.tenantId, is(tenantId));
      assertThat(finalState.createResourceTicketTaskId, is("CREATE_RESOURCE_TICKET_TASK_ID"));
      assertThat(finalState.createResourceTicketPollCount, is(3));
      assertThat(finalState.resourceTicketId, is(resourceTicketId));
      assertThat(finalState.createProjectTaskId, is("CREATE_PROJECT_TASK_ID"));
      assertThat(finalState.createProjectPollCount, is(1));
    }
  }

  private AllocateTenantResourcesTaskService.State buildValidStartState(
      TaskState.TaskStage startStage,
      AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage) {

    AllocateTenantResourcesTaskService.State startState = new AllocateTenantResourcesTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.quotaLineItems = new ArrayList<>(Arrays.asList(
        new QuotaLineItem("vm.count", 1, QuotaUnit.COUNT),
        new QuotaLineItem("disk.count", 1, QuotaUnit.COUNT)));

    if (startStage != null) {
      startState.taskState = new AllocateTenantResourcesTaskService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;
    }

    return startState;
  }
}
