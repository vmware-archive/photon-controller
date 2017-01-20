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
import com.vmware.photon.controller.api.client.resource.ImagesApi;
import com.vmware.photon.controller.api.client.resource.ProjectApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.model.NetworkConnection;
import com.vmware.photon.controller.api.model.ServiceType;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.VmNetworks;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.servicesmanager.clients.EtcdClient;
import com.vmware.photon.controller.servicesmanager.clients.KubernetesClient;
import com.vmware.photon.controller.servicesmanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.servicesmanager.helpers.TestEnvironment;
import com.vmware.photon.controller.servicesmanager.helpers.TestHelper;
import com.vmware.photon.controller.servicesmanager.helpers.TestHost;
import com.vmware.photon.controller.servicesmanager.servicedocuments.KubernetesServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.statuschecks.KubernetesStatusChecker;
import com.vmware.photon.controller.servicesmanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesEtcdNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesMasterNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
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
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link KubernetesServiceCreateTask} class.
 */
public class KubernetesServiceCreateTaskTest {

  private TestHost host;
  private KubernetesServiceCreateTask taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private KubernetesServiceCreateTaskState buildValidStartState(
      TaskState.TaskStage stage, KubernetesServiceCreateTaskState.TaskState.SubStage subStage) throws Throwable {

    KubernetesServiceCreateTaskState state =
        ReflectionUtils.buildValidStartState(KubernetesServiceCreateTaskState.class);
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private KubernetesServiceCreateTaskState buildValidPatchState(
      TaskState.TaskStage stage, KubernetesServiceCreateTaskState.TaskState.SubStage subStage) {

    KubernetesServiceCreateTaskState patchState = new KubernetesServiceCreateTaskState();
    patchState.taskState = new KubernetesServiceCreateTaskState.TaskState();
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
      taskService = new KubernetesServiceCreateTask();
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
      taskService = new KubernetesServiceCreateTask();
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
                                    KubernetesServiceCreateTaskState.TaskState.SubStage subStage) throws Throwable {

      KubernetesServiceCreateTaskState startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      KubernetesServiceCreateTaskState savedState = host.getServiceState(
          KubernetesServiceCreateTaskState.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.STARTED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.STARTED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      KubernetesServiceCreateTaskState.TaskState.SubStage subStage) throws Throwable {

      KubernetesServiceCreateTaskState startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CREATED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CREATED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},

          {TaskState.TaskStage.STARTED, null},

          {TaskState.TaskStage.FINISHED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FINISHED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FINISHED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},

          {TaskState.TaskStage.FAILED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FAILED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FAILED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},

          {TaskState.TaskStage.CANCELLED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CANCELLED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CANCELLED,
              KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      KubernetesServiceCreateTaskState startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          KubernetesServiceCreateTaskState.class, NotNull.class);
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
      taskService = new KubernetesServiceCreateTask();
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
                                      KubernetesServiceCreateTaskState.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      KubernetesServiceCreateTaskState.TaskState.SubStage patchSubStage)
        throws Throwable {

      KubernetesServiceCreateTaskState startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      KubernetesServiceCreateTaskState patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      KubernetesServiceCreateTaskState savedState = host.getServiceState(KubernetesServiceCreateTaskState.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           KubernetesServiceCreateTaskState.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           KubernetesServiceCreateTaskState.TaskState.SubStage patchSubStage)
        throws Throwable {

      KubernetesServiceCreateTaskState startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      KubernetesServiceCreateTaskState patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidSubStageUpdates")
    public Object[][] getInvalidSubStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},

          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_WORKERS},
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
      KubernetesServiceCreateTaskState startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      KubernetesServiceCreateTaskState patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          KubernetesServiceCreateTaskState.TaskState.SubStage.SETUP_ETCD);

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
          ReflectionUtils.getAttributeNamesWithAnnotation(
              KubernetesServiceCreateTaskState.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the Kubernetes service create task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/services/scripts");
    private final File scriptLogDirectory = new File("/tmp/services/logs");
    private final File storageDirectory = new File("/tmp/services");

    private TestEnvironment machine;
    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ImagesApi imagesApi;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private Task taskReturnedByCreateVm;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByStartVm;
    private Task taskReturnedByGetVmNetwork;
    private KubernetesClient kubernetesClient;
    private EtcdClient etcdClient;
    private Set<String> kubernetesNodeIps;

    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private KubernetesServiceCreateTaskState startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      kubernetesClient = mock(KubernetesClient.class);
      etcdClient = mock(EtcdClient.class);

      apiClient = mock(ApiClient.class);
      imagesApi = mock(ImagesApi.class);
      projectApi = mock(ProjectApi.class);
      vmApi = mock(VmApi.class);
      doReturn(imagesApi).when(apiClient).getImagesApi();
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(vmApi).when(apiClient).getVmApi();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .kubernetesClient(kubernetesClient)
          .etcdClient(etcdClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .statusCheckHelper(new StatusCheckHelper())
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path etcdUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesEtcdNodeTemplate.ETCD_USER_DATA_TEMPLATE);
      Path masterUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesMasterNodeTemplate.MASTER_USER_DATA_TEMPLATE);
      Path workerUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), KubernetesWorkerNodeTemplate.WORKER_USER_DATA_TEMPLATE);
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), NodeTemplateUtils.META_DATA_TEMPLATE);

      Files.createFile(etcdUserDataTemplate);
      Files.createFile(masterUserDataTemplate);
      Files.createFile(workerUserDataTemplate);
      Files.createFile(metaDataTemplate);

      taskReturnedByCreateVm = new Task();
      taskReturnedByCreateVm.setId("createVmTaskId");
      taskReturnedByCreateVm.setState("COMPLETED");

      taskReturnedByAttachIso = new Task();
      taskReturnedByAttachIso.setId("attachIsoTaskId");
      taskReturnedByAttachIso.setState("COMPLETED");

      taskReturnedByStartVm = new Task();
      taskReturnedByStartVm.setId("startVmTaskId");
      taskReturnedByStartVm.setState("COMPLETED");

      taskReturnedByGetVmNetwork = new Task();
      taskReturnedByGetVmNetwork.setId("getVmNetworkTaskId");
      taskReturnedByGetVmNetwork.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByCreateVm.setEntity(entity);
      taskReturnedByAttachIso.setEntity(entity);
      taskReturnedByStartVm.setEntity(entity);
      taskReturnedByGetVmNetwork.setEntity(entity);

      kubernetesNodeIps = new HashSet<>();
      kubernetesNodeIps.add("100.0.0.1");

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
      taskReturnedByCreateVm = null;
      taskReturnedByAttachIso = null;

      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test(dataProvider = "serviceSizes")
    public void testEndToEndSuccess(int serviceSize, com.vmware.photon.controller.api.model.ServiceState expectedState)
        throws Throwable {

      createServiceEntity(serviceSize);
      mockVmProvisioningTaskService(true);
      mockKubernetesClient();

      KubernetesServiceCreateTaskState savedState = machine.callServiceAndWaitForState(
          KubernetesServiceCreateTaskFactory.SELF_LINK,
          startState,
          KubernetesServiceCreateTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      TestHelper.assertTaskStateFinished(savedState.taskState);
      ServiceState.State service = cloudStoreMachine.getServiceState(
          ServiceStateFactory.SELF_LINK + "/" + savedState.serviceId,
          ServiceState.State.class);

      assertThat(service.serviceState, is(expectedState));
      assertThat(service.extendedProperties.size(), is(14));
      assertThat(service.extendedProperties.get("service_version"), is("v1.3.5"));
    }

    @DataProvider(name = "serviceSizes")
    public Object[][] getServiceSizes() {
      return new Object[][]{
          {1, com.vmware.photon.controller.api.model.ServiceState.READY},
          {3, com.vmware.photon.controller.api.model.ServiceState.CREATING},
      };
    }

    @Test
    public void testEndToEndFailureProvisionVmFails() throws Throwable {

      createServiceEntity(1);
      mockVmProvisioningTaskService(false);
      mockKubernetesClient();

      KubernetesServiceCreateTaskState savedState = machine.callServiceAndWaitForState(
          KubernetesServiceCreateTaskFactory.SELF_LINK,
          startState,
          KubernetesServiceCreateTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, Matchers.containsString("vm provisioning failed"));
    }

    @Test
    public void testEndToEndFailUpdateExtendedPropertyFails() throws Throwable {
      createServiceEntity(1);
      mockVmProvisioningTaskService(true);

      doAnswer(invocation -> {
        ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
        return null;
      }).when(etcdClient).checkStatus(
          any(String.class), any(FutureCallback.class));

      doAnswer(invocation -> {
        ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(kubernetesNodeIps);
        return null;
      }).when(kubernetesClient).getNodeAddressesAsync(
          any(String.class), any(FutureCallback.class));

      // throws a mock IOException. Because the bug we usually see in getVersionAsync is a Connection reset
      // by peers which is an IO exception
      doAnswer(invocation -> {
        ((FutureCallback<String>) invocation.getArguments()[1]).onFailure(new IOException("mock Exception"));
        return null;
      }).when(kubernetesClient).getVersionAsync(
          any(String.class), any(FutureCallback.class));

      startState.versionPollDelay = 1;
      KubernetesServiceCreateTaskState savedState = machine.callServiceAndWaitForState(
          KubernetesServiceCreateTaskFactory.SELF_LINK,
          startState,
          KubernetesServiceCreateTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      assertThat(savedState.versionPollIterations, is(savedState.versionMaxPollIterations));
      // verify that failure to update extended properties fails the task.
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));

    }

    @Test
    public void testEndToEndFailureWaitServiceVmFails() throws Throwable {

      KubernetesStatusChecker statusChecker = mock(KubernetesStatusChecker.class);
      doThrow(new RuntimeException("wait service vm failed")).when(statusChecker).checkNodeStatus(
          anyString(), any(FutureCallback.class));

      StatusCheckHelper statusCheckHelper = mock(StatusCheckHelper.class);
      when(statusCheckHelper.createStatusChecker(any(Service.class), any(NodeType.class)))
          .thenReturn(statusChecker);

      if (machine != null) {
        machine.stop();
        machine = null;
      }

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .kubernetesClient(kubernetesClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .statusCheckHelper(statusCheckHelper)
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      createServiceEntity(1);
      mockVmProvisioningTaskService(true);

      KubernetesServiceCreateTaskState savedState = machine.callServiceAndWaitForState(
          KubernetesServiceCreateTaskFactory.SELF_LINK,
          startState,
          KubernetesServiceCreateTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, Matchers.containsString("wait service vm failed"));
    }

    private void createServiceEntity(int size) throws Throwable {

      ServiceState.State service = new ServiceState.State();
      service.serviceState = com.vmware.photon.controller.api.model.ServiceState.CREATING;
      service.serviceName = "kubernetesService";
      service.serviceType = ServiceType.KUBERNETES;
      service.imageId = "imageId";
      service.projectId = "porjectId";
      service.diskFlavorName = "diskFlavorName";
      service.masterVmFlavorName = "masterVmFlavorName";
      service.otherVmFlavorName = "otherVmFlavorName";
      service.vmNetworkId = "vmNetworkId";
      service.workerCount = size;
      service.extendedProperties = new HashMap<>();
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK, "1.1.1.1");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_DNS, "2.2.2.2");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY, "3.3.3.3");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK, "4.4.4.4");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS,
          "10.0.0.1,10.0.0.2,10.0.0.3,");
      service.extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP, "100.0.0.1");
      service.extendedProperties.put(
          ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE, "example-ca-cert");
      service.documentSelfLink = UUID.randomUUID().toString();

      cloudStoreMachine.callServiceAndWaitForState(
          ServiceStateFactory.SELF_LINK,
          service,
          ServiceState.State.class,
          state -> true);

      startState.serviceId = service.documentSelfLink;
    }

    private void mockVmProvisioningTaskService(boolean isSuccess) throws Throwable {
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
      networkConnection.setIpAddress("100.0.0.1");
      networkConnection.setMacAddress("00:0c:29:9e:6b:00");

      VmNetworks vmNetworks = new VmNetworks();
      vmNetworks.setNetworkConnections(Collections.singleton(networkConnection));

      taskReturnedByGetVmNetwork.setResourceProperties(vmNetworks);
    }

    private void mockKubernetesClient() throws Throwable {
      doAnswer(invocation -> {
        ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
        return null;
      }).when(etcdClient).checkStatus(
          any(String.class), any(FutureCallback.class));

      doAnswer(invocation -> {
        ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(kubernetesNodeIps);
        return null;
      }).when(kubernetesClient).getNodeAddressesAsync(
          any(String.class), any(FutureCallback.class));

      doAnswer(invocation -> {
        ((FutureCallback<String>) invocation.getArguments()[1]).onSuccess("v1.3.5");
        return null;
      }).when(kubernetesClient).getVersionAsync(
          any(String.class), any(FutureCallback.class));
    }
  }
}
