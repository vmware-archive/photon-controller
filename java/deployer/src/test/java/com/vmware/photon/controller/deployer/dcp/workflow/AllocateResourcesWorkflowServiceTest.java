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

import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link AllocateResourcesWorkflowService}.
 */
public class AllocateResourcesWorkflowServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private AllocateResourcesWorkflowService allocateResourcesWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      allocateResourcesWorkflowService = new AllocateResourcesWorkflowService();
    }

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(allocateResourcesWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private AllocateResourcesWorkflowService allocateResourcesWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateResourcesWorkflowService = new AllocateResourcesWorkflowService();
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

    @Test
    public void testMinimalStartState() throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateManagementPlaneLayoutWorkflowService.State savedState =
          testHost.getServiceState(CreateManagementPlaneLayoutWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(
        TaskState.TaskStage startStage,
        AllocateResourcesWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.STARTED, AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.STARTED, AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "StartStagesWhichTransitionToStarted")
    public void testStartStateTransitionsToStarted(
        TaskState.TaskStage startStage,
        AllocateResourcesWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AllocateResourcesWorkflowService.State savedState =
          testHost.getServiceState(AllocateResourcesWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "StartStagesWhichTransitionToStarted")
    public Object[][] getStartStagesWhichTransitionToStarted() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
      };
    }

    @Test(dataProvider = "FinalStartStages")
    public void testFinalStartState(TaskState.TaskStage startStage) throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AllocateResourcesWorkflowService.State savedState =
          testHost.getServiceState(AllocateResourcesWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(startStage));
    }

    @DataProvider(name = "FinalStartStages")
    public Object[][] getFinalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "fieldNamesWithMissingValue", expectedExceptions = XenonRuntimeException.class)
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      fail("Expect to throw exception on invalid start state");
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          AllocateResourcesWorkflowService.State.class,
          NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private AllocateResourcesWorkflowService allocateResourcesWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateResourcesWorkflowService = new AllocateResourcesWorkflowService();
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
        AllocateResourcesWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        AllocateResourcesWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);

      AllocateResourcesWorkflowService.State patchState =
          allocateResourcesWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      AllocateResourcesWorkflowService.State savedState =
          testHost.getServiceState(AllocateResourcesWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{

          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
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
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        AllocateResourcesWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        AllocateResourcesWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);
      AllocateResourcesWorkflowService.State patchState =
          allocateResourcesWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{

          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},

          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
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
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.ALLOCATE_TENANT_RESOURCES},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.UPDATE_VMS},
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
      };
    }

    @Test(dataProvider = "InvalidPatchStateAttributes", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateInvalidAttributeSet(String attributeName) throws Throwable {
      startService(TaskState.TaskStage.CREATED, null);

      AllocateResourcesWorkflowService.State patchState =
          allocateResourcesWorkflowService.buildPatch(
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              null);

      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidPatchStateAttributes")
    public Object[][] getInvalidPatchStateAttributes() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateResourcesWorkflowService.State.class, Immutable.class));
    }

    private void startService(
        TaskState.TaskStage startStage,
        AllocateResourcesWorkflowService.TaskState.SubStage subStage) throws Throwable {
      AllocateResourcesWorkflowService.State startState = buildValidStartState(startStage, subStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateResourcesWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private ApiClientFactory apiClientFactory;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private FlavorApi flavorApi;
    private AllocateResourcesWorkflowService.State startState;
    private TasksApi tasksApi;
    private TenantsApi tenantsApi;
    private TestEnvironment testEnvironment;
    private Set<VmService.State> vmStates;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostCount(1)
          .build();

      startState = buildValidStartState();
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      TestHelper.createDeploymentService(cloudStoreEnvironment);

      HostService.State hostState =
          TestHelper.createHostService(cloudStoreEnvironment, Collections.singleton(UsageTag.MGMT.name()));

      Set<ContainerTemplateService.State> templateStates = new HashSet<>(3);
      for (int i = 0; i < 3; i++) {
        templateStates.add(TestHelper.createContainerTemplateService(testEnvironment));
      }

      vmStates = new HashSet<>(3);
      for (int i = 0; i < 3; i++) {
        vmStates.add(TestHelper.createVmService(testEnvironment, hostState));
      }

      for (ContainerTemplateService.State templateState : templateStates) {
        for (VmService.State vmState : vmStates) {
          TestHelper.createContainerService(testEnvironment, templateState, vmState);
        }
      }

      ApiClient apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      flavorApi = mock(FlavorApi.class);
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      tenantsApi = mock(TenantsApi.class);
      doReturn(tenantsApi).when(apiClient).getTenantsApi();

      doAnswer(MockHelper.mockCreateFlavorAsync("FLAVOR_TASK_ID", "FLAVOR_ID", "COMPLETED"))
          .when(flavorApi)
          .createAsync(any(FlavorCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateTenantAsync("TENANT_TASK_ID", "TENANT_ID", "COMPLETED"))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateResourceTicketAsync("RESOURCE_TICKET_TASK_ID", "RESOURCE_TICKET_ID", "COMPLETED"))
          .when(tenantsApi)
          .createResourceTicketAsync(eq("TENANT_ID"), any(ResourceTicketCreateSpec.class),
              Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateProjectAsync("PROJECT_TASK_ID", "PROJECT_ID", "COMPLETED"))
          .when(tenantsApi)
          .createProjectAsync(eq("TENANT_ID"), any(ProjectCreateSpec.class), Matchers.<FutureCallback<Task>>any());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
    }

    @Test
    public void testSuccess() throws Throwable {

      AllocateResourcesWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.tenantId, is("TENANT_ID"));
      assertThat(finalState.resourceTicketId, is("RESOURCE_TICKET_ID"));
      assertThat(finalState.projectId, is("PROJECT_ID"));

      for (VmService.State vmState : vmStates) {
        VmService.State state = testEnvironment.getServiceState(vmState.documentSelfLink, VmService.State.class);
        assertThat(state.projectServiceLink, is(ProjectServiceFactory.SELF_LINK + "/PROJECT_ID"));
      }
    }

    @Test
    public void testCreateFlavorFailure() throws Throwable {

      Task failedTask = ApiTestUtils.createFailingTask(1, 1, "errorCode", "errorMessage");

      doAnswer(MockHelper.mockCreateFlavorAsync(failedTask))
          .when(flavorApi)
          .createAsync(any(FlavorCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      AllocateResourcesWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(failedTask)));
    }

    @Test
    public void testAllocateTenantResourcesFailure() throws Throwable {

      Task failedTask = ApiTestUtils.createFailingTask(1, 1, "errorCode", "errorMessage");

      doAnswer(MockHelper.mockCreateTenantAsync(failedTask))
          .when(tenantsApi)
          .createAsync(eq(Constants.TENANT_NAME), Matchers.<FutureCallback<Task>>any());

      AllocateResourcesWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is(ApiUtils.getErrors(failedTask)));
    }
  }

  private AllocateResourcesWorkflowService.State buildValidStartState() {
    AllocateResourcesWorkflowService.State startState = new AllocateResourcesWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;

    return startState;
  }

  private AllocateResourcesWorkflowService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      AllocateResourcesWorkflowService.TaskState.SubStage substage) {
    AllocateResourcesWorkflowService.State startState = buildValidStartState();
    startState.taskState = new AllocateResourcesWorkflowService.TaskState();
    startState.taskState.stage = taskStage;
    startState.taskState.subStage = substage;
    return startState;
  }
}
