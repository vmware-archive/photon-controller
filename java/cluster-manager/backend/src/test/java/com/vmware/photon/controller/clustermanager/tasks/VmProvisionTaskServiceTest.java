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

import com.vmware.photon.controller.api.model.NetworkConnection;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.clustermanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.clustermanager.helpers.TestEnvironment;
import com.vmware.photon.controller.clustermanager.helpers.TestHelper;
import com.vmware.photon.controller.clustermanager.helpers.TestHost;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * This class implements tests for the {@link VmProvisionTaskService} class.
 */
public class VmProvisionTaskServiceTest {

  private TestHost host;
  private VmProvisionTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private VmProvisionTaskService.State buildValidStartState(
      TaskState.TaskStage stage, VmProvisionTaskService.State.TaskState.SubStage subStage) throws Throwable {

    VmProvisionTaskService.State startState = ReflectionUtils.buildValidStartState(
        VmProvisionTaskService.State.class);
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return startState;
  }

  private VmProvisionTaskService.State buildValidPatchState(
      TaskState.TaskStage stage, VmProvisionTaskService.State.TaskState.SubStage subStage) {

    VmProvisionTaskService.State patchState = new VmProvisionTaskService.State();
    patchState.taskState = new VmProvisionTaskService.State.TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;

    if (stage == TaskState.TaskStage.STARTED &&
        subStage.ordinal() > VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM.ordinal()) {
      patchState.vmId = "vmId";
    }

    return patchState;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new VmProvisionTaskService();
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
      taskService = new VmProvisionTaskService();
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
                                    VmProvisionTaskService.State.TaskState.SubStage subStage)
        throws Throwable {

      VmProvisionTaskService.State startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      VmProvisionTaskService.State savedState = host.getServiceState(VmProvisionTaskService.State.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.STARTED,
              VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.STARTED,
              VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.STARTED,
              VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      VmProvisionTaskService.State.TaskState.SubStage subStage) throws Throwable {

      VmProvisionTaskService.State startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.CREATED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.CREATED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.CREATED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},

          {TaskState.TaskStage.STARTED, null},

          {TaskState.TaskStage.FINISHED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FINISHED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.FINISHED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.FINISHED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},

          {TaskState.TaskStage.FAILED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FAILED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.FAILED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.FAILED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},

          {TaskState.TaskStage.CANCELLED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.CANCELLED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.CANCELLED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.CANCELLED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      VmProvisionTaskService.State startState = ReflectionUtils.buildValidStartState(
          VmProvisionTaskService.State.class);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          VmProvisionTaskService.State.class, NotNull.class);
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
      taskService = new VmProvisionTaskService();
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
                                      VmProvisionTaskService.State.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      VmProvisionTaskService.State.TaskState.SubStage patchSubStage) throws Throwable {

      VmProvisionTaskService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      VmProvisionTaskService.State patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      VmProvisionTaskService.State savedState = host.getServiceState(VmProvisionTaskService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage,
                                        VmProvisionTaskService.State.TaskState.SubStage startSubStage,
                                        TaskState.TaskStage patchStage,
                                        VmProvisionTaskService.State.TaskState.SubStage patchSubStage)
        throws Throwable {

      VmProvisionTaskService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      VmProvisionTaskService.State patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidStageUpdates")
    public Object[][] getInvalidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},

          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.ATTACH_ISO},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.START_VM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmProvisionTaskService.State.TaskState.SubStage.VERIFY_VM},
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
      VmProvisionTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      VmProvisionTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          VmProvisionTaskService.State.TaskState.SubStage.CREATE_VM);

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
              VmProvisionTaskService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the VmProvisioningTaskService Xenon task.
   */
  public class EndToEndTest {

    private final File scriptDirectory = new File("/tmp/clusters/scripts");
    private final File scriptLogDirectory = new File("/tmp/clusters/logs");
    private final File storageDirectory = new File("/tmp/clusters");

    private TestEnvironment machine;
    private ListeningExecutorService listeningExecutorService;
    private ApiClient apiClient;
    private ProjectApi projectApi;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private Task taskReturnedByCreateVm;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByStartVm;
    private Task taskReturnedByGetVmNetwork;
    private VmProvisionTaskService.State startState;

    @BeforeMethod
    public void setUpClass() throws Throwable {

      FileUtils.deleteDirectory(storageDirectory);

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      apiClient = mock(ApiClient.class);
      projectApi = mock(ProjectApi.class);
      vmApi = mock(VmApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();

      Path userDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), "user-data");
      Path metaDataTemplate = Paths.get(scriptDirectory.getAbsolutePath(), "meta-data");

      Files.createFile(userDataTemplate);
      Files.createFile(metaDataTemplate);

      startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      startState.controlFlags = null;
      startState.userData.filePath = userDataTemplate.toString();
      startState.metaData.filePath = metaDataTemplate.toString();
      String instanceId = UUID.randomUUID().toString();
      startState.userData.parameters = new HashMap<>();
      startState.userData.parameters.put("$DNS", "DNS=255.255.255.255");
      startState.metaData.parameters = new HashMap<>();
      startState.metaData.parameters.put("$INSTANCE_ID", instanceId);

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
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;

      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() {
      listeningExecutorService.shutdown();
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      machine = createTestEnvironment();

      mockCreateVm(true);
      mockAttachIso(true);
      mockStartVm(true);
      mockVerifyVm(true);

      VmProvisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmProvisionTaskFactoryService.SELF_LINK,
              startState,
              VmProvisionTaskService.State.class,
              new Predicate<VmProvisionTaskService.State>() {
                @Override
                public boolean test(VmProvisionTaskService.State vmProvisioningTask) {
                  return TaskUtils.finalTaskStages.contains(vmProvisioningTask.taskState.stage);
                }
              });

      TestHelper.assertTaskStateFinished(serviceState.taskState);
      assertThat(serviceState.vmId, is("vmId"));
      assertThat(serviceState.vmIpAddress, is("IP_ADDRESS"));
    }

    @Test
    public void testEndToEndFailureCreateVmFails() throws Throwable {

      machine = createTestEnvironment();

      mockCreateVm(false);
      mockAttachIso(true);
      mockStartVm(true);
      mockVerifyVm(true);

      VmProvisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmProvisionTaskFactoryService.SELF_LINK,
              startState,
              VmProvisionTaskService.State.class,
              new Predicate<VmProvisionTaskService.State>() {
                @Override
                public boolean test(VmProvisionTaskService.State vmProvisioningTask) {
                  return TaskUtils.finalTaskStages.contains(vmProvisioningTask.taskState.stage);
                }
              });

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, Matchers.containsString("create vm failed"));
    }

    @Test
    public void testEndToEndFailureAttachIsoVmFails() throws Throwable {

      machine = createTestEnvironment();

      mockCreateVm(true);
      mockAttachIso(false);
      mockStartVm(true);
      mockVerifyVm(true);

      VmProvisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmProvisionTaskFactoryService.SELF_LINK,
              startState,
              VmProvisionTaskService.State.class,
              new Predicate<VmProvisionTaskService.State>() {
                @Override
                public boolean test(VmProvisionTaskService.State vmProvisioningTask) {
                  return TaskUtils.finalTaskStages.contains(vmProvisioningTask.taskState.stage);
                }
              });

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailureStartVmFails() throws Throwable {

      machine = createTestEnvironment();

      mockCreateVm(true);
      mockAttachIso(true);
      mockStartVm(false);
      mockVerifyVm(true);

      VmProvisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmProvisionTaskFactoryService.SELF_LINK,
              startState,
              VmProvisionTaskService.State.class,
              new Predicate<VmProvisionTaskService.State>() {
                @Override
                public boolean test(VmProvisionTaskService.State vmProvisioningTask) {
                  return TaskUtils.finalTaskStages.contains(vmProvisioningTask.taskState.stage);
                }
              });

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, Matchers.containsString("start vm failed"));
    }

    @Test
    public void testEndToEndFailureVerifyVmFails() throws Throwable {

      machine = createTestEnvironment();

      mockCreateVm(true);
      mockAttachIso(true);
      mockStartVm(true);
      mockVerifyVm(false);

      VmProvisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmProvisionTaskFactoryService.SELF_LINK,
              startState,
              VmProvisionTaskService.State.class,
              new Predicate<VmProvisionTaskService.State>() {
                @Override
                public boolean test(VmProvisionTaskService.State vmProvisioningTask) {
                  return TaskUtils.finalTaskStages.contains(vmProvisioningTask.taskState.stage);
                }
              });

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, Matchers.containsString("verify vm failed"));
    }

    private void mockCreateVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
            return null;
          }
        }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("create vm failed"))
            .when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));
      }
    }

    private void mockAttachIso(boolean isSuccess) throws Throwable {
      String scriptFileName = "esx-create-vm-iso";
      if (isSuccess) {
        TestHelper.createSuccessScriptFile(scriptDirectory, scriptFileName);
      } else {
        TestHelper.createFailScriptFile(scriptDirectory, scriptFileName);
      }

      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
    }

    private void mockStartVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStartVm);
            return null;
          }
        }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("start vm failed"))
            .when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));
      }
    }

    private void mockVerifyVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetVmNetwork);
            return null;
          }
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

      taskReturnedByGetVmNetwork.setResourceProperties(vmNetworks);
    }

    private TestEnvironment createTestEnvironment() throws Throwable {
      return new TestEnvironment.Builder()
          .apiClient(apiClient)
          .listeningExecutorService(listeningExecutorService)
          .scriptsDirectory(scriptDirectory.getAbsolutePath())
          .hostCount(1)
          .build();
    }
  }
}
