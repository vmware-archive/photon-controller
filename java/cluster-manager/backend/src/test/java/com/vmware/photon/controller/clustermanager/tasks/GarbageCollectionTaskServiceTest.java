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

import com.vmware.photon.controller.api.model.ClusterState;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ClusterApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmFactoryService;
import com.vmware.photon.controller.clustermanager.entities.InactiveVmService;
import com.vmware.photon.controller.clustermanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.clustermanager.helpers.TestEnvironment;
import com.vmware.photon.controller.clustermanager.helpers.TestHelper;
import com.vmware.photon.controller.clustermanager.helpers.TestHost;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
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

import com.google.common.util.concurrent.FutureCallback;
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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements tests for the {@link GarbageCollectionTaskService} class.
 */
public class GarbageCollectionTaskServiceTest {

  private TestHost host;
  private GarbageCollectionTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private GarbageCollectionTaskService.State buildValidState(TaskState.TaskStage stage) throws Throwable {
    GarbageCollectionTaskService.State state = ReflectionUtils.buildValidStartState(
        GarbageCollectionTaskService.State.class);
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.taskState.stage = stage;
    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new GarbageCollectionTaskService();
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
      taskService = new GarbageCollectionTaskService();
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
      GarbageCollectionTaskService.State startState = buildValidState(stage);
      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      GarbageCollectionTaskService.State savedState = host.getServiceState(
          GarbageCollectionTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
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
      taskService = new GarbageCollectionTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously(ClusterServiceFactory.SELF_LINK + clusterUri);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }

      try {
        host.deleteServiceSynchronously(GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri);
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

      GarbageCollectionTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri);

      GarbageCollectionTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri, null))
          .setBody(patchState);
      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      GarbageCollectionTaskService.State savedState = host.getServiceState(
          GarbageCollectionTaskService.State.class,
          GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri);

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

          // GarbageCollectionTaskService is restartable from any terminal states.
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage,
                                        TaskState.TaskStage patchStage) throws Throwable {
      GarbageCollectionTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri);

      GarbageCollectionTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, GarbageCollectionTaskFactoryService.SELF_LINK + clusterUri, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
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
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private ClusterApi clusterApi;
    private VmApi vmApi;
    private TestEnvironment machine;
    private GarbageCollectionTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidState(TaskState.TaskStage.CREATED);
      startState.controlFlags = 0x0;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);
      ProjectApi projectApi = mock(ProjectApi.class);
      clusterApi = mock(ClusterApi.class);
      vmApi = mock(VmApi.class);
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(clusterApi).when(apiClient).getClusterApi();
      doReturn(vmApi).when(apiClient).getVmApi();

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (machine != null) {
        machine.stop();
        machine = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      startState.clusterId = "clusterId";
      mockInactiveVmService(startState.clusterId, 3);
      mockStopVm(true);
      mockDeleteVm(true);

      GarbageCollectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageCollectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageCollectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
      verifyInactiveVm(startState.clusterId, 0);
    }

    @Test
    public void testNoInactiveVm() throws Throwable {
      startState.clusterId = "clusterId";
      mockStopVm(true);
      mockDeleteVm(true);

      GarbageCollectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageCollectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageCollectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
      verifyInactiveVm(startState.clusterId, 0);
    }

    @Test
    public void testStopVmFailure() throws Throwable {
      startState.clusterId = "clusterId";
      mockInactiveVmService(startState.clusterId, 3);
      mockStopVm(false);
      mockDeleteVm(true);

      GarbageCollectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageCollectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageCollectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      verifyInactiveVm(startState.clusterId, 3);
    }

    @Test
    public void testDeleteVmFailure() throws Throwable {
      startState.clusterId = "clusterId";
      mockInactiveVmService(startState.clusterId, 3);
      mockStopVm(false);
      mockDeleteVm(true);

      GarbageCollectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageCollectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageCollectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      verifyInactiveVm(startState.clusterId, 3);
    }

    private void mockInactiveVmService(String clusterId, int count) throws Throwable {
      for (int i = 0; i < count; i++) {
        InactiveVmService.State inactiveVm = ReflectionUtils.buildValidStartState(
            InactiveVmService.State.class);
        inactiveVm.clusterId = clusterId;
        machine.callServiceAndWaitForState(
            InactiveVmFactoryService.SELF_LINK,
            inactiveVm,
            InactiveVmService.State.class,
            (v) -> true);
      }
    }

    private void mockDeleteVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          Task taskReturnedByDeleteVm = new Task();
          taskReturnedByDeleteVm.setId("deleteVmTaskId");
          taskReturnedByDeleteVm.setState("COMPLETED");

          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteVm);
          return null;
        }).when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("delete vm failed"))
            .when(vmApi).deleteAsync(any(String.class), any(FutureCallback.class));
      }
    }

    private void mockStopVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          Task taskReturnedByStopVm = new Task();
          taskReturnedByStopVm.setId("stopVmTaskId");
          taskReturnedByStopVm.setState("COMPLETED");
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
          return null;
        }).when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("stop vm failed"))
            .when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));
      }
    }

    private void verifyInactiveVm(String clusterId, int expectedCount) throws Throwable {

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(InactiveVmService.State.class));
      QueryTask.Query clusterIdClause = new QueryTask.Query()
          .setTermPropertyName("clusterId")
          .setTermMatchValue(clusterId);
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(clusterIdClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(expectedCount));
    }
  }
}
