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

import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.github.dockerjava.api.DockerException;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.mockito.Matchers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Implements tests for {@link CreateAndValidateContainerWorkflowService}.
 */
public class CreateAndValidateContainerWorkflowServiceTest {

  private TestHost host;
  private CreateAndValidateContainerWorkflowService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateAndValidateContainerWorkflowService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED, null);
  }

  private CreateAndValidateContainerWorkflowService.State buildValidStartupState(TaskState.TaskStage stage) {
    return buildValidStartupState(stage, null);
  }

  private CreateAndValidateContainerWorkflowService.State buildValidStartupState(
      TaskState.TaskStage stage,
      CreateAndValidateContainerWorkflowService.TaskState.SubStage startSubStage) {
    CreateAndValidateContainerWorkflowService.State state = new CreateAndValidateContainerWorkflowService.State();
    state.taskState = new CreateAndValidateContainerWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = startSubStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.containerServiceLink = "CONTAINER_SERVICE_LINK";
    state.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";

    return state;
  }

  private CreateAndValidateContainerWorkflowService.State buildValidPatchState() {
    return buildValidPatchState(
        TaskState.TaskStage.STARTED,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER);
  }

  private CreateAndValidateContainerWorkflowService.State buildValidPatchState(
      TaskState.TaskStage stage,
      CreateAndValidateContainerWorkflowService.TaskState.SubStage subStage) {

    CreateAndValidateContainerWorkflowService.State state = new CreateAndValidateContainerWorkflowService.State();
    state.taskState = new CreateAndValidateContainerWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      ListeningExecutorService listeningExecutorService,
      DockerProvisionerFactory dockerProvisionerFactory,
      HealthCheckHelperFactory healthCheckHelperFactory,
      ServerSet cloudStoreServerSet,
      DeployerConfig deployerConfig)
      throws Throwable {
    return new TestEnvironment.Builder()
        .dockerProvisionerFactory(dockerProvisionerFactory)
        .listeningExecutorService(listeningExecutorService)
        .healthCheckerFactory(healthCheckHelperFactory)
        .deployerContext(deployerConfig.getDeployerContext())
        .cloudServerSet(cloudStoreServerSet)
        .hostCount(1)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateAndValidateContainerWorkflowService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
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
    public void setUpTest() throws Throwable {
      service = new CreateAndValidateContainerWorkflowService();
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
    public void testMinimalStartState(
        TaskState.TaskStage stage,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage subStage
    ) throws Throwable {

      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState(stage, subStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateAndValidateContainerWorkflowService.State savedState = host.getServiceState(
          CreateAndValidateContainerWorkflowService.State.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null}
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

      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateAndValidateContainerWorkflowService.State savedState =
          host.getServiceState(CreateAndValidateContainerWorkflowService.State.class);
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
    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateAndValidateContainerWorkflowService.State.class,
          NotNull.class);
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
      service = new CreateAndValidateContainerWorkflowService();
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
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {

      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      CreateAndValidateContainerWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      CreateAndValidateContainerWorkflowService.State savedState =
          host.getServiceState(CreateAndValidateContainerWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER},

          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP},

          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage
     * @param startSubStage
     * @param targetStage
     * @param targetSubStage
     * @throws Throwable
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        CreateAndValidateContainerWorkflowService.TaskState.SubStage targetSubStage)

        throws Throwable {

      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      CreateAndValidateContainerWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (DcpRuntimeException e) {
      }
    }

    @DataProvider(name = "illegalStageUpdatesInvalidPatch")
    public Object[][] getIllegalStageUpdatesInvalidPatch() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              CreateAndValidateContainerWorkflowService.TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      CreateAndValidateContainerWorkflowService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      CreateAndValidateContainerWorkflowService.State patchState = buildValidPatchState();
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
      List<String> immutableAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateAndValidateContainerWorkflowService.State.class,
          Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the create container task.
   */
  public class EndToEndTest {
    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private ListeningExecutorService listeningExecutorService;
    private CreateAndValidateContainerWorkflowService.State startState;

    private DockerProvisioner dockerProvisioner;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private DeployerConfig deployerConfig;

    @BeforeClass
    private void setup() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      deployerConfig = ConfigBuilder.build(DeployerConfig.class, this.getClass().getResource(configFilePath).getPath());
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @BeforeMethod
    public void setupTest() throws Throwable {
      dockerProvisioner = mock(DockerProvisioner.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);

      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);

      machine = createTestEnvironment(listeningExecutorService, dockerProvisionerFactory, healthCheckHelperFactory,
          cloudStoreMachine.getServerSet(), deployerConfig);
      setupDependencies();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
      dockerProvisioner = null;
      dockerProvisioner = null;
    }

    /**
     * This test verifies the success scenario when launching container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskSuccess() throws Throwable {
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenReturn("id");

      HealthChecker healthChecker = new HealthChecker() {
        @Override
        public boolean isReady() {
          return true;
        }
      };
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
      when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

      when(healthCheckHelperFactory.create(
          any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).thenReturn(healthCheckHelper);

      CreateAndValidateContainerWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateAndValidateContainerWorkflowFactoryService.SELF_LINK,
              startState,
              CreateAndValidateContainerWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    /**
     * This test verifies the failure scenario inside create container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskFailureWhenServiceFailsToStart() throws Throwable {
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenReturn("id");

      HealthChecker healthChecker = new HealthChecker() {
        @Override
        public boolean isReady() {
          return false;
        }
      };
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
      when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

      when(healthCheckHelperFactory.create(
          any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).thenReturn(healthCheckHelper);

      CreateAndValidateContainerWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateAndValidateContainerWorkflowFactoryService.SELF_LINK,
              startState,
              CreateAndValidateContainerWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    /**
     * This test verifies the failure scenario inside create container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskFailureInsideCreateContainer() throws Throwable {
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), Matchers.<String>anyVararg())).thenThrow(new
          DockerException("Start container " + "failed", 500));

      CreateAndValidateContainerWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateAndValidateContainerWorkflowFactoryService.SELF_LINK,
              startState,
              CreateAndValidateContainerWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void setupDependencies() throws Throwable {
      VmService.State vmServiceStartState = TestHelper.getVmServiceStartState();
      vmServiceStartState.ipAddress = "127.0.0.1";
      VmService.State vmServiceState = TestHelper.createVmService(machine, vmServiceStartState);

      // Use zookeeper for mocking service start inside container
      ContainerTemplateService.State containerTemplateState = TestHelper.getContainerTemplateServiceStartState(
          ContainersConfig.ContainerType.Zookeeper);

      ContainerTemplateService.State containerTemplateServiceState =
          TestHelper.createContainerTemplateService(machine, containerTemplateState);

      ContainerService.State containerStartState =
          TestHelper.createContainerService(machine, containerTemplateServiceState, vmServiceState);

      DeploymentService.State deploymentServiceState = TestHelper.createDeploymentService(cloudStoreMachine);

      startState = buildValidStartupState();
      startState.controlFlags = 0x0;
      startState.containerServiceLink = containerStartState.documentSelfLink;
      startState.maxRetries = 1;
      startState.taskPollDelay = 10; // 10msec for testing
      startState.deploymentServiceLink = deploymentServiceState.documentSelfLink;
    }
  }
}
