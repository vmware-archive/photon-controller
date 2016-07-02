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
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalSwitchTask.TaskState;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link DeleteLogicalSwitchTaskService}.
 */
public class DeleteLogicalSwitchTaskServiceTest {

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

      DeleteLogicalSwitchTaskService service = new DeleteLogicalSwitchTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static DeleteLogicalSwitchTaskService deleteLogicalSwitchTaskService;

    @BeforeClass
    public void setupClass() throws Throwable {
      host = new TestHost.Builder().build();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      host.stop();
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      deleteLogicalSwitchTaskService = new DeleteLogicalSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testStateTransition(TaskState.TaskStage startStage,
                                    TaskState.SubStage startSubStage,
                                    TaskState.TaskStage expectedStage,
                                    TaskState.SubStage expectedSubStage) throws Throwable {

      DeleteLogicalSwitchTask createdState = createDeleteLogicalSwitchTaskService(
          host,
          deleteLogicalSwitchTaskService,
          startStage,
          startSubStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DeleteLogicalSwitchTask savedState = host.getServiceState(DeleteLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null}
      };
    }

    @Test
    public void testRestartDisabled() throws Throwable {
      try {
        createDeleteLogicalSwitchTaskService(
            host,
            deleteLogicalSwitchTaskService,
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.DELETE_SWITCH,
            0);
        fail("should have failed due to invalid START state");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), is("Service state is invalid (START). Restart is disabled."));
      }
    }

    @Test(dataProvider = "notBlankFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      DeleteLogicalSwitchTask startState = new DeleteLogicalSwitchTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(deleteLogicalSwitchTaskService, startState);
        fail("should have failed due to violation of not blank restraint");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "notBlankFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][] {
          {"logicalSwitchId", "logicalSwitchId cannot be null"},
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static DeleteLogicalSwitchTaskService deleteLogicalSwitchTaskService;

    @BeforeClass
    public void setupClass() throws Throwable {
      host = new TestHost.Builder().build();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @BeforeMethod
    public void setupTest() {
      deleteLogicalSwitchTaskService = new DeleteLogicalSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         TaskState.SubStage patchSubStage) throws Throwable {

      DeleteLogicalSwitchTask createdState = createDeleteLogicalSwitchTaskService(
          host,
          deleteLogicalSwitchTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      DeleteLogicalSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DeleteLogicalSwitchTask savedState = host.getServiceState(DeleteLogicalSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "invalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           TaskState.SubStage patchSubStage) throws Throwable {

      DeleteLogicalSwitchTask createdState = createDeleteLogicalSwitchTaskService(
          host,
          deleteLogicalSwitchTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      DeleteLogicalSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
      fail("Should have failed due to invalid stage transition");
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH},
      };
    }

    @Test(dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName, String expectedErrorMessage) throws Throwable {

      DeleteLogicalSwitchTask createdState = createDeleteLogicalSwitchTaskService(
          host,
          deleteLogicalSwitchTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DeleteLogicalSwitchTask patchState = buildPatchState(TaskState.TaskStage.FINISHED, null);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patch);
        fail("Should have failed due to changing immutable fields");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags", "controlFlags is immutable"},
          {"nsxManagerEndpoint", "nsxManagerEndpoint is immutable"},
          {"username", "username is immutable"},
          {"password", "password is immutable"},
          {"logicalSwitchId", "logicalSwitchId is immutable"},
          {"executionDelay", "executionDelay is immutable"},
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  TaskState.TaskStage targetStage,
                                  TaskState.SubStage targetSubStage) throws Throwable {

      DeleteLogicalSwitchTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DeleteLogicalSwitchTask savedState = host.getServiceState(DeleteLogicalSwitchTask.class, documentSelfLink);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }
  }

  /**
   * End-to-end tests.
   */
  public class EndToEndTest {

    TestEnvironment testEnvironment;
    NsxClientFactory nsxClientFactory;

    @BeforeMethod
    public void setupTest() throws Throwable {
      nsxClientFactory = mock(NsxClientFactory.class);
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .nsxClientFactory(nsxClientFactory)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test
    public void testSuccessfulDeleteLogicalSwitchTask() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteLogicalSwitch(true)
          .checkLogicalSwitchExistence(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testFailedToDeleteLogicalSwitchTask() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteLogicalSwitch(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("deleteLogicalSwitch failed"));
    }

    @Test
    public void testFailedToCheckLogicalSwitchExistence() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteLogicalSwitch(true)
          .checkLogicalSwitchExistence(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("checkLogicalSwitchExistence failed"));
    }

    private DeleteLogicalSwitchTask startService() throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          DeleteLogicalSwitchTaskService.FACTORY_LINK,
          buildStartState(TaskState.TaskStage.CREATED, null, 0),
          DeleteLogicalSwitchTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
    }
  }

  private static DeleteLogicalSwitchTask createDeleteLogicalSwitchTaskService(TestHost testHost,
                                                                              DeleteLogicalSwitchTaskService service,
                                                                              TaskState.TaskStage startStage,
                                                                              TaskState.SubStage subStage,
                                                                              int controlFlags) throws Throwable {
    Operation result = testHost.startServiceSynchronously(service, buildStartState(startStage, subStage, controlFlags));
    return result.getBody(DeleteLogicalSwitchTask.class);
  }

  private static DeleteLogicalSwitchTask buildStartState(TaskState.TaskStage startStage,
                                                         TaskState.SubStage subStage,
                                                         int controlFlags) {
    DeleteLogicalSwitchTask startState = new DeleteLogicalSwitchTask();
    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.logicalSwitchId = "logicalSwitchId";

    return startState;
  }

  private static DeleteLogicalSwitchTask buildPatchState(TaskState.TaskStage patchStage,
                                                         TaskState.SubStage patchSubStage) {
    DeleteLogicalSwitchTask patchState = new DeleteLogicalSwitchTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
