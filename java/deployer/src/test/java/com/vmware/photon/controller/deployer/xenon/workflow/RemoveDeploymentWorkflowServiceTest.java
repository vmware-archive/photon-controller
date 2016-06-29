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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.VmDiskOperation;
import com.vmware.photon.controller.api.base.FlavoredCompact;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.DisksApi;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TenantService;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.DeleteAgentTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link RemoveDeploymentWorkflowService} class.
 */
public class RemoveDeploymentWorkflowServiceTest {

  public static RemoveDeploymentWorkflowService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage) {

    RemoveDeploymentWorkflowService.State startState = new RemoveDeploymentWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new RemoveDeploymentWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;
    }

    return startState;
  }

  public RemoveDeploymentWorkflowService.State buildFullStartService(
      @Nullable TaskState.TaskStage startStage,
      @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage) {
    RemoveDeploymentWorkflowService.State state = buildValidStartState(startStage, startSubStage);

    return state;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private RemoveDeploymentWorkflowService removeDeploymentWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      removeDeploymentWorkflowService = new RemoveDeploymentWorkflowService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(removeDeploymentWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private RemoveDeploymentWorkflowService removeDeploymentWorkflowService;
    private boolean serviceCreated = false;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      removeDeploymentWorkflowService = new RemoveDeploymentWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
        serviceCreated = false;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      RemoveDeploymentWorkflowService.State serviceState =
          testHost.getServiceState(RemoveDeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
      assertThat(serviceState.taskState.stage, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "AutoProgressedStartStages")
    public void testAutoProgressedStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      RemoveDeploymentWorkflowService.State serviceState =
          testHost.getServiceState(RemoveDeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE));
    }

    @DataProvider(name = "AutoProgressedStartStages")
    public Object[][] getAutoProgressedStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      RemoveDeploymentWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = null;
      startService(startState);

      RemoveDeploymentWorkflowService.State serviceState =
          testHost.getServiceState(RemoveDeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testFailureRequiredFieldMissing(String fieldName) throws Throwable {
      RemoveDeploymentWorkflowService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              RemoveDeploymentWorkflowService.State.class, NotNull.class));
    }

    private void startService(RemoveDeploymentWorkflowService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(removeDeploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private RemoveDeploymentWorkflowService removeDeploymentWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      removeDeploymentWorkflowService = new RemoveDeploymentWorkflowService();
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
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      RemoveDeploymentWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(removeDeploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      RemoveDeploymentWorkflowService.State patchState =
          RemoveDeploymentWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      RemoveDeploymentWorkflowService.State serviceState =
          testHost.getServiceState(RemoveDeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable RemoveDeploymentWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      RemoveDeploymentWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(removeDeploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      RemoveDeploymentWorkflowService.State patchState =
          RemoveDeploymentWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, RemoveDeploymentWorkflowService.TaskState.SubStage.DEPROVISION_HOSTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFieldNames")
    public void testInvalidPatchStateValue(String fieldName) throws Throwable {
      RemoveDeploymentWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(removeDeploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      RemoveDeploymentWorkflowService.State patchState =
          RemoveDeploymentWorkflowService.buildPatch(
              TaskState.TaskStage.STARTED,
              RemoveDeploymentWorkflowService.TaskState.SubStage.REMOVE_FROM_API_FE,
              null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              RemoveDeploymentWorkflowService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the iso creation task.
   */
  public class EndToEndTest {
    private DeployerContext deployerContext;
    private static final String configFilePath = "/config.yml";

    private static final int NUMBER_OF_MGMT_ONLY_HOST = 4;
    private static final int NUMBER_OF_CLOUD_ONLY_HOST = 2;
    private static final int NUMBER_OF_MGMT_AND_CLOUD_HOST = 3;

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");

    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;

    private RemoveDeploymentWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreTestEnvironment;
    private SystemConfig systemConfig;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      startState = buildFullStartService(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreTestEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      this.systemConfig = spy(SystemConfig.createInstance(cloudStoreTestEnvironment.getHosts()[0]));
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (null != cloudStoreTestEnvironment) {
        cloudStoreTestEnvironment.stop();
        cloudStoreTestEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
      FileUtils.deleteDirectory(storageDirectory);
    }

    @DataProvider(name = "HostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
          // {5},
      };
    }

    private int queryNumOfContainers() throws Throwable {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      return documentLinks.size();
    }

    private void createContainerService(String containerId) throws Throwable {

      ContainerService.State containerService = new ContainerService.State();
      containerService.containerId = containerId;
      containerService.vmServiceLink = "vmServiceLink";
      containerService.containerTemplateServiceLink = "ctLink";
      testEnvironment.sendPostAndWait(ContainerFactoryService.SELF_LINK, containerService);
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndSuccessWithoutVirtualNetwork(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount, false, true, true);

      createContainerService("containerName1");
      int numOfContainers = queryNumOfContainers();
      assertThat(numOfContainers, is(1));

      RemoveDeploymentWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveDeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              RemoveDeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      numOfContainers = queryNumOfContainers();
      assertThat(numOfContainers, is(0));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      verifyVmServiceStates();
      verifyTenantServiceState();
      verifyProjectServiceState();
      verifyImageServiceState();
      verifyFlavorServiceStates();
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndSuccessWithVirtualNetwork(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount, true, true, true);

      createContainerService("containerName1");
      int numOfContainers = queryNumOfContainers();
      assertThat(numOfContainers, is(1));

      RemoveDeploymentWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveDeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              RemoveDeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      numOfContainers = queryNumOfContainers();
      assertThat(numOfContainers, is(0));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      verifyVmServiceStates();
      verifyTenantServiceState();
      verifyProjectServiceState();
      verifyImageServiceState();
      verifyFlavorServiceStates();
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndFailFromRemoveAPIFE(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount, false, false, true);

      RemoveDeploymentWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveDeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              RemoveDeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndFailDeprovisionManagementHosts(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount, false, true, false);

      RemoveDeploymentWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveDeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              RemoveDeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void verifyVmServiceStates() throws Throwable {
      List<VmService.State> states = queryForServiceStates(VmService.State.class, testEnvironment);

      // The number of VmService entities should be 0
      assertThat(states.size(), is(0));
    }

    private void verifyTenantServiceState() {
      List<TenantService.State> states = queryForServiceStates(TenantService.State.class, cloudStoreTestEnvironment);

      assertThat(states.size(), is(0));
    }

    private void verifyProjectServiceState() {
      List<ProjectService.State> states = queryForServiceStates(ProjectService.State.class, cloudStoreTestEnvironment);

      assertThat(states.size(), is(0));
    }

    private void verifyImageServiceState() {
      List<ImageService.State> states = queryForServiceStates(ImageService.State.class, cloudStoreTestEnvironment);

      assertThat(states.size(), is(0));
    }

    private void verifyFlavorServiceStates() {
      List<FlavorService.State> states = queryForServiceStates(FlavorService.State.class, cloudStoreTestEnvironment);

      assertThat(states.size(), is(0));
    }

    private <T extends ServiceDocument> List<T> queryForServiceStates(
        Class<T> classType, MultiHostEnvironment<?> multiHostEnvironment) {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(classType));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      try {
        NodeGroupBroadcastResponse queryResponse = multiHostEnvironment.sendBroadcastQueryAndWait(queryTask);
        Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

        List<T> states = new ArrayList<>();
        for (String documentLink : documentLinks) {
          states.add(multiHostEnvironment.getServiceState(documentLink, classType));
        }

        return states;
      } catch (Throwable t) {
        // If we cannot query, return null so that upper level should handle or rethrow.
        return null;
      }
    }


    private void startTestEnvironment(Integer hostCount,
                                      boolean virtualNetworkEnabled,
                                      boolean removeFromApifeSuccess,
                                      boolean deprovisionHostSuccess) throws Throwable {
      mockApiClient(removeFromApifeSuccess);

      MockHelper.mockCreateScriptFile(deployerContext, DeleteAgentTaskService.SCRIPT_NAME, deprovisionHostSuccess);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, deprovisionHostSuccess);

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .containersConfig(null)
          .hostClientFactory(hostClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .apiClientFactory(apiClientFactory)
          .dockerProvisionerFactory(null)
          .cloudServerSet(cloudStoreTestEnvironment.getServerSet())
          .hostCount(hostCount)
          .build();

      startState.deploymentServiceLink =
          TestHelper.createDeploymentService(cloudStoreTestEnvironment, false, virtualNetworkEnabled)
              .documentSelfLink;
      if (virtualNetworkEnabled) {
        DeploymentService.State deploymentPatchState = new DeploymentService.State();
        deploymentPatchState.networkZoneId = "networkZoneId";
        cloudStoreTestEnvironment.sendPatchAndWait(startState.deploymentServiceLink, deploymentPatchState);
      }

      createHostServices(
          Collections.singleton(UsageTag.MGMT.name()),
          NUMBER_OF_MGMT_ONLY_HOST);
      createHostServices(
          Collections.singleton(UsageTag.CLOUD.name()),
          NUMBER_OF_CLOUD_ONLY_HOST);
      createHostServices(
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())),
          NUMBER_OF_MGMT_AND_CLOUD_HOST);
    }

    private void mockApiClient(boolean isSuccess) throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);
      ProjectApi projectApi = mock(ProjectApi.class);
      TasksApi tasksApi = mock(TasksApi.class);
      VmApi vmApi = mock(VmApi.class);
      DisksApi diskApi = mock(DisksApi.class);
      FlavorApi flavorApi = mock(FlavorApi.class);
      ImagesApi imagesApi = mock(ImagesApi.class);
      TenantsApi tenantsApi = mock(TenantsApi.class);

      Tenant tenant1 = new Tenant();
      tenant1.setId("tenant1");
      tenant1.setName("tenant1");
      final ResourceList<Tenant> tenantResourceList = new ResourceList<>(Arrays.asList(tenant1));

      Project project1 = new Project();
      project1.setId("project1");
      project1.setName("project1");
      final ResourceList<Project> projectResourceList = new ResourceList<>(Arrays.asList(project1));

      PersistentDisk disk1 = new PersistentDisk();
      disk1.setId("disk1");
      disk1.setName("disk1");
      final ResourceList<PersistentDisk> diskResourceList = new ResourceList<>(Arrays.asList(disk1));

      FlavoredCompact vm1 = new FlavoredCompact();
      vm1.setId("vm1");
      vm1.setName("vm1");
      vm1.setKind("vm");
      FlavoredCompact vm2 = new FlavoredCompact();
      vm2.setId("vm2");
      vm2.setName("vm2");
      vm2.setKind("vm");
      final ResourceList<FlavoredCompact> vmResourceList = new ResourceList<>(Arrays.asList(vm1, vm2));

      Image image1 = new Image();
      image1.setId("image1");
      image1.setName("image1");
      final ResourceList<Image> imageResourceList = new ResourceList<>(Arrays.asList(image1));

      Flavor flavor1 = new Flavor();
      flavor1.setId("flavor1");
      flavor1.setName("flavor1");
      final ResourceList<Flavor> flavorResourceList = new ResourceList<>(Arrays.asList(flavor1));

      final Task taskReturnedByDeleteVm = TestHelper.createCompletedApifeTask("DELETE_VM");
      final Task taskReturnedByDeleteVmFlavor = TestHelper.createCompletedApifeTask("DELETE_VM_FLAVOR");
      final Task taskReturnedByDeleteDisk = TestHelper.createCompletedApifeTask("DELETE_DISK");
      final Task taskReturnedByDeleteImage = TestHelper.createCompletedApifeTask("DELETE_IMAGE");
      final Task taskReturnedByDeleteTenant = TestHelper.createCompletedApifeTask("DELETE_TENANT");
      final Task taskReturnedByDeleteProject = TestHelper.createCompletedApifeTask("DELETE_PROJECT");
      final Task taskReturnedByDetachDiskOperation = TestHelper.createCompletedApifeTask("DETACH_DISK");
      final Task taskReturnedByStopVm = TestHelper.createCompletedApifeTask("STOP_VM");

      if (isSuccess) {
        // List all tenants
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Tenant>>) invocation.getArguments()[0]).onSuccess
                (tenantResourceList);
            return null;
          }
        }).when(tenantsApi).listAllAsync(
            any(FutureCallback.class));

        // List all projects
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Project>>) invocation.getArguments()[1]).onSuccess
                (projectResourceList);
            return null;
          }
        }).when(tenantsApi).getProjectsAsync(any(String.class), any(FutureCallback.class));

        // List all vms
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<FlavoredCompact>>) invocation.getArguments()[1]).onSuccess
                (vmResourceList);
            return null;
          }
        }).when(projectApi).getVmsInProjectAsync(any(String.class), any(FutureCallback.class));

        // List all disks
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<PersistentDisk>>) invocation.getArguments()[1]).onSuccess
                (diskResourceList);
            return null;
          }
        }).when(projectApi).getDisksInProjectAsync(any(String.class), any(FutureCallback.class));

        // List all images
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Image>>) invocation.getArguments()[0]).onSuccess
                (imageResourceList);
            return null;
          }
        }).when(imagesApi).getImagesAsync(any(FutureCallback.class));

        // List all flavors
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Flavor>>) invocation.getArguments()[0]).onSuccess
                (flavorResourceList);
            return null;
          }
        }).when(flavorApi).listAllAsync(any(FutureCallback.class));

        // Delete project
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteProject);
            return null;
          }
        }).when(projectApi).deleteAsync(
            any(String.class), any(FutureCallback.class));

        // Delete tenant
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteTenant);
            return null;
          }
        }).when(tenantsApi).deleteAsync(any(String.class), any(FutureCallback.class));

        // Perform start operation
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
            return null;
          }
        }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback
            .class));

        // Perform stop operation
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
            return null;
          }
        }).when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback
            .class));

        // Delete VM flavor
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteVmFlavor);
            return null;
          }
        }).when(flavorApi).deleteAsync(any(String.class), any(FutureCallback.class));

        // Delete VM
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteVm);
            return null;
          }
        }).when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));

        // Detach disk
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByDetachDiskOperation);
            return null;
          }
        }).when(vmApi).detachDiskAsync(anyString(), any(VmDiskOperation.class), any(FutureCallback.class));

        // Delete disk
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteDisk);
            return null;
          }
        }).when(diskApi).deleteAsync(anyString(), any(FutureCallback.class));


        // Delete image
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteImage);
            return null;
          }
        }).when(imagesApi).deleteAsync(anyString(), any(FutureCallback.class));
      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Tenant>>) invocation.getArguments()[0]).onFailure(new Exception
                ("failed!"));
            return null;
          }
        }).when(tenantsApi).listAllAsync(
            any(FutureCallback.class));
      }

      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(diskApi).when(apiClient).getDisksApi();
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      doReturn(imagesApi).when(apiClient).getImagesApi();
      doReturn(tenantsApi).when(apiClient).getTenantsApi();
      doReturn(apiClient).when(apiClientFactory).create();
    }

    private void createHostServices(Set<String> usageTags, int count) throws Throwable {
      for (int i = 0; i < count; i++) {
        TestHelper.createHostService(cloudStoreTestEnvironment, usageTags);
      }
    }
  }
}
