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

package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ClusterApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.clustermanager.helpers.TestEnvironment;
import com.vmware.photon.controller.clustermanager.helpers.TestHelper;
import com.vmware.photon.controller.clustermanager.helpers.TestHost;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterResizeTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.templates.KubernetesSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link ClusterResizeTaskService} class.
 */
public class ClusterResizeTaskServiceTest {

  private TestHost host;
  private ClusterResizeTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private ClusterResizeTask buildValidStartState(
      TaskState.TaskStage stage, ClusterResizeTask.TaskState.SubStage subStage) throws Throwable {

    ClusterResizeTask state = ReflectionUtils.buildValidStartState(ClusterResizeTask.class);
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private ClusterResizeTask buildValidPatchState(
      TaskState.TaskStage stage, ClusterResizeTask.TaskState.SubStage subStage) {

    ClusterResizeTask patchState = new ClusterResizeTask();
    patchState.taskState = new ClusterResizeTask.TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;

    return patchState;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new ClusterResizeTaskService();
    }

    /**
     * Tests that the taskService starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);
      assertThat(taskService.getOptions(), is(expected));
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
      taskService = new ClusterResizeTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test(dataProvider = "validStartStates")
    public void testValidStartState(TaskState.TaskStage stage,
                                    ClusterResizeTask.TaskState.SubStage subStage) throws Throwable {

      ClusterResizeTask startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      ClusterResizeTask savedState = host.getServiceState(
          ClusterResizeTask.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.STARTED,
              ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      ClusterResizeTask.TaskState.SubStage subStage) throws Throwable {

      ClusterResizeTask startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.CREATED,
              ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},

          {TaskState.TaskStage.STARTED, null},

          {TaskState.TaskStage.FINISHED,
              ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.FINISHED,
              ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},

          {TaskState.TaskStage.FAILED,
              ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.FAILED,
              ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},

          {TaskState.TaskStage.CANCELLED,
              ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.CANCELLED,
              ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      ClusterResizeTask startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          ClusterResizeTask.class, NotNull.class);
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
      taskService = new ClusterResizeTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage,
                                      ClusterResizeTask.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      ClusterResizeTask.TaskState.SubStage patchSubStage) throws Throwable {

      ClusterResizeTask startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      ClusterResizeTask patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      ClusterResizeTask savedState = host.getServiceState(ClusterResizeTask.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           ClusterResizeTask.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           ClusterResizeTask.TaskState.SubStage patchSubStage)
        throws Throwable {

      ClusterResizeTask startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState,
          ClusterMaintenanceTaskFactoryService.SELF_LINK + TestHost.SERVICE_URI);

      ClusterResizeTask patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              ClusterMaintenanceTaskFactoryService.SELF_LINK + TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidSubStageUpdates")
    public Object[][] getInvalidSubStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ClusterResizeTask.TaskState.SubStage.RESIZE_CLUSTER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "immutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      ClusterResizeTask startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ClusterResizeTask patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          ClusterResizeTask.TaskState.SubStage.INITIALIZE_CLUSTER);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI))
          .setBody(patchState);

      host.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "immutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ImmutableList.of("controlFlags", "clusterId", "newSlaveCount"));
    }
  }

  /**
   * End-to-end tests for the  cluster resize task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/clusters/scripts");
    private final File scriptLogDirectory = new File("/tmp/clusters/logs");
    private final File storageDirectory = new File("/tmp/clusters");

    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ClusterApi clusterApi;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private KubernetesClient kubernetesClient;

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private ClusterResizeTask startState;

    @BeforeClass
    public void setUpClass() throws Throwable {

      FileUtils.deleteDirectory(storageDirectory);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      apiClient = mock(ApiClient.class);
      clusterApi = mock(ClusterApi.class);
      projectApi = mock(ProjectApi.class);
      vmApi = mock(VmApi.class);
      doReturn(clusterApi).when(apiClient).getClusterApi();
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(vmApi).when(apiClient).getVmApi();

      kubernetesClient = mock(KubernetesClient.class);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .kubernetesClient(kubernetesClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .statusCheckHelper(new StatusCheckHelper())
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path slaveUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesSlaveNodeTemplate.SLAVE_USER_DATA_TEMPLATE);
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), NodeTemplateUtils.META_DATA_TEMPLATE);

      Files.createFile(slaveUserDataTemplate);
      Files.createFile(metaDataTemplate);

      startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      startState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      if (machine != null) {
        machine.stop();
        machine = null;
      }

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }

      startState = null;

      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test(dataProvider = "endToEndSuccessData")
    public void testEndToEndSuccess(Integer currentSlaveCount,
                                    Integer newSlaveCount,
                                    ClusterState expectedClusterState) throws Throwable {

      mockCluster(currentSlaveCount);
      mockGetClusterVms(5, true);
      mockResizeCluster(true);
      startState.newSlaveCount = newSlaveCount;

      ClusterResizeTask savedState = machine.callServiceAndWaitForState(
          ClusterResizeTaskFactoryService.SELF_LINK,
          startState,
          ClusterResizeTask.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(savedState.taskState);

      // Verify that a Cluster document has been updated
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ClusterService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(1));
      ClusterService.State clusterState = cloudStoreMachine.getServiceState(documentLinks.iterator().next(),
          ClusterService.State.class);

      assertThat(clusterState.documentSelfLink, containsString(savedState.clusterId));
      verifyCluster(savedState.clusterId, newSlaveCount, expectedClusterState);
    }

    @DataProvider(name = "endToEndSuccessData")
    public Object[][] getEndToEndSuccessData() {
      return new Object[][]{
          {5, 5, ClusterState.READY}, // Delta = 0
          {5, 7, ClusterState.RESIZING}, // Delta > 0
      };
    }

    @Test
    public void testEndToEndFailureContractClusterFails() throws Throwable {

      mockCluster(5);
      mockGetClusterVms(5, true);
      mockResizeCluster(true);
      startState.newSlaveCount = 3;

      ClusterResizeTask savedState = machine.callServiceAndWaitForState(
          ClusterResizeTaskFactoryService.SELF_LINK,
          startState,
          ClusterResizeTask.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      verifyCluster(savedState.clusterId, 5, ClusterState.READY);
    }

    private void mockResizeCluster(boolean isSuccess) throws Throwable {

      Task taskReturnedByCreateVm = new Task();
      taskReturnedByCreateVm.setId("createVmTaskId");
      taskReturnedByCreateVm.setState("COMPLETED");

      Task taskReturnedByAttachIso = new Task();
      taskReturnedByAttachIso.setId("attachIsoTaskId");
      taskReturnedByAttachIso.setState("COMPLETED");

      Task taskReturnedByStartVm = new Task();
      taskReturnedByStartVm.setId("startVmTaskId");
      taskReturnedByStartVm.setState("COMPLETED");

      Task taskReturnedByGetVmNetwork = new Task();
      taskReturnedByGetVmNetwork.setId("getVmNetworkTaskId");
      taskReturnedByGetVmNetwork.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByCreateVm.setEntity(entity);
      taskReturnedByAttachIso.setEntity(entity);
      taskReturnedByStartVm.setEntity(entity);
      taskReturnedByGetVmNetwork.setEntity(entity);

      Set<String> kubernetesNodeIps = new HashSet<>();
      kubernetesNodeIps.add("10.0.0.1");

      // Mock createVm
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("vm provisioning failed")).when(projectApi)
            .createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));
      }

      // Mock attachIso
      String scriptFileName = "esx-create-vm-iso";
      TestHelper.createSuccessScriptFile(scriptDirectory, scriptFileName);
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());

      // Mock startVm
      doAnswer(invocation -> {
        ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByStartVm);
        return null;
      }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));

      // Mock verifyVm
      doAnswer(invocation -> {
        ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetVmNetwork);
        return null;
      }).when(vmApi).getNetworksAsync(anyString(), any(FutureCallback.class));

      NetworkConnection networkConnection = new NetworkConnection();
      networkConnection.setNetwork("VM VLAN");
      networkConnection.setIpAddress("10.0.0.1");

      VmNetworks vmNetworks = new VmNetworks();
      vmNetworks.setNetworkConnections(Collections.singleton(networkConnection));

      taskReturnedByGetVmNetwork.setResourceProperties(vmNetworks);

      // Mock callGetNetworks
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(kubernetesNodeIps);
          return null;
        }).when(kubernetesClient).getNodeAddressesAsync(
            any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("wait cluster vm failed")).when(kubernetesClient).getNodeAddressesAsync(
            any(String.class), any(FutureCallback.class));
      }
    }

    private void mockCluster(int slaveCount) throws Throwable {
      ClusterService.State clusterState = ReflectionUtils.buildValidStartState(ClusterService.State.class);
      clusterState.slaveCount = slaveCount;
      clusterState.clusterState = ClusterState.READY;
      clusterState.clusterType = ClusterType.KUBERNETES;
      clusterState.extendedProperties = new HashMap<>();
      clusterState.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      clusterState.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.0.0.1");
      ClusterService.State savedClusterState = cloudStoreMachine.callServiceAndWaitForState(
          ClusterServiceFactory.SELF_LINK,
          clusterState,
          ClusterService.State.class,
          (clusterDocument) -> true);

      startState.clusterId = ServiceUtils.getIDFromDocumentSelfLink(savedClusterState.documentSelfLink);

      ClusterMaintenanceTaskService.State maintenanceState = new ClusterMaintenanceTaskService.State();
      maintenanceState.documentSelfLink = startState.clusterId;
      machine.callServiceAndWaitForState(
          ClusterMaintenanceTaskFactoryService.SELF_LINK,
          maintenanceState,
          ClusterMaintenanceTaskService.State.class,
          (maintenanceDocument) -> true);
    }

    private void mockGetClusterVms(int nodeCount, boolean isSuccess) throws Throwable {
      if (isSuccess) {
        final List<Vm> vmList = new ArrayList<>();
        final Set<String> vmTags = new HashSet<>();
        vmTags.add(ClusterUtil.createClusterNodeTag(startState.clusterId, NodeType.KubernetesSlave));

        for (int i = 0; i < nodeCount; ++i) {
          Vm vm = new Vm();
          vm.setId(String.format("vm-%d", i));
          vm.setTags(vmTags);
          vmList.add(vm);
        }

        doAnswer(invocation -> {
          ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(new ResourceList<>(vmList));
          return null;
        }).when(clusterApi).getVmsInClusterAsync(any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("get cluster vms failed"))
            .when(clusterApi).getVmsInClusterAsync(any(String.class), any(FutureCallback.class));
      }
    }

    private void verifyCluster(
        String clusterId,
        int expectedSlaveCount,
        ClusterState expectedClusterState) throws Throwable {

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ClusterService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(1));
      ClusterService.State clusterState = cloudStoreMachine.getServiceState(documentLinks.iterator().next(),
          ClusterService.State.class);

      assertThat(clusterState.documentSelfLink, containsString(clusterId));
      assertThat(clusterState.slaveCount, is(expectedSlaveCount));
      assertThat(clusterState.clusterState, is(expectedClusterState));
    }
  }
}
