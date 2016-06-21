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

import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.ConnectVmToSwitchTask.TaskState;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
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
                                    TaskState.SubStage startSubStage,
                                    TaskState.TaskStage expectedStage,
                                    TaskState.SubStage expectedSubStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(host, connectVmToSwitchTaskService,
          startStage, startSubStage, ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      ConnectVmToSwitchTask savedState = host.getServiceState(ConnectVmToSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createConnectVmToSwitchTaskService(host, connectVmToSwitchTaskService, TaskState.TaskStage.STARTED,
          TaskState.SubStage.CONNECT_VM_TO_SWITCH, 0);
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
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null}
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
          {"vmLocationId", "vmLocationId cannot be null"},
          {"vmId", "vmId cannot be null"},
          {"networkId", "networkId cannot be null"}
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
                                         TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         TaskState.SubStage patchSubStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CONNECT_VM_TO_SWITCH,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      patchTasktoState(createdState.documentSelfLink, startStage, startSubStage);

      ConnectVmToSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      ConnectVmToSwitchTask savedState = host.getServiceState(ConnectVmToSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransitions")
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           TaskState.SubStage patchSubStage) throws Throwable {

      ConnectVmToSwitchTask createdState = createConnectVmToSwitchTaskService(
          host,
          connectVmToSwitchTaskService,
          TaskState.TaskStage.CREATED,
          TaskState.SubStage.CONNECT_VM_TO_SWITCH,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      patchTasktoState(createdState.documentSelfLink, startStage, startSubStage);

      ConnectVmToSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
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
          TaskState.SubStage.CONNECT_VM_TO_SWITCH,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);

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
          TaskState.SubStage.CONNECT_VM_TO_SWITCH,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      ConnectVmToSwitchTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);

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

    private void patchTasktoState(String documentSelfLink,
                                  TaskState.TaskStage targetStage,
                                  TaskState.SubStage targetSubStage) throws Throwable {
      Pair<TaskState.TaskStage, TaskState.SubStage>[] transitionSequence = new Pair[]{
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK),
          Pair.of(TaskState.TaskStage.FINISHED, null)
      };

      for (Pair<TaskState.TaskStage, TaskState.SubStage> state : transitionSequence) {
        ConnectVmToSwitchTask patchState = buildPatchState(state.getLeft(), state.getRight());
        Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
            .setBody(patchState);

        Operation result = host.sendRequestAndWait(patch);
        assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

        ConnectVmToSwitchTask savedState = host.getServiceState(ConnectVmToSwitchTask.class, documentSelfLink);
        assertThat(savedState.taskState.stage, is(state.getLeft()));
        assertThat(savedState.taskState.subStage, is(state.getRight()));

        if (savedState.taskState.stage == targetStage && savedState.taskState.subStage == targetSubStage) {
          break;
        }
      }

    }

    @DataProvider(name = "validStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.CONNECT_VM_TO_SWITCH,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CREATED, null},
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
          {"vmLocationId"},
          {"vmId"},
          {"networkId"},
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
          .cloudStoreHelper(new CloudStoreHelper())
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
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("createLogicalPort failed"));
    }

    @Test
    public void testFailedToUpdateVirtualNetwork() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(true, "logicalPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      ConnectVmToSwitchTask savedState = startService();
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
    }

    @Test
    public void testSuccessfulConnection() throws Throwable {
      VirtualNetworkService.State virtualNetwork = createVirtualNetworkInCloudStore();

      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createLogicalPort(true, "logicalPortId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      ConnectVmToSwitchTask savedState = startService(
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetwork.documentSelfLink));
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));
      assertThat(savedState.toVmPortId, is("logicalPortId"));

      Map<String, String> expectedToVmPortIds = new HashMap<>();
      expectedToVmPortIds.put("vm-id", "logicalPortId");
      expectedToVmPortIds.put("vm-1", "port1");

      virtualNetwork = getVirtualNetworkInCloudStore(virtualNetwork.documentSelfLink);
      assertThat(virtualNetwork.logicalSwitchDownlinkPortIds.size(), is(2));
      assertThat(virtualNetwork.logicalSwitchDownlinkPortIds, equalTo(expectedToVmPortIds));
    }

    private ConnectVmToSwitchTask startService() throws Throwable {
      return startService("network-id");
    }

    private ConnectVmToSwitchTask startService(String networkId) throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          ConnectVmToSwitchTaskService.FACTORY_LINK,
          buildStartState(TaskState.TaskStage.CREATED, null, networkId, 0),
          ConnectVmToSwitchTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }

    private VirtualNetworkService.State createVirtualNetworkInCloudStore() throws Throwable {
      VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
      virtualNetwork.name = "virtual_network_name";
      virtualNetwork.state = NetworkState.CREATING;
      virtualNetwork.routingType = RoutingType.ROUTED;
      virtualNetwork.parentId = "parentId";
      virtualNetwork.parentKind = "parentKind";
      virtualNetwork.tier0RouterId = "logical_tier0_router_id";
      virtualNetwork.logicalRouterId = "logical_tier1_router_id";
      virtualNetwork.logicalSwitchId = "logical_switch_id";
      virtualNetwork.logicalSwitchDownlinkPortIds = new HashMap<>();
      virtualNetwork.logicalSwitchDownlinkPortIds.put("vm-1", "port1");

      Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.state = NetworkState.READY;
      result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      return result.getBody(VirtualNetworkService.State.class);
    }

    private VirtualNetworkService.State getVirtualNetworkInCloudStore(String serviceUrl) throws Throwable {
     return testEnvironment.getServiceState(serviceUrl, VirtualNetworkService.State.class);
    }
  }

  private static ConnectVmToSwitchTask createConnectVmToSwitchTaskService(TestHost testHost,
                                                                          ConnectVmToSwitchTaskService service,
                                                                          TaskState.TaskStage startStage,
                                                                          TaskState.SubStage startSubStage,
                                                                          int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service, buildStartState(startStage, startSubStage,
        "network-id", controlFlag));
    return result.getBody(ConnectVmToSwitchTask.class);
  }

  private static ConnectVmToSwitchTask buildStartState(TaskState.TaskStage startStage,
                                                       TaskState.SubStage subStage,
                                                       String networkId,
                                                       int controlFlags) {
    ConnectVmToSwitchTask startState = new ConnectVmToSwitchTask();
    startState.taskState = new TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.logicalSwitchId = "switch-id";
    startState.toVmPortDisplayName = "port-to-vm";
    startState.vmLocationId = "vm-location-id";
    startState.vmId = "vm-id";
    startState.networkId = networkId;
    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";

    return startState;
  }

  private static ConnectVmToSwitchTask buildPatchState(TaskState.TaskStage patchStage,
                                                       TaskState.SubStage patchSubStage) {

    ConnectVmToSwitchTask patchState = new ConnectVmToSwitchTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
