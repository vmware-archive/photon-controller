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

import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DisconnectVmFromSwitchTask.TaskState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.DisconnectVmFromSwitchTaskService}.
 */
public class DisconnectVmFromSwitchTaskServiceTest {
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

      DisconnectVmFromSwitchTaskService service = new DisconnectVmFromSwitchTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static DisconnectVmFromSwitchTaskService disconnectVmFromSwitchTaskService;

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
      disconnectVmFromSwitchTaskService = new DisconnectVmFromSwitchTaskService();
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

      DisconnectVmFromSwitchTask createdState = createDisconnectVmFromSwitchTaskService(host,
          disconnectVmFromSwitchTaskService, startStage, startSubStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      DisconnectVmFromSwitchTask savedState = host.getServiceState(DisconnectVmFromSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createDisconnectVmFromSwitchTaskService(host, disconnectVmFromSwitchTaskService, TaskState.TaskStage.STARTED,
          TaskState.SubStage.GET_NSX_CONFIGURATION, 0);
    }

    @Test(dataProvider = "notEmptyFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      DisconnectVmFromSwitchTask startState = new DisconnectVmFromSwitchTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(disconnectVmFromSwitchTaskService, startState);
        fail("should have failed due to violation of not empty constraint");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null}
      };
    }

    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][]{
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
    private static DisconnectVmFromSwitchTaskService disconnectVmFromSwitchTaskService;

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
      disconnectVmFromSwitchTaskService = new DisconnectVmFromSwitchTaskService();
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

      DisconnectVmFromSwitchTask createdState = createDisconnectVmFromSwitchTaskService(host,
          disconnectVmFromSwitchTaskService, TaskState.TaskStage.CREATED, null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      DisconnectVmFromSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      DisconnectVmFromSwitchTask savedState = host.getServiceState(DisconnectVmFromSwitchTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransitions")
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           TaskState.SubStage patchSubStage) throws Throwable {

      DisconnectVmFromSwitchTask createdState = createDisconnectVmFromSwitchTaskService(host,
          disconnectVmFromSwitchTaskService, TaskState.TaskStage.CREATED, null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      patchTaskToState(createdState.documentSelfLink, startStage, startSubStage);

      DisconnectVmFromSwitchTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName) throws Throwable {

      DisconnectVmFromSwitchTask createdState = createDisconnectVmFromSwitchTaskService(
          host,
          disconnectVmFromSwitchTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      DisconnectVmFromSwitchTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.GET_NSX_CONFIGURATION);

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
      DisconnectVmFromSwitchTask createdState = createDisconnectVmFromSwitchTaskService(
          host,
          disconnectVmFromSwitchTaskService,
          TaskState.TaskStage.CREATED,
          null,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      DisconnectVmFromSwitchTask patchState = buildPatchState(TaskState.TaskStage.STARTED,
          TaskState.SubStage.GET_NSX_CONFIGURATION);

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
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH,
              TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK,
              TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK},
          {TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH,
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
          {"vmId"},
          {"networkId"},
      };
    }

    @DataProvider(name = "writeOnceFields")
    public Object[][] getWriteOnceFields() {
      return new Object[][] {
          {"nsxAddress"},
          {"nsxUsername"},
          {"nsxPassword"},
          {"logicalSwitchId"},
          {"logicalSwitchDownlinkPortIds"}
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  TaskState.TaskStage targetStage,
                                  TaskState.SubStage targetSubStage) throws Throwable {

      Pair<TaskState.TaskStage, TaskState.SubStage>[] transitionSequence = new Pair[]{
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NSX_CONFIGURATION),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.GET_VIRTUAL_NETWORK),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.DISCONNECT_VM_FROM_SWITCH),
          Pair.of(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VIRTUAL_NETWORK),
          Pair.of(TaskState.TaskStage.FINISHED, null)
      };

      for (Pair<TaskState.TaskStage, TaskState.SubStage> state : transitionSequence) {
        DisconnectVmFromSwitchTask patchState = buildPatchState(state.getLeft(), state.getRight());
        Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
            .setBody(patchState);

        Operation result = host.sendRequestAndWait(patch);
        assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

        DisconnectVmFromSwitchTask savedState = host.getServiceState(DisconnectVmFromSwitchTask.class,
            documentSelfLink);
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
    public void testFailedToGetNsxConfiguration() throws Throwable {
      DisconnectVmFromSwitchTask savedState = startDisconnectVmFromSwitchService("network-id", "vm-id");
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("Found 0 deployment service(s)."));
    }

    @Test
    public void testFailedToGetVirtualNetwork() throws Throwable {
      startDeploymentService();
      DisconnectVmFromSwitchTask savedState = startDisconnectVmFromSwitchService("network-id", "vm-id");

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
    }

    @Test
    public void testFailedFindConnectingPortOnSwitch() throws Throwable {
      startDeploymentService();
      VirtualNetworkService.State virtualNetwork = startVirtualNetworkService();

      DisconnectVmFromSwitchTask savedState = startDisconnectVmFromSwitchService(
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetwork.documentSelfLink), "vm-id");

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, startsWith("No connecting port was found on logical switch"));
    }

    @Test
    public void testFailedToDeleteLogicalPort() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteLogicalPort(false, true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      startDeploymentService();
      VirtualNetworkService.State virtualNetwork = startVirtualNetworkService();

      DisconnectVmFromSwitchTask savedState = startDisconnectVmFromSwitchService(
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetwork.documentSelfLink), "vm-1");

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("deleteLogicalPort failed"));
    }

    @Test
    public void testSuccessfullyDisconnect() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteLogicalPort(true, true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      startDeploymentService();
      VirtualNetworkService.State virtualNetwork = startVirtualNetworkService();

      DisconnectVmFromSwitchTask savedState = startDisconnectVmFromSwitchService(
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetwork.documentSelfLink), "vm-1");

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FINISHED));

      virtualNetwork = getVirtualNetwork(virtualNetwork.documentSelfLink);
      assertThat(virtualNetwork.logicalSwitchDownlinkPortIds.size(), is(0));
    }

    private DisconnectVmFromSwitchTask startDisconnectVmFromSwitchService(String networkId,
                                                                          String vmId) throws Throwable {
      DisconnectVmFromSwitchTask startState = buildStartState(TaskState.TaskStage.CREATED, null, 0);
      startState.networkId = networkId;
      startState.vmId = vmId;

      return testEnvironment.callServiceAndWaitForState(
          DisconnectVmFromSwitchTaskService.FACTORY_LINK,
          startState,
          DisconnectVmFromSwitchTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }

    private void startDeploymentService() throws Throwable {
      DeploymentService.State deploymentStartState =
          ReflectionUtils.buildValidStartState(DeploymentService.State.class);

      deploymentStartState.sdnEnabled = true;
      deploymentStartState.networkManagerAddress = "networkManagerAddress";
      deploymentStartState.networkManagerUsername = "networkManagerUsername";
      deploymentStartState.networkManagerPassword = "networkManagerPassword";

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true
      );
    }

    private VirtualNetworkService.State startVirtualNetworkService() throws Throwable {
      VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
      virtualNetwork.name = "virtual_network_name";
      virtualNetwork.state = SubnetState.CREATING;
      virtualNetwork.routingType = RoutingType.ROUTED;
      virtualNetwork.parentId = "parentId";
      virtualNetwork.parentKind = "parentKind";
      virtualNetwork.tier0RouterId = "logical_tier0_router_id";
      virtualNetwork.logicalRouterId = "logical_tier1_router_id";
      virtualNetwork.logicalSwitchId = "logical_switch_id";
      virtualNetwork.logicalSwitchDownlinkPortIds = new HashMap<>();
      virtualNetwork.logicalSwitchDownlinkPortIds.put("vm-1", "port1");
      virtualNetwork.size = 16;

      Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.state = SubnetState.READY;
      result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      return result.getBody(VirtualNetworkService.State.class);
    }

    private VirtualNetworkService.State getVirtualNetwork(String serviceUrl) throws Throwable {
      return testEnvironment.getServiceState(serviceUrl, VirtualNetworkService.State.class);
    }
  }

  private static DisconnectVmFromSwitchTask createDisconnectVmFromSwitchTaskService(
      TestHost testHost,
      DisconnectVmFromSwitchTaskService service,
      TaskState.TaskStage startStage,
      TaskState.SubStage startSubStage,
      int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service,
        buildStartState(startStage, startSubStage, controlFlag));
    return result.getBody(DisconnectVmFromSwitchTask.class);
  }

  private static DisconnectVmFromSwitchTask buildStartState(TaskState.TaskStage startStage,
                                                            TaskState.SubStage subStage,
                                                            int controlFlags) {

    DisconnectVmFromSwitchTask startState = new DisconnectVmFromSwitchTask();
    startState.taskState = new DisconnectVmFromSwitchTask.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.vmId = "vm-id";
    startState.networkId = "network-id";

    return startState;
  }

  private static DisconnectVmFromSwitchTask buildPatchState(TaskState.TaskStage patchStage,
                                                            TaskState.SubStage patchSubStage) {

    DisconnectVmFromSwitchTask patchState = new DisconnectVmFromSwitchTask();
    patchState.taskState = new DisconnectVmFromSwitchTask.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
