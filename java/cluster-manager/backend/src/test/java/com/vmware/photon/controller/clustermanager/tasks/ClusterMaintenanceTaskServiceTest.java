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
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.templates.KubernetesSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableSet;
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
import java.io.IOException;
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
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link ClusterResizeTaskService} class.
 */
public class ClusterMaintenanceTaskServiceTest {

  private TestHost host;
  private ClusterMaintenanceTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private ClusterMaintenanceTaskService.State buildValidState(TaskState.TaskStage stage) throws Throwable {
    ClusterMaintenanceTaskService.State state = ReflectionUtils.buildValidStartState(
        ClusterMaintenanceTaskService.State.class);
    state.taskState.stage = stage;
    state.maxRetryCount = 1;
    state.retryIntervalSecond = 1;
    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new ClusterMaintenanceTaskService();
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
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);
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
      taskService = new ClusterMaintenanceTaskService();
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
    public void testValidStartState(TaskState.TaskStage stage) throws Throwable {
      ClusterMaintenanceTaskService.State startState = buildValidState(stage);
      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ClusterMaintenanceTaskService.State savedState = host.getServiceState(
          ClusterMaintenanceTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.FAILED}
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage) throws Throwable {
      ClusterMaintenanceTaskService.State startState = buildValidState(stage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    private final String clusterUri = "/" + UUID.randomUUID().toString();

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new ClusterMaintenanceTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously(ClusterServiceFactory.SELF_LINK + clusterUri);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }

      try {
        host.deleteServiceSynchronously(ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    // Because the task relies on DeployerXenonServiceHost to provide the cloud store helper for querying
    // the cluster entity, test host will not work in this case.
    @Test(dataProvider = "validStageUpdates", enabled = false)
    public void testValidStageUpdates(TaskState.TaskStage startStage,
                                      TaskState.TaskStage patchStage) throws Throwable {
      ClusterService.State clusterDocument = ReflectionUtils.buildValidStartState(ClusterService.State.class);
      clusterDocument.clusterState = ClusterState.READY;
      host.startServiceSynchronously(new ClusterService(), clusterDocument,
          ClusterServiceFactory.SELF_LINK + clusterUri);

      ClusterMaintenanceTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri);

      ClusterMaintenanceTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri, null))
          .setBody(patchState);
      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ClusterMaintenanceTaskService.State savedState = host.getServiceState(
          ClusterMaintenanceTaskService.State.class,
          ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri);

      if (patchStage != TaskState.TaskStage.STARTED) {
        assertThat(savedState.taskState.stage, is(patchStage));
      }
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() throws Throwable {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          // ClusterMaintenanceTaskService is restartable from any terminal states.
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           TaskState.TaskStage patchStage) throws Throwable {
      ClusterMaintenanceTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri);

      ClusterMaintenanceTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, ClusterMaintenanceTaskFactoryService.SELF_LINK + clusterUri, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidSubStageUpdates")
    public Object[][] getInvalidSubStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }
  }

  /**
   * End-to-end tests for the  cluster resize task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/clusters/scripts");
    private final File scriptLogDirectory = new File("/tmp/clusters/logs");
    private final File storageDirectory = new File("/tmp/clusters");

    private String clusterId;

    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ClusterApi clusterApi;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private KubernetesClient kubernetesClient;

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {

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
      FileUtils.deleteDirectory(storageDirectory);
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path slaveUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesSlaveNodeTemplate.SLAVE_USER_DATA_TEMPLATE);
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), NodeTemplateUtils.META_DATA_TEMPLATE);

      Files.createFile(slaveUserDataTemplate);
      Files.createFile(metaDataTemplate);

      taskService = new ClusterMaintenanceTaskService();

      clusterId = UUID.randomUUID().toString();
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

      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @Test(dataProvider = "clusterStates")
    public void testSuccessForClusterState(ClusterState clusterState) throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetClusterVms(5, true, true);
      mockCluster(5, clusterState);

      ClusterMaintenanceTaskService.State maintenanceTask = startMaintenance();
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FINISHED));
    }

    @DataProvider(name = "clusterStates")
    public Object[][] getClusterStates() {
      return new Object[][]{
          {ClusterState.READY},
          {ClusterState.CREATING},
          {ClusterState.RESIZING},
          {ClusterState.ERROR},
      };
    }

    @Test
    public void testFailureWhenGarbageInspectionFails() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetClusterVms(5, true, false);
      mockCluster(5, ClusterState.RESIZING);

      ClusterMaintenanceTaskService.State maintenanceTask = startMaintenance(TaskState.TaskStage.FAILED);
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(maintenanceTask.retryCount, is(maintenanceTask.maxRetryCount));

      // Wait until cluster state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      ClusterService.State clusterState = cloudStoreMachine.getServiceState(
          ClusterServiceFactory.SELF_LINK + "/" + clusterId,
          ClusterService.State.class);
      assertThat(clusterState.clusterState, is(ClusterState.ERROR));
    }

    @Test
    public void testFailureWhenExpandingClusterFails() throws Throwable {
      mockVmProvision(false);
      mockVmDelete(true);
      mockGetClusterVms(5, true, true);
      mockCluster(3, ClusterState.RESIZING);

      ClusterMaintenanceTaskService.State maintenanceTask = startMaintenance(TaskState.TaskStage.FAILED);
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(maintenanceTask.retryCount, is(maintenanceTask.maxRetryCount));

      // Wait until cluster state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      ClusterService.State clusterState = cloudStoreMachine.getServiceState(
          ClusterServiceFactory.SELF_LINK + "/" + clusterId,
          ClusterService.State.class);
      assertThat(clusterState.clusterState, is(ClusterState.ERROR));
    }

    @Test
    public void testSuccessForDeletingCluster() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetClusterVms(5, true, true);
      mockCluster(5, ClusterState.PENDING_DELETE);

      ClusterMaintenanceTaskService.State maintenanceTask = startMaintenance();
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FINISHED));

      assertThat(queryClusterLinks().size(), is(0));
    }

    @Test(dataProvider = "otherClusterExistenceForTestingDeletedCluster")
    public void testSuccessForDeletedCluster(boolean otherClusterExist) throws Throwable {
      if (otherClusterExist) {
        mockCluster(5, ClusterState.READY);
      }

      // Create a maintenance task for this cluster.
      ClusterMaintenanceTaskService.State taskState = buildValidState(TaskState.TaskStage.CREATED);
      String clusterId = UUID.randomUUID().toString();
      taskState.documentSelfLink = clusterId;

      // Start the maintenance task
      taskState = machine.callServiceSynchronously(
          ClusterMaintenanceTaskFactoryService.SELF_LINK, taskState, ClusterMaintenanceTaskService.State.class);

      // Patch the task to Started state.
      ClusterMaintenanceTaskService.State patchState = new ClusterMaintenanceTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = TaskState.TaskStage.STARTED;
      Operation patchOp = machine.sendPatchAndWait(taskState.documentSelfLink, patchState);
      assertThat(patchOp.getStatusCode(), is(200));

      // Wait till the task gets deleted
      Thread.sleep(90000);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ClusterMaintenanceTaskService.State.class));
      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(ClusterMaintenanceTaskService.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(ClusterMaintenanceTaskFactoryService.SELF_LINK + "/" + clusterId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(idClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(0));
    }

    @DataProvider(name = "otherClusterExistenceForTestingDeletedCluster")
    public Object[][] getOtherClusterExistenceForTestingDeletedCluster() {
      return new Object[][]{
          {true},
          {false}
      };
    }

    @Test
    public void testFailureWhenClusterDeletionFails() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetClusterVms(5, true, false);
      mockCluster(5, ClusterState.PENDING_DELETE);
      ClusterMaintenanceTaskService.State maintenanceTask = startMaintenance();

      // Wait until cluster state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private Set<String> queryClusterLinks() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ClusterService.State.class));

      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(ClusterService.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(ClusterServiceFactory.SELF_LINK + "/" + clusterId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(idClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      return QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
    }

    private ClusterMaintenanceTaskService.State startMaintenance() throws Throwable {
      return startMaintenance(null);
    }

    private ClusterMaintenanceTaskService.State startMaintenance(TaskState.TaskStage finalStage) throws Throwable {

      // Create a maintenance task for this cluster.
      ClusterMaintenanceTaskService.State taskState = buildValidState(TaskState.TaskStage.CREATED);
      taskState.documentSelfLink = clusterId;

      // Start the maintenance task
      taskState = machine.callServiceSynchronously(
          ClusterMaintenanceTaskFactoryService.SELF_LINK, taskState, ClusterMaintenanceTaskService.State.class);

      // Patch the task to Started state.
      ClusterMaintenanceTaskService.State patchState = new ClusterMaintenanceTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = TaskState.TaskStage.STARTED;
      Operation patchOp = machine.sendPatchAndWait(taskState.documentSelfLink, patchState);
      assertThat(patchOp.getStatusCode(), is(200));

      return machine.waitForServiceState(
          ClusterMaintenanceTaskService.State.class,
          taskState.documentSelfLink,
          (ClusterMaintenanceTaskService.State state) -> {
            if (finalStage == null) {
              return TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal();
            } else {
              return state.taskState.stage == finalStage;
            }
          });
    }

    private void mockCluster(int slaveCount, ClusterState clusterState) throws Throwable {
      ClusterService.State cluster = ReflectionUtils.buildValidStartState(ClusterService.State.class);
      cluster.slaveCount = slaveCount;
      cluster.clusterState = clusterState;
      cluster.clusterType = ClusterType.KUBERNETES;
      cluster.extendedProperties = new HashMap<>();
      cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.0.0.1");
      cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS,
          "10.0.0.2,10.0.0.3,10.0.0.4");
      cluster.documentSelfLink = clusterId;
      cloudStoreMachine.callServiceAndWaitForState(
          ClusterServiceFactory.SELF_LINK,
          cluster,
          ClusterService.State.class,
          (clusterDocument) -> true);
    }

    private void mockGetClusterVms(int nodeCount, boolean hasInactiveVm, boolean isSuccess) throws Throwable {
      if (isSuccess) {
        final List<Vm> vmList = new ArrayList<>();
        final Set<String> vmNames = new HashSet<>();
        final Set<String> vmMasterTags = ImmutableSet.of(ClusterUtil.createClusterNodeTag(
            clusterId, NodeType.KubernetesMaster));
        final Set<String> vmSlaveTags = ImmutableSet.of(ClusterUtil.createClusterNodeTag(
            clusterId, NodeType.KubernetesSlave));

        for (int i = 0; i < nodeCount; ++i) {
          String vmId = new UUID(0, i).toString();
          Vm vm = new Vm();
          vm.setId(vmId);
          vm.setName(vmId);
          vm.setTags(i == 0 ? vmMasterTags : vmSlaveTags);
          vmList.add(vm);
          if (!hasInactiveVm || i % 2 == 0) {
            // make roughly half of the vms "inactive" and not return them in
            // kubernetesClient.getNodeNamesAsync
            vmNames.add(vmId);
          }
        }

        doAnswer(invocation -> {
          ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(new ResourceList<>(vmList));
          return null;
        }).when(clusterApi).getVmsInClusterAsync(any(String.class), any(FutureCallback.class));
        doAnswer(invocation -> {
          ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(vmNames);
          return null;
        }).when(kubernetesClient).getNodeNamesAsync(
            any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new IOException("get cluster vms failed"))
            .when(clusterApi).getVmsInClusterAsync(any(String.class), any(FutureCallback.class));
        doThrow(new IOException("get node names failed")).when(kubernetesClient).getNodeNamesAsync(
            any(String.class), any(FutureCallback.class));
      }
    }

    private void mockVmProvision(boolean isSuccess) throws Throwable {

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
        ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStartVm);
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

      // Mock kubernetesClient.getNodeAddressesAsync
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

    private void mockVmDelete(boolean isSuccess) throws Throwable {
      Task taskReturnedByStopVm = new Task();
      taskReturnedByStopVm.setId("stopVmTaskId");
      taskReturnedByStopVm.setState("COMPLETED");

      Task taskReturnedByDeleteVm = new Task();
      taskReturnedByDeleteVm.setId("deleteVmTaskId");
      taskReturnedByDeleteVm.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByStopVm.setEntity(entity);
      taskReturnedByDeleteVm.setEntity(entity);

      if (isSuccess) {
        // Mock stopVm
        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
          return null;
        }).when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));

        // Mock deleteVm
        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteVm);
          return null;
        }).when(vmApi).deleteAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("stop cluster vm failed"))
            .when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));

        doThrow(new RuntimeException("delete cluster vm failed"))
            .when(vmApi).deleteAsync(anyString(), any(FutureCallback.class));
      }
    }
  }
}
