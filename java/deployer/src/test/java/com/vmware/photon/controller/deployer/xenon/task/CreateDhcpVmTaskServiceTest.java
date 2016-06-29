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

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.XenonBasedHealthChecker;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ApiTestUtils;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.ApiUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements tests for {@link CreateDhcpVmTaskService} class.
 */
public class CreateDhcpVmTaskServiceTest {

  /**
   * This dummy test enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private CreateDhcpVmTaskService createDhcpVmTaskService;

    @BeforeMethod
    public void setUpTest() {
      createDhcpVmTaskService = new CreateDhcpVmTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(createDhcpVmTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link CreateDhcpVmTaskService#handleStart(Operation)} method.
   */
  public class HandleStartTest {

    private CreateDhcpVmTaskService createDhcpVmTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createDhcpVmTaskService = new CreateDhcpVmTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage taskStage,
                                    CreateDhcpVmTaskService.TaskState.SubStage subStage)
        throws Throwable {

      CreateDhcpVmTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State serviceState =
          testHost.getServiceState(CreateDhcpVmTaskService.State.class);

      assertThat(serviceState.vmServiceLink, is("VM_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.WAIT_FOR_VM_CREATION},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.UPDATE_METADATA},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.WAIT_FOR_METADATA_UPDATE},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.WAIT_FOR_ATTACH_ISO},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage taskStage,
                                           CreateDhcpVmTaskService.TaskState.SubStage subStage)
        throws Throwable {

      CreateDhcpVmTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State serviceState =
          testHost.getServiceState(CreateDhcpVmTaskService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(CreateDhcpVmTaskService.TaskState.SubStage.CREATE_VM));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.CREATE_VM},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(TaskState.TaskStage taskStage,
                                       CreateDhcpVmTaskService.TaskState.SubStage subStage)
        throws Throwable {

      CreateDhcpVmTaskService.State startState = buildValidStartState(taskStage, subStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State serviceState =
          testHost.getServiceState(CreateDhcpVmTaskService.State.class);

      assertThat(serviceState.taskState.stage, is(taskStage));
      assertThat(serviceState.taskState.subStage, is(subStage));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "OptionalFieldNames")
    public void testOptionalFieldValuePersisted(String fieldName) throws Throwable {
      CreateDhcpVmTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, ReflectionUtils.getDefaultAttributeValue(declaredField));
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State serviceState =
          testHost.getServiceState(CreateDhcpVmTaskService.State.class);

      assertThat(declaredField.get(serviceState), is(ReflectionUtils.getDefaultAttributeValue(declaredField)));
    }

    @DataProvider(name = "OptionalFieldNames")
    public Object[][] getOptionalFieldNames() {
      return new Object[][]{
          {"createVmTaskId"},
          {"createVmPollCount"},
          {"vmId"},
          {"updateVmMetadataTaskId"},
          {"updateVmMetadataPollCount"},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      CreateDhcpVmTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateDhcpVmTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link CreateDhcpVmTaskService#handlePatch(Operation)} method.
   */
  public class HandlePatchTest {

    private CreateDhcpVmTaskService createDhcpVmTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createDhcpVmTaskService = new CreateDhcpVmTaskService();
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
                                         CreateDhcpVmTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         CreateDhcpVmTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {

      CreateDhcpVmTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(CreateDhcpVmTaskService.buildPatch(patchStage, patchSubStage, null));

      op = testHost.sendRequestAndWait(patchOp);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State serviceState =
          testHost.getServiceState(CreateDhcpVmTaskService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(CreateDhcpVmTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           CreateDhcpVmTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           CreateDhcpVmTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {

      CreateDhcpVmTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(CreateDhcpVmTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(CreateDhcpVmTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      CreateDhcpVmTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(createDhcpVmTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      CreateDhcpVmTaskService.State patchState = CreateDhcpVmTaskService.buildPatch(
          TaskState.TaskStage.STARTED, CreateDhcpVmTaskService.TaskState.SubStage.CREATE_VM, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateDhcpVmTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link CreateDhcpVmTaskService} class.
   */
  public class EndToEndTest {

    private final ArgumentMatcher<FlavorCreateSpec> vmFlavorCreateSpecMatcher =
        new ArgumentMatcher<FlavorCreateSpec>() {
          @Override
          public boolean matches(Object argument) {
            return ((FlavorCreateSpec) argument).getKind().equals("vm");
          }
        };

    private final ArgumentMatcher<FlavorCreateSpec> diskFlavorCreateSpecMatcher =
        new ArgumentMatcher<FlavorCreateSpec>() {
          @Override
          public boolean matches(Object argument) {
            return ((FlavorCreateSpec) argument).getKind().equals("ephemeral-disk");
          }
        };

    private final Task failedTask = ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage");

    private ApiClientFactory apiClientFactory;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerTestConfig deployerTestConfig;
    private FlavorApi flavorApi;
    private ListeningExecutorService listeningExecutorService;
    private ProjectApi projectApi;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;
    private CreateDhcpVmTaskService.State startState;
    private TasksApi tasksApi;
    private TestEnvironment testEnvironment;
    private VmApi vmApi;
    private String vmId;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private XenonBasedHealthChecker healthChecker;

    @BeforeClass
    public void setUpClass() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerTestConfig =
          ConfigBuilder.build(DeployerTestConfig.class, this.getClass().getResource("/config.yml").getPath());
      TestHelper.setContainersConfig(deployerTestConfig);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      healthChecker = mock(XenonBasedHealthChecker.class);
      doReturn(healthChecker).when(healthCheckHelperFactory)
          .create(any(Service.class), anyInt(), anyString());


      testEnvironment = new TestEnvironment.Builder()
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerTestConfig.getDeployerContext())
          .dockerProvisionerFactory(null)
          .hostCount(1)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .build();

      FileUtils.copyDirectory(Paths.get(this.getClass().getResource("/configurations/").getPath()).toFile(),
          Paths.get(deployerTestConfig.getDeployerContext().getConfigDirectory()).toFile());
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);
      doReturn(apiClient).when(apiClientFactory).create();
      flavorApi = mock(FlavorApi.class);
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      projectApi = mock(ProjectApi.class);
      doReturn(projectApi).when(apiClient).getProjectApi();
      tasksApi = mock(TasksApi.class);
      doReturn(tasksApi).when(apiClient).getTasksApi();
      vmApi = mock(VmApi.class);
      doReturn(vmApi).when(apiClient).getVmApi();
      vmId = UUID.randomUUID().toString();

      doAnswer(MockHelper.mockCreateVmAsync("CREATE_VM_TASK_ID", vmId, "QUEUED"))
          .when(projectApi)
          .createVmAsync(anyString(), any(VmCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_VM_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_VM_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_VM_TASK_ID", vmId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockSetMetadataAsync("SET_METADATA_TASK_ID", vmId, "QUEUED"))
          .when(vmApi)
          .setMetadataAsync(anyString(), any(VmMetadata.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("SET_METADATA_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("SET_METADATA_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("SET_METADATA_TASK_ID", vmId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("SET_METADATA_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doReturn(TestHelper.createTask("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "QUEUED"))
          .when(vmApi)
          .uploadAndAttachIso(anyString(), anyString());

      doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("UPLOAD_AND_ATTACH_ISO_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockPerformStartOperationAsync("START_VM_TASK_ID", vmId, "QUEUED"))
          .when(vmApi)
          .performStartOperationAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockGetTaskAsync("START_VM_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("START_VM_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync("START_VM_TASK_ID", vmId, "COMPLETED"))
          .when(tasksApi)
          .getTaskAsync(eq("START_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      doReturn(new ServiceConfigurator()).when(serviceConfiguratorFactory).create();

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, FlavorService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      HostService.State hostState = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name()));

      VmService.State vmStartState = TestHelper.getVmServiceStartState(hostState);
      vmStartState.imageId = "IMAGE_ID";
      vmStartState.projectId = "PROJECT_ID";
      VmService.State vmState = TestHelper.createVmService(testEnvironment, vmStartState);

      for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {

        //
        // Lightwave and the load balancer can't be placed on the same VM, so skip the load balancer here.
        //

        if (containerType == ContainersConfig.ContainerType.LoadBalancer) {
          continue;
        }

        ContainerTemplateService.State templateState = TestHelper.createContainerTemplateService(testEnvironment,
            deployerTestConfig.getContainersConfig().getContainerSpecs().get(containerType.name()));
        TestHelper.createContainerService(testEnvironment, templateState, vmState);
      }

      startState = buildValidStartState(null, null);
      startState.vmServiceLink = vmState.documentSelfLink;
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
      startState.maxDhcpAgentPollIterations = 3;

      FileUtils.copyDirectory(Paths.get(this.getClass().getResource("/scripts/").getPath()).toFile(),
          Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory()).toFile());
      Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory(), "esx-create-vm-iso").toFile()
          .setExecutable(true, true);
      Paths.get(deployerTestConfig.getDeployerContext().getScriptLogDirectory()).toFile().mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory()).toFile());
      FileUtils.deleteDirectory(Paths.get(deployerTestConfig.getDeployerContext().getScriptLogDirectory()).toFile());
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, FlavorService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(Paths.get(deployerTestConfig.getDeployerContext().getConfigDirectory()).toFile());
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
      listeningExecutorService.shutdown();
    }

    @Test(dataProvider = "HostStates")
    public void testSuccess(HostService.State hostStartState,
                            Integer expectedCpuCount,
                            Long expectedMemoryMb)
        throws Throwable {

      doReturn(true).when(healthChecker).isReady();

      //
      // N.B. Ignore the host service document which was created during test setup. It will be
      // deleted along with this document during cleanup, and should not impact the test.
      //

      HostService.State hostState = TestHelper.createHostService(cloudStoreEnvironment, hostStartState);

      VmService.State vmStartState = TestHelper.getVmServiceStartState(hostState);
      vmStartState.imageId = "IMAGE_ID";
      vmStartState.projectId = "PROJECT_ID";
      VmService.State vmState = TestHelper.createVmService(testEnvironment, vmStartState);

      for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {

        //
        // Lightwave and the load balancer can't be placed on the same VM, so skip the load balancer here.
        //

        if (containerType == ContainersConfig.ContainerType.LoadBalancer) {
          continue;
        }

        ContainerTemplateService.State templateState = TestHelper.createContainerTemplateService(testEnvironment,
            deployerTestConfig.getContainersConfig().getContainerSpecs().get(containerType.name()));
        TestHelper.createContainerService(testEnvironment, templateState, vmState);
      }

      startState.vmServiceLink = vmState.documentSelfLink;

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.attachIsoTaskId, is("UPLOAD_AND_ATTACH_ISO_TASK_ID"));
      assertThat(finalState.attachIsoPollCount, is(3));
      assertThat(finalState.startVmTaskId, is("START_VM_TASK_ID"));
      assertThat(finalState.startVmPollCount, is(3));
      assertThat(finalState.dhcpAgentPollIterations, is(1));

      verify(projectApi).createVmAsync(
          eq("PROJECT_ID"),
          eq(getExpectedVmCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      verify(tasksApi, times(3)).getTaskAsync(
          eq("CREATE_VM_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      ArgumentCaptor<VmMetadata> metadataCaptor = ArgumentCaptor.forClass(VmMetadata.class);

      VmService.State finalVmState = testEnvironment.getServiceState(startState.vmServiceLink, VmService.State.class);
      assertThat(finalVmState.vmId, is(vmId));

      verify(vmApi).setMetadataAsync(
          eq(vmId),
          metadataCaptor.capture(),
          Matchers.<FutureCallback<Task>>any());

      assertThat(metadataCaptor.getValue().getMetadata(), is(getExpectedMetadata()));

      verify(tasksApi, times(3)).getTaskAsync(
          eq("SET_METADATA_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      assertTrue(FileUtils.contentEquals(
          Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory(), "user-data").toFile(),
          Paths.get(this.getClass().getResource("/fixtures/user-data.yml").getPath()).toFile()));

      assertTrue(FileUtils.contentEquals(
          Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory(), "meta-data").toFile(),
          Paths.get(this.getClass().getResource("/fixtures/meta-data.yml").getPath()).toFile()));

      verify(vmApi).uploadAndAttachIso(
          eq(vmId),
          eq(Paths.get(finalState.vmConfigDirectory, "config.iso").toString()));

      verify(tasksApi, times(3)).getTaskAsync(
          eq("UPLOAD_AND_ATTACH_ISO_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());

      verify(vmApi).performStartOperationAsync(
          eq(vmId),
          Matchers.<FutureCallback<Task>>any());

      verify(tasksApi, times(3)).getTaskAsync(
          eq("START_VM_TASK_ID"),
          Matchers.<FutureCallback<Task>>any());
    }

    @Test
    public void testSuccessNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateFlavorAsync("CREATE_VM_FLAVOR_TASK_ID", "VM_FLAVOR_ID", "COMPLETED"))
          .when(flavorApi)
          .createAsync(argThat(vmFlavorCreateSpecMatcher), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateFlavorAsync("CREATE_DISK_FLAVOR_TASK_ID", "DISK_FLAVOR_ID", "COMPLETED"))
          .when(flavorApi)
          .createAsync(argThat(diskFlavorCreateSpecMatcher), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockCreateVmAsync("CREATE_VM_TASK_ID", vmId, "COMPLETED"))
          .when(projectApi)
          .createVmAsync(anyString(), any(VmCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      doAnswer(MockHelper.mockSetMetadataAsync("SET_METADATA_TASK_ID", vmId, "COMPLETED"))
          .when(vmApi)
          .setMetadataAsync(anyString(), any(VmMetadata.class), Matchers.<FutureCallback<Task>>any());

      doReturn(TestHelper.createTask("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "COMPLETED"))
          .when(vmApi)
          .uploadAndAttachIso(anyString(), anyString());

      doAnswer(MockHelper.mockPerformStartOperationAsync("START_VM_TASK_ID", vmId, "COMPLETED"))
          .when(vmApi)
          .performStartOperationAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.createVmTaskId, nullValue());
      assertThat(finalState.createVmPollCount, is(0));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, nullValue());
      assertThat(finalState.updateVmMetadataPollCount, is(0));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, nullValue());
      assertThat(finalState.startVmPollCount, is(0));
      assertThat(finalState.dhcpAgentPollIterations, is(1));

      verify(projectApi).createVmAsync(
          eq("PROJECT_ID"),
          eq(getExpectedVmCreateSpec()),
          Matchers.<FutureCallback<Task>>any());

      VmService.State finalVmState = testEnvironment.getServiceState(startState.vmServiceLink, VmService.State.class);
      assertThat(finalVmState.vmId, is(vmId));

      ArgumentCaptor<VmMetadata> captor = ArgumentCaptor.forClass(VmMetadata.class);

      verify(vmApi).setMetadataAsync(
          eq(vmId),
          captor.capture(),
          Matchers.<FutureCallback<Task>>any());

      assertThat(captor.getValue().getMetadata(), is(getExpectedMetadata()));

      assertTrue(FileUtils.contentEquals(
          Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory(), "user-data").toFile(),
          Paths.get(this.getClass().getResource("/fixtures/user-data.yml").getPath()).toFile()));

      assertTrue(FileUtils.contentEquals(
          Paths.get(deployerTestConfig.getDeployerContext().getScriptDirectory(), "meta-data").toFile(),
          Paths.get(this.getClass().getResource("/fixtures/meta-data.yml").getPath()).toFile()));

      verify(vmApi).uploadAndAttachIso(
          eq(vmId),
          eq(Paths.get(finalState.vmConfigDirectory, "config.iso").toString()));

      verify(vmApi).performStartOperationAsync(
          eq(vmId),
          Matchers.<FutureCallback<Task>>any());
    }

    @DataProvider(name = "HostStates")
    private Object[][] getHostStates() {

      HostService.State hostStateWithResourceValues = TestHelper.getHostServiceStartState(
          Collections.singleton(UsageTag.MGMT.name()), HostState.READY);
      hostStateWithResourceValues.cpuCount = 8;
      hostStateWithResourceValues.memoryMb = 2048;

      HostService.State hostStateWithResourceOverrides = TestHelper.getHostServiceStartState(
          Collections.singleton(UsageTag.MGMT.name()), HostState.READY);
      hostStateWithResourceOverrides.cpuCount = 8;
      hostStateWithResourceOverrides.memoryMb = 1024;
      hostStateWithResourceOverrides.metadata.put(
          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE, Integer.toString(7));
      hostStateWithResourceOverrides.metadata.put(
          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_MB_OVERWRITE, Long.toString(1792));
      hostStateWithResourceOverrides.metadata.put(
          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE, Integer.toString(80));

      return new Object[][]{
          {hostStateWithResourceValues, 6, 1636L},
          {hostStateWithResourceOverrides, 7, 1792L},
      };
    }

    private FlavorCreateSpec getExpectedVmFlavorCreateSpec(int expectedCpuCount, long expectedMemoryMb) {
      List<QuotaLineItem> vmCost = new ArrayList<>();
      vmCost.add(new QuotaLineItem("vm", 1.0, QuotaUnit.COUNT));
      vmCost.add(new QuotaLineItem("vm.flavor.NAME", 1.0, QuotaUnit.COUNT));
      vmCost.add(new QuotaLineItem("vm.cpu", expectedCpuCount, QuotaUnit.COUNT));
      vmCost.add(new QuotaLineItem("vm.memory", expectedMemoryMb, QuotaUnit.MB));
      vmCost.add(new QuotaLineItem("vm.cost", 1.0, QuotaUnit.COUNT));

      FlavorCreateSpec vmFlavorCreateSpec = new FlavorCreateSpec();
      vmFlavorCreateSpec.setName("mgmt-vm-NAME");
      vmFlavorCreateSpec.setKind("vm");
      vmFlavorCreateSpec.setCost(vmCost);
      return vmFlavorCreateSpec;
    }

    private FlavorCreateSpec getExpectedDiskFlavorCreateSpec() {
      List<QuotaLineItem> diskCost = new ArrayList<>();
      diskCost.add(new QuotaLineItem("ephemeral-disk", 1.0, QuotaUnit.COUNT));
      diskCost.add(new QuotaLineItem("ephemeral-disk.flavor.NAME", 1.0, QuotaUnit.COUNT));
      diskCost.add(new QuotaLineItem("ephemeral-disk.cost", 1.0, QuotaUnit.COUNT));

      FlavorCreateSpec diskFlavorCreateSpec = new FlavorCreateSpec();
      diskFlavorCreateSpec.setName("mgmt-vm-disk-NAME");
      diskFlavorCreateSpec.setKind("ephemeral-disk");
      diskFlavorCreateSpec.setCost(diskCost);
      return diskFlavorCreateSpec;
    }

    private VmCreateSpec getExpectedVmCreateSpec() {
      AttachedDiskCreateSpec bootDiskCreateSpec = new AttachedDiskCreateSpec();
      bootDiskCreateSpec.setName("NAME-bootdisk");
      bootDiskCreateSpec.setBootDisk(true);
      bootDiskCreateSpec.setFlavor("mgmt-vm-disk-NAME");
      bootDiskCreateSpec.setKind(EphemeralDisk.KIND);

      LocalitySpec hostLocalitySpec = new LocalitySpec();
      hostLocalitySpec.setId("hostAddress");
      hostLocalitySpec.setKind("host");

      LocalitySpec datastoreLocalitySpec = new LocalitySpec();
      datastoreLocalitySpec.setId("datastore1");
      datastoreLocalitySpec.setKind("datastore");

      LocalitySpec portGroupLocalitySpec = new LocalitySpec();
      portGroupLocalitySpec.setId("VM Network");
      portGroupLocalitySpec.setKind("portGroup");

      VmCreateSpec vmCreateSpec = new VmCreateSpec();
      vmCreateSpec.setName("NAME");
      vmCreateSpec.setFlavor("mgmt-vm-NAME");
      vmCreateSpec.setSourceImageId("IMAGE_ID");
      vmCreateSpec.setEnvironment(new HashMap<>());
      vmCreateSpec.setAttachedDisks(Collections.singletonList(bootDiskCreateSpec));
      vmCreateSpec.setAffinities(Arrays.asList(hostLocalitySpec, datastoreLocalitySpec, portGroupLocalitySpec));
      return vmCreateSpec;
    }

    private Map<String, String> getExpectedMetadata() {
      return Stream.of(ContainersConfig.ContainerType.values())
          .filter((containerType) -> containerType != ContainersConfig.ContainerType.LoadBalancer)
          .map((containerType) ->
              deployerTestConfig.getContainersConfig().getContainerSpecs().get(containerType.name()))
          .flatMap((containerSpec) -> containerSpec.getPortBindings().values().stream()
              .collect(Collectors.toMap(
                  (hostPort) -> "CONTAINER_" + hostPort,
                  (hostPort -> containerSpec.getServiceName())))
              .entrySet().stream())
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    public void testCreateVmFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("CREATE_VM_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("CREATE_VM_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
    }

    @Test
    public void testCreateVmFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockCreateVmAsync(failedTask))
          .when(projectApi)
          .createVmAsync(anyString(), any(VmCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, nullValue());
      assertThat(finalState.createVmPollCount, is(0));
    }

    @Test
    public void testCreateVmExceptionInCreateVmCall() throws Throwable {

      doThrow(new IOException("I/O exception during createVmAsync call"))
          .when(projectApi)
          .createVmAsync(anyString(), any(VmCreateSpec.class), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during createVmAsync call"));
      assertThat(finalState.createVmTaskId, nullValue());
      assertThat(finalState.createVmPollCount, is(0));
    }

    @Test
    public void testCreateVmExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("CREATE_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during getTaskAsync call"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(1));
    }

    @Test
    public void testSetMetadataFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("SET_METADATA_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("SET_METADATA_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("SET_METADATA_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
    }

    @Test
    public void testSetMetadataFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockSetMetadataAsync(failedTask))
          .when(vmApi)
          .setMetadataAsync(eq(vmId), any(VmMetadata.class), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, nullValue());
      assertThat(finalState.updateVmMetadataPollCount, is(0));
    }

    @Test
    public void testSetMetadataExceptionInSetMetadataCall() throws Throwable {

      doThrow(new IOException("I/O exception during setMetadataAsync call"))
          .when(vmApi)
          .setMetadataAsync(eq(vmId), any(VmMetadata.class), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during setMetadataAsync call"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, nullValue());
      assertThat(finalState.updateVmMetadataPollCount, is(0));
    }

    @Test
    public void testSetMetadataExceptionInGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("SET_METADATA_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during getTaskAsync call"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(1));
    }

    @Test
    public void testAttachIsoFailureInServiceConfig() throws Throwable {

      ServiceConfigurator serviceConfigurator = mock(ServiceConfigurator.class);
      doReturn(serviceConfigurator).when(serviceConfiguratorFactory).create();

      doThrow(new RuntimeException("Runtime exception during config directory copy"))
          .when(serviceConfigurator)
          .copyDirectory(anyString(), anyString());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("Runtime exception during config directory copy"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
    }

    @Test
    public void testAttachIsoFailureInScriptRunner() throws Throwable {

      TestHelper.createFailScriptFile(deployerTestConfig.getDeployerContext(), "esx-create-vm-iso");

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Creating the configuration ISO for VM " +
          vmId + " failed with exit code 1"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
    }

    @Test
    public void testAttachIsoFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("UPLOAD_AND_ATTACH_ISO_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("UPLOAD_AND_ATTACH_ISO_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.attachIsoTaskId, is("UPLOAD_AND_ATTACH_ISO_TASK_ID"));
      assertThat(finalState.attachIsoPollCount, is(3));
    }

    @Test
    public void testAttachIsoFailureNoPolling() throws Throwable {

      doReturn(failedTask)
          .when(vmApi)
          .uploadAndAttachIso(anyString(), anyString());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
    }

    @Test
    public void testStartVmFailure() throws Throwable {

      doAnswer(MockHelper.mockGetTaskAsync("START_VM_TASK_ID", vmId, "QUEUED"))
          .doAnswer(MockHelper.mockGetTaskAsync("START_VM_TASK_ID", vmId, "STARTED"))
          .doAnswer(MockHelper.mockGetTaskAsync(failedTask))
          .when(tasksApi)
          .getTaskAsync(eq("START_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, is("START_VM_TASK_ID"));
      assertThat(finalState.startVmPollCount, is(3));
    }

    @Test
    public void testStartVmFailureNoTaskPolling() throws Throwable {

      doAnswer(MockHelper.mockPerformStartOperationAsync(failedTask))
          .when(vmApi)
          .performStartOperationAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(failedTask)));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, nullValue());
      assertThat(finalState.startVmPollCount, is(0));
    }

    @Test
    public void testStartVmFailureExceptionDuringStartVmCall() throws Throwable {

      doThrow(new IOException("I/O exception during performStartOperationAsync call"))
          .when(vmApi)
          .performStartOperationAsync(anyString(), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("I/O exception during performStartOperationAsync call"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, nullValue());
      assertThat(finalState.startVmPollCount, is(0));
    }

    @Test
    public void testStartVmFailureExceptionDuringGetTaskCall() throws Throwable {

      doThrow(new IOException("I/O exception during getTaskAsync call"))
          .when(tasksApi)
          .getTaskAsync(eq("START_VM_TASK_ID"), Matchers.<FutureCallback<Task>>any());

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("I/O exception during getTaskAsync call"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, is("START_VM_TASK_ID"));
      assertThat(finalState.startVmPollCount, is(1));
    }

    @Test
    public void testWaitForDhcpAgentFailure() throws Throwable {

      doReturn(false).when(healthChecker).isReady();

      CreateDhcpVmTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateDhcpVmTaskFactoryService.SELF_LINK,
              startState,
              CreateDhcpVmTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("The DHCP Agent endpoint on VM ipAddress failed to become ready after 3 polling iterations"));
      assertThat(finalState.createVmTaskId, is("CREATE_VM_TASK_ID"));
      assertThat(finalState.createVmPollCount, is(3));
      assertThat(finalState.vmId, is(vmId));
      assertThat(finalState.updateVmMetadataTaskId, is("SET_METADATA_TASK_ID"));
      assertThat(finalState.updateVmMetadataPollCount, is(3));
      assertThat(finalState.serviceConfigDirectory, notNullValue());
      assertThat(finalState.vmConfigDirectory, notNullValue());
      assertThat(finalState.startVmTaskId, is("START_VM_TASK_ID"));
      assertThat(finalState.startVmPollCount, is(3));
      assertThat(finalState.dhcpAgentPollIterations, is(3));
    }
  }

  private CreateDhcpVmTaskService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      CreateDhcpVmTaskService.TaskState.SubStage subStage) {

    CreateDhcpVmTaskService.State startState = new CreateDhcpVmTaskService.State();
    startState.vmServiceLink = "VM_SERVICE_LINK";
    startState.ntpEndpoint = "NTP_ENDPOINT";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (taskStage != null) {
      startState.taskState = new CreateDhcpVmTaskService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
