/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalSwitchTask;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.CreateLogicalSwitchTaskService}.
 */
public class CreateLogicalSwitchTaskServiceTest {
  private static TestHost host;
  private static CreateLogicalSwitchTaskService createLogicalSwitchTaskService;

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the initialization of service itself.
   */
  public static class InitializationTest {
    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);

      CreateLogicalSwitchTaskService service = new CreateLogicalSwitchTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {

    @BeforeClass
    public void setupClass() throws Throwable {
      host = TestHost.create();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      createLogicalSwitchTaskService = new CreateLogicalSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testStateTransition(TaskState.TaskStage startStage,
                                    TaskState.TaskStage expectedStage) throws Throwable {

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(startStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      CreateLogicalSwitchTask savedState = host.getServiceState(CreateLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(dataProvider = "notEmptyFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      CreateLogicalSwitchTask startState = new CreateLogicalSwitchTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(createLogicalSwitchTaskService, startState);
        fail("should have failed due to violation of not empty restraint");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED}
      };
    }

    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][] {
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"},
          {"transportZoneId", "transportZoneId cannot be null"},
          {"displayName", "displayName cannot be null"},
          {"executionDelay", "executionDelay cannot be null"}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public static class HandlePatchTest {

    @BeforeClass
    public void setupClass() throws Throwable {
      host = TestHost.create();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      createLogicalSwitchTaskService = new CreateLogicalSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         TaskState.TaskStage patchStage) throws Throwable {

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(startStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      CreateLogicalSwitchTask patchState = buildPatchState(patchStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      CreateLogicalSwitchTask savedState = host.getServiceState(CreateLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @Test(dataProvider = "invalidStageTransitions")
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.TaskStage patchStage) throws Throwable {

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(startStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      CreateLogicalSwitchTask patchState = buildPatchState(patchStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to invalid stage transition");
      } catch (BadRequestException e) {
      }
    }

    @Test(dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName, String expectedErrorMessage) throws Throwable {

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(TaskState.TaskStage.CREATED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      CreateLogicalSwitchTask patchState = buildPatchState(TaskState.TaskStage.FINISHED);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to changing immutable fields");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @Test(dataProvider = "writeOnceFields")
    public void testChangeWriteOnceFields(String fieldName, String expectedErrorMessage) throws Throwable {
      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(TaskState.TaskStage.CREATED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      CreateLogicalSwitchTask patchState = buildPatchState(TaskState.TaskStage.FINISHED);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);
      host.sendRequestAndWait(patch);

      patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to changing a writeonce field multiple times");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
      };
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags", "controlFlags is immutable"},
          {"nsxManagerEndpoint", "nsxManagerEndpoint is immutable"},
          {"username", "username is immutable"},
          {"password", "password is immutable"},
          {"transportZoneId", "transportZoneId is immutable"},
          {"displayName", "displayName is immutable"},
          {"executionDelay", "executionDelay is immutable"}
      };
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"id", "id cannot be set or changed in a patch"}
      };
    }
  }

  /**
   * End-to-end tests.
   */
  public class EndToEndTest {

    @BeforeClass
    public void setupClass() throws Throwable {
      host = TestHost.create();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      createLogicalSwitchTaskService = spy(new CreateLogicalSwitchTaskService());
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test
    public void testFailedToCreateLogicalSwitchTask() throws Throwable {
      LogicalSwitchApi logicalSwitchApi = mock(LogicalSwitchApi.class);
      doReturn(logicalSwitchApi)
          .when(createLogicalSwitchTaskService)
          .getLogicalSwitchApi(any(CreateLogicalSwitchTask.class));

      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[1])
              .onFailure(new Exception("service does not exist"));
        }
        return null;
      }).when(logicalSwitchApi)
          .createLogicalSwitch(any(LogicalSwitchCreateSpec.class), any(FutureCallback.class));

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(TaskState.TaskStage.CREATED, 0);
      host.waitForState(createdState.documentSelfLink,
          CreateLogicalSwitchTask.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      CreateLogicalSwitchTask savedState = host.getServiceState(CreateLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testFailedToGetLogicalSwitchState() throws Throwable {
      LogicalSwitchApi logicalSwitchApi = mock(LogicalSwitchApi.class);
      doReturn(logicalSwitchApi)
          .when(createLogicalSwitchTaskService)
          .getLogicalSwitchApi(any(CreateLogicalSwitchTask.class));

      String logicalSwitchId = UUID.randomUUID().toString();
      LogicalSwitch logicalSwitch = new LogicalSwitch();
      logicalSwitch.setId(logicalSwitchId);

      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[1])
              .onSuccess(logicalSwitch);
        }
        return null;
      }).when(logicalSwitchApi)
          .createLogicalSwitch(any(LogicalSwitchCreateSpec.class), any(FutureCallback.class));

      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onFailure(new Exception("Service is not available"));
        }
        return null;
      }).when(logicalSwitchApi)
          .getLogicalSwitchState(eq(logicalSwitchId), any(FutureCallback.class));

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(TaskState.TaskStage.CREATED, 0);
      host.waitForState(createdState.documentSelfLink,
          CreateLogicalSwitchTask.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      CreateLogicalSwitchTask savedState = host.getServiceState(CreateLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testSuccessfulCreate() throws Throwable {
      LogicalSwitchApi logicalSwitchApi = mock(LogicalSwitchApi.class);
      doReturn(logicalSwitchApi)
          .when(createLogicalSwitchTaskService)
          .getLogicalSwitchApi(any(CreateLogicalSwitchTask.class));

      String logicalSwitchId = UUID.randomUUID().toString();
      LogicalSwitch logicalSwitch = new LogicalSwitch();
      logicalSwitch.setId(logicalSwitchId);

      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitch>) invocation.getArguments()[1])
              .onSuccess(logicalSwitch);
        }
        return null;
      }).when(logicalSwitchApi)
          .createLogicalSwitch(any(LogicalSwitchCreateSpec.class), any(FutureCallback.class));

      LogicalSwitchState inProgressState = new LogicalSwitchState();
      inProgressState.setState(NsxSwitch.State.IN_PROGRESS);

      LogicalSwitchState successState = new LogicalSwitchState();
      successState.setState(NsxSwitch.State.SUCCESS);
      successState.setId(logicalSwitchId);

      doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onSuccess(inProgressState);
        }
        return null;
      }).doAnswer(invocation -> {
        if (invocation.getArguments()[1] != null) {
          ((FutureCallback<LogicalSwitchState>) invocation.getArguments()[1])
              .onSuccess(successState);
        }
        return null;
      }).when(logicalSwitchApi)
          .getLogicalSwitchState(eq(logicalSwitchId), any(FutureCallback.class));

      CreateLogicalSwitchTask createdState = createLogicalSwitchTaskService(TaskState.TaskStage.CREATED, 0);
      host.waitForState(createdState.documentSelfLink,
          CreateLogicalSwitchTask.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      CreateLogicalSwitchTask savedState = host.getServiceState(CreateLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(savedState.id, is(logicalSwitchId));
    }
  }

  private static CreateLogicalSwitchTask createLogicalSwitchTaskService(TaskState.TaskStage startStage,
                                                                        int controlFlags) throws Throwable {
    CreateLogicalSwitchTask startState = new CreateLogicalSwitchTask();
    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.controlFlags = controlFlags;
    startState.displayName = "switch1";
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.transportZoneId = UUID.randomUUID().toString();
    startState.executionDelay = 100;

    Operation result = host.startServiceSynchronously(createLogicalSwitchTaskService, startState);
    return result.getBody(CreateLogicalSwitchTask.class);
  }

  private static CreateLogicalSwitchTask buildPatchState(TaskState.TaskStage patchStage) {
    CreateLogicalSwitchTask patchState = new CreateLogicalSwitchTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;

    return patchState;
  }
}
