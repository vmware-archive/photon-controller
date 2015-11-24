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
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskServiceTest;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT},
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

    @Test(dataProvider = "fieldNamesWithMissingValue", expectedExceptions = IllegalStateException.class)
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

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = IllegalStateException.class)
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
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT},

          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT},

          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_FLAVORS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_TENANT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_RESOURCE_TICKET},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateResourcesWorkflowService.TaskState.SubStage.CREATE_PROJECT},

          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidPatchStateAttributes", expectedExceptions = IllegalStateException.class)
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
   * End-to-end tests for the create flavor task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private DeployerContext deployerContext;
    private ApiClientFactory apiClientFactory;

    private AllocateResourcesWorkflowService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();
      TestHelper.createDeploymentService(cloudStoreMachine);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      apiClientFactory = mock(ApiClientFactory.class);

      startState = buildValidStartState();
      startState.controlFlags = 0;
      startState.taskPollDelay = 10;
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
      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {

      setupApiClient(false, false, false, false);
      machine = createTestEnvironment(deployerContext, apiClientFactory,
          cloudStoreMachine.getServerSet(), hostCount);
      setupServiceDocuments();

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      TenantService.State tenantServiceFinalState =
          cloudStoreMachine.getServiceState(finalState.tenantServiceLink, TenantService.State.class);
      assertThat(tenantServiceFinalState.name, is(Constants.TENANT_NAME));

      ResourceTicketService.State resourceTicketServiceFinalState =
          cloudStoreMachine.getServiceState(finalState.resourceTicketServiceLink, ResourceTicketService.State.class);
      assertThat(resourceTicketServiceFinalState.name, is(Constants.RESOURCE_TICKET_NAME));

      ProjectService.State projectServiceFinalState =
          cloudStoreMachine.getServiceState(finalState.projectServiceLink, ProjectService.State.class);
      assertThat(projectServiceFinalState.name, is(Constants.PROJECT_NAME));

      for (String vmServiceLink : finalState.vmServiceLinks) {
        VmService.State vmServiceFinalState =
            machine.getServiceState(vmServiceLink, VmService.State.class);
        assertThat(vmServiceFinalState.projectServiceLink, is(projectServiceFinalState.documentSelfLink));
      }
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureZeroVmServiceEntity(Integer hostCount) throws Throwable {

      setupApiClient(false, false, false, false);
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(),
          hostCount);

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Found 0 vms"));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateFlavorThrowsException(Integer hostCount) throws Throwable {

      setupApiClient(true, false, false, false);
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(),
          hostCount);
      setupServiceDocuments();

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CREATE_FLAVORS"));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateTenantThrowsException(Integer hostCount) throws Throwable {

      setupApiClient(false, true, false, false);
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(),
          hostCount);
      setupServiceDocuments();

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CREATE_TENANT"));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateResourceTicketThrowsException(Integer hostCount) throws Throwable {

      setupApiClient(false, false, true, false);
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(),
          hostCount);
      setupServiceDocuments();

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CREATE_RESOURCE_TICKET"));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateProjectThrowsException(Integer hostCount) throws Throwable {

      setupApiClient(false, false, false, true);
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(),
          hostCount);
      setupServiceDocuments();

      AllocateResourcesWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateResourcesWorkflowFactoryService.SELF_LINK,
              startState,
              AllocateResourcesWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CREATE_PROJECT"));
    }

    private void setupApiClient(
        boolean failCreateFlavors,
        boolean failCreateTenant,
        boolean failCreateResourceTicket,
        boolean failCreateProject) throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);;
      FlavorApi flavorApi = mock(FlavorApi.class);
      TenantsApi tenantsApi = mock(TenantsApi.class);
      final Task taskReturnedByCreateFlavor;
      final Task taskReturnedByCreateDiskFlavor;
      final Task taskReturnedByCreateTenant;
      final Task taskReturnedByCreateResourceTicket;
      final Task taskReturnedByCreateProject;

      doReturn(flavorApi).when(apiClient).getFlavorApi();
      doReturn(tenantsApi).when(apiClient).getTenantsApi();

      taskReturnedByCreateFlavor = new Task();
      taskReturnedByCreateFlavor.setId("createFlavorTaskId");
      taskReturnedByCreateFlavor.setState("COMPLETED");
      FlavorService.State flavorService = TestHelper.createFlavor(cloudStoreMachine, null);
      Task.Entity taskEntity = new Task.Entity();
      taskEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(flavorService.documentSelfLink));
      taskReturnedByCreateFlavor.setEntity(taskEntity);

      taskReturnedByCreateDiskFlavor = new Task();
      taskReturnedByCreateDiskFlavor.setId("createDiskFlavorTaskId");
      taskReturnedByCreateDiskFlavor.setState("COMPLETED");

      taskReturnedByCreateTenant = new Task();
      taskReturnedByCreateTenant.setId("createTenantTaskId");
      taskReturnedByCreateTenant.setState("COMPLETED");

      TenantService.State tenantState = TestHelper.createTenant(cloudStoreMachine);
      String tenantId = ServiceUtils.getIDFromDocumentSelfLink(tenantState.documentSelfLink);
      Task.Entity tenantEntity = new Task.Entity();
      tenantEntity.setId(tenantId);
      taskReturnedByCreateTenant.setEntity(tenantEntity);

      taskReturnedByCreateResourceTicket = new Task();
      taskReturnedByCreateResourceTicket.setId("createResourceTicketTaskId");
      taskReturnedByCreateResourceTicket.setState("COMPLETED");
      ResourceTicketService.State resourceState = TestHelper.createResourceTicket(tenantId, cloudStoreMachine);
      String rtId = ServiceUtils.getIDFromDocumentSelfLink(resourceState.documentSelfLink);
      Task.Entity resourceTicketEntity = new Task.Entity();
      resourceTicketEntity.setId(rtId);
      taskReturnedByCreateResourceTicket.setEntity(resourceTicketEntity);

      taskReturnedByCreateProject = new Task();
      taskReturnedByCreateProject.setId("createProjectTaskId");
      taskReturnedByCreateProject.setState("COMPLETED");
      Task.Entity projectEntity = new Task.Entity();
      ProjectService.State projectState = TestHelper.createProject(tenantId, rtId, cloudStoreMachine);
      projectEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(projectState.documentSelfLink));
      taskReturnedByCreateProject.setEntity(projectEntity);

      if (failCreateFlavors) {
        doThrow(new RuntimeException("Exception during CREATE_FLAVORS"))
            .when(flavorApi).createAsync(any(FlavorCreateSpec.class), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateFlavor);
            return null;
          }
        })
            .when(flavorApi).createAsync(any(FlavorCreateSpec.class), any(FutureCallback.class));;
      }

      if (failCreateTenant) {
        doThrow(new RuntimeException("Exception during CREATE_TENANT"))
            .when(tenantsApi).createAsync(any(String.class), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateTenant);
            return null;
          }
        })
          .when(tenantsApi).createAsync(any(String.class), any(FutureCallback.class));
      }

      if (failCreateResourceTicket) {
        doThrow(new RuntimeException("Exception during CREATE_RESOURCE_TICKET"))
            .when(tenantsApi).createResourceTicketAsync(
            any(String.class), any(ResourceTicketCreateSpec.class), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateResourceTicket);
            return null;
          }
        })
          .when(tenantsApi).createResourceTicketAsync(
            any(String.class), any(ResourceTicketCreateSpec.class), any(FutureCallback.class));
      }

      if (failCreateProject) {
        doThrow(new RuntimeException("Exception during CREATE_PROJECT"))
            .when(tenantsApi).createProjectAsync(
              any(String.class), any(ProjectCreateSpec.class), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateProject);
            return null;
          }
        })
          .when(tenantsApi).createProjectAsync(
            any(String.class), any(ProjectCreateSpec.class), any(FutureCallback.class));
      }

      doReturn(apiClient).when(apiClientFactory).create();
    }

    private void setupServiceDocuments() throws Throwable {

      HostService.State hostStartState =
          TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));

      VmService.State vmServiceState = TestHelper.createVmService(machine, hostStartState);
      ContainerTemplateService.State containerTemplateSavedState1 = TestHelper.createContainerTemplateService(machine);
      ContainerTemplateService.State containerTemplateSavedState2 = TestHelper.createContainerTemplateService(machine);
      TestHelper.createContainerService(machine, containerTemplateSavedState1, vmServiceState);
      TestHelper.createContainerService(machine, containerTemplateSavedState2, vmServiceState);
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
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
