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

import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.nsxclient.apis.FabricApi;
import com.vmware.photon.controller.nsxclient.datatypes.FabricNodeState;
import com.vmware.photon.controller.nsxclient.datatypes.TransportNodeState;
import com.vmware.photon.controller.stats.plugin.gen.StatsPluginConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertTrue;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link ProvisionHostTaskService} class.
 */
public class ProvisionHostTaskServiceTest {

  private final String configFilePath = "/config.yml";

  /**
   * Dummy test case to make IntelliJ recognize this as a test class.
   */
  @Test
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public static class InitializationTest {

    private ProvisionHostTaskService provisionHostTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(provisionHostTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public static class HandleStartTest {

    private ProvisionHostTaskService provisionHostTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
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

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage taskStage,
                                    ProvisionHostTaskService.TaskState.SubStage taskSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(taskStage, taskSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return TestHelper.getValidStartStages(ProvisionHostTaskService.TaskState.SubStage.class);
    }

    @Test
    public void testNullStartStage() throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.CREATED));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage taskStage,
                                       ProvisionHostTaskService.TaskState.SubStage taskSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(taskStage, taskSubStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
      assertThat(serviceState.taskState.subStage, nullValue());
      assertThat(serviceState.controlFlags, is(0));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(provisionHostTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the HandlePatch method.
   */
  public static class HandlePatchTest {

    private ProvisionHostTaskService provisionHostTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionHostTaskService = new ProvisionHostTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testHost != null) {
        TestHost.destroy(testHost);
        testHost = null;
      }
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         ProvisionHostTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         ProvisionHostTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionHostTaskService.buildPatch(patchStage, patchSubStage, null));
      assertThat(testHost.sendRequestAndWait(patchOperation).getStatusCode(), is(200));

      ProvisionHostTaskService.State serviceState = testHost.getServiceState(ProvisionHostTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(ProvisionHostTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           ProvisionHostTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           ProvisionHostTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionHostTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(ProvisionHostTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ProvisionHostTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(provisionHostTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionHostTaskService.State patchState = ProvisionHostTaskService.buildPatch(TaskState.TaskStage.STARTED,
          ProvisionHostTaskService.TaskState.SubStage.GET_NETWORK_MANAGER_INFO, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link ProvisionHostTaskService} task.
   */
  public static class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private AgentControlClient agentControlClient;
    private AgentControlClientFactory agentControlClientFactory;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerContext deployerContext;
    private DeploymentService.State deploymentState;
    private FabricApi fabricApi;
    private HostClient hostClient;
    private HostClientFactory hostClientFactory;
    private HostService.State hostState;
    private HttpFileServiceClient httpFileServiceClient;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private NsxClientFactory nsxClientFactory;
    private ProvisionHostTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {

      //
      // Delete the storage directory to guard against garbage from a previous run.
      //
      FileUtils.deleteDirectory(storageDirectory);

      //
      // Create the test environment.
      //
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource("/config.yml").getPath()).getDeployerContext();
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      nsxClientFactory = mock(NsxClientFactory.class);

      cloudStoreEnvironment = new com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.Builder()
          .hostClientFactory(hostClientFactory)
          .build();

      testEnvironment = new TestEnvironment.Builder()
          .agentControlClientFactory(agentControlClientFactory)
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .nsxClientFactory(nsxClientFactory)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      assertTrue(scriptDirectory.mkdirs());
      assertTrue(scriptLogDirectory.mkdirs());
      assertTrue(vibDirectory.mkdirs());

      TestHelper.createSuccessScriptFile(deployerContext, ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME);
      TestHelper.createSuccessScriptFile(deployerContext, ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME);

      TestHelper.createSourceFile("vib1.vib", vibDirectory);
      TestHelper.createSourceFile("vib2.vib", vibDirectory);
      TestHelper.createSourceFile("vib3.vib", vibDirectory);

      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);

      deploymentState = TestHelper.createDeploymentService(cloudStoreEnvironment, true, true);
      hostState = TestHelper.createHostService(cloudStoreEnvironment, Collections.singleton(UsageTag.MGMT.name()),
          HostState.NOT_PROVISIONED);

      NsxClient nsxClient = mock(NsxClient.class);
      doReturn(nsxClient).when(nsxClientFactory).create(anyString(), anyString(), anyString());
      fabricApi = mock(FabricApi.class);
      doReturn(fabricApi).when(nsxClient).getFabricApi();

      doAnswer(MockHelper.mockRegisterFabricNode("FABRIC_NODE_ID"))
          .when(fabricApi)
          .registerFabricNode(any(), any());

      doAnswer(MockHelper.mockGetFabricNodeState(FabricNodeState.PENDING))
          .doAnswer(MockHelper.mockGetFabricNodeState(FabricNodeState.IN_PROGRESS))
          .doAnswer(MockHelper.mockGetFabricNodeState(FabricNodeState.SUCCESS))
          .when(fabricApi)
          .getFabricNodeState(eq("FABRIC_NODE_ID"), any());

      doAnswer(MockHelper.mockGetTransportZone("TRANSPORT_ZONE_ID"))
          .when(fabricApi)
          .getTransportZone(eq("TRANSPORT_ZONE_ID"), any());

      doAnswer(MockHelper.mockCreateTransportNode("TRANSPORT_NODE_ID"))
          .when(fabricApi)
          .createTransportNode(any(), any());

      doAnswer(MockHelper.mockGetTransportNodeState(TransportNodeState.PENDING))
          .doAnswer(MockHelper.mockGetTransportNodeState(TransportNodeState.IN_PROGRESS))
          .doAnswer(MockHelper.mockGetTransportNodeState(TransportNodeState.SUCCESS))
          .when(fabricApi)
          .getTransportNodeState(eq("TRANSPORT_NODE_ID"), any());

      httpFileServiceClient = mock(HttpFileServiceClient.class);

      doReturn(httpFileServiceClient)
          .when(httpFileServiceClientFactory)
          .create(anyString(), anyString(), anyString());

      doReturn(MockHelper.mockUploadFile(HttpsURLConnection.HTTP_OK))
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      agentControlClient = mock(AgentControlClient.class);

      doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.OK))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.OK))
          .when(agentControlClient)
          .getAgentStatus(any());

      doAnswer(MockHelper.mockProvisionAgent(ProvisionResultCode.OK))
          .when(agentControlClient)
          .provision(
              any(),
              any(),
              anyBoolean(),
              any(),
              anyString(),
              anyInt(),
              anyDouble(),
              anyString(),
              anyString(),
              any(),
              anyBoolean(),
              anyString(),
              anyString(),
              anyString(),
              any());

      doReturn(agentControlClient).when(agentControlClientFactory).create();

      hostClient = mock(HostClient.class);
      doReturn(hostClient).when(hostClientFactory).create();

      doAnswer(
          MockHelper.mockGetHostConfig(
              Arrays.asList("datastore1", "datastore2"),
              Arrays.asList("VM Network 1", "VM Network 2"),
              "ESXi 6.0"))
          .when(hostClient)
          .getHostConfig(any());

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.deploymentServiceLink = deploymentState.documentSelfLink;
      startState.hostServiceLink = hostState.documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DatastoreService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {

      //
      // Delete the test environment.
      //
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
      listeningExecutorService.shutdown();

      //
      // Clean up the storage directory.
      //
      FileUtils.deleteDirectory(storageDirectory);
    }

    @Test
    public void testSuccessWithNsx() throws Throwable {

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      verify(fabricApi).registerFabricNode(any(), any());
      verify(fabricApi, times(3)).getFabricNodeState(eq("FABRIC_NODE_ID"), any());
      verify(fabricApi, times(1)).getTransportZone(eq("TRANSPORT_ZONE_ID"), any());
      verify(fabricApi).createTransportNode(any(), any());
      verify(fabricApi, times(3)).getTransportNodeState(eq("TRANSPORT_NODE_ID"), any());
      verifyNoMoreInteractions(fabricApi);

      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib1.vib"), anyString(), eq(false));
      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib2.vib"), anyString(), eq(false));
      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib3.vib"), anyString(), eq(false));
      verifyNoMoreInteractions(httpFileServiceClient);

      verify(agentControlClient, times(7))
          .setIpAndPort(eq(hostState.hostAddress), eq(hostState.agentPort));

      ArgumentCaptor<StatsPluginConfig> pluginConfigCaptor = ArgumentCaptor.forClass(StatsPluginConfig.class);

      verify(agentControlClient)
          .provision(
              eq((List<String>) null),
              eq(deploymentState.imageDataStoreNames),
              eq(deploymentState.imageDataStoreUsedForVMs),
              eq((List<String>) null),
              eq(hostState.hostAddress),
              eq(hostState.agentPort),
              eq(0.0),
              eq(deploymentState.syslogEndpoint),
              eq(finalState.agentLogLevel),
              pluginConfigCaptor.capture(),
              eq(true),
              eq(ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink)),
              eq(ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink)),
              eq(deploymentState.ntpEndpoint),
              any());

      assertThat(pluginConfigCaptor.getValue().isStats_enabled(), is(deploymentState.statsEnabled));

      verify(agentControlClient, times(6)).getAgentStatus(any());

      verifyNoMoreInteractions(agentControlClient);

      HostService.State finalHostState = cloudStoreEnvironment.getServiceState(hostState.documentSelfLink,
          HostService.State.class);
      assertThat(finalHostState.reportedDatastores, containsInAnyOrder("datastore1", "datastore2"));
    }

    @Test
    public void testSuccessWithoutNsx() throws Throwable {

      DeploymentService.State deploymentState = TestHelper.createDeploymentService(cloudStoreEnvironment, true, false);
      startState.deploymentServiceLink = deploymentState.documentSelfLink;

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      verifyNoMoreInteractions(fabricApi);

      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib1.vib"), anyString(), eq(false));
      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib2.vib"), anyString(), eq(false));
      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib3.vib"), anyString(), eq(false));
      verifyNoMoreInteractions(httpFileServiceClient);

      verify(agentControlClient, times(7))
          .setIpAndPort(eq(hostState.hostAddress), eq(hostState.agentPort));

      ArgumentCaptor<StatsPluginConfig> pluginConfigCaptor = ArgumentCaptor.forClass(StatsPluginConfig.class);

      verify(agentControlClient)
          .provision(
              eq((List<String>) null),
              eq(deploymentState.imageDataStoreNames),
              eq(deploymentState.imageDataStoreUsedForVMs),
              eq((List<String>) null),
              eq(hostState.hostAddress),
              eq(hostState.agentPort),
              eq(0.0),
              eq(deploymentState.syslogEndpoint),
              eq(finalState.agentLogLevel),
              pluginConfigCaptor.capture(),
              eq(true),
              eq(ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink)),
              eq(ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink)),
              eq(deploymentState.ntpEndpoint),
              any());

      assertThat(pluginConfigCaptor.getValue().isStats_enabled(), is(deploymentState.statsEnabled));

      verify(agentControlClient, times(6)).getAgentStatus(any());

      verifyNoMoreInteractions(agentControlClient);

      HostService.State finalHostState = cloudStoreEnvironment.getServiceState(hostState.documentSelfLink,
          HostService.State.class);
      assertThat(finalHostState.reportedDatastores, containsInAnyOrder("datastore1", "datastore2"));
    }

    @Test
    public void testSuccessWithSingleVib() throws Throwable {

      FileUtils.deleteDirectory(vibDirectory);
      assertTrue(vibDirectory.mkdirs());
      TestHelper.createSourceFile("vib1.vib", vibDirectory);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      verify(httpFileServiceClient)
          .uploadFile(eq(vibDirectory.getAbsolutePath() + "/vib1.vib"), anyString(), eq(false));

      verifyNoMoreInteractions(httpFileServiceClient);
    }

    @Test
    public void testSucessWithAllowedDevices() throws Throwable {

      HostService.State hostStartState = TestHelper.getHostServiceStartState(UsageTag.MGMT, HostState.NOT_PROVISIONED);
      hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES, "datastore1, datastore2");
      hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS, "VM Network 1,VM Network 2");
      hostState = TestHelper.createHostService(cloudStoreEnvironment, hostStartState);
      startState.hostServiceLink = hostState.documentSelfLink;

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      ArgumentCaptor<StatsPluginConfig> pluginConfigCaptor = ArgumentCaptor.forClass(StatsPluginConfig.class);

      verify(agentControlClient)
          .provision(
              eq(Arrays.asList("datastore1", "datastore2")),
              eq(deploymentState.imageDataStoreNames),
              eq(deploymentState.imageDataStoreUsedForVMs),
              eq(Arrays.asList("VM Network 1", "VM Network 2")),
              eq(hostState.hostAddress),
              eq(hostState.agentPort),
              eq(0.0),
              eq(deploymentState.syslogEndpoint),
              eq(finalState.agentLogLevel),
              pluginConfigCaptor.capture(),
              eq(true),
              eq(ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink)),
              eq(ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink)),
              eq(deploymentState.ntpEndpoint),
              any());
    }

    @Test
    public void testRegisterFabricNodeFailure() throws Throwable {

      doThrow(new IOException("I/O exception during registerFabricNode"))
          .when(fabricApi)
          .registerFabricNode(any(), any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("I/O exception during registerFabricNode"));
    }

    @Test(dataProvider = "FailingFabricNodeStates")
    public void testWaitForFabricNodeFailure(FabricNodeState fabricNodeState) throws Throwable {

      doAnswer(MockHelper.mockGetFabricNodeState(fabricNodeState))
          .when(fabricApi)
          .getFabricNodeState(eq("FABRIC_NODE_ID"), any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("Registering host hostAddress as a fabric node failed with result"));
      assertThat(finalState.taskState.failure.message, containsString(fabricNodeState.toString()));
      assertThat(finalState.taskState.failure.message, containsString("fabric node ID FABRIC_NODE_ID"));
    }

    @DataProvider(name = "FailingFabricNodeStates")
    public Object[][] getFailingFabricNodeStates() {
      return new Object[][]{
          {FabricNodeState.FAILED},
          {FabricNodeState.PARTIAL_SUCCESS},
          {FabricNodeState.ORPHANED},
      };
    }

    @Test
    public void testGetTransportZoneFailure() throws Throwable {
      doThrow(new IOException("I/O exception during getTransportZone"))
          .when(fabricApi)
          .getTransportZone(eq("TRANSPORT_ZONE_ID"), any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("I/O exception during getTransportZone"));
    }

    @Test
    public void testCreateTransportNodeFailure() throws Throwable {

      doThrow(new IOException("I/O exception during createTransportNode"))
          .when(fabricApi)
          .createTransportNode(any(), any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, is("I/O exception during createTransportNode"));
    }

    @Test(dataProvider = "FailingTransportNodeStates")
    public void testWaitForTransportNodeFailure(TransportNodeState transportNodeState) throws Throwable {

      doAnswer(MockHelper.mockGetTransportNodeState(transportNodeState))
          .when(fabricApi)
          .getTransportNodeState(eq("TRANSPORT_NODE_ID"), any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("Registering host hostAddress as a transport node failed with result"));
      assertThat(finalState.taskState.failure.message, containsString(transportNodeState.toString()));
      assertThat(finalState.taskState.failure.message, containsString("transport node ID TRANSPORT_NODE_ID"));
    }

    @DataProvider(name = "FailingTransportNodeStates")
    public Object[][] getFailingTransportNodeStates() {
      return new Object[][]{
          {TransportNodeState.FAILED},
          {TransportNodeState.PARTIAL_SUCCESS},
          {TransportNodeState.ORPHANED},
      };
    }

    @Test
    public void testConfigureSyslogFailure() throws Throwable {

      TestHelper.createFailScriptFile(deployerContext, ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("Configuring syslog on host hostAddress failed with exit code 1"));
    }

    @Test
    public void testUploadVibFailure() throws Throwable {

      doReturn(MockHelper.mockUploadFile(HttpsURLConnection.HTTP_UNAUTHORIZED))
          .when(httpFileServiceClient)
          .uploadFile(anyString(), anyString(), anyBoolean());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          containsString("Unexpected HTTP result 401 when uploading VIB file"));
      assertThat(finalState.taskState.failure.message, containsString("to host hostAddress"));
    }

    @Test
    public void testInstallVibFailure() throws Throwable {

      TestHelper.createFailScriptFile(deployerContext, ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME);

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Installing VIB file"));
      assertThat(finalState.taskState.failure.message, containsString("to host hostAddress failed with exit code 1"));
    }

    @Test
    public void testWaitForAgentFailure() throws Throwable {

      doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .when(agentControlClient)
          .getAgentStatus(any());

      startState.agentStartMaxPollCount = 3;

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("The agent on host hostAddress failed to become ready after installation after 3 retries"));
    }

    @Test
    public void testProvisionAgentFailure() throws Throwable {

      doAnswer(MockHelper.mockProvisionAgent(ProvisionResultCode.SYSTEM_ERROR))
          .when(agentControlClient)
          .provision(
              any(),
              any(),
              anyBoolean(),
              any(),
              anyString(),
              anyInt(),
              anyDouble(),
              anyString(),
              anyString(),
              any(),
              anyBoolean(),
              anyString(),
              anyString(),
              anyString(),
              any());

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("Provisioning the agent on host hostAddress failed with error " +
              "com.vmware.photon.controller.common.clients.exceptions.SystemErrorException"));
    }

    @Test
    public void testWaitForAgentRestartFailure() throws Throwable {

      doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.OK))
          .doAnswer(MockHelper.mockGetAgentStatus(AgentStatusCode.RESTARTING))
          .when(agentControlClient)
          .getAgentStatus(any());

      startState.agentRestartMaxPollCount = 3;

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }

    @Test
    public void testWaitForHostUpdateFailure() throws Throwable {

      doAnswer(MockHelper.mockGetHostConfig(GetConfigResultCode.SYSTEM_ERROR))
          .when(hostClient)
          .getHostConfig(any());

      startState.hostStatusMaxPollCount = 3;

      ProvisionHostTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostTaskFactoryService.SELF_LINK,
              startState,
              ProvisionHostTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message,
          is("Host hostAddress failed to become ready after 3 retries"));
    }
  }

  private static ProvisionHostTaskService.State buildValidStartState(
      ProvisionHostTaskService.TaskState.TaskStage stage,
      ProvisionHostTaskService.TaskState.SubStage subStage) {
    ProvisionHostTaskService.State startState = new ProvisionHostTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.networkZoneId = "TRANSPORT_ZONE_ID";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (stage != null) {
      startState.taskState = new ProvisionHostTaskService.TaskState();
      startState.taskState.stage = stage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
