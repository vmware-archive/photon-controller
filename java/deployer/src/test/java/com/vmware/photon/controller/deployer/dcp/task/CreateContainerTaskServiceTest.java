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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServiceFileConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowService;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.github.dockerjava.api.DockerException;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.internal.matchers.NotNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link CreateContainerTaskService} class.
 */
public class CreateContainerTaskServiceTest {

  private TestHost host;
  private CreateContainerTaskService service;
  private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateContainerTaskService.State buildValidStartupState() {
    return buildValidStartupState(
        TaskState.TaskStage.CREATED);
  }

  private CreateContainerTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {

    CreateContainerTaskService.State state = new CreateContainerTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.containerServiceLink = "CONTAINER_SERVICE_LINK";
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";

    if (TaskState.TaskStage.FINISHED == stage) {
      state.containerId = "containerId";
    }

    return state;
  }

  private CreateContainerTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private CreateContainerTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    CreateContainerTaskService.State state = new CreateContainerTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (TaskState.TaskStage.FINISHED == stage) {
      state.containerId = "containerId";
    }

    return state;
  }

  private TestEnvironment createTestEnvironment(
      DeployerConfig deployerConfig,
      ListeningExecutorService listeningExecutorService,
      DockerProvisionerFactory dockerProvisionerFactory,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .containersConfig(deployerConfig.getContainersConfig())
        .deployerContext(deployerConfig.getDeployerContext())
        .dockerProvisionerFactory(dockerProvisionerFactory)
        .listeningExecutorService(listeningExecutorService)
        .cloudServerSet(cloudStoreMachine.getServerSet())
        .hostCount(hostCount)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateContainerTaskService();
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
    public void setUpTest() throws Throwable {
      service = new CreateContainerTaskService();
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
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {

      CreateContainerTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State savedState = host.getServiceState(
          CreateContainerTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStatesWithoutUploadImageId() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
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
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      CreateContainerTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State savedState =
          host.getServiceState(CreateContainerTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
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
      CreateContainerTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateContainerTaskService.State.class, NotNull.class);
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
      service = new CreateContainerTaskService();
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
     * This test verifies that legal stage transitions succeed.
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

      CreateContainerTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateContainerTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateContainerTaskService.State savedState =
          host.getServiceState(CreateContainerTaskService.State.class);
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

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      CreateContainerTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateContainerTaskService.State patchState = buildValidPatchState(targetStage);
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

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateContainerTaskService.State patchState = buildValidPatchState();
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
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateContainerTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the create container task.
   */
  public class EndToEndTest {
    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private ListeningExecutorService listeningExecutorService;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private CreateContainerTaskService.State startState;
    private AuthClientHandler.ImplicitClient implicitClient;

    private DeployerConfig deployerConfig;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      implicitClient = new AuthClientHandler.ImplicitClient("client_id", "http://login", "http://logout");

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    @BeforeMethod
    public void setUpTest() throws Exception {
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);

      startState = buildValidStartupState();
      startState.controlFlags = 0x0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    /**
     * This test verifies the failure scenario when launching container without a proper docker endpoint.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskFailureToReachDockerEndpoint() throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);
      setupValidOtherServiceDocuments(ContainersConfig.ContainerType.Chairman);

      CreateContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.containerId, nullValue());
    }

    /**
     * This test verifies the failure scenario which returns null result from provisioner.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTaskFailureWithNull() throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenCallRealMethod();

      setupValidOtherServiceDocuments(ContainersConfig.ContainerType.Chairman);

      setupDeploymentServiceDocument(null);

      CreateContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.containerId, nullValue());
      assertTrue(finalState.taskState.failure.message.contains("Create container returned null"));
    }

    /**
     * This test verifies the failure scenario with internal docker exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTaskFailureInsideDocker() throws Throwable {

      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenThrow(new
          DockerException("Start container " + "failed", 500));

      setupValidOtherServiceDocuments(ContainersConfig.ContainerType.Chairman);

      setupDeploymentServiceDocument(null);

      CreateContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.containerId, nullValue());
      assertThat(finalState.taskState.failure.message, org.hamcrest.Matchers.containsString("Start container failed"));
    }

    /**
     * This test verifies the success scenario when launching container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test(dataProvider = "mandatoryEnvironmentVariable")
    public void testTaskSuccess(ContainersConfig.ContainerType containerType) throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenReturn("id");

      setupValidOtherServiceDocuments(containerType);
      setupDeploymentServiceDocument(implicitClient);

      CreateContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertEquals(finalState.containerId, "id");

      ArgumentCaptor<Map> volumeBindingsArgument = ArgumentCaptor.forClass(Map.class);
      verify(dockerProvisioner, times(1)).launchContainer(anyString(), anyString(), anyInt(), anyLong(),
          volumeBindingsArgument.capture(), anyMap(), anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers
              .<String>anyVararg());

      String expectedKey = ServiceFileConstants.VM_MUSTACHE_DIRECTORY + ServiceFileConstants
          .CONTAINER_CONFIG_ROOT_DIRS.get(containerType);
      assertTrue(volumeBindingsArgument.getValue().containsKey(expectedKey));
      if (containerType == ContainersConfig.ContainerType.Zookeeper) {
        assertThat(volumeBindingsArgument.getValue().get(expectedKey), is(ServiceFileConstants
            .CONTAINER_CONFIG_DIRECTORY + "," + CreateContainerTaskService.ZOOKEEPER_CONF_DIR + "," +
            CreateContainerTaskService.ZOOKEEPER_DATA_DIR));
      } else if (containerType == ContainersConfig.ContainerType.LoadBalancer) {
        assertThat(volumeBindingsArgument.getValue().get(expectedKey), is(ServiceFileConstants
            .CONTAINER_CONFIG_DIRECTORY + "," + CreateContainerTaskService.HAPROXY_CONF_DIR));
      } else {
        assertThat(volumeBindingsArgument.getValue().get(expectedKey), is(ServiceFileConstants
            .CONTAINER_CONFIG_DIRECTORY));
      }
    }

    @DataProvider(name = "mandatoryEnvironmentVariable")
    public Object[][] getMandatoryEnvironmentVariables() {

      return new Object[][]{
          {ContainersConfig.ContainerType.Chairman},
          {ContainersConfig.ContainerType.RootScheduler},
          {ContainersConfig.ContainerType.Housekeeper},
          {ContainersConfig.ContainerType.CloudStore},
          {ContainersConfig.ContainerType.ManagementApi},
          {ContainersConfig.ContainerType.Zookeeper},
          {ContainersConfig.ContainerType.Deployer},
          {ContainersConfig.ContainerType.LoadBalancer},
      };
    }

    /**
     * This test verifies the success scenario when the container to be created
     * already has a container id in the container service entity.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTaskSuccessWhenSkippedDueToContainerId() throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenReturn("id");

      setupValidOtherServiceDocumentsWithContainerId(ContainersConfig.ContainerType.Chairman);
      setupDeploymentServiceDocument(implicitClient);

      CreateContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertEquals(finalState.containerId, "id");
    }

    /**
     * This method sets up valid service documents which are needed for test
     * with container id.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void setupValidOtherServiceDocumentsWithContainerId(ContainersConfig.ContainerType type) throws Throwable {
      String containerTemplateServiceLink = getContainerTemplateService(type);
      Set<ContainerService.State> containerServices = getContainerServiceForTemplate(containerTemplateServiceLink);

      ContainerService.State containerService = containerServices.iterator().next();
      containerService.containerId = "id";
      startState.containerServiceLink = containerService.documentSelfLink;
    }

    /**
     * This method sets up valid service documents which are needed for test.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void setupValidOtherServiceDocuments(ContainersConfig.ContainerType type) throws Throwable {
      String containerTemplateServiceLink = getContainerTemplateService(type);
      Set<ContainerService.State> containerServices = getContainerServiceForTemplate(containerTemplateServiceLink);

      ContainerService.State containerService = containerServices.iterator().next();
      startState.containerServiceLink = containerService.documentSelfLink;
    }

    private void setupDeploymentServiceDocument(AuthClientHandler.ImplicitClient implicitClient) throws Throwable {
      DeploymentService.State deploymentStartState = TestHelper.getDeploymentServiceStartState(false);
      if (implicitClient != null) {
        deploymentStartState.oAuthResourceLoginEndpoint = implicitClient.loginURI;
        deploymentStartState.oAuthLogoutEndpoint = implicitClient.logoutURI;
      }
      deploymentStartState.oAuthServerAddress = "https://lookupService";
      deploymentStartState.oAuthServerPort = 433;
      deploymentStartState.oAuthEnabled = true;
      deploymentStartState.syslogEndpoint = "1.2.3.4:514";
      deploymentStartState.statsStoreEndpoint = "2.3.4.5:678";
      deploymentStartState.ntpEndpoint = "5.6.7.8";

      startState.deploymentServiceLink =
          TestHelper.createDeploymentService(cloudStoreMachine, deploymentStartState).documentSelfLink;
    }

    private String getContainerTemplateService(ContainersConfig.ContainerType type) throws Throwable {

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
          .setTermMatchValue(type.name());

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(nameClause);

      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(1));
      return documentLinks.iterator().next();
    }

    private Set<ContainerService.State> getContainerServiceForTemplate(String containerTemplateServiceLink)
        throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

      QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
          .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
          .setTermMatchValue(containerTemplateServiceLink);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);

      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      Set<ContainerService.State> containerServices = new HashSet<>();
      for (String documentLink : documentLinks) {
        containerServices.add(machine.getServiceState(documentLink, ContainerService.State.class));
      }

      return containerServices;
    }

    private void createHostEntitiesAndAllocateVmsAndContainers(
        int mgmtCount,
        int cloudCount) throws Throwable {

      for (int i = 0; i < mgmtCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));
      }

      for (int i = 0; i < cloudCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()));
      }

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());


      CreateManagementPlaneLayoutWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      TestHelper.createDeploymentService(cloudStoreMachine);
    }
  }
}
