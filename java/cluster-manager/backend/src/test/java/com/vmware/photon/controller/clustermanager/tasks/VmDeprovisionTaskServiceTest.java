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

import com.vmware.photon.controller.api.ApiError;
import com.vmware.photon.controller.api.Step;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
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
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import org.hamcrest.Matchers;
import org.mockito.internal.matchers.NotNull;
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link VmDeprovisionTaskService} class.
 */
public class VmDeprovisionTaskServiceTest {

  private TestHost host;
  private VmDeprovisionTaskService taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private VmDeprovisionTaskService.State buildValidStartState(
      TaskState.TaskStage stage, VmDeprovisionTaskService.State.TaskState.SubStage subStage) throws Throwable {

    VmDeprovisionTaskService.State startState = ReflectionUtils.buildValidStartState(
        VmDeprovisionTaskService.State.class);
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return startState;
  }

  private VmDeprovisionTaskService.State buildValidPatchState(
      TaskState.TaskStage stage, VmDeprovisionTaskService.State.TaskState.SubStage subStage) {

    VmDeprovisionTaskService.State patchState = new VmDeprovisionTaskService.State();
    patchState.taskState = new VmDeprovisionTaskService.State.TaskState();
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
      taskService = new VmDeprovisionTaskService();
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
      taskService = new VmDeprovisionTaskService();
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
                                    VmDeprovisionTaskService.State.TaskState.SubStage subStage)
        throws Throwable {

      VmDeprovisionTaskService.State startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      VmDeprovisionTaskService.State savedState = host.getServiceState(VmDeprovisionTaskService.State.class);

      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.taskState.subStage, is(subStage));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.STARTED,
              VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.STARTED,
              VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      VmDeprovisionTaskService.State.TaskState.SubStage subStage) throws Throwable {

      VmDeprovisionTaskService.State startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.CREATED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},

          {TaskState.TaskStage.STARTED, null},

          {TaskState.TaskStage.FINISHED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.FINISHED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},

          {TaskState.TaskStage.FAILED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.FAILED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},

          {TaskState.TaskStage.CANCELLED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.CANCELLED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      VmDeprovisionTaskService.State startState = ReflectionUtils.buildValidStartState(
          VmDeprovisionTaskService.State.class);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          VmDeprovisionTaskService.State.class, NotNull.class);
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
      taskService = new VmDeprovisionTaskService();
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
                                      VmDeprovisionTaskService.State.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      VmDeprovisionTaskService.State.TaskState.SubStage patchSubStage)
        throws Throwable {

      VmDeprovisionTaskService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      VmDeprovisionTaskService.State patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      VmDeprovisionTaskService.State savedState = host.getServiceState(VmDeprovisionTaskService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "invalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage,
                                        VmDeprovisionTaskService.State.TaskState.SubStage startSubStage,
                                        TaskState.TaskStage patchStage,
                                        VmDeprovisionTaskService.State.TaskState.SubStage patchSubStage)
        throws Throwable {

      VmDeprovisionTaskService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      VmDeprovisionTaskService.State patchState = buildValidPatchState(patchStage, patchSubStage);
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

          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, VmDeprovisionTaskService.State.TaskState.SubStage.DELETE_VM},
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
      VmDeprovisionTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      VmDeprovisionTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          VmDeprovisionTaskService.State.TaskState.SubStage.STOP_VM);

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
              VmDeprovisionTaskService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the VmProvisioningTaskService Xenon task.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private ApiClient apiClient;
    private VmApi vmApi;
    private TasksApi tasksApi;
    private Task taskReturnedByStopVm;
    private Task taskReturnedByDeleteVm;
    private VmDeprovisionTaskService.State startState;

    @BeforeMethod
    public void setUpClass() throws Throwable {

      apiClient = mock(ApiClient.class);
      vmApi = mock(VmApi.class);
      tasksApi = mock(TasksApi.class);
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      startState.controlFlags = null;

      taskReturnedByStopVm = new Task();
      taskReturnedByStopVm.setId("stopVmTaskId");
      taskReturnedByStopVm.setState("COMPLETED");

      taskReturnedByDeleteVm = new Task();
      taskReturnedByDeleteVm.setId("deleteVmTaskId");
      taskReturnedByDeleteVm.setState("COMPLETED");
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      mockStopVm(true);
      mockDeleteVm(true);

      VmDeprovisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmDeprovisionTaskFactoryService.SELF_LINK,
              startState,
              VmDeprovisionTaskService.State.class,
              task -> TaskUtils.finalTaskStages.contains(task.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }

    @Test(dataProvider = "tasksFailedVmNotFound")
    public void testEndToEndSuccessIgnoreVmNotFound(Task taskFailedVmNotFound) throws Throwable {

      taskFailedVmNotFound.setState("ERROR");
      ApiError apiError = new ApiError();
      apiError.setCode("VmNotFound");
      apiError.setMessage("errorMessage");
      Step failedStep = new Step();
      failedStep.setState("ERROR");
      failedStep.setOperation("stepOperation");
      failedStep.addError(apiError);
      taskFailedVmNotFound.setSteps(Arrays.asList(failedStep));
      mockStopVm(true);
      mockDeleteVm(true);

      VmDeprovisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmDeprovisionTaskFactoryService.SELF_LINK,
              startState,
              VmDeprovisionTaskService.State.class,
              task -> TaskUtils.finalTaskStages.contains(task.taskState.stage));

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }

    @DataProvider(name = "tasksFailedVmNotFound")
    public Object[][] getTasksFailedVmNotFound() {
      return new Object[][]{
          {taskReturnedByStopVm},
          {taskReturnedByDeleteVm}
      };
    }

    @Test
    public void testEndToEndFailureStopVmFails() throws Throwable {

      mockStopVm(false);
      mockDeleteVm(true);

      VmDeprovisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmDeprovisionTaskFactoryService.SELF_LINK,
              startState,
              VmDeprovisionTaskService.State.class,
              task -> TaskUtils.finalTaskStages.contains(task.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, Matchers.containsString("stop vm failed"));
    }

    @Test
    public void testEndToEndFailureDeleteVmFails() throws Throwable {

      mockStopVm(true);
      mockDeleteVm(false);

      VmDeprovisionTaskService.State serviceState =
          machine.callServiceAndWaitForState(
              VmDeprovisionTaskFactoryService.SELF_LINK,
              startState,
              VmDeprovisionTaskService.State.class,
              task -> TaskUtils.finalTaskStages.contains(task.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, Matchers.containsString("delete vm failed"));
    }

    private void mockDeleteVm(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
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
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
          return null;
        }).when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("stop vm failed"))
            .when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));
      }
    }
  }
}
