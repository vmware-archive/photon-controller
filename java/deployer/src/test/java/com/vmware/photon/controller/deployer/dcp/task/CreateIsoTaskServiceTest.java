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

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ApiTestUtils;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowService;
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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link CreateIsoTaskService} class.
 */
public class CreateIsoTaskServiceTest {

  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    CreateIsoTaskService service;

    @BeforeMethod
    public void setUp() {
      service = new CreateIsoTaskService();
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
  public class HandleStart {

    CreateIsoTaskService service;
    TestHost host;

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CreateIsoTaskService();
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

    /**
     * This test verifies that service instances can be created with specific
     * start states.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStages")
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));
    }

    @DataProvider(name = "validStartStages")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state is transitioned to
     * STARTED state as part of the start operation handling.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "ChangedStartStates")
    public void testStartStateChanged(TaskState.TaskStage startStage) throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(startStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateIsoTaskService.State savedState = host.getServiceState(CreateIsoTaskService.State.class);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "ChangedStartStates")
    public Object[][] getChangedStartStates() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
      };
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "TerminalStartStages")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(stage);
      startState.controlFlags = null;
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateIsoTaskService.State savedState = host.getServiceState(CreateIsoTaskService.State.class);
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getStartStateNotChanged() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(dataProvider = "RequiredAttributeNames", expectedExceptions = XenonRuntimeException.class)
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(null);
      startState.getClass().getDeclaredField(attributeName).set(startState, null);
      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "RequiredAttributeNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateIsoTaskService.State.class, NotNull.class));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatch {

    private CreateIsoTaskService service;
    private TestHost host;

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CreateIsoTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
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
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateIsoTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateIsoTaskService.State savedState = host.getServiceState(CreateIsoTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that legal stage transitions succeed, where
     * the transition from START to FINISH will persist the isoFilename.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testValidStageUpdatesIsoFilename() throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.FINISHED);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateIsoTaskService.State savedState = host.getServiceState(CreateIsoTaskService.State.class);
      TestHelper.assertTaskStateFinished(savedState.taskState);
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the patch state is invalid.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStageUpdatesInvalidPatch() throws Throwable {

      CreateIsoTaskService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.CREATED);

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
        TaskState.TaskStage targetStage)
        throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "illegalStageUpdatesInvalidStart")
    public Object[][] getIllegalStageUpdatesInvalidStart() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
      };
    }

    /**
     * This test verifies that the service instance fails when isOperationProcessingDisabled is supplied
     * in the patch state.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchIsOperationProcessingDisabled() throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(null);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState();
      patchState.controlFlags = 0;

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the patch state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateIsoTaskService.State startState = buildValidStartupState(null);
      host.startServiceSynchronously(service, startState);

      CreateIsoTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);

      if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else if (declaredField.getType() == Boolean.class) {
        declaredField.set(patchState, false);
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateIsoTaskService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the iso creation task.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");

    private DeployerConfig deployerConfig;
    private DeployerContext deployerContext;
    private ContainersConfig containersConfig;
    private ListeningExecutorService listeningExecutorService;
    private CreateIsoTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    private ApiClientFactory apiClientFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;
    private ApiClient apiClient;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByGetTask;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      FileUtils.deleteDirectory(storageDirectory);
      deployerConfig = ConfigBuilder.build(DeployerConfig.class, this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      deployerContext = deployerConfig.getDeployerContext();
      containersConfig = deployerConfig.getContainersConfig();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartupState(null);
      startState.controlFlags = null;

      apiClient = mock(ApiClient.class);
      vmApi = mock(VmApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      apiClientFactory = mock(ApiClientFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      doReturn(new ServiceConfigurator()).when(serviceConfiguratorFactory).create();
      doReturn(apiClient).when(apiClientFactory).create();

      String configPath = CreateIsoTaskServiceTest.class.getResource("/configurations/").getPath();
      FileUtils.copyDirectory(new File(configPath), new File(deployerContext.getConfigDirectory()));
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path userDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), "meta-data");
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), "user-data");

      Files.createFile(userDataTemplate);
      Files.createFile(metaDataTemplate);

      startState.userDataTemplate.filePath = userDataTemplate.toString();
      startState.metaDataTemplate.filePath = metaDataTemplate.toString();

      testEnvironment = createTestEnvironment(deployerContext, containersConfig, listeningExecutorService,
          apiClientFactory, serviceConfiguratorFactory, cloudStoreMachine.getServerSet(), 1);

      startState.vmId = "VM_ID";

      Task.Entity vmEntity = new Task.Entity();
      vmEntity.setId("vmId");

      taskReturnedByAttachIso = new Task();
      taskReturnedByAttachIso.setId("taskId");
      taskReturnedByAttachIso.setState("STARTED");
      taskReturnedByAttachIso.setEntity(vmEntity);

      taskReturnedByGetTask = new Task();
      taskReturnedByGetTask.setId("taskId");
      taskReturnedByGetTask.setState("COMPLETED");

      createHostServiceEntitiesAndAllocateVmsAndContainers(3, 7);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      // create real user-data and meta-data file templates
      String userdata = CreateIsoTaskServiceTest.class.getResource("/user-data").getPath();
      String metadata = CreateIsoTaskServiceTest.class.getResource("/meta-data").getPath();

      Files.copy(Paths.get(userdata), Paths.get(startState.userDataTemplate.filePath),
          StandardCopyOption.REPLACE_EXISTING);
      Files.copy(Paths.get(metadata), Paths.get(startState.metaDataTemplate.filePath),
          StandardCopyOption.REPLACE_EXISTING);

      // create some parameters for the above templates
      String instanceId = UUID.randomUUID().toString();
      startState.userDataTemplate.parameters.put("$DNS", "DNS=255.255.255.255");
      startState.metaDataTemplate.parameters.put("$INSTANCE_ID", instanceId);
      startState.userDataTemplate.parameters.put("$NTP", "1.2.3.4");

      // create a fake script to generate an iso that just creates the user-data and meta-data from the templates
      String script = Resources.toString(
          CreateIsoTaskServiceTest.class.getResource("/create-iso-script"), Charsets.UTF_8);
      TestHelper.createScriptFile(deployerContext, script, CreateIsoTaskService.SCRIPT_NAME);

      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      // verify the generated user-data and meta-data files contain the replaced parameter strings.
      String userDataContents = new String(
          Files.readAllBytes(Paths.get(deployerContext.getScriptDirectory() + "/user-data")), "UTF-8");

      String metaDataContents = new String(
          Files.readAllBytes(Paths.get(deployerContext.getScriptDirectory() + "/meta-data")), "UTF-8");

      assertThat(userDataContents.contains("DNS=255.255.255.255"), is(true));
      System.out.println(userDataContents);
      assertThat(userDataContents.contains("- sh /tmp/update-ntp.sh 1.2.3.4"), is(true));
      assertThat(metaDataContents.contains(instanceId), is(true));
    }

    @Test
    public void testEndToEndFailureScriptMissing() throws Throwable {
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("No such file or directory"));
    }

    @Test
    public void testEndToEndFailureScriptNonZeroExit() throws Throwable {
      TestHelper.createFailScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(CreateIsoTaskService.SCRIPT_NAME +
          " returned 1"));
    }

    @Test
    public void testEndToEndFailureAttachIsoThrowsException() throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
      doThrow(new RuntimeException("Exception during uploadAndAttachIso"))
          .when(vmApi).uploadAndAttachIso(anyString(), anyString());

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during uploadAndAttachIso"));
    }

    @Test
    public void testEndToEndFailureGetTaskThrowsException() throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doThrow(new RuntimeException("Exception during getTask")).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Exception during getTask"));
    }

    @Test(dataProvider = "attachIsoFailureTasks")
    public void testEndToEndFailureAttachIsoReturnsErrorTaskState(final Task task) throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
      doReturn(task).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    @Test
    public void testFailureToRetrieveConfiguration() throws Throwable {
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, false);

      CreateIsoTaskService.State serviceState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, containsString("Config not available"));
    }

    @DataProvider(name = "attachIsoFailureTasks")
    public Object[][] getAttachIsoFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
      };
    }

    @Test(dataProvider = "getTaskFailureTasks")
    public void testEndToEndFailureGetTaskReturnsErrorTaskState(Task task) throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(task).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(ApiUtils.getErrors(task)));
    }

    @DataProvider(name = "getTaskFailureTasks")
    public Object[][] getGetTaskFailureResponses() {
      return new Object[][]{
          {ApiTestUtils.createFailingTask(0, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(0, 2, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
          {ApiTestUtils.createFailingTask(2, 1, "errorCode", "errorMessage")},
      };
    }

    @Test
    public void testEndToEndFailureAttachIsoReturnsInvalidTaskState() throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      taskReturnedByAttachIso.setState("unknown");

      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    @Test
    public void testEndToEndFailureGetTaskReturnsInvalidTaskState() throws Throwable {
      TestHelper.createSuccessScriptFile(deployerContext, CreateIsoTaskService.SCRIPT_NAME);
      taskReturnedByGetTask.setState("unknown");

      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));

      CreateIsoTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateIsoTaskFactoryService.SELF_LINK,
              startState,
              CreateIsoTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Unknown task state: unknown"));
    }

    /**
     * This method sets up valid service documents which are needed for test.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void createHostServiceEntitiesAndAllocateVmsAndContainers(
        int mgmtCount,
        int cloudCount) throws Throwable {

      for (int i = 0; i < mgmtCount; i++) {
        HostService.State hostService = TestHelper.createHostService(cloudStoreMachine,
            Collections.singleton(UsageTag.MGMT.name()));
        if (i == 0) {
          TestHelper.createVmService(testEnvironment, hostService, "VM_ID");
        }
      }

      for (int i = 0; i < cloudCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()));
      }

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();

      workflowStartState.taskPollDelay = 10;
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());


      CreateManagementPlaneLayoutWorkflowService.State serviceState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }
  }

  private CreateIsoTaskService.State buildValidStartupState(TaskState.TaskStage startStage) {
    CreateIsoTaskService.State startState = new CreateIsoTaskService.State();
    startState.vmId = "VM_ID";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    startState.metaDataTemplate = new CreateIsoTaskService.FileTemplate();
    startState.metaDataTemplate.filePath = "test";
    startState.metaDataTemplate.parameters = new HashMap();

    startState.userDataTemplate = new CreateIsoTaskService.FileTemplate();
    startState.userDataTemplate.filePath = "test";
    startState.userDataTemplate.parameters = new HashMap();

    startState.placeConfigFilesInISO = true;

    return startState;
  }

  private CreateIsoTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private CreateIsoTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    CreateIsoTaskService.State state = new CreateIsoTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  public TestEnvironment createTestEnvironment(
      DeployerContext deployerContext,
      ContainersConfig containersConfig,
      ListeningExecutorService listeningExecutorService,
      ApiClientFactory apiClientFactory,
      ServiceConfiguratorFactory serviceConfiguratorFactory,
      ServerSet cloudServerSet,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .containersConfig(containersConfig)
        .apiClientFactory(apiClientFactory)
        .listeningExecutorService(listeningExecutorService)
        .serviceConfiguratorFactory(serviceConfiguratorFactory)
        .cloudServerSet(cloudServerSet)
        .hostCount(hostCount)
        .build();
  }
}
