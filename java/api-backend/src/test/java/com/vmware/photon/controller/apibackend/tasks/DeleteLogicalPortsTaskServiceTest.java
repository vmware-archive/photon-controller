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
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalPortsTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalPortsTask.TaskState;
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
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.DeleteLogicalPortsTaskService}.
 */
public class DeleteLogicalPortsTaskServiceTest {

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

      DeleteLogicalPortsTaskService service = new DeleteLogicalPortsTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static DeleteLogicalPortsTaskService deleteLogicalPortsTaskService;

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
      deleteLogicalPortsTaskService = new DeleteLogicalPortsTaskService();
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

      DeleteLogicalPortsTask createdState = createDeleteLogicalPortsTaskService(
          host,
          deleteLogicalPortsTaskService,
          startStage,
          startSubStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DeleteLogicalPortsTask savedState = host.getServiceState(DeleteLogicalPortsTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null}
      };
    }

    @Test
    public void testRestartDisabled() throws Throwable {
      try {
        createDeleteLogicalPortsTaskService(
            host,
            deleteLogicalPortsTaskService,
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.GET_LINK_PORTS,
            ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);
        fail("should have failed due to invalid START state");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), is("Service state is invalid (START). Restart is disabled."));
      }
    }

    @Test(dataProvider = "notBlankFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      DeleteLogicalPortsTask startState = new DeleteLogicalPortsTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(deleteLogicalPortsTaskService, startState);
        fail("should have failed due to violation of not blank restraint");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "notBlankFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][] {
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"},
          {"logicalTier0RouterId", "logicalTier0RouterId cannot be null"},
          {"logicalTier1RouterId", "logicalTier1RouterId cannot be null"},
          {"logicalSwitchId", "logicalSwitchId cannot be null"},
          {"executionDelay", "executionDelay cannot be null"}
      };
    }
  }

  /**
   * Tests for the handlePatch.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static DeleteLogicalPortsTaskService deleteLogicalPortsTaskService;

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
      deleteLogicalPortsTaskService = new DeleteLogicalPortsTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         TaskState.SubStage startSubStage,
                                         TaskState.TaskStage targetStage,
                                         TaskState.SubStage targetSubStage) throws Throwable {

      DeleteLogicalPortsTask createdState = createDeleteLogicalPortsTaskService(
          host,
          deleteLogicalPortsTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      DeleteLogicalPortsTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DeleteLogicalPortsTask savedState = host.getServiceState(DeleteLogicalPortsTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransition() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH_PORT,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH_PORT,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_SWITCH_PORT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER1_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER0_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER1_ROUTER_DOWN_LINK_PORT}
      };
    }

    @Test(dataProvider = "invalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.SubStage startSubStage,
                                           TaskState.TaskStage targetStage,
                                           TaskState.SubStage targetSubStage) throws Throwable {

      DeleteLogicalPortsTask createdState = createDeleteLogicalPortsTaskService(
          host,
          deleteLogicalPortsTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);
      DeleteLogicalPortsTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
      fail("Should have failed due to invalid stage transition");
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER1_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER0_ROUTER_LINK_PORT},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_LINK_PORTS,
              TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_DELETE_TIER1_ROUTER_DOWN_LINK_PORT}
      };
    }

    @Test(dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName, String expectedErrorMessage) throws Throwable {

      DeleteLogicalPortsTask createdState = createDeleteLogicalPortsTaskService(
          host,
          deleteLogicalPortsTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DeleteLogicalPortsTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.GET_LINK_PORTS);

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
          {"logicalTier0RouterId", "logicalTier0RouterId is immutable"},
          {"logicalTier1RouterId", "logicalTier1RouterId is immutable"},
          {"logicalSwitchId", "logicalSwitchId is immutable"},
          {"executionDelay", "executionDelay is immutable"}
      };
    }

    @Test(dataProvider = "writeOnceFields")
    public void testChangeWriteOnceFields(String fieldName, String expectedErrorMessage) throws Throwable {
      DeleteLogicalPortsTask createdState = createDeleteLogicalPortsTaskService(host,
          deleteLogicalPortsTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DeleteLogicalPortsTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.GET_LINK_PORTS);

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
        fail("Should have failed due to changing write-once field");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"logicalLinkPortOnTier0Router", "logicalLinkPortOnTier0Router cannot be set or changed in a patch"},
          {"logicalLinkPortOnTier1Router", "logicalLinkPortOnTier1Router cannot be set or changed in a patch"},
          {"logicalDownLinkPortOnTier1Router", "logicalDownLinkPortOnTier1Router cannot be set or changed in a patch"},
          {"logicalPortOnSwitch", "logicalPortOnSwitch cannot be set or changed in a patch"},
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  TaskState.TaskStage targetStage,
                                  TaskState.SubStage targetSubStage) throws Throwable {

      DeleteLogicalPortsTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DeleteLogicalPortsTask savedState = host.getServiceState(DeleteLogicalPortsTask.class, documentSelfLink);
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
    public void testSuccessfulDeleteLogicalPorts() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, true, true)
          .deleteLogicalPort(true)
          .checkLogicalRouterPortExistence(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test
    public void testFailedToListLogicalPorts() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("listLogicalRouterPorts failed"));
    }

    @Test
    public void testFailedToDeleteTier1LogicalRouterPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("deleteLogicalRouterPort failed"));
    }

    @Test
    public void testFailedToCheckTier1RouterPortExistence() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true)
          .checkLogicalRouterPortExistence(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("checkLogicalRouterPortExistence failed"));
    }

    @Test
    public void testFailedToDeleteTier0LogicalRouterPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, false)
          .checkLogicalRouterPortExistence(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("deleteLogicalRouterPort failed"));
    }

    @Test
    public void testFailedToCheckTier0RouterPortExistence() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, true)
          .checkLogicalRouterPortExistence(true, false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("checkLogicalRouterPortExistence failed"));
    }

    @Test
    public void testFailedToDeleteTier1RouterDownLinkPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, true, false)
          .checkLogicalRouterPortExistence(true, true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("deleteLogicalRouterPort failed"));
    }

    @Test
    public void testFailedToCheckTier1RouterDownLinkPortExistence() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, true, true)
          .checkLogicalRouterPortExistence(true, true, false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("checkLogicalRouterPortExistence failed"));
    }

    @Test
    public void testFailedToDeleteLogicalSwitchPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .listLogicalRouterPorts(true)
          .deleteLogicalRouterPort(true, true, true)
          .deleteLogicalPort(false)
          .checkLogicalRouterPortExistence(true, true, true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      DeleteLogicalPortsTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, containsString("deleteLogicalPort failed"));
    }

    private DeleteLogicalPortsTask startService() throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          DeleteLogicalPortsTaskService.FACTORY_LINK,
          buildStartState(TaskState.TaskStage.CREATED, null, 0),
          DeleteLogicalPortsTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
    }
  }

  private static DeleteLogicalPortsTask createDeleteLogicalPortsTaskService(TestHost testHost,
                                                                            DeleteLogicalPortsTaskService service,
                                                                            TaskState.TaskStage startStage,
                                                                            TaskState.SubStage subStage,
                                                                            int controlFlags) throws Throwable {
    Operation result = testHost.startServiceSynchronously(service, buildStartState(startStage, subStage, controlFlags));
    return result.getBody(DeleteLogicalPortsTask.class);
  }

  private static DeleteLogicalPortsTask buildStartState(TaskState.TaskStage startStage,
                                                        TaskState.SubStage subStage,
                                                        int controlFlags) {
    DeleteLogicalPortsTask startState = new DeleteLogicalPortsTask();
    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.logicalTier0RouterId = "logicalTier0RouterId";
    startState.logicalTier1RouterId = "logicalTier1RouterId";
    startState.logicalSwitchId = "logicalSwitchId";
    startState.executionDelay = 10;

    return startState;
  }

  private static DeleteLogicalPortsTask buildPatchState(TaskState.TaskStage patchStage,
                                                        TaskState.SubStage patchSubStage) {
    DeleteLogicalPortsTask patchState = new DeleteLogicalPortsTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
