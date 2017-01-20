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
import com.vmware.photon.controller.api.client.resource.ProjectApi;
import com.vmware.photon.controller.api.client.resource.ServiceApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.model.NetworkConnection;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.VmNetworks;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.servicesmanager.helpers.TestEnvironment;
import com.vmware.photon.controller.servicesmanager.helpers.TestHelper;
import com.vmware.photon.controller.servicesmanager.helpers.TestHost;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
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
 * This class implements tests for the {@link ServiceMaintenanceTask} class.
 */
public class ServiceMaintenanceTaskTest {

  private TestHost host;
  private ServiceMaintenanceTask taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private ServiceMaintenanceTask.State buildValidState(TaskState.TaskStage stage) throws Throwable {
    ServiceMaintenanceTask.State state = ReflectionUtils.buildValidStartState(
        ServiceMaintenanceTask.State.class);
    state.taskState.stage = stage;
    return state;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new ServiceMaintenanceTask();
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
      taskService = new ServiceMaintenanceTask();
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
      ServiceMaintenanceTask.State startState = buildValidState(stage);
      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ServiceMaintenanceTask.State savedState = host.getServiceState(
          ServiceMaintenanceTask.State.class);
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
      ServiceMaintenanceTask.State startState = buildValidState(stage);
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
    private final String serviceUri = "/" + UUID.randomUUID().toString();

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new ServiceMaintenanceTask();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously(ServiceStateFactory.SELF_LINK + serviceUri);
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }

      try {
        host.deleteServiceSynchronously(ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri);
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
          ServiceStateFactory.SELF_LINK + serviceUri);

      ServiceMaintenanceTask.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri);

      ServiceMaintenanceTask.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri, null))
          .setBody(patchState);
      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ServiceMaintenanceTask.State savedState = host.getServiceState(
          ServiceMaintenanceTask.State.class,
          ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri);

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

          // ServiceMaintenanceTask is restartable from any terminal states.
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           TaskState.TaskStage patchStage) throws Throwable {
      ServiceMaintenanceTask.State startState = buildValidState(startStage);
      host.startServiceSynchronously(taskService, startState,
          ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri);

      ServiceMaintenanceTask.State patchState = buildValidState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, ServiceMaintenanceTaskFactory.SELF_LINK + serviceUri, null))
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
   * End-to-end tests for the  service resize task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/services/scripts");
    private final File scriptLogDirectory = new File("/tmp/services/logs");
    private final File storageDirectory = new File("/tmp/services");

    private String serviceId;

    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ServiceApi serviceApi;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private KubernetesClient kubernetesClient;

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      apiClient = mock(ApiClient.class);
      serviceApi = mock(ServiceApi.class);
      projectApi = mock(ProjectApi.class);
      vmApi = mock(VmApi.class);
      doReturn(serviceApi).when(apiClient).getServiceApi();
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

      Path workerUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesWorkerNodeTemplate.WORKER_USER_DATA_TEMPLATE);
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), NodeTemplateUtils.META_DATA_TEMPLATE);

      Files.createFile(workerUserDataTemplate);
      Files.createFile(metaDataTemplate);

      taskService = new ServiceMaintenanceTask();

      serviceId = UUID.randomUUID().toString();
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

    @Test(dataProvider = "serviceStates")
    public void testSuccessForServiceState(com.vmware.photon.controller.api.model.ServiceState serviceState)
        throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetServiceVms(5, true, true);
      mockService(5, serviceState);

      ServiceMaintenanceTask.State maintenanceTask = startMaintenance();
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FINISHED));
    }

    @DataProvider(name = "serviceStates")
    public Object[][] getServiceStates() {
      return new Object[][]{
          {com.vmware.photon.controller.api.model.ServiceState.READY},
          {com.vmware.photon.controller.api.model.ServiceState.CREATING},
          {com.vmware.photon.controller.api.model.ServiceState.RESIZING},
          {com.vmware.photon.controller.api.model.ServiceState.RECOVERABLE_ERROR},
      };
    }

    @Test
    public void testServiceStateFatalError() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetServiceVms(5, true, true);
      mockService(1, com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR);

      // If the service is in fatal_error state, maintainence should be cancelled
      ServiceMaintenanceTask.State maintenanceTask = startMaintenance();
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.CANCELLED));
    }

    @DataProvider(name = "fatalError")
    public Object[][] getServiceFatalStates() {
      return new Object[][]{
          {com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR}
      };
    }

    @Test
    public void testFailureWhenGarbageInspectionFails() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetServiceVms(5, true, false);
      mockService(5, com.vmware.photon.controller.api.model.ServiceState.RESIZING);

      ServiceMaintenanceTask.State maintenanceTask = startMaintenance(TaskState.TaskStage.FAILED);
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));

      // Wait until service state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      ServiceState.State serviceState = cloudStoreMachine.getServiceState(
          ServiceStateFactory.SELF_LINK + "/" + serviceId,
          ServiceState.State.class);
      assertThat(serviceState.serviceState, is(com.vmware.photon.controller.api.model.ServiceState.RECOVERABLE_ERROR));
    }

    @Test
    public void testFailureWhenExpandingServiceFails() throws Throwable {
      mockVmProvision(false);
      mockVmDelete(true);
      mockGetServiceVms(5, true, true);
      mockService(3, com.vmware.photon.controller.api.model.ServiceState.RESIZING);

      ServiceMaintenanceTask.State maintenanceTask = startMaintenance(TaskState.TaskStage.FAILED);
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));

      // Wait until service state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      ServiceState.State serviceState = cloudStoreMachine.getServiceState(
          ServiceStateFactory.SELF_LINK + "/" + serviceId,
          ServiceState.State.class);
      assertThat(serviceState.serviceState, is(com.vmware.photon.controller.api.model.ServiceState.RECOVERABLE_ERROR));
    }

    @Test
    public void testSuccessForDeletingService() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetServiceVms(5, true, true);
      mockService(5, com.vmware.photon.controller.api.model.ServiceState.PENDING_DELETE);

      ServiceMaintenanceTask.State maintenanceTask = startMaintenance();
      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FINISHED));

      assertThat(queryServiceLinks().size(), is(0));
    }

    @Test(dataProvider = "otherServiceExistenceForTestingDeletedService")
    public void testSuccessForDeletedService(boolean otherServiceExist) throws Throwable {
      if (otherServiceExist) {
        mockService(5, com.vmware.photon.controller.api.model.ServiceState.READY);
      }

      // Create a maintenance task for this service.
      ServiceMaintenanceTask.State taskState = buildValidState(TaskState.TaskStage.CREATED);
      String serviceId = UUID.randomUUID().toString();
      taskState.documentSelfLink = serviceId;

      // Start the maintenance task
      taskState = machine.callServiceSynchronously(
          ServiceMaintenanceTaskFactory.SELF_LINK, taskState, ServiceMaintenanceTask.State.class);

      // Patch the task to Started state.
      ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = TaskState.TaskStage.STARTED;
      Operation patchOp = machine.sendPatchAndWait(taskState.documentSelfLink, patchState);
      assertThat(patchOp.getStatusCode(), is(200));

      // Wait till the task gets deleted
      Thread.sleep(90000);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ServiceMaintenanceTask.State.class));
      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(ServiceMaintenanceTask.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(ServiceMaintenanceTaskFactory.SELF_LINK + "/" + serviceId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(idClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = machine.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(0));
    }

    @DataProvider(name = "otherServiceExistenceForTestingDeletedService")
    public Object[][] getOtherServiceExistenceForTestingDeletedService() {
      return new Object[][]{
          {true},
          {false}
      };
    }

    @Test
    public void testFailureWhenServiceDeletionFails() throws Throwable {
      mockVmProvision(true);
      mockVmDelete(true);
      mockGetServiceVms(5, true, false);
      mockService(5, com.vmware.photon.controller.api.model.ServiceState.PENDING_DELETE);
      ServiceMaintenanceTask.State maintenanceTask = startMaintenance();

      // Wait until service state is updated - it is an async update in the maintenance task
      Thread.sleep(1000);

      assertThat(maintenanceTask.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private Set<String> queryServiceLinks() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ServiceState.State.class));

      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(ServiceState.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(ServiceStateFactory.SELF_LINK + "/" + serviceId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(idClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      return QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
    }

    private ServiceMaintenanceTask.State startMaintenance() throws Throwable {
      return startMaintenance(null);
    }

    private ServiceMaintenanceTask.State startMaintenance(TaskState.TaskStage finalStage) throws Throwable {

      // Create a maintenance task for this service.
      ServiceMaintenanceTask.State taskState = buildValidState(TaskState.TaskStage.CREATED);
      taskState.documentSelfLink = serviceId;

      // Start the maintenance task
      taskState = machine.callServiceSynchronously(
          ServiceMaintenanceTaskFactory.SELF_LINK, taskState, ServiceMaintenanceTask.State.class);

      // Patch the task to Started state.
      ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = TaskState.TaskStage.STARTED;
      Operation patchOp = machine.sendPatchAndWait(taskState.documentSelfLink, patchState);
      assertThat(patchOp.getStatusCode(), is(200));

      return machine.waitForServiceState(
          ServiceMaintenanceTask.State.class,
          taskState.documentSelfLink,
          (ServiceMaintenanceTask.State state) -> {
            if (finalStage == null) {
              return TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal();
            } else {
              return state.taskState.stage == finalStage;
            }
          });
    }

    private void mockService(int workerCount, com.vmware.photon.controller.api.model.ServiceState serviceState)
        throws Throwable {
      ServiceState.State service = ReflectionUtils.buildValidStartState(ServiceState.State.class);
      service.workerCount = workerCount;
      service.serviceState = serviceState;
      service.serviceType = ServiceType.KUBERNETES;
      service.extendedProperties = new HashMap<>();
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "10.2.0.0/16");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "10.0.0.1");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS,
          "10.0.0.2,10.0.0.3,10.0.0.4");
      service.documentSelfLink = serviceId;
      cloudStoreMachine.callServiceAndWaitForState(
          ServiceStateFactory.SELF_LINK,
          service,
          ServiceState.State.class,
          (serviceDocument) -> true);
    }

    private void mockGetServiceVms(int nodeCount, boolean hasInactiveVm, boolean isSuccess) throws Throwable {
      if (isSuccess) {
        final List<Vm> vmList = new ArrayList<>();
        final Set<String> vmNames = new HashSet<>();
        final Set<String> vmMasterTags = ImmutableSet.of(ServicesUtil.createServiceNodeTag(
            serviceId, NodeType.KubernetesMaster));
        final Set<String> vmWorkerTags = ImmutableSet.of(ServicesUtil.createServiceNodeTag(
            serviceId, NodeType.KubernetesWorker));

        for (int i = 0; i < nodeCount; ++i) {
          String vmId = new UUID(0, i).toString();
          Vm vm = new Vm();
          vm.setId(vmId);
          vm.setName(vmId);
          vm.setTags(i == 0 ? vmMasterTags : vmWorkerTags);
          vmList.add(vm);
          if (!hasInactiveVm || i % 2 == 0) {
            // make roughly half of the vms "inactive" and not return them in
            // kubernetesClient.getAvailableNodeNamesAsync
            vmNames.add(vmId);
          }
        }

        doAnswer(invocation -> {
          ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(new ResourceList<>(vmList));
          return null;
        }).when(serviceApi).getVmsInServiceAsync(any(String.class), any(FutureCallback.class));
        doAnswer(invocation -> {
          ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(vmNames);
          return null;
        }).when(kubernetesClient).getAvailableNodeNamesAsync(
            any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new IOException("get service vms failed"))
            .when(serviceApi).getVmsInServiceAsync(any(String.class), any(FutureCallback.class));
        doThrow(new IOException("get node names failed")).when(kubernetesClient).getAvailableNodeNamesAsync(
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
      networkConnection.setMacAddress("00:0c:29:9e:6b:00");

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
        doThrow(new RuntimeException("wait service vm failed")).when(kubernetesClient).getNodeAddressesAsync(
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
        doThrow(new RuntimeException("stop service vm failed"))
            .when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));

        doThrow(new RuntimeException("delete service vm failed"))
            .when(vmApi).deleteAsync(anyString(), any(FutureCallback.class));
      }
    }
  }
}
