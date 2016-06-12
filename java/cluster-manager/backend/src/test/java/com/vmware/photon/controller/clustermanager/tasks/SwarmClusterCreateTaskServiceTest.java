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
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.clients.EtcdClient;
import com.vmware.photon.controller.clustermanager.clients.SwarmClient;
import com.vmware.photon.controller.clustermanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.clustermanager.helpers.TestEnvironment;
import com.vmware.photon.controller.clustermanager.helpers.TestHelper;
import com.vmware.photon.controller.clustermanager.helpers.TestHost;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.clustermanager.statuschecks.SwarmStatusChecker;
import com.vmware.photon.controller.clustermanager.templates.EtcdNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.templates.SwarmMasterNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.SwarmSlaveNodeTemplate;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
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
 * This class implements tests for the {@link SwarmClusterCreateTaskService} class.
 */
public class SwarmClusterCreateTaskServiceTest {

  private TestHost host;
  private SwarmClusterCreateTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private SwarmClusterCreateTask buildValidStartState(
      TaskState.TaskStage stage, SwarmClusterCreateTask.TaskState.SubStage subStage) throws Throwable {

    SwarmClusterCreateTask state = ReflectionUtils.buildValidStartState(SwarmClusterCreateTask.class);
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private SwarmClusterCreateTask buildValidPatchState(
      TaskState.TaskStage stage, SwarmClusterCreateTask.TaskState.SubStage subStage) {

    SwarmClusterCreateTask patchState = new SwarmClusterCreateTask();
    patchState.taskState = new SwarmClusterCreateTask.TaskState();
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
      taskService = new SwarmClusterCreateTaskService();
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
      taskService = new SwarmClusterCreateTaskService();
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
                                    SwarmClusterCreateTask.TaskState.SubStage subStage) throws Throwable {

      SwarmClusterCreateTask startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      SwarmClusterCreateTask savedState = host.getServiceState(
          SwarmClusterCreateTask.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.STARTED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.STARTED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      SwarmClusterCreateTask.TaskState.SubStage subStage) throws Throwable {

      SwarmClusterCreateTask startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CREATED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CREATED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},

          {TaskState.TaskStage.FINISHED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FINISHED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FINISHED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},

          {TaskState.TaskStage.FAILED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FAILED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FAILED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},

          {TaskState.TaskStage.CANCELLED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CANCELLED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CANCELLED,
              SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      SwarmClusterCreateTask startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          SwarmClusterCreateTask.class, NotNull.class);
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
      taskService = new SwarmClusterCreateTaskService();
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
                                      SwarmClusterCreateTask.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      SwarmClusterCreateTask.TaskState.SubStage patchSubStage) throws Throwable {

      SwarmClusterCreateTask startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      SwarmClusterCreateTask patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      SwarmClusterCreateTask savedState = host.getServiceState(SwarmClusterCreateTask.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           SwarmClusterCreateTask.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           SwarmClusterCreateTask.TaskState.SubStage patchSubStage)
        throws Throwable {

      SwarmClusterCreateTask startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      SwarmClusterCreateTask patchState = buildValidPatchState(patchStage, patchSubStage);
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

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},

          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_MASTER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, SwarmClusterCreateTask.TaskState.SubStage.SETUP_SLAVES},
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
      SwarmClusterCreateTask startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      SwarmClusterCreateTask patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          SwarmClusterCreateTask.TaskState.SubStage.SETUP_ETCD);

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
              SwarmClusterCreateTask.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the Swarm cluster create task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/clusters/scripts");
    private final File scriptLogDirectory = new File("/tmp/clusters/logs");
    private final File storageDirectory = new File("/tmp/clusters");

    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ImagesApi imagesApi;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private Task taskReturnedByCreateVm;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByStartVm;
    private Task taskReturnedByGetVmNetwork;
    private EtcdClient etcdClient;
    private SwarmClient swarmClient;
    private Set<String> swarmNodeIps;
    private String leaderIp = "leaderIp";

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private SwarmClusterCreateTask startState;

    @BeforeClass
    public void setUpClass() throws Throwable {

      FileUtils.deleteDirectory(storageDirectory);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      etcdClient = mock(EtcdClient.class);
      swarmClient = mock(SwarmClient.class);

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
          .etcdClient(etcdClient)
          .swarmClient(swarmClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .statusCheckHelper(new StatusCheckHelper())
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path etcdUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), EtcdNodeTemplate.ETCD_USER_DATA_TEMPLATE);
      Path masterUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), SwarmMasterNodeTemplate.MASTER_USER_DATA_TEMPLATE);
      Path slaveUserDataTemplate =
          Paths.get(scriptDirectory.getAbsolutePath(), SwarmSlaveNodeTemplate.SLAVE_USER_DATA_TEMPLATE);
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), NodeTemplateUtils.META_DATA_TEMPLATE);

      Files.createFile(etcdUserDataTemplate);
      Files.createFile(masterUserDataTemplate);
      Files.createFile(slaveUserDataTemplate);
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

      swarmNodeIps = new HashSet<>();
      swarmNodeIps.add(leaderIp);
      swarmNodeIps.add("127.0.0.1");

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

    @Test(dataProvider = "clusterSizes")
    public void testEndToEndSuccess(int clusterSize, ClusterState expectedState) throws Throwable {

      createClusterEntity(clusterSize);
      mockVmProvisioningTaskService(true);
      mockSwarmClient();

      SwarmClusterCreateTask savedState = machine.callServiceAndWaitForState(
          SwarmClusterCreateTaskFactoryService.SELF_LINK,
          startState,
          SwarmClusterCreateTask.class,
          (SwarmClusterCreateTask state) ->
              TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(savedState.taskState);
      ClusterService.State cluster = cloudStoreMachine.getServiceState(
          ClusterServiceFactory.SELF_LINK + "/" + savedState.clusterId,
          ClusterService.State.class);

      assertThat(cluster.clusterState, is(expectedState));
    }

    @DataProvider(name = "clusterSizes")
    public Object[][] getClusterSizes() {
      return new Object[][]{
          {1, ClusterState.READY},
          {3, ClusterState.CREATING},
      };
    }

    @Test
    public void testEndToEndFailureProvisionVmFails() throws Throwable {

      createClusterEntity(1);
      mockVmProvisioningTaskService(false);
      mockSwarmClient();

      SwarmClusterCreateTask savedState = machine.callServiceAndWaitForState(
          SwarmClusterCreateTaskFactoryService.SELF_LINK,
          startState,
          SwarmClusterCreateTask.class,
          (SwarmClusterCreateTask state) ->
              TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message,
          Matchers.containsString("Failed to rollout SwarmEtcd"));
    }

    @Test
    public void testEndToEndFailureWaitClusterVmFails() throws Throwable {
      SwarmStatusChecker statusChecker = mock(SwarmStatusChecker.class);
      doThrow(new RuntimeException("SetupEtcds did not finish")).when(statusChecker).checkNodeStatus(
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
          .etcdClient(etcdClient)
          .swarmClient(swarmClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .statusCheckHelper(statusCheckHelper)
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      createClusterEntity(1);
      mockVmProvisioningTaskService(true);

      SwarmClusterCreateTask savedState = machine.callServiceAndWaitForState(
          SwarmClusterCreateTaskFactoryService.SELF_LINK,
          startState,
          SwarmClusterCreateTask.class,
          (SwarmClusterCreateTask state) ->
              TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message,
          Matchers.containsString("Failed to rollout SwarmEtcd"));
    }

    private void createClusterEntity(int size) throws Throwable {

      ClusterService.State cluster = new ClusterService.State();
      cluster.clusterState = ClusterState.CREATING;
      cluster.clusterName = "swarmCluster";
      cluster.clusterType = ClusterType.SWARM;
      cluster.imageId = "imageId";
      cluster.projectId = "porjectId";
      cluster.diskFlavorName = "diskFlavorName";
      cluster.masterVmFlavorName = "masterVmFlavorName";
      cluster.otherVmFlavorName = "otherVmFlavorName";
      cluster.vmNetworkId = "vmNetworkId";
      cluster.slaveCount = size;
      cluster.extendedProperties = new HashMap<>();
      cluster.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_DNS, "2.2.2.2");
      cluster.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, "3.3.3.3");
      cluster.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, "4.4.4.4");
      cluster.extendedProperties.put(
          ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS, "10.0.0.1,10.0.0.2,10.0.0.3,");
      cluster.documentSelfLink = UUID.randomUUID().toString();

      cloudStoreMachine.callServiceAndWaitForState(
          ClusterServiceFactory.SELF_LINK,
          cluster,
          ClusterService.State.class,
          state -> true);

      startState.clusterId = cluster.documentSelfLink;
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
      networkConnection.setIpAddress("127.0.0.1");

      VmNetworks vmNetworks = new VmNetworks();
      vmNetworks.setNetworkConnections(Collections.singleton(networkConnection));

      taskReturnedByGetVmNetwork.setResourceProperties(vmNetworks);
    }

    private void mockSwarmClient() throws Throwable {
      doAnswer(invocation -> {
        ((FutureCallback<Boolean>) invocation.getArguments()[1]).onSuccess(true);
        return null;
      }).when(etcdClient).checkStatus(
          any(String.class), any(FutureCallback.class));

      doAnswer(invocation -> {
        ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(swarmNodeIps);
        return null;
      }).when(swarmClient).getNodeAddressesAsync(
          any(String.class), any(FutureCallback.class));
    }
  }
}
