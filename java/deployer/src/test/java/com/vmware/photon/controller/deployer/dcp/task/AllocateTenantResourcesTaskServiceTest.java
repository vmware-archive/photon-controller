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

import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
      assertThat(allocateTenantResourcesTaskService.getOptions(), is(EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION)));
    }
  }

  /**
   * This class implements tests for the handleStart method.
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
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.quotaLineItems, is(Arrays.asList(
          new QuotaLineItem("vm.count", 1, QuotaUnit.COUNT),
          new QuotaLineItem("disk.count", 1, QuotaUnit.COUNT))));
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

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage startStage,
                                           AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage)
        throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
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

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
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

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = XenonRuntimeException.class)
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
   * This class implements tests for the handlePatch method.
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

      Operation patchOp = testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(AllocateTenantResourcesTaskService.buildPatch(patchStage, patchSubStage, null)));
      assertThat(patchOp.getStatusCode(), is(200));

      AllocateTenantResourcesTaskService.State serviceState =
          testHost.getServiceState(AllocateTenantResourcesTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           AllocateTenantResourcesTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      AllocateTenantResourcesTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(allocateTenantResourcesTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(AllocateTenantResourcesTaskService.buildPatch(patchStage, patchSubStage, null)));
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},

          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},

          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
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
      } catch (XenonRuntimeException e) {
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
   * This class implements tests for the CREATE_TENANT sub-stage.
   */
  public class CreateTenantTest {

    private ApiClient apiClient;
    private ApiClientFactory apiClientFactory;
    private String entityId;
    private AllocateTenantResourcesTaskService.State startState;
    private String taskId;
    private TasksApi tasksApi;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() {
      apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();
      entityId = UUID.randomUUID().toString();
      taskId = UUID.randomUUID().toString();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "COMPLETED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.tenantId, is(entityId));
    }

    @Test
    public void testSuccessAfterPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.tenantId, is(entityId));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateTenantFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(response))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testCreateTenantException() throws Throwable {

      doThrow(new IOException("IO exception during createAsync call"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during createAsync call"));
    }

    @Test
    public void testCreateTenantUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "UNKNOWN"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testGetTaskFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testGetTaskException() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doThrow(new IOException("IO exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during getTaskAsync call"));
    }

    @Test
    public void testGetTaskUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "UNKNOWN"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_TENANT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @DataProvider(name = "TaskFailureResponses")
    public Object[][] getTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 2, "errorCode", "errorMessage")},
      };
    }
  }

  /**
   * This class implements tests for the CREATE_RESOURCE_TICKET sub-stage.
   */
  public class CreateResourceTicketTest {

    private ApiClient apiClient;
    private ApiClientFactory apiClientFactory;
    private String entityId;
    private AllocateTenantResourcesTaskService.State startState;
    private String taskId;
    private TasksApi tasksApi;
    private String tenantId;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() {
      apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();
      entityId = UUID.randomUUID().toString();
      taskId = UUID.randomUUID().toString();
      tenantId = UUID.randomUUID().toString();
      startState.tenantId = tenantId;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "COMPLETED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.resourceTicketId, is(entityId));
    }

    @Test
    public void testSuccessAfterPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage,
          is(AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.resourceTicketId, is(entityId));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateResourceTicketFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(response))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testCreateResourceTicketException() throws Throwable {

      doThrow(new IOException("IO exception during createResourceTicketAsync call"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during createResourceTicketAsync call"));
    }

    @Test
    public void testCreateResourceTicketUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "UNKNOWN"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testGetTaskFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testGetTaskException() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doThrow(new IOException("IO exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during getTaskAsync call"));
    }

    @Test
    public void testGetTaskUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateResourceTicketAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "UNKNOWN"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_RESOURCE_TICKET);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @DataProvider(name = "TaskFailureResponses")
    public Object[][] getTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 2, "errorCode", "errorMessage")},
      };
    }
  }

  /**
   * This class implements tests for the CREATE_PROJECT sub-stage.
   */
  public class CreateProjectTest {

    private ApiClient apiClient;
    private ApiClientFactory apiClientFactory;
    private String entityId;
    private String resourceTicketId;
    private AllocateTenantResourcesTaskService.State startState;
    private String taskId;
    private TasksApi tasksApi;
    private String tenantId;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();
      entityId = UUID.randomUUID().toString();
      taskId = UUID.randomUUID().toString();
      resourceTicketId = UUID.randomUUID().toString();
      startState.resourceTicketId = resourceTicketId;
      tenantId = UUID.randomUUID().toString();
      startState.tenantId = tenantId;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "COMPLETED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.projectId, is(entityId));
    }

    @Test
    public void testSuccessAfterPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(finalState.projectId, is(entityId));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateProjectFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(response))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testCreateProjectException() throws Throwable {

      doThrow(new IOException("IO exception during createProjectAsync call"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during createProjectAsync call"));
    }

    @Test
    public void testCreateProjectUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "UNKNOWN"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testGetTaskFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
    }

    @Test
    public void testGetTaskException() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doThrow(new IOException("IO exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("IO exception during getTaskAsync call"));
    }

    @Test
    public void testGetTaskUnknownTaskStatus() throws Throwable {

      doAnswer(MockHelper.mockCreateProjectAsync(taskId, entityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(taskId, entityId, "UNKNOWN"))
          .when(tasksApi)
          .getTaskAsync(eq(taskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.subStage !=
                  AllocateTenantResourcesTaskService.TaskState.SubStage.CREATE_PROJECT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("Unknown task state: UNKNOWN"));
    }

    @DataProvider(name = "TaskFailureResponses")
    public Object[][] getTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 2, "errorCode", "errorMessage")},
      };
    }
  }

  /**
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private ApiClient apiClient;
    private ApiClientFactory apiClientFactory;
    private String projectEntityId;
    private String projectTaskId;
    private String resourceTicketEntityId;
    private String resourceTicketTaskId;
    private AllocateTenantResourcesTaskService.State startState;
    private TasksApi tasksApi;
    private String tenantEntityId;
    private TenantsApi tenantsApi;
    private String tenantTaskId;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() {
      apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();
      projectEntityId = UUID.randomUUID().toString();
      projectTaskId = UUID.randomUUID().toString();
      resourceTicketEntityId = UUID.randomUUID().toString();
      resourceTicketTaskId = UUID.randomUUID().toString();
      tenantEntityId = UUID.randomUUID().toString();
      tenantTaskId = UUID.randomUUID().toString();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "COMPLETED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync(resourceTicketTaskId, resourceTicketEntityId, "COMPLETED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantEntityId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync(projectTaskId, projectEntityId, "COMPLETED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantEntityId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.stage != TaskState.TaskStage.STARTED);

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(0));
      assertThat(finalState.tenantId, is(tenantEntityId));
      assertThat(finalState.resourceTicketId, is(resourceTicketEntityId));
      assertThat(finalState.projectId, is(projectEntityId));
    }

    @Test
    public void testSuccessAfterPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(tenantTaskId), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantEntityId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(resourceTicketTaskId), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync(projectTaskId, projectEntityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantEntityId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(projectTaskId, projectEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(projectTaskId, projectEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(projectTaskId, projectEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(projectTaskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.stage != TaskState.TaskStage.STARTED);

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(0));
      assertThat(finalState.tenantId, is(tenantEntityId));
      assertThat(finalState.resourceTicketId, is(resourceTicketEntityId));
      assertThat(finalState.projectId, is(projectEntityId));
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateTenantFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "STARTED"))
          .doAnswer(MockHelper.mockCreateTenantAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(tenantTaskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.stage != TaskState.TaskStage.STARTED);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
      assertThat(finalState.controlFlags, is(0));
      assertThat(finalState.tenantId, nullValue());
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateResourceTicketFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(tenantTaskId), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantEntityId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(resourceTicketTaskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.stage != TaskState.TaskStage.STARTED);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
      assertThat(finalState.controlFlags, is(0));
      assertThat(finalState.tenantId, is(tenantEntityId));
      assertThat(finalState.resourceTicketId, nullValue());
    }

    @Test(dataProvider = "TaskFailureResponses")
    public void testCreateProjectFailure(Task response) throws Throwable {

      doAnswer(MockHelper.mockCreateTenantAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(tenantTaskId, tenantEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(tenantTaskId), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq(tenantEntityId), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(resourceTicketTaskId, resourceTicketEntityId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq(resourceTicketTaskId), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync(projectTaskId, projectEntityId, "QUEUED"))
          .when(tenantsApi)
          .createProjectAsync(eq(tenantEntityId), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync(projectTaskId, projectEntityId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync(projectTaskId, projectEntityId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(response))
          .when(tasksApi)
          .getTaskAsync(eq(projectTaskId), Matchers.<FutureCallback<Task>>any());

      AllocateTenantResourcesTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateTenantResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateTenantResourcesTaskService.State.class,
              (state) -> state.taskState.stage != TaskState.TaskStage.STARTED);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(response)));
      assertThat(finalState.controlFlags, is(0));
      assertThat(finalState.tenantId, is(tenantEntityId));
      assertThat(finalState.resourceTicketId, is(resourceTicketEntityId));
      assertThat(finalState.projectId, nullValue());
    }

    @DataProvider(name = "TaskFailureResponses")
    public Object[][] getTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 2, "errorCode", "errorMessage")},
      };
    }
  }

  private AllocateTenantResourcesTaskService.State buildValidStartState(
      TaskState.TaskStage startStage,
      AllocateTenantResourcesTaskService.TaskState.SubStage startSubStage) {

    AllocateTenantResourcesTaskService.State startState = new AllocateTenantResourcesTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
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
