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

import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.hamcrest.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * This class implements tests for {@link AllocateClusterManagerResourcesTaskServiceTest} class.
 */
public class AllocateClusterManagerResourcesTaskServiceTest {

  private TestHost host;
  private AllocateClusterManagerResourcesTaskService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private AllocateClusterManagerResourcesTaskService.State buildValidStartupState() throws Throwable {
    return buildValidStartupState(TaskState.TaskStage.CREATED, null);
  }

  private AllocateClusterManagerResourcesTaskService.State buildValidStartupState(
      TaskState.TaskStage stage,
      AllocateClusterManagerResourcesTaskService.TaskState.SubStage subStage) throws Throwable {
    AllocateClusterManagerResourcesTaskService.State state = ReflectionUtils.buildValidStartState(
        AllocateClusterManagerResourcesTaskService.State.class);
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  private AllocateClusterManagerResourcesTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED, null);
  }

  private AllocateClusterManagerResourcesTaskService.State buildValidPatchState(
      TaskState.TaskStage stage,
      AllocateClusterManagerResourcesTaskService.TaskState.SubStage subStage) {
    AllocateClusterManagerResourcesTaskService.State state = new AllocateClusterManagerResourcesTaskService.State();
    state.taskState = new AllocateClusterManagerResourcesTaskService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new AllocateClusterManagerResourcesTaskService();
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
      service = new AllocateClusterManagerResourcesTaskService();
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
    public void testMinimalStartState(
        TaskState.TaskStage stage,
        AllocateClusterManagerResourcesTaskService.TaskState.SubStage subStage) throws Throwable {

      AllocateClusterManagerResourcesTaskService.State startState = buildValidStartupState(stage, subStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      AllocateClusterManagerResourcesTaskService.State savedState = host.getServiceState(
          AllocateClusterManagerResourcesTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null}
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
      AllocateClusterManagerResourcesTaskService.State startState = ReflectionUtils.buildValidStartState(
          AllocateClusterManagerResourcesTaskService.State.class);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          AllocateClusterManagerResourcesTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
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
      service = new AllocateClusterManagerResourcesTaskService();
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
        AllocateClusterManagerResourcesTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        AllocateClusterManagerResourcesTaskService.TaskState.SubStage targetSubStage)
        throws Throwable {

      AllocateClusterManagerResourcesTaskService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      AllocateClusterManagerResourcesTaskService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      AllocateClusterManagerResourcesTaskService.State savedState =
          host.getServiceState(AllocateClusterManagerResourcesTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        AllocateClusterManagerResourcesTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        AllocateClusterManagerResourcesTaskService.TaskState.SubStage targetSubStage)
        throws Throwable {

      AllocateClusterManagerResourcesTaskService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      AllocateClusterManagerResourcesTaskService.State patchState = buildValidPatchState(targetStage, targetSubStage);
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
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},

          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_OTHER_VM_FLAVOR},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_VM_DISK_FLAVOR},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      AllocateClusterManagerResourcesTaskService.State startState = buildValidStartupState(
          TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      AllocateClusterManagerResourcesTaskService.State patchState = buildValidPatchState(
          TaskState.TaskStage.STARTED,
          AllocateClusterManagerResourcesTaskService.TaskState.SubStage.CREATE_MASTER_VM_FLAVOR);

      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI))
          .setBody(patchState);

      host.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AllocateClusterManagerResourcesTaskService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the create VM task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private AllocateClusterManagerResourcesTaskService.State startState;

    private DeployerContext deployerContext;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;
    private ApiClient apiClient;
    private FlavorApi flavorApi;
    private Task taskReturnedByCreateFlavor;

    @BeforeMethod
    public void setUpClass() throws Throwable {
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmTaskServiceTest.class.getResource(configFilePath).getPath())
          .getDeployerContext();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      apiClient = mock(ApiClient.class);
      flavorApi = mock(FlavorApi.class);
      apiClientFactory = mock(ApiClientFactory.class);
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      doReturn(apiClient).when(apiClientFactory).create();
      doReturn(apiClient).when(apiClientFactory).create(any(String.class));
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      taskReturnedByCreateFlavor = TestHelper.createCompletedApifeTask("CREATE_FLAVOR");

      startState = buildValidStartupState();
      startState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
      taskReturnedByCreateFlavor = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    private TestEnvironment createTestEnvironment() throws Throwable {
      return new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .listeningExecutorService(listeningExecutorService)
          .apiClientFactory(apiClientFactory)
          .hostCount(1)
          .build();
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {
      machine = createTestEnvironment();
      mockCreateFlavor(true);
      setupServiceDocuments();

      AllocateClusterManagerResourcesTaskService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateClusterManagerResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateClusterManagerResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureCreateFlavorFails() throws Throwable {
      machine = createTestEnvironment();
      mockCreateFlavor(false);
      setupServiceDocuments();

      AllocateClusterManagerResourcesTaskService.State finalState =
          machine.callServiceAndWaitForState(
              AllocateClusterManagerResourcesTaskFactoryService.SELF_LINK,
              startState,
              AllocateClusterManagerResourcesTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, Matchers.containsString("create flavor failed"));
    }

    private void setupServiceDocuments() throws Throwable {
      VmService.State vmState = ReflectionUtils.buildValidStartState(VmService.State.class);
      vmState.ipAddress = "1.2.3.4";
      TestHelper.createVmService(machine, vmState);
      VmService.State vmSavedState = TestHelper.createVmService(machine, vmState);

      ContainerTemplateService.State containerTemplateState =
          ReflectionUtils.buildValidStartState(ContainerTemplateService.State.class);
      containerTemplateState.name = ContainersConfig.ContainerType.PhotonControllerCore.name();
      containerTemplateState.cpuCount = 1;
      containerTemplateState.memoryMb = 1024L;
      containerTemplateState.diskGb = 1;
      ContainerTemplateService.State containerTemplateSavedState = TestHelper.createContainerTemplateService(machine,
          containerTemplateState);

      ContainerService.State containerState = ReflectionUtils.buildValidStartState(ContainerService.State.class);
      containerState.vmServiceLink = vmSavedState.documentSelfLink;
      containerState.containerTemplateServiceLink = containerTemplateSavedState.documentSelfLink;
      TestHelper.createContainerService(machine, containerState);
    }

    private void mockCreateFlavor(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateFlavor);
            return null;
          }
        }).when(flavorApi).createAsync(any(FlavorCreateSpec.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("create flavor failed"))
            .when(flavorApi).createAsync(any(FlavorCreateSpec.class), any(FutureCallback.class));
      }
    }
  }
}
