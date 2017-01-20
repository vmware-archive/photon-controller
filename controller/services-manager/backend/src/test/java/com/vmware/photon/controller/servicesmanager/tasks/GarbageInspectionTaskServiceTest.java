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
package com.vmware.photon.controller.servicesmanager.tasks;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.resource.ServiceApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.model.NetworkConnection;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmNetworks;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.entities.InactiveVmService;
import com.vmware.photon.controller.servicesmanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.servicesmanager.helpers.TestEnvironment;
import com.vmware.photon.controller.servicesmanager.helpers.TestHelper;
import com.vmware.photon.controller.servicesmanager.helpers.TestHost;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.servicesmanager.util.ServicesUtil;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class implements tests for the {@link GarbageInspectionTaskService} class.
 */
public class GarbageInspectionTaskServiceTest {

  private TestHost host;
  private GarbageInspectionTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private GarbageInspectionTaskService.State buildValidState(TaskState.TaskStage stage) throws Throwable {
    GarbageInspectionTaskService.State state = ReflectionUtils.buildValidStartState(
        GarbageInspectionTaskService.State.class);
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
      taskService = new GarbageInspectionTaskService();
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
      taskService = new GarbageInspectionTaskService();
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
      GarbageInspectionTaskService.State startState = buildValidState(stage);
      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      GarbageInspectionTaskService.State savedState = host.getServiceState(
          GarbageInspectionTaskService.State.class);
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
    private final String serviceId = "/" + UUID.randomUUID().toString();

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new GarbageInspectionTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously(ServiceStateFactory.SELF_LINK + serviceId);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }

      try {
        host.deleteServiceSynchronously(GarbageInspectionTaskFactoryService.SELF_LINK + serviceId);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    // Because the task relies on DeployerXenonServiceHost to provide the cloud store helper for querying
    // the service entity, test host will not work in this case.
    @Test(dataProvider = "validStageUpdates", enabled = false)
    public void testValidStageUpdates(TaskState.TaskStage startStage,
                                      TaskState.TaskStage patchStage) throws Throwable {
      ServiceState.State serviceDocument = ReflectionUtils.buildValidStartState(ServiceState.State.class);
      serviceDocument.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      host.startServiceSynchronously(new ServiceState(), serviceDocument,
          ServiceStateFactory.SELF_LINK + serviceId);

      GarbageInspectionTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          GarbageInspectionTaskFactoryService.SELF_LINK + serviceId);

      GarbageInspectionTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              GarbageInspectionTaskFactoryService.SELF_LINK + serviceId, null))
          .setBody(patchState);
      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      GarbageInspectionTaskService.State savedState = host.getServiceState(
          GarbageInspectionTaskService.State.class,
          GarbageInspectionTaskFactoryService.SELF_LINK + serviceId);

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

          // GarbageInspectionTaskService is restartable from any terminal states.
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage,
                                        TaskState.TaskStage patchStage) throws Throwable {
      GarbageInspectionTaskService.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          GarbageInspectionTaskFactoryService.SELF_LINK + serviceId);

      GarbageInspectionTaskService.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, GarbageInspectionTaskFactoryService.SELF_LINK + serviceId, null))
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

    private KubernetesClient kubernetesClient;
    private ServiceApi serviceApi;
    private VmApi vmApi;
    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private GarbageInspectionTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidState(TaskState.TaskStage.CREATED);
      startState.controlFlags = 0x0;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      kubernetesClient = mock(KubernetesClient.class);
      ApiClient apiClient = mock(ApiClient.class);
      serviceApi = mock(ServiceApi.class);
      doReturn(serviceApi).when(apiClient).getServiceApi();
      vmApi = mock(VmApi.class);
      doReturn(vmApi).when(apiClient).getVmApi();

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreMachine.getServerSet());

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .kubernetesClient(kubernetesClient)
          .statusCheckHelper(new StatusCheckHelper())
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
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
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      startState.serviceId = mockServiceService();
      mockKubernetes();
      mockServiceApi(startState.serviceId);
      mockVmApi(true);

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
      verifyInactiveVm(startState.serviceId);
    }

    @Test
    public void testEndToEndHarborSuccess() throws Throwable {
      startState.serviceId = mockServiceService(ServiceType.HARBOR);
      mockServiceApi(startState.serviceId);
      mockVmApi(true);

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }

    @Test
    public void testEndToEndHarborFailure() throws Throwable {
      startState.serviceId = mockServiceService(ServiceType.HARBOR);

      doThrow(new IOException()).when(serviceApi).getVmsInServiceAsync(anyString(), any(FutureCallback.class));

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    public void testServiceNotFound() throws Throwable {
      startState.serviceId = "invalid-service-id";

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
              60000,
              3);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testServiceApiCallFailure() throws Throwable {
      startState.serviceId = mockServiceService();

      doThrow(new IOException()).when(serviceApi).getVmsInServiceAsync(anyString(), any(FutureCallback.class));

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testVmApiCallFailure() throws Throwable {
      startState.serviceId = mockServiceService();
      mockServiceApi(startState.serviceId);

      doThrow(new IOException()).when(vmApi).getNetworksAsync(anyString(), any(FutureCallback.class));

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testKubernetesCallFailure() throws Throwable {
      startState.serviceId = mockServiceService();
      mockServiceApi(startState.serviceId);
      mockVmApi(true);

      doThrow(new IOException()).when(kubernetesClient).getAvailableNodeNamesAsync(anyString(),
          any(FutureCallback.class));

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testRepeat() throws Throwable {
      startState.serviceId = mockServiceService();
      mockServiceApi(startState.serviceId);
      mockVmApi(true);
      mockKubernetes();

      GarbageInspectionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
      TestHelper.assertTaskStateFinished(serviceState.taskState);
      verifyInactiveVm(startState.serviceId);

      // repeat inspection should not yield duplicated results
      GarbageInspectionTaskService.State repeatServiceState =
          machine.callServiceAndWaitForState(
              GarbageInspectionTaskFactoryService.SELF_LINK,
              startState,
              GarbageInspectionTaskService.State.class,
              state -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(repeatServiceState.taskState);
      verifyInactiveVm(startState.serviceId);
    }

    private String mockServiceService() throws Throwable {
      return mockServiceService(ServiceType.KUBERNETES);
    }

    private String mockServiceService(ServiceType serviceType) throws Throwable {
      ServiceState.State serviceState = ReflectionUtils.buildValidStartState(ServiceState.State.class);
      serviceState.workerCount = 1;
      serviceState.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      serviceState.serviceType = serviceType;
      serviceState.extendedProperties = new HashMap<>();
      serviceState.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      // Set the master Ip to empty so that GarbageInspectionTaskService will trigger WaitForNetworkTaskService
      // which will validate the testVmApiCallFailure Test.
      serviceState.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "");
      ServiceState.State savedServiceState = cloudStoreMachine.callServiceAndWaitForState(
          ServiceStateFactory.SELF_LINK,
          serviceState,
          ServiceState.State.class,
          (serviceDocument) -> true);

      return ServiceUtils.getIDFromDocumentSelfLink(savedServiceState.documentSelfLink);
    }

    private void mockKubernetes() throws IOException {
      final Set<String> nodeNames = new HashSet<>();
      nodeNames.add("activeVm");

      doAnswer(invocation -> {
        ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(nodeNames);
        return null;
      }).when(kubernetesClient).getAvailableNodeNamesAsync(
          anyString(), any(FutureCallback.class));
    }

    private void mockServiceApi(String serviceId) throws IOException {
      final List<Vm> vms = new ArrayList<>();
      String masterTag = ServicesUtil.createServiceNodeTag(serviceId, NodeType.KubernetesMaster);
      String workerTag = ServicesUtil.createServiceNodeTag(serviceId, NodeType.KubernetesWorker);

      Vm masterVm = new Vm();
      masterVm.setName("masterVm");
      masterVm.setId("masterVmId");
      masterVm.setTags(ImmutableSet.of(masterTag));
      vms.add(masterVm);

      Vm activeVm = new Vm();
      activeVm.setName("activeVm");
      activeVm.setId("activeVmId");
      activeVm.setTags(ImmutableSet.of(workerTag));
      vms.add(activeVm);

      Vm inactiveVm = new Vm();
      inactiveVm.setName("inactiveVm");
      inactiveVm.setId("inactiveVmId");
      inactiveVm.setTags(ImmutableSet.of(workerTag));
      vms.add(inactiveVm);

      doAnswer(invocation -> {
        ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(new ResourceList<>(vms));
        return null;
      }).when(serviceApi).getVmsInServiceAsync(
          anyString(), any(FutureCallback.class));
    }

    private void mockVmApi(boolean isSuccess) throws Throwable {
      Task task = new Task();
      task.setId("getVmNetworkTaskId");
      task.setState("COMPLETED");

      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(task);
          return null;
        }).when(vmApi).getNetworksAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("verify vm failed"))
            .when(vmApi).getNetworksAsync(anyString(), any(FutureCallback.class));
      }

      NetworkConnection networkConnection = new NetworkConnection();
      networkConnection.setNetwork("VM VLAN");
      networkConnection.setIpAddress("IP_ADDRESS");
      networkConnection.setMacAddress("00:0c:29:9e:6b:00");

      VmNetworks vmNetworks = new VmNetworks();
      vmNetworks.setNetworkConnections(Collections.singleton(networkConnection));

      task.setResourceProperties(vmNetworks);
    }

    private void verifyInactiveVm(String serviceId) throws Throwable {

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(InactiveVmService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(1));
      InactiveVmService.State serviceState = machine.getServiceState(documentLinks.iterator().next(),
          InactiveVmService.State.class);

      assertThat(serviceState.documentSelfLink, containsString("/inactiveVmId"));
      assertThat(serviceState.serviceId, is(serviceId));
    }
  }
}
