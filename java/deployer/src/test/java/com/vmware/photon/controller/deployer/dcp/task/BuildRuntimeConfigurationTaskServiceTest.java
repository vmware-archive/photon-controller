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
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.NodeGroupBroadcastResponse;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.configuration.LoadBalancerServer;
import com.vmware.photon.controller.deployer.configuration.ZookeeperServer;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowService;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.mockito.internal.matchers.NotNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link BuildRuntimeConfigurationTaskService} class.
 */
public class BuildRuntimeConfigurationTaskServiceTest {

  private TestHost host;
  private BuildRuntimeConfigurationTaskService service;
  private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private BuildRuntimeConfigurationTaskService.State buildValidStartupState() {
    return buildValidStartupState(
        TaskState.TaskStage.CREATED);
  }

  private BuildRuntimeConfigurationTaskService.State buildValidStartupState(
      TaskState.TaskStage stage) {

    BuildRuntimeConfigurationTaskService.State state = new BuildRuntimeConfigurationTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.containerServiceLink = "CONTAINER_SERVICE_LINK";
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";

    return state;
  }

  private BuildRuntimeConfigurationTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private BuildRuntimeConfigurationTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    BuildRuntimeConfigurationTaskService.State state = new BuildRuntimeConfigurationTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      DeployerConfig deployerConfig,
      ListeningExecutorService listeningExecutorService,
      int hostCount)
      throws Throwable {
    cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

    return new TestEnvironment.Builder()
        .containersConfig(deployerConfig.getContainersConfig())
        .deployerContext(deployerConfig.getDeployerContext())
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
      service = new BuildRuntimeConfigurationTaskService();
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
      service = new BuildRuntimeConfigurationTaskService();
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

      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State savedState = host.getServiceState(
          BuildRuntimeConfigurationTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
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

      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State savedState =
          host.getServiceState(BuildRuntimeConfigurationTaskService.State.class);
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
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(BuildRuntimeConfigurationTaskService.State.class,
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
      service = new BuildRuntimeConfigurationTaskService();
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

      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      BuildRuntimeConfigurationTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State savedState =
          host.getServiceState(BuildRuntimeConfigurationTaskService.State.class);
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

      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      BuildRuntimeConfigurationTaskService.State patchState = buildValidPatchState(targetStage);
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

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      BuildRuntimeConfigurationTaskService.State patchState = buildValidPatchState();
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
          = ReflectionUtils.getAttributeNamesWithAnnotation(BuildRuntimeConfigurationTaskService.State.class,
          Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the build runtime configuration task.
   */
  public class EndToEndTest {
    private static final String configFilePath = "/config.yml";

    private TestEnvironment machine;
    private ListeningExecutorService listeningExecutorService;
    private BuildRuntimeConfigurationTaskService.State startState;
    private AuthClientHandler.ImplicitClient implicitClient;

    private DeployerConfig deployerConfig;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
        deployerConfig = ConfigBuilder.build(DeployerConfig.class,
            this.getClass().getResource(configFilePath).getPath());
        TestHelper.setContainersConfig(deployerConfig);
      implicitClient = new AuthClientHandler.ImplicitClient("client_id", "http://login", "http://logout");
    }

    @BeforeMethod
    public void setUpTest() throws Exception {
      startState = buildValidStartupState();
      startState.controlFlags = 0x0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      if (null != machine) {
        machine.stop();
        machine = null;
      }

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
      startState = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    /**
     * This test verifies the success scenario when building runtime configuration
     * for a container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "mandatoryEnvironmentVariable")
    public void testTaskSuccess(
        ContainersConfig.ContainerType containerType,
        List<String> requiredRuntimeEnvironmentVariables) throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, 1);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);

      setupValidOtherServiceDocuments(containerType);
      setupDeploymentServiceDocument(implicitClient);

      BuildRuntimeConfigurationTaskService.State finalState =
          machine.callServiceAndWaitForState(
              BuildRuntimeConfigurationTaskFactoryService.SELF_LINK,
              startState,
              BuildRuntimeConfigurationTaskService.State.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(finalState.taskState);

      ContainerService.State containerService = machine.getServiceState(startState.containerServiceLink,
          ContainerService.State.class);
      verifyExpectedKeysPresent(containerService.dynamicParameters, requiredRuntimeEnvironmentVariables);

      assertTrue(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
          .ENV_ENABLE_SYSLOG));
      String enableSyslog = containerService.dynamicParameters.get(BuildRuntimeConfigurationTaskService
          .ENV_ENABLE_SYSLOG);
      assertThat(enableSyslog, is("true"));

      assertTrue(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
          .ENV_SYSLOG_ENDPOINT));
      String syslogEndpoint = containerService.dynamicParameters.get(BuildRuntimeConfigurationTaskService
          .ENV_SYSLOG_ENDPOINT);
      assertThat(syslogEndpoint, is("1.2.3.4:514"));

      if (containerType == ContainersConfig.ContainerType.Deployer) {
        assertTrue(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
            .ENV_LOADBALANCER_PORT));
        String apifePort = containerService.dynamicParameters.get(BuildRuntimeConfigurationTaskService
            .ENV_LOADBALANCER_PORT);
        assertThat(apifePort, is("443"));
      }

      if (containerType == ContainersConfig.ContainerType.LoadBalancer) {
        assertTrue(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
            .ENV_LOADBALANCER_SERVERS));
        String servers = containerService.dynamicParameters.get(BuildRuntimeConfigurationTaskService
            .ENV_LOADBALANCER_SERVERS);
        Type listType = new TypeToken<ArrayList<LoadBalancerServer>>() {}.getType();
        List<LoadBalancerServer> serverList = new Gson().fromJson(servers, listType);
        assertThat(serverList.size(), is(3));
        assertThat(serverList.get(0).getServerName(), is("server-1"));
      }

      if (containerType == ContainersConfig.ContainerType.Zookeeper) {
        assertTrue(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
            .ENV_ZOOKEEPER_QUORUM));
        String servers = containerService.dynamicParameters.get(BuildRuntimeConfigurationTaskService
            .ENV_ZOOKEEPER_QUORUM);
        Type listType = new TypeToken<ArrayList<ZookeeperServer>>() {}.getType();
        List<ZookeeperServer> serverList = new Gson().fromJson(servers, listType);
        assertThat(serverList.size(), is(3));
        assertThat(serverList.get(0).getZookeeperInstance().startsWith("server.1"), is(true));
        assertThat(containerService.dynamicParameters.containsKey(BuildRuntimeConfigurationTaskService
            .ENV_ZOOKEEPER_STANDALONE), is(false));
      }
    }

    private void verifyExpectedKeysPresent(Map<String, String> actual, List<String> expectedKeys) {
      for (String key : expectedKeys) {
        assertTrue(actual.containsKey(key));
        assertNotNull(actual.get(key));
      }
    }

    @DataProvider(name = "mandatoryEnvironmentVariable")
    public Object[][] getMandatoryEnvironmentVariables() {
      List<String> chairmanList = new ArrayList<String>();
      chairmanList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      chairmanList.add(BuildRuntimeConfigurationTaskService.ENV_CHAIRMAN_REGISTRATION_ADDRESS);

      List<String> rootSchedulerList = new ArrayList<String>();
      rootSchedulerList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      rootSchedulerList.add(BuildRuntimeConfigurationTaskService.ENV_ROOT_SCHEDULER_REGISTRATION_ADDRESS);

      List<String> housekeeperList = new ArrayList<String>();
      housekeeperList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      housekeeperList.add(BuildRuntimeConfigurationTaskService.ENV_HOUSEKEEPER_REGISTRATION_ADDRESS);

      List<String> cloudStoreList = new ArrayList<String>();
      cloudStoreList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      cloudStoreList.add(BuildRuntimeConfigurationTaskService.ENV_CLOUD_STORE_REGISTRATION_ADDRESS);

      List<String> managementApiList = new ArrayList<String>();
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_API_REGISTRATION_ADDRESS);
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_ESX_HOST);
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_DATASTORE);
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_ENABLE_AUTH);
      managementApiList.add(BuildRuntimeConfigurationTaskService.ENV_SHARED_SECRET);

      List<String> deployerList = new ArrayList<String>();
      deployerList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM_URL);
      deployerList.add(BuildRuntimeConfigurationTaskService.ENV_DEPLOYER_REGISTRATION_ADDRESS);
      deployerList.add(BuildRuntimeConfigurationTaskService.ENV_LOADBALANCER_IP);
      deployerList.add(BuildRuntimeConfigurationTaskService.ENV_LOADBALANCER_PORT);
      deployerList.add(BuildRuntimeConfigurationTaskService.ENV_SHARED_SECRET);

      List<String> zookeeperList = new ArrayList<String>();
      zookeeperList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM);
      zookeeperList.add(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_MY_ID);

      List<String> loadBalancerList = new ArrayList<String>();
      loadBalancerList.add(BuildRuntimeConfigurationTaskService.ENV_LOADBALANCER_SERVERS);

      List<String> lightwaveList = new ArrayList<String>();
      lightwaveList.add(BuildRuntimeConfigurationTaskService.ENV_LIGHTWAVE_ADDRESS);
      lightwaveList.add(BuildRuntimeConfigurationTaskService.ENV_LIGHTWAVE_ADMIN_USERNAME);
      lightwaveList.add(BuildRuntimeConfigurationTaskService.ENV_LIGHTWAVE_PASSWORD);
      lightwaveList.add(BuildRuntimeConfigurationTaskService.ENV_LIGHTWAVE_DOMAIN);

      return new Object[][]{
          {
              ContainersConfig.ContainerType.Chairman,
              chairmanList
          },
          {
              ContainersConfig.ContainerType.RootScheduler,
              rootSchedulerList
          },
          {
              ContainersConfig.ContainerType.Housekeeper,
              housekeeperList
          },
          {
              ContainersConfig.ContainerType.CloudStore,
              cloudStoreList
          },
          {
              ContainersConfig.ContainerType.ManagementApi,
              managementApiList
          },
          {
              ContainersConfig.ContainerType.Zookeeper,
              zookeeperList

          },
          {
              ContainersConfig.ContainerType.Deployer,
              deployerList
          },
          {
              ContainersConfig.ContainerType.LoadBalancer,
              loadBalancerList
          },
          {
              ContainersConfig.ContainerType.Lightwave,
              lightwaveList
          },
      };
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
      deploymentStartState.oAuthUserName = "Administrator";
      deploymentStartState.oAuthPassword = "somepassword";
      deploymentStartState.oAuthTenantName = "esxcloud";
      deploymentStartState.syslogEndpoint = "1.2.3.4:514";
      deploymentStartState.ntpEndpoint = "5.6.7.8";
      deploymentStartState.state = DeploymentState.CREATING;

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
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryResults(queryResponse);

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
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryResults(queryResponse);

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

      workflowStartState.isAuthEnabled = true;

      CreateManagementPlaneLayoutWorkflowService.State serviceState =
          machine.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
      TestHelper.createDeploymentService(cloudStoreMachine);
    }
  }
}
