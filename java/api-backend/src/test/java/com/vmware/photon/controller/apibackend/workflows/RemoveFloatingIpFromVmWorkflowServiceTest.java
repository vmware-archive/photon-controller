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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmWorkflowDocument;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.workflows.RemoveFloatingIpFromVmWorkflowService}.
 */
public class RemoveFloatingIpFromVmWorkflowServiceTest {

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

      RemoveFloatingIpFromVmWorkflowService service = new RemoveFloatingIpFromVmWorkflowService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows
   * .RemoveFloatingIpFromVmWorkflowService#handleCreate} is called, the workflow will validate the state object and
   * behave correctly.
   */
  public static class HandleCreateTest {

    private RemoveFloatingIpFromVmWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkInCloudStore(testEnvironment);
      startState = buildStartState(
          RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
          null,
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink),
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a field of the initial state has null value and is annotated with
     * default value, the workflow will initialize the state with the default value and succeed.
     */
    @Test
    public void succeedsWithNullDefaultFields() throws Throwable {

      startState.taskState = null;
      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* cannot be null",
        dataProvider = "NotNullFields")
    public void failsWithNullMandatoryFields(String fieldName) throws Throwable {

      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testEnvironment.callServiceAndWaitForState(
          RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
          startState,
          RemoveFloatingIpFromVmWorkflowDocument.class,
          (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "NotNullFields")
    public Object[][] getNotNullFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              RemoveFloatingIpFromVmWorkflowDocument.class, NotBlank.class));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {

    private RemoveFloatingIpFromVmWorkflowDocument startState;
    private TestEnvironment testEnvironment;
    private VirtualNetworkService.State virtualNetworkDocument;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      virtualNetworkDocument = createVirtualNetworkInCloudStore(testEnvironment);
      startState = buildStartState(
          RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
          null,
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink),
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a valid start state is given, the workflow will validate the state and succeed.
     */
    @Test
    public void succeedsWithValidStartState() throws Throwable {

      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage stage,
                                           RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildStartState(
          stage,
          subStage,
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink),
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());

      testEnvironment.callServiceAndWaitForState(
          RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
          startState,
          RemoveFloatingIpFromVmWorkflowDocument.class,
          (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() {
      return new Object[][]{
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED, null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED, null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED, null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CANCELLED, null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},
      };
    }
  }

  /**
   * Tests for handlePatch.
   */
  public static class HandlePatchTest {

    private RemoveFloatingIpFromVmWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkInCloudStore(testEnvironment);
      startState = buildStartState(
          RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
          null,
          ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkDocument.documentSelfLink),
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandlePatch()
              .disableOperationProcessingOnStageTransition()
              .build());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    /**
     * Verifies that when a valid stage/sub-stage patch state is given, the workflow will validate
     * the state and succeed.
     */
    @Test(dataProvider = "ValidStageAndSubStagePatch")
    public void succeedsWithValidStageAndSubStagePatch(
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage currentStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage currentSubStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage patchStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage patchSubStage
    ) throws Throwable {

      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE
                      == state.taskState.subStage);

      patchTaskToState(finalState.documentSelfLink, currentStage, currentSubStage);

      RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatchState(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(RemoveFloatingIpFromVmWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() {
      return new Object[][]{
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null},
      };
    }

    /**
     * Verifies that when an invalid stage/sub-stage patch state is given, the workflow will validate
     * the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidStageAndSubStagePatch")
    public void failsWithInvalidStageAndSubStagePatch(
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage firstPatchStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage firstPatchSubStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage secondPatchStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE
                      == state.taskState.subStage);

      patchTaskToState(finalState.documentSelfLink, firstPatchStage, firstPatchSubStage);

      RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatchState(secondPatchStage, secondPatchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(RemoveFloatingIpFromVmWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch()
        throws Throwable {

      return new Object[][]{
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},

          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED,
              null,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE},
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage targetStage,
                                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage targetSubStage)
        throws Throwable {

      if (targetStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED ||
          targetStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CANCELLED) {
        RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatchState(targetStage, targetSubStage);
        testEnvironment.sendPatchAndWait(documentSelfLink, patchState);
      } else {
        Pair<RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage,
            RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage>[] transitionSequence =
            new Pair[]{
                Pair.of(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED,
                    RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE),
                Pair.of(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED, null)
            };

        for (Pair<RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage,
            RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage> state : transitionSequence) {
          RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatchState(state.getLeft(), state.getRight());
          testEnvironment.sendPatchAndWait(documentSelfLink, patchState);

          if (state.getLeft() == targetStage && state.getRight() == targetSubStage) {
            break;
          }
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
    public void testFailedToRemoveNatRule() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteNatRule(false)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      RemoveFloatingIpFromVmWorkflowDocument savedState = startService();
      assertThat(savedState.taskState.stage, is(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("deleteNatRule failed"));
    }

    @Test
    public void testSuccessfulRemoveFloatingIp() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .deleteNatRule(true)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      RemoveFloatingIpFromVmWorkflowDocument savedState = startService();
      assertThat(savedState.taskState.stage, is(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.FINISHED));
      assertThat(savedState.taskServiceEntity, notNullValue());
      assertThat(savedState.taskServiceEntity.documentSelfLink, notNullValue());

      Map<String, String> expectedVmIdToNatRuleIdMap = new HashMap<>();
      expectedVmIdToNatRuleIdMap.put("vmId2", "natRuleId2");

      VirtualNetworkService.State virtualNetwork = testEnvironment.getServiceState(
          savedState.taskServiceEntity.documentSelfLink,
          VirtualNetworkService.State.class);
      assertThat(virtualNetwork.vmIdToNatRuleIdMap.size(), is(1));
      assertThat(virtualNetwork.vmIdToNatRuleIdMap, equalTo(expectedVmIdToNatRuleIdMap));
    }

    private RemoveFloatingIpFromVmWorkflowDocument startService() throws Throwable {
      VirtualNetworkService.State virtualNetworkState = createVirtualNetworkInCloudStore(testEnvironment);
      String networkId = ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkState.documentSelfLink);
      return testEnvironment.callServiceAndWaitForState(
          RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
          buildStartState(RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED, null, networkId, 0),
          RemoveFloatingIpFromVmWorkflowDocument.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }
  }

  private static VirtualNetworkService.State createVirtualNetworkInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
    virtualNetwork.name = "virtual_network_name";
    virtualNetwork.state = SubnetState.READY;
    virtualNetwork.routingType = RoutingType.ROUTED;
    virtualNetwork.parentId = "parentId";
    virtualNetwork.parentKind = "parentKind";
    virtualNetwork.tier0RouterId = "logical_tier0_router_id";
    virtualNetwork.logicalRouterId = "logical_tier1_router_id";
    virtualNetwork.logicalSwitchId = "logical_switch_id";
    virtualNetwork.vmIdToNatRuleIdMap = new HashMap<>();
    virtualNetwork.vmIdToNatRuleIdMap.put("vmId1", "natRuleId1");
    virtualNetwork.vmIdToNatRuleIdMap.put("vmId2", "natRuleId2");

    Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
    VirtualNetworkService.State patchState = new VirtualNetworkService.State();
    patchState.state = SubnetState.READY;
    result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(VirtualNetworkService.State.class);
  }

  private static RemoveFloatingIpFromVmWorkflowDocument buildStartState(
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage startStage,
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage subStage,
      String networkId,
      int controlFlags) {
    RemoveFloatingIpFromVmWorkflowDocument startState = new RemoveFloatingIpFromVmWorkflowDocument();
    startState.taskState = new RemoveFloatingIpFromVmWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.networkId = networkId;
    startState.nsxAddress = "https://192.168.1.1";
    startState.nsxUsername = "nsxUsername";
    startState.nsxPassword = "nsxPassword";
    startState.vmId = "vmId1";

    return startState;
  }

  private static RemoveFloatingIpFromVmWorkflowDocument buildPatchState(
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage patchStage,
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage patchSubStage) {

    RemoveFloatingIpFromVmWorkflowDocument patchState = new RemoveFloatingIpFromVmWorkflowDocument();
    patchState.taskState = new RemoveFloatingIpFromVmWorkflowDocument.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
