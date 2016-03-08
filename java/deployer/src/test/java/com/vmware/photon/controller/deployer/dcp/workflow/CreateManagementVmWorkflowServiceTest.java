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

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
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
import static org.mockito.Mockito.spy;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests {@link CreateManagementVmWorkflowService}.
 */
public class CreateManagementVmWorkflowServiceTest {

  private TestHost host;
  private CreateManagementVmWorkflowService service;

  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() {
      service = new CreateManagementVmWorkflowService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStart {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new CreateManagementVmWorkflowService());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * This test verifies that service instances can be created with specific
     * start states.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(
        TaskState.TaskStage stage,
        CreateManagementVmWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState(stage, subStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmWorkflowService.State savedState =
          host.getServiceState(CreateManagementVmWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state is transitioned to
     * STARTED state as part of the start operation handling.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState();
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmWorkflowService.State savedState =
          host.getServiceState(CreateManagementVmWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState(stage, null);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateManagementVmWorkflowService.State savedState
          = host.getServiceState(CreateManagementVmWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
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
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState();
      startState.getClass().getDeclaredField(attributeName).set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils
          .getAttributeNamesWithAnnotation(CreateManagementVmWorkflowService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatch {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new CreateManagementVmWorkflowService());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * This test verifies that legal stage and substage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        CreateManagementVmWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        CreateManagementVmWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {

      CreateManagementVmWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      CreateManagementVmWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateManagementVmWorkflowService.State savedState
          = host.getServiceState(CreateManagementVmWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the patch state is invalid.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStageUpdatesInvalidPatch() throws Throwable {

      CreateManagementVmWorkflowService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED, null);
      host.startServiceSynchronously(service, startState);

      CreateManagementVmWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.CREATED, null);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidStart", expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStageUpdatesInvalidStart(
        TaskState.TaskStage startStage,
        CreateManagementVmWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        CreateManagementVmWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      CreateManagementVmWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "illegalStageUpdatesInvalidStart")
    public Object[][] getIllegalStageUpdatesInvalidStart() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM},
      };
    }

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateManagementVmWorkflowService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateManagementVmWorkflowService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes = ReflectionUtils
          .getAttributeNamesWithAnnotation(CreateManagementVmWorkflowService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the iso creation task.
   */
  public class EndToEndTest {

    private File scriptDirectory;
    private File scriptLogDirectory;

    private static final String configFilePath = "/config.yml";
    private final File storageDirectory = new File("/tmp/createIso");

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private ListeningExecutorService listeningExecutorService;

    private CreateManagementVmWorkflowService.State startState;

    private ProjectService.State projectServiceStartState;

    private DeployerConfig deployerConfig;
    private DeployerContext deployerContext;
    private ContainersConfig containersConfig;
    private DockerProvisioner dockerProvisioner;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private ApiClientFactory apiClientFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;
    private ApiClient apiClient;
    private ProjectApi projectApi;
    private TasksApi tasksApi;
    private VmApi vmApi;
    private ImagesApi imagesApi;

    private Task taskReturnedByCreateVm;
    private Task taskReturnedBySetMetadata;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByGetTask;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      FileUtils.deleteDirectory(storageDirectory);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @BeforeMethod
    public void setUp() throws Throwable {

      projectServiceStartState = new ProjectService.State();
      projectServiceStartState.name = "projectName";
      projectServiceStartState.resourceTicketId = "resourceTicketServiceLink";
      projectServiceStartState.documentSelfLink = "projectid";

      dockerProvisioner = mock(DockerProvisioner.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());

      apiClient = mock(ApiClient.class);
      projectApi = mock(ProjectApi.class);
      tasksApi = mock(TasksApi.class);
      vmApi = mock(VmApi.class);
      imagesApi = mock(ImagesApi.class);

      apiClientFactory = mock(ApiClientFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      doReturn(apiClient).when(apiClientFactory).create();
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(imagesApi).when(apiClient).getImagesApi();

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
      taskReturnedByGetTask.setEntity(entity);
      taskReturnedBySetMetadata.setEntity(entity);

      Task.Entity vmEntity = new Task.Entity();
      vmEntity.setId("vmId");

      taskReturnedByAttachIso = new Task();
      taskReturnedByAttachIso.setId("taskId");
      taskReturnedByAttachIso.setState("STARTED");
      taskReturnedByAttachIso.setEntity(vmEntity);

      Task.Entity taskEntity = new Task.Entity();
      taskEntity.setId("taskEntityId");
      taskReturnedByGetTask.setEntity(taskEntity);

      startState = buildValidStartupState();
      startState.controlFlags = 0;
      startState.childPollInterval = 10;
      startState.taskPollDelay = 10;
      startState.ntpEndpoint = "1.2.3.4";

      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          CreateManagementVmWorkflowService.class.getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      deployerContext = deployerConfig.getDeployerContext();
      containersConfig = deployerConfig.getContainersConfig();
      TestHelper.setContainersConfig(deployerConfig);

      scriptDirectory = new File(deployerContext.getScriptDirectory());
      scriptLogDirectory = new File(deployerContext.getScriptLogDirectory());
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "user-data.template"));
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "meta-data.template"));
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }
      startState = null;
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "hostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), hostCount);

      mockSuccessfulVmCreate();
      mockSuccessfulCreateIso();
      mockSuccessfulStartVm();

      doReturn("Docker info").when(dockerProvisioner).getInfo();

      CreateManagementVmWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementVmWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailsWhenCreateVmFails(Integer hostCount) throws Throwable {

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), hostCount);

      mockSuccessfulVmCreate(); // necessary due to the long timeouts on entity retrieval
      taskReturnedByCreateVm.setState("FAILED");

      CreateManagementVmWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementVmWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailsWhenCreateIsoFails(Integer hostCount) throws Throwable {

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), hostCount);

      mockSuccessfulVmCreate();

      CreateManagementVmWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementVmWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailsWhenStartVmFails(Integer hostCount) throws Throwable {

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), hostCount);

      mockSuccessfulVmCreate();
      mockSuccessfulCreateIso();

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onFailure(new RuntimeException());
          return null;
        }
      }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));

      CreateManagementVmWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementVmWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailsWhenWaitForDockerFails(Integer hostCount) throws Throwable {

      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), hostCount);

      mockSuccessfulVmCreate();
      mockSuccessfulCreateIso();
      mockSuccessfulStartVm();

      doThrow(new RuntimeException("Could not connect to Docker!")).when(dockerProvisioner).getInfo();

      CreateManagementVmWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementVmWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementVmWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message,
          containsString("The docker endpoint on VM 1.1.1.1 failed to become ready after 600 polling iterations"));
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }

    private void mockSuccessfulStartVm() throws Throwable {
      final Task performOperationReturnValue = new Task();
      performOperationReturnValue.setState("COMPLETED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(performOperationReturnValue);
          return null;
        }
      }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));
    }

    private void mockSuccessfulCreateIso() throws Throwable {
      String scriptFileName = "esx-create-vm-iso";
      TestHelper.createSuccessScriptFile(deployerContext, scriptFileName);
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));
    }

    private void mockSuccessfulVmCreate() throws Throwable {
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

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()));

      ImageService.State imageServiceState = TestHelper.createImageService(cloudStoreMachine);

      FlavorService.State vmFlavorServiceState = TestHelper.createFlavor(cloudStoreMachine, null);
      FlavorService.State diskFlavorServiceState = TestHelper.createFlavor(cloudStoreMachine, "mgmt-vm-disk-NAME");

      ProjectService.State projectServiceState = TestHelper.createProject("tenant1", "rt1", cloudStoreMachine);

      VmService.State vmServiceStartState = TestHelper.getVmServiceStartState(hostServiceState);
      vmServiceStartState.ipAddress = "1.1.1.1";
      vmServiceStartState.imageServiceLink = imageServiceState.documentSelfLink;
      vmServiceStartState.vmFlavorServiceLink = vmFlavorServiceState.documentSelfLink;
      vmServiceStartState.diskFlavorServiceLink = diskFlavorServiceState.documentSelfLink;
      vmServiceStartState.projectServiceLink = projectServiceState.documentSelfLink;
      VmService.State vmServiceState = TestHelper.createVmService(machine, vmServiceStartState);
      startState.vmServiceLink = vmServiceState.documentSelfLink;
      startState.ntpEndpoint = "1.2.3.4";

      ContainerTemplateService.State containerTemplateSavedState1 = TestHelper.createContainerTemplateService(machine,
          containersConfig.getContainerSpecs().get(ContainersConfig.ContainerType.Chairman.name()));
      ContainerTemplateService.State containerTemplateSavedState2 = TestHelper.createContainerTemplateService(machine,
          containersConfig.getContainerSpecs().get(ContainersConfig.ContainerType.Deployer.name()));
      TestHelper.createContainerService(machine, containerTemplateSavedState1, vmServiceState);
      TestHelper.createContainerService(machine, containerTemplateSavedState2, vmServiceState);
    }

    public TestEnvironment createTestEnvironment(
        DeployerContext deployerContext,
        ListeningExecutorService listeningExecutorService,
        ApiClientFactory apiClientFactory,
        DockerProvisionerFactory dockerProvisionerFactory,
        ServiceConfiguratorFactory serviceConfiguratorFactory,
        ServerSet cloudStoreSet,
        int hostCount)
        throws Throwable {

      return new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(cloudStoreSet)
          .hostCount(hostCount)
          .build();
    }
  }

  private CreateManagementVmWorkflowService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED, null);
  }

  private CreateManagementVmWorkflowService.State buildValidStartupState(
      TaskState.TaskStage stage,
      CreateManagementVmWorkflowService.TaskState.SubStage subStage) {
    CreateManagementVmWorkflowService.State state = new CreateManagementVmWorkflowService.State();
    state.taskState = new CreateManagementVmWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.vmServiceLink = "vmServiceLink";
    state.ntpEndpoint = "1.2.3.4";
    return state;
  }

  private CreateManagementVmWorkflowService.State buildValidPatchState() {
    return buildValidPatchState(
        TaskState.TaskStage.STARTED,
        CreateManagementVmWorkflowService.TaskState.SubStage.CREATE_VM);
  }

  private CreateManagementVmWorkflowService.State buildValidPatchState(
      TaskState.TaskStage stage,
      CreateManagementVmWorkflowService.TaskState.SubStage subStage) {

    CreateManagementVmWorkflowService.State state = new CreateManagementVmWorkflowService.State();
    state.taskState = new CreateManagementVmWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    return state;
  }
}
