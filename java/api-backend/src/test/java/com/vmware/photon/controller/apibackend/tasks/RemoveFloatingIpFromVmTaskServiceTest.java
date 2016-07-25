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
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmTask;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.AssertJUnit.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.tasks.RemoveFloatingIpFromVmTaskService}.
 */
public class RemoveFloatingIpFromVmTaskServiceTest {

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

      RemoveFloatingIpFromVmTaskService service = new RemoveFloatingIpFromVmTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {
    private static TestHost host;
    private static RemoveFloatingIpFromVmTaskService assignFloatingIpToVmTaskService;

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
      assignFloatingIpToVmTaskService = new RemoveFloatingIpFromVmTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "expectedStateTransition")
    public void testStateTransition(RemoveFloatingIpFromVmTask.TaskState.TaskStage startStage,
                                    RemoveFloatingIpFromVmTask.TaskState.SubStage startSubStage,
                                    RemoveFloatingIpFromVmTask.TaskState.TaskStage expectedStage,
                                    RemoveFloatingIpFromVmTask.TaskState.SubStage expectedSubStage) throws Throwable {

      RemoveFloatingIpFromVmTask createdState = createRemoveFloatingIpFromVmTaskService(host,
          assignFloatingIpToVmTaskService,
          startStage,
          startSubStage,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED);

      RemoveFloatingIpFromVmTask savedState = host.getServiceState(RemoveFloatingIpFromVmTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(expectedStage));
      assertThat(savedState.taskState.subStage, is(expectedSubStage));
      assertThat(savedState.documentExpirationTimeMicros > 0, is(true));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testRestartDisabled() throws Throwable {
      createRemoveFloatingIpFromVmTaskService(host,
          assignFloatingIpToVmTaskService,
          RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
          RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE, 0);
    }

    @Test(dataProvider = "notEmptyFields")
    public void testInvalidInitialState(String fieldName, String expectedErrorMessage) throws Throwable {
      RemoveFloatingIpFromVmTask startState = new RemoveFloatingIpFromVmTask();
      Field[] fields = startState.getClass().getDeclaredFields();
      for (Field field : fields) {
        if (field.getName() != fieldName) {
          field.set(startState, ReflectionUtils.getDefaultAttributeValue(field));
        }
      }

      try {
        host.startServiceSynchronously(assignFloatingIpToVmTaskService, startState);
        fail("should have failed due to violation of not empty constraint");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is(expectedErrorMessage));
      }
    }

    @DataProvider(name = "expectedStateTransition")
    private Object[][] getStates() {
      return new Object[][] {
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED,
              null},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.CANCELLED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.CANCELLED,
              null},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED,
              null}
      };
    }

    @DataProvider(name = "notEmptyFields")
    private Object[][] getNotEmptyFields() {
      return new Object[][]{
          {"nsxAddress", "nsxAddress cannot be null"},
          {"nsxUsername", "nsxUsername cannot be null"},
          {"nsxPassword", "nsxPassword cannot be null"},
          {"virtualNetworkId", "virtualNetworkId cannot be null"},
          {"logicalTier1RouterId", "logicalTier1RouterId cannot be null"},
          {"natRuleId", "natRuleId cannot be null"},
      };
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public static class HandlePatchTest {
    private static TestHost host;
    private static RemoveFloatingIpFromVmTaskService assignFloatingIpToVmTaskService;

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
      assignFloatingIpToVmTaskService = new RemoveFloatingIpFromVmTaskService();
      host.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @Test(dataProvider = "validStageTransitions")
    public void testValidStageTransition(RemoveFloatingIpFromVmTask.TaskState.TaskStage startStage,
                                         RemoveFloatingIpFromVmTask.TaskState.SubStage startSubStage,
                                         RemoveFloatingIpFromVmTask.TaskState.TaskStage patchStage,
                                         RemoveFloatingIpFromVmTask.TaskState.SubStage patchSubStage) throws Throwable {

      RemoveFloatingIpFromVmTask createdState = createRemoveFloatingIpFromVmTaskService(
          host,
          assignFloatingIpToVmTaskService,
          RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
          RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      patchTasktoState(createdState.documentSelfLink, startStage, startSubStage);

      RemoveFloatingIpFromVmTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      Operation result = host.sendRequestAndWait(patch);
      assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

      RemoveFloatingIpFromVmTask savedState = host.getServiceState(RemoveFloatingIpFromVmTask.class,
          createdState.documentSelfLink);
      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStageTransitions")
    public void testInvalidStageTransition(RemoveFloatingIpFromVmTask.TaskState.TaskStage startStage,
                                           RemoveFloatingIpFromVmTask.TaskState.SubStage startSubStage,
                                           RemoveFloatingIpFromVmTask.TaskState.TaskStage patchStage,
                                           RemoveFloatingIpFromVmTask.TaskState.SubStage patchSubStage)
        throws Throwable {

      RemoveFloatingIpFromVmTask createdState = createRemoveFloatingIpFromVmTaskService(
          host,
          assignFloatingIpToVmTaskService,
          RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
          RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      patchTasktoState(createdState.documentSelfLink, startStage, startSubStage);

      RemoveFloatingIpFromVmTask patchState = buildPatchState(patchStage, patchSubStage);
      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "immutableFields")
    public void testChangeImmutableFields(String fieldName) throws Throwable {

      RemoveFloatingIpFromVmTask createdState = createRemoveFloatingIpFromVmTaskService(
          host,
          assignFloatingIpToVmTaskService,
          RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
          RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
          ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED
      );

      RemoveFloatingIpFromVmTask patchState = buildPatchState(RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
          RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);

      Field field = patchState.getClass().getDeclaredField(fieldName);
      field.set(patchState, ReflectionUtils.getDefaultAttributeValue(field));

      Operation patch = Operation
          .createPatch(UriUtils.buildUri(host, createdState.documentSelfLink))
          .setBody(patchState);

      host.sendRequestAndWait(patch);
    }

    private void patchTasktoState(String documentSelfLink,
                                  RemoveFloatingIpFromVmTask.TaskState.TaskStage targetStage,
                                  RemoveFloatingIpFromVmTask.TaskState.SubStage targetSubStage) throws Throwable {
      Pair<RemoveFloatingIpFromVmTask.TaskState.TaskStage, RemoveFloatingIpFromVmTask.TaskState.SubStage>[]
          transitionSequence = new Pair[]{
          Pair.of(RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE),
          Pair.of(RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK),
          Pair.of(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED, null)
      };

      for (Pair<RemoveFloatingIpFromVmTask.TaskState.TaskStage, RemoveFloatingIpFromVmTask.TaskState.SubStage> state :
          transitionSequence) {
        RemoveFloatingIpFromVmTask patchState = buildPatchState(state.getLeft(), state.getRight());
        Operation patch = Operation.createPatch(UriUtils.buildUri(host, documentSelfLink))
            .setBody(patchState);

        Operation result = host.sendRequestAndWait(patch);
        assertThat(result.getStatusCode(), is(HttpStatus.SC_OK));

        RemoveFloatingIpFromVmTask savedState = host.getServiceState(
            RemoveFloatingIpFromVmTask.class, documentSelfLink);
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
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED,
              null},

          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED,
              null},

          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @DataProvider(name = "invalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][] {
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED,
              null},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
              null},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
              null},
          {RemoveFloatingIpFromVmTask.TaskState.TaskStage.CANCELLED,
              null,
              RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED,
              null},
      };
    }

    @DataProvider(name = "immutableFields")
    public Object[][] getImmutableFields() {
      return new Object[][] {
          {"controlFlags"},
          {"nsxAddress"},
          {"nsxUsername"},
          {"nsxPassword"},
          {"virtualNetworkId"},
          {"logicalTier1RouterId"},
          {"natRuleId"},
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
    public void testFailedToRemoveNatRule() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteNatRule(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      RemoveFloatingIpFromVmTask savedState = startService();
      assertThat(savedState.taskState.stage, is(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("deleteNatRule failed"));
    }

    @Test
    public void testFailedToUpdateVirtualNetwork() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteNatRule(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      RemoveFloatingIpFromVmTask savedState = startService();
      assertThat(savedState.taskState.stage, is(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
    }

    @Test
    public void testSuccessfulRemoveFloatingIp() throws Throwable {
      VirtualNetworkService.State virtualNetwork = createVirtualNetworkInCloudStore();

      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteNatRule(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      RemoveFloatingIpFromVmTask savedState = startService(
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetwork.documentSelfLink));
      assertThat(savedState.taskState.stage, is(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED));

      Map<String, String> expectedFloatingIpToNatRuleMap = new HashMap<>();
      expectedFloatingIpToNatRuleMap.put("natRuleId2", "5.6.7.8");

      virtualNetwork = getVirtualNetworkInCloudStore(virtualNetwork.documentSelfLink);
      assertThat(virtualNetwork.natRuleToFloatingIpMap.size(), is(1));
      assertThat(virtualNetwork.natRuleToFloatingIpMap, equalTo(expectedFloatingIpToNatRuleMap));
    }

    private RemoveFloatingIpFromVmTask startService() throws Throwable {
      return startService("network-id");
    }

    private RemoveFloatingIpFromVmTask startService(String networkId) throws Throwable {
      return testEnvironment.callServiceAndWaitForState(
          RemoveFloatingIpFromVmTaskService.FACTORY_LINK,
          buildStartState(RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED, null, networkId, 0),
          RemoveFloatingIpFromVmTask.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }

    private VirtualNetworkService.State createVirtualNetworkInCloudStore() throws Throwable {
      VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
      virtualNetwork.name = "virtual_network_name";
      virtualNetwork.state = SubnetState.READY;
      virtualNetwork.routingType = RoutingType.ROUTED;
      virtualNetwork.parentId = "parentId";
      virtualNetwork.parentKind = "parentKind";
      virtualNetwork.tier0RouterId = "logical_tier0_router_id";
      virtualNetwork.logicalRouterId = "logical_tier1_router_id";
      virtualNetwork.logicalSwitchId = "logical_switch_id";
      virtualNetwork.natRuleToFloatingIpMap = new HashMap<>();
      virtualNetwork.natRuleToFloatingIpMap.put("natRuleId", "1.2.3.4");
      virtualNetwork.natRuleToFloatingIpMap.put("natRuleId2", "5.6.7.8");

      Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
      VirtualNetworkService.State patchState = new VirtualNetworkService.State();
      patchState.state = SubnetState.READY;
      result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

      return result.getBody(VirtualNetworkService.State.class);
    }

    private VirtualNetworkService.State getVirtualNetworkInCloudStore(String serviceUrl) throws Throwable {
      return testEnvironment.getServiceState(serviceUrl, VirtualNetworkService.State.class);
    }
  }

  private static RemoveFloatingIpFromVmTask createRemoveFloatingIpFromVmTaskService(
      TestHost testHost,
      RemoveFloatingIpFromVmTaskService service,
      RemoveFloatingIpFromVmTask.TaskState.TaskStage startStage,
      RemoveFloatingIpFromVmTask.TaskState.SubStage startSubStage,
      int controlFlag) throws Throwable {

    Operation result = testHost.startServiceSynchronously(service, buildStartState(startStage, startSubStage,
        "network-id", controlFlag));
    return result.getBody(RemoveFloatingIpFromVmTask.class);
  }

  private static RemoveFloatingIpFromVmTask buildStartState(RemoveFloatingIpFromVmTask.TaskState.TaskStage startStage,
                                                          RemoveFloatingIpFromVmTask.TaskState.SubStage subStage,
                                                          String virtualNetworkId,
                                                          int controlFlags) {
    RemoveFloatingIpFromVmTask startState = new RemoveFloatingIpFromVmTask();
    startState.taskState = new RemoveFloatingIpFromVmTask.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.virtualNetworkId = virtualNetworkId;
    startState.nsxAddress = "https://192.168.1.1";
    startState.nsxUsername = "nsxUsername";
    startState.nsxPassword = "nsxPassword";
    startState.logicalTier1RouterId = "logicalTier1RouterId";
    startState.natRuleId = "natRuleId";

    return startState;
  }

  private static RemoveFloatingIpFromVmTask buildPatchState(RemoveFloatingIpFromVmTask.TaskState.TaskStage patchStage,
                                                          RemoveFloatingIpFromVmTask.TaskState.SubStage patchSubStage) {

    RemoveFloatingIpFromVmTask patchState = new RemoveFloatingIpFromVmTask();
    patchState.taskState = new RemoveFloatingIpFromVmTask.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
