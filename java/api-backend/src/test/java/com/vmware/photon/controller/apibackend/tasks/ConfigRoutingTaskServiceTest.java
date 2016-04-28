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

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureRoutingTask;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureRoutingTask.TaskState;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.ConfigureRoutingTaskService}.
 */
public class ConfigRoutingTaskServiceTest {

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
    private static TestHost host;
    private static ConfigureRoutingTaskService configureRoutingTaskService;

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
      configureRoutingTaskService = new ConfigureRoutingTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testSuccessfulHandleStart(TaskState.TaskStage startStage,
                                          TaskState.SubStage startSubStage,
                                          TaskState.TaskStage expectedStage,
                                          TaskState.SubStage expectedSubStage) throws Throwable {

      ConfigureRoutingTask createdState = createConfigureRoutingTaskService(host, configureRoutingTaskService,
          startStage, startSubStage, RoutingType.ROUTED, ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureRoutingTask savedState = host.getServiceState(ConfigureRoutingTask.class, createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createConfigureRoutingTaskService(host, configureRoutingTaskService, TaskState.TaskStage.STARTED,
          TaskState.SubStage.CREATE_SWITCH_PORT, RoutingType.ISOLATED, 0);
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null}
      };
    }

    @Test(dataProvider = "notEmptyFields")
    public void testNotEmptyFields(String fieldName, String expectedErrorMessage) throws Throwable {
      ConfigureRoutingTask startState = new ConfigureRoutingTask();

      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      startState.taskState = new TaskState();
      startState.taskState.stage = TaskState.TaskStage.CREATED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_SWITCH_PORT;

      try {
        host.startServiceSynchronously(configureRoutingTaskService, startState);
        fail("should have failed due to violation of not empty restraint");
      } catch (Exception e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }


    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][] {
          {"routingType", "routingType cannot be null"},
          {"nsxManagerEndpoint", "nsxManagerEndpoint cannot be null"},
          {"username", "username cannot be null"},
          {"password", "password cannot be null"},
          {"logicalSwitchPortDisplayName", "logicalSwitchPortDisplayName cannot be null"},
          {"logicalSwitchId", "logicalSwitchId cannot be null"},
          {"logicalRouterPortDisplayName", "logicalRouterPortDisplayName cannot be null"},
          {"logicalRouterId", "logicalRouterId cannot be null"},
          {"logicalRouterPortIp", "logicalRouterPortIp cannot be null"},
          {"logicalRouterPortIpPrefixLen", "logicalRouterPortIpPrefixLen cannot be null"}
      };
    }
  }

  /**
   * Tests for handlePatch.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static ConfigureRoutingTaskService configureRoutingTaskService;

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
      configureRoutingTaskService = new ConfigureRoutingTaskService();
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
                                         TaskState.SubStage targetSubStage,
                                         RoutingType routingType) throws Throwable {
      ConfigureRoutingTask createdState = createConfigureRoutingTaskService(host,
          configureRoutingTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CREATE_SWITCH_PORT,
          routingType,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      ConfigureRoutingTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      ConfigureRoutingTask savedState = host.getServiceState(ConfigureRoutingTask.class, createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransition")
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.SubStage startSubStage,
                                           TaskState.TaskStage targetStage,
                                           TaskState.SubStage targetSubStage,
                                           RoutingType routingType) throws Throwable {
      ConfigureRoutingTask createdState = createConfigureRoutingTaskService(host,
          configureRoutingTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CREATE_SWITCH_PORT,
          routingType,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      ConfigureRoutingTask patchState = buildPatchState(targetStage, targetSubStage);
      Operation patch = Operation.createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName) throws Throwable {
      ConfigureRoutingTask createdState = createConfigureRoutingTaskService(host,
          configureRoutingTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CREATE_SWITCH_PORT,
          RoutingType.ROUTED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureRoutingTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.CREATE_SWITCH_PORT);

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
      ConfigureRoutingTask createdState = createConfigureRoutingTaskService(host,
          configureRoutingTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CREATE_SWITCH_PORT,
          RoutingType.ROUTED,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConfigureRoutingTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.CREATE_SWITCH_PORT);

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
    public Object[][] getValidStageTransition() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.FINISHED, null, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER,
              TaskState.TaskStage.FINISHED, null, RoutingType.ROUTED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.FAILED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.FAILED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT,
              TaskState.TaskStage.FAILED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER,
              TaskState.TaskStage.FAILED, null, RoutingType.ROUTED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.FAILED, null, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.FAILED, null, RoutingType.ISOLATED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ROUTED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.CANCELLED, null, RoutingType.ISOLATED},
      };
    }

    @DataProvider(name = "invalidStageTransition")
    public Object[][] getInvalidStageTransition() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT, TaskState.TaskStage.STARTED,
              TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER, RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT, TaskState.TaskStage.STARTED,
              TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.FINISHED, null, RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT,
              TaskState.TaskStage.FINISHED, null, RoutingType.ISOLATED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER,
              RoutingType.ISOLATED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER,
              RoutingType.ROUTED},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
              TaskState.TaskStage.FINISHED, null, RoutingType.ROUTED},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT,
              TaskState.TaskStage.FINISHED, null, RoutingType.ROUTED},
      };
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags"},
          {"routingType"},
          {"nsxManagerEndpoint"},
          {"username"},
          {"password"},
          {"logicalSwitchPortDisplayName"},
          {"logicalSwitchId"},
          {"logicalRouterPortDisplayName"},
          {"logicalRouterId"},
          {"logicalRouterPortIp"},
          {"logicalRouterPortIpPrefixLen"}
      };
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"logicalSwitchPortId"},
          {"logicalRouterPortId"}
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  TaskState.TaskStage targetStage,
                                  TaskState.SubStage targetSubStage) throws Throwable {

      Pair<TaskState.TaskStage, TaskState.SubStage>[] transitionSequence = new Pair[]{
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_SWITCH_PORT),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER),
          Pair.of(TaskState.TaskStage.FINISHED, null)
      };

      for (Pair<TaskState.TaskStage, TaskState.SubStage> state : transitionSequence) {
        ConfigureRoutingTask patchState = buildPatchState(state.getLeft(), state.getRight());
        Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
            .setBody(patchState);

        Operation result = host.sendRequestAndWait(patch);
        assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

        ConfigureRoutingTask savedState = host.getServiceState(ConfigureRoutingTask.class, documentSelfLink);
        assertThat(savedState.taskState.stage, is(state.getLeft()));
        assertThat(savedState.taskState.subStage, is(state.getRight()));

        if (savedState.taskState.stage == targetStage && savedState.taskState.subStage == targetSubStage) {
          break;
        }
      }
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
    public void testFailedToCreateSwitchPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(false, "logicalPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      ConfigureRoutingTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testFailedToCreateRouterPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(true, "logicalPortId")
          .createLogicalRouterDownLinkPort(false, "logicalRouterPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      ConfigureRoutingTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testSuccessfullyLinkSwitchAndRouter() throws Throwable {
      String logicalPortId = UUID.randomUUID().toString();
      String logicalRouterDownLinkPortId = UUID.randomUUID().toString();

      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(true, logicalPortId)
          .createLogicalRouterDownLinkPort(true, logicalRouterDownLinkPortId)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      ConfigureRoutingTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(savedState.logicalSwitchPortId, is(logicalPortId));
      assertThat(savedState.logicalRouterPortId, is(logicalRouterDownLinkPortId));
    }

    private ConfigureRoutingTask startService() throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          ConfigureRoutingTaskService.FACTORY_LINK,
          buildStartState(TaskState.TaskStage.CREATED, null, RoutingType.ISOLATED, 0),
          ConfigureRoutingTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
    }
  }

  private static ConfigureRoutingTask createConfigureRoutingTaskService(
      TestHost testHost,
      ConfigureRoutingTaskService service,
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      RoutingType routingType,
      int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service,
        buildStartState(stage, subStage, routingType, controlFlag));
    return result.getBody(ConfigureRoutingTask.class);
  }

  private static ConfigureRoutingTask buildStartState(TaskState.TaskStage startStage,
                                                      TaskState.SubStage subStage,
                                                      RoutingType routingType,
                                                      int controlFlag) {

    ConfigureRoutingTask startState = new ConfigureRoutingTask();

    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;

    startState.routingType = routingType;
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.logicalSwitchPortDisplayName = "port-to-router";
    startState.logicalSwitchId = UUID.randomUUID().toString();
    startState.logicalRouterPortDisplayName = "port-to-switch";
    startState.logicalRouterId = UUID.randomUUID().toString();
    startState.logicalRouterPortIp = "192.168.2.254";
    startState.logicalRouterPortIpPrefixLen = 24;
    startState.controlFlags = controlFlag;

    return startState;
  }

  private static ConfigureRoutingTask buildPatchState(TaskState.TaskStage patchStage,
                                                      TaskState.SubStage patchSubStage) {

    ConfigureRoutingTask patchState = new ConfigureRoutingTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
