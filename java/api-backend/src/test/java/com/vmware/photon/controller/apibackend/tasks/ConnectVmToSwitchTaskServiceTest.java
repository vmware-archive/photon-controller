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
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.ConnectVmToSwitchTaskService}.
 */
public class ConnectVmToSwitchTaskServiceTest {

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

      ConnectVmToSwitchTaskService service = new ConnectVmToSwitchTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static ConnectVmToSwitchTaskService connectVmToSwitchTaskService;

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
      connectVmToSwitchTaskService = new ConnectVmToSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testStateTransition(TaskState.TaskStage startStage,
                                    TaskState.TaskStage expectedStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(host, connectVmToSwitchTaskService,
          startStage, ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConnectVmToSwitchTask savedState = host.getServiceState(ConnectVmToSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createConnectVmToSwitchTaskService(host, connectVmToSwitchTaskService, TaskState.TaskStage.STARTED, 0);
    }

    @Test(dataProvider = "notEmptyFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      ConnectVmToSwitchTask startState = new ConnectVmToSwitchTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(connectVmToSwitchTaskService, startState);
        fail("should have failed due to violation of not empty constraint");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED}
      };
    }

    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][]{
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"},
          {"logicalSwitchId", "logicalSwitchId cannot be null"},
          {"toVmPortDisplayName", "toVmPortDisplayName cannot be null"},
          {"vmLocationId", "vmLocationId cannot be null"}
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static ConnectVmToSwitchTaskService connectVmToSwitchTaskService;

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
      connectVmToSwitchTaskService = new ConnectVmToSwitchTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         TaskState.TaskStage patchStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          startStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(patchStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      ConnectVmToSwitchTask savedState = host.getServiceState(ConnectVmToSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransitions")
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.TaskStage patchStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          startStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(patchStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          TaskState.TaskStage.CREATED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(TaskState.TaskStage.FINISHED);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* cannot be set or changed in a patch",
        dataProvider = "writeOnceFields")
    public void testChangeWriteOnceFields(String fieldName) throws Throwable {
      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          TaskState.TaskStage.CREATED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(TaskState.TaskStage.FINISHED);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);
      host.sendRequestAndWait(patch);

      patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},
      };
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
      };
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags"},
          {"nsxManagerEndpoint"},
          {"username"},
          {"password"},
          {"logicalSwitchId"},
          {"toVmPortDisplayName"},
          {"vmLocationId"}
      };
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"toVmPortId"}
      };
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
    public void testFailedToConnectVmToSwitch() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(false, "logicalPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      ConnectVmToSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testSuccessfulConnection() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(true, "logicalPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      ConnectVmToSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(savedState.toVmPortId, is("logicalPortId"));
    }

    private ConnectVmToSwitchTask startService() throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          ConnectVmToSwitchTaskService.FACTORY_LINK,
          buildStartState(TaskState.TaskStage.CREATED, 0),
          ConnectVmToSwitchTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }
  }

  private static ConnectVmToSwitchTask createConnectVmToSwitchTaskService(TestHost testHost,
                                                                          ConnectVmToSwitchTaskService service,
                                                                          TaskState.TaskStage startStage,
                                                                          int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service, buildStartState(startStage, controlFlag));
    return result.getBody(ConnectVmToSwitchTask.class);
  }

  private static ConnectVmToSwitchTask buildStartState(TaskState.TaskStage startStage, int controlFlags) {
    ConnectVmToSwitchTask startState = new ConnectVmToSwitchTask();
    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.controlFlags = controlFlags;
    startState.logicalSwitchId = "switch-id";
    startState.toVmPortDisplayName = "port-to-vm";
    startState.vmLocationId = "vm-location-id";
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";

    return startState;
  }

  private static ConnectVmToSwitchTask buildPatchState(TaskState.TaskStage patchStage) {
    ConnectVmToSwitchTask patchState = new ConnectVmToSwitchTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;

    return patchState;
  }
}
