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
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.FlavorService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.ArgumentMatcher;
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
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
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
 * This class implements tests for {@link CreateFlavorTaskService} class.
 */
public class CreateFlavorTaskServiceTest {

  private TestHost host;
  private CreateFlavorTaskService service;

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateFlavorTaskService();
    }

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
      service = new CreateFlavorTaskService();
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
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateFlavorTaskService.State savedState = host.getServiceState(
          CreateFlavorTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
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

    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateFlavorTaskService.State savedState = host.getServiceState(CreateFlavorTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
    }

    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateFlavorTaskService.State savedState = host.getServiceState(CreateFlavorTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.vmServiceLink, is("vmServiceLink"));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateFlavorTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateFlavorTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }

    @Test
    public void testChangeQueryTaskInterval() throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState();
      startState.queryTaskInterval = 12345;

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateFlavorTaskService.State savedState = host.getServiceState(
          CreateFlavorTaskService.State.class);
      assertThat(savedState.queryTaskInterval, is(12345));
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
      service = new CreateFlavorTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateFlavorTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateFlavorTaskService.State savedState = host.getServiceState(CreateFlavorTaskService.State.class);
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

    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateFlavorTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateFlavorTaskService.State patchState = buildValidPatchState(targetStage);
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

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateFlavorTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateFlavorTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      if (declaredField.getType() == Boolean.class) {
        declaredField.set(patchState, Boolean.FALSE);
      } else if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateFlavorTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
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
    private CreateFlavorTaskService.State startState;
    private ApiClient apiClient;
    private FlavorApi flavorApi;
    private TasksApi tasksApi;
    private Task taskReturnedByCreateVmFlavor;
    private Task taskReturnedByGetCreateVmFlavorTask;
    private Task taskReturnedByCreateDiskFlavor;
    private Task taskReturnedByGetCreateDiskFlavorTask;
    private VmService.State vmServiceState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      apiClient = mock(ApiClient.class);
      flavorApi = mock(FlavorApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();

      apiClientFactory = mock(ApiClientFactory.class);
      doReturn(apiClient).when(apiClientFactory).create();

      startState = buildValidStartupState();
      startState.controlFlags = 0;
      startState.queryTaskInterval = 10;

      taskReturnedByCreateVmFlavor = new Task();
      taskReturnedByCreateVmFlavor.setId("createVmFlavorTaskId");
      taskReturnedByCreateVmFlavor.setState("STARTED");

      taskReturnedByGetCreateVmFlavorTask = new Task();
      taskReturnedByGetCreateVmFlavorTask.setId("createVmFlavorTaskId");
      taskReturnedByGetCreateVmFlavorTask.setState("COMPLETED");

      taskReturnedByCreateDiskFlavor = new Task();
      taskReturnedByCreateDiskFlavor.setId("createDiskFlavorTaskId");
      taskReturnedByCreateDiskFlavor.setState("STARTED");

      taskReturnedByGetCreateDiskFlavorTask = new Task();
      taskReturnedByGetCreateDiskFlavorTask.setId("createDiskFlavorTaskId");
      taskReturnedByGetCreateDiskFlavorTask.setState("COMPLETED");
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

    @Test
    public void testEndToEndSuccess() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateFlavorTaskFactoryService.SELF_LINK,
              startState,
              CreateFlavorTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      VmService.State vmServiceFinalState =
          machine.getServiceState(vmServiceState.documentSelfLink, VmService.State.class);
      assertThat(vmServiceFinalState.flavorServiceLink, notNullValue());

      FlavorService.State vmFlavorState =
          machine.getServiceState(vmServiceFinalState.flavorServiceLink, FlavorService.State.class);
      assertThat(vmFlavorState.vmFlavorName, is("mgmt-vm-NAME"));
      assertThat(vmFlavorState.diskFlavorName, is("mgmt-vm-disk-NAME"));
      assertThat(vmFlavorState.cpuCount, is(2));
      assertThat(vmFlavorState.memoryMb, is(6144));
      assertThat(vmFlavorState.diskGb, is(12));
    }

    @Test
    public void testEndToEndSuccessWithVmResourceOverwrite() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(true, false);

      CreateFlavorTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateFlavorTaskFactoryService.SELF_LINK,
              startState,
              CreateFlavorTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      VmService.State vmServiceFinalState =
          machine.getServiceState(vmServiceState.documentSelfLink, VmService.State.class);
      assertThat(vmServiceFinalState.flavorServiceLink, notNullValue());

      FlavorService.State vmFlavorState =
          machine.getServiceState(vmServiceFinalState.flavorServiceLink, FlavorService.State.class);
      assertThat(vmFlavorState.vmFlavorName, is("mgmt-vm-NAME"));
      assertThat(vmFlavorState.diskFlavorName, is("mgmt-vm-disk-NAME"));
      assertThat(vmFlavorState.cpuCount, is(1));
      assertThat(vmFlavorState.memoryMb, is(2));
      assertThat(vmFlavorState.diskGb, is(3));
    }

    @Test
    public void testEndToEndSuccessWithHostConfig() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, true);

      CreateFlavorTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateFlavorTaskFactoryService.SELF_LINK,
              startState,
              CreateFlavorTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      VmService.State vmServiceFinalState =
          machine.getServiceState(vmServiceState.documentSelfLink, VmService.State.class);
      assertThat(vmServiceFinalState.flavorServiceLink, notNullValue());

      FlavorService.State vmFlavorState =
          machine.getServiceState(vmServiceFinalState.flavorServiceLink, FlavorService.State.class);
      assertThat(vmFlavorState.vmFlavorName, is("mgmt-vm-NAME"));
      assertThat(vmFlavorState.diskFlavorName, is("mgmt-vm-disk-NAME"));
      assertThat(vmFlavorState.cpuCount, is(7));
      assertThat(vmFlavorState.memoryMb, is(7168));
      assertThat(vmFlavorState.diskGb, is(12));
    }

    @Test
    public void testEndToEndFailureZeroContainerService() throws Throwable {
      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidVmServiceDocument(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Document links is empty"));
    }

    @Test
    public void testEndToEndFailureCreateVmFlavorThrowsException() throws Throwable {

      setupCreateVmFlavorCall(true, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CreateVmFlavor"));
    }

    @Test
    public void testEndToEndFailureCreateDiskFlavorThrowsException() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(true, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during CreateDiskFlavor"));
    }

    @Test
    public void testEndToEndFailureGetCreateVmFlavorTaskThrowsException() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(true, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailureGetCreateDiskFlavorTaskThrowsException() throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(true, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureCreateVmFlavorReturnsErrorTaskState(final Task task) throws Throwable {

      setupCreateVmFlavorCall(false, task);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureGetCreateVmFlavorTaskReturnsErrorTaskState(final Task task) throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, task);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureCreateDiskFlavorReturnsErrorTaskState(final Task task) throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, task);
      setupGetCreateDiskFlavorTaskCall(false, taskReturnedByGetCreateDiskFlavorTask);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "errorTasksResponses")
    public void testEndToEndFailureGetCreateDiskFlavorTaskReturnsErrorTaskState(final Task task) throws Throwable {

      setupCreateVmFlavorCall(false, taskReturnedByCreateVmFlavor);
      setupGetCreateVmFlavorTaskCall(false, taskReturnedByGetCreateVmFlavorTask);
      setupCreateDiskFlavorCall(false, taskReturnedByCreateDiskFlavor);
      setupGetCreateDiskFlavorTaskCall(false, task);

      machine = createTestEnvironment(deployerContext, apiClientFactory, cloudStoreMachine.getServerSet(), 1);
      setupValidServiceDocuments(false, false);

      CreateFlavorTaskService.State finalState = machine.callServiceAndWaitForState(
          CreateFlavorTaskFactoryService.SELF_LINK,
          startState,
          CreateFlavorTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "errorTasksResponses")
    public Object[][] getErrorTasksResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createUnknownTask()}
      };
    }

    private void setupValidVmServiceDocument(boolean overwriteVmResource, boolean addHostResource) throws Throwable {

      HostService.State hostStartState =
          TestHelper.getHostServiceStartState(Collections.singleton(UsageTag.MGMT.name()), HostState.READY);
      if (overwriteVmResource) {
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE, "1");
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE, "2");
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE, "3");
      }
      if (addHostResource) {
        hostStartState.cpuCount = 8;
        hostStartState.memoryMb = 8192;
      }

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine, hostStartState);
      vmServiceState = TestHelper.createVmService(machine, hostServiceState);
      startState.vmServiceLink = vmServiceState.documentSelfLink;
    }

    private void setupValidContainerAndContainerTemplateDocuments() throws Throwable {
      ContainerTemplateService.State containerTemplateSavedState1 = TestHelper.createContainerTemplateService(machine);
      ContainerTemplateService.State containerTemplateState2 = TestHelper.getContainerTemplateServiceStartState();
      containerTemplateState2.cpuCount = 2;
      containerTemplateState2.memoryMb = 4096;
      containerTemplateState2.diskGb = 8;
      ContainerTemplateService.State containerTemplateSavedState2 = TestHelper.createContainerTemplateService
          (machine, containerTemplateState2);
      TestHelper.createContainerService(machine, containerTemplateSavedState1, vmServiceState);
      TestHelper.createContainerService(machine, containerTemplateSavedState2, vmServiceState);
    }

    private void setupValidServiceDocuments(boolean overwriteVmResource, boolean addHostResource) throws Throwable {
      setupValidVmServiceDocument(overwriteVmResource, addHostResource);
      setupValidContainerAndContainerTemplateDocuments();
    }

    private void setupCreateVmFlavorCall(boolean isThrow, final Task task) throws Throwable {

      ArgumentMatcher<FlavorCreateSpec> vmFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().equals("mgmt-vm-NAME");
        }
      };

      if (isThrow) {
        doThrow(new RuntimeException("Exception during CreateVmFlavor"))
            .when(flavorApi).createAsync(argThat(vmFlavorSpecMatcher), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
            return null;
          }
        }).when(flavorApi).createAsync(argThat(vmFlavorSpecMatcher), any(FutureCallback.class));
      }
    }

    private void setupGetCreateVmFlavorTaskCall(boolean isThrow, final Task task) throws Throwable {

      if (isThrow) {
        doThrow(new RuntimeException("Exception during GetCreateVmFlavorTask"))
            .when(tasksApi).getTaskAsync(eq("createVmFlavorTaskId"), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
            return null;
          }
        }).when(tasksApi).getTaskAsync(eq("createVmFlavorTaskId"), any(FutureCallback.class));
      }
    }

    private void setupCreateDiskFlavorCall(boolean isThrow, final Task task) throws Throwable {

      ArgumentMatcher<FlavorCreateSpec> diskFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().equals("mgmt-vm-disk-NAME");
        }
      };

      if (isThrow) {
        doThrow(new RuntimeException("Exception during CreateDiskFlavor"))
            .when(flavorApi).createAsync(argThat(diskFlavorSpecMatcher), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
            return null;
          }
        }).when(flavorApi).createAsync(argThat(diskFlavorSpecMatcher), any(FutureCallback.class));
      }
    }

    private void setupGetCreateDiskFlavorTaskCall(boolean isThrow, final Task task) throws Throwable {

      if (isThrow) {
        doThrow(new RuntimeException("Exception during GetCreateDiskFlavorTask"))
            .when(tasksApi).getTaskAsync(eq("createDiskFlavorTaskId"), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
            return null;
          }
        }).when(tasksApi).getTaskAsync(eq("createDiskFlavorTaskId"), any(FutureCallback.class));
      }
    }
  }

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateFlavorTaskService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED);
  }

  private CreateFlavorTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {

    CreateFlavorTaskService.State state = new CreateFlavorTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.vmServiceLink = "vmServiceLink";
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  private CreateFlavorTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private CreateFlavorTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    CreateFlavorTaskService.State state = new CreateFlavorTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      DeployerContext deployerContext,
      ApiClientFactory apiClientFactory,
      ServerSet cloudStoreSet,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .apiClientFactory(apiClientFactory)
        .cloudServerSet(cloudStoreSet)
        .hostCount(hostCount)
        .build();
  }
}
