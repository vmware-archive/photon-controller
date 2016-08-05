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
import com.vmware.photon.controller.apibackend.servicedocuments.AssignFloatingIpToVmWorkflowDocument;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
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
 * Tests for {@link com.vmware.photon.controller.apibackend.workflows.AssignFloatingIpToVmWorkflowService}.
 */
public class AssignFloatingIpToVmWorkflowServiceTest {

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

      AssignFloatingIpToVmWorkflowService service = new AssignFloatingIpToVmWorkflowService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows
   * .AssignFloatingIpToVmWorkflowService#handleCreate} is called, the workflow will validate the state object and
   * behave correctly.
   */
  public static class HandleCreateTest {

    private AssignFloatingIpToVmWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkInCloudStore(testEnvironment);
      startState = buildStartState(
          AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
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
      AssignFloatingIpToVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
              startState,
              AssignFloatingIpToVmWorkflowDocument.class,
              (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);

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
          AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
          startState,
          AssignFloatingIpToVmWorkflowDocument.class,
          (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "NotNullFields")
    public Object[][] getNotNullFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AssignFloatingIpToVmWorkflowDocument.class, NotBlank.class));
    }
  }

  /**
   * Tests for handleStart.
   */
  public static class HandleStartTest {

    private AssignFloatingIpToVmWorkflowDocument startState;
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
          AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
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

      AssignFloatingIpToVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
              startState,
              AssignFloatingIpToVmWorkflowDocument.class,
              (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage stage,
                                           AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage subStage)
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
          AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
          startState,
          AssignFloatingIpToVmWorkflowDocument.class,
          (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() {
      return new Object[][]{
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED, null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED, null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED, null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CANCELLED, null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},
      };
    }
  }

  /**
   * Tests for handlePatch.
   */
  public static class HandlePatchTest {

    private AssignFloatingIpToVmWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      VirtualNetworkService.State virtualNetworkDocument = createVirtualNetworkInCloudStore(testEnvironment);
      startState = buildStartState(
          AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
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
        AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage currentStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage currentSubStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage patchStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage patchSubStage
    ) throws Throwable {

      AssignFloatingIpToVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
              startState,
              AssignFloatingIpToVmWorkflowDocument.class,
              (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE
                      == state.taskState.subStage);

      patchTaskToState(finalState.documentSelfLink, currentStage, currentSubStage);

      AssignFloatingIpToVmWorkflowDocument patchState = buildPatchState(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(AssignFloatingIpToVmWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() {
      return new Object[][]{
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null},
      };
    }

    /**
     * Verifies that when an invalid stage/sub-stage patch state is given, the workflow will validate
     * the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidStageAndSubStagePatch")
    public void failsWithInvalidStageAndSubStagePatch(
        AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage firstPatchStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage firstPatchSubStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage secondPatchStage,
        AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      AssignFloatingIpToVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
              startState,
              AssignFloatingIpToVmWorkflowDocument.class,
              (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE
                      == state.taskState.subStage);

      patchTaskToState(finalState.documentSelfLink, firstPatchStage, firstPatchSubStage);

      AssignFloatingIpToVmWorkflowDocument patchState = buildPatchState(secondPatchStage, secondPatchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(AssignFloatingIpToVmWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch()
        throws Throwable {

      return new Object[][]{
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CANCELLED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},

          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null},
          {AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED,
              null,
              AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
              AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE},
      };
    }

    private void patchTaskToState(String documentSelfLink,
                                  AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage targetStage,
                                  AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage targetSubStage)
      throws Throwable {

      if (targetStage == AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED ||
          targetStage == AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CANCELLED) {
        AssignFloatingIpToVmWorkflowDocument patchState = buildPatchState(targetStage, targetSubStage);
        testEnvironment.sendPatchAndWait(documentSelfLink, patchState);
      } else {
        Pair<AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage,
            AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage>[] transitionSequence =
            new Pair[]{
                Pair.of(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED,
                    AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE),
                Pair.of(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED, null)
            };

        for (Pair<AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage,
            AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage> state : transitionSequence) {
          AssignFloatingIpToVmWorkflowDocument patchState = buildPatchState(state.getLeft(), state.getRight());
          testEnvironment.sendPatchAndWait(documentSelfLink, patchState);

          if (state.getLeft() == targetStage && state.getRight() == targetSubStage) {
            break;
          }
        }
      }
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {
      AssignFloatingIpToVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
              startState,
              AssignFloatingIpToVmWorkflowDocument.class,
              (state) -> AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage.CREATE_NAT_RULE
                      == state.taskState.subStage);

      AssignFloatingIpToVmWorkflowDocument patchState = buildPatchState(
          AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED,
          null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(AssignFloatingIpToVmWorkflowDocument.class);

    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              AssignFloatingIpToVmWorkflowDocument.class, Immutable.class));
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
    public void testFailedToCreateNatRule() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createNatRule(false, "natRuleId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      AssignFloatingIpToVmWorkflowDocument savedState = startService();
      assertThat(savedState.taskState.stage, is(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.statusCode, is(400));
      assertThat(savedState.taskState.failure.message, is("createNatRule failed"));
    }

    @Test
    public void testSuccessfulAssignFloatingIp() throws Throwable {
      NsxClientMock nsxClientMock = new NsxClientMock.Builder()
          .createNatRule(true, "natRuleId")
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      AssignFloatingIpToVmWorkflowDocument savedState = startService();
      assertThat(savedState.taskState.stage, is(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.FINISHED));
      assertThat(savedState.taskServiceEntity, notNullValue());
      assertThat(savedState.taskServiceEntity.documentSelfLink, notNullValue());
      assertThat(savedState.taskServiceState, notNullValue());
      assertThat(savedState.taskServiceState.state, is(TaskService.State.TaskState.COMPLETED));

      Map<String, String> expectedFloatingIpToNatRuleMap = new HashMap<>();
      expectedFloatingIpToNatRuleMap.put("natRuleId", "5.6.7.8");

      VirtualNetworkService.State virtualNetwork =
          getVirtualNetworkInCloudStore(savedState.taskServiceEntity.documentSelfLink);
      assertThat(virtualNetwork.natRuleToFloatingIpMap.size(), is(1));
      assertThat(virtualNetwork.natRuleToFloatingIpMap, equalTo(expectedFloatingIpToNatRuleMap));
    }

    private AssignFloatingIpToVmWorkflowDocument startService() throws Throwable {
      VirtualNetworkService.State virtualNetworkState = createVirtualNetworkInCloudStore(testEnvironment);
      String networkId = ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkState.documentSelfLink);
      return testEnvironment.callServiceAndWaitForState(
          AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
          buildStartState(AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage.CREATED, null, networkId, 0),
          AssignFloatingIpToVmWorkflowDocument.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }

    private VirtualNetworkService.State getVirtualNetworkInCloudStore(String serviceUrl) throws Throwable {
      return testEnvironment.getServiceState(serviceUrl, VirtualNetworkService.State.class);
    }
  }

  private static VirtualNetworkService.State createVirtualNetworkInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
    virtualNetwork.name = "virtual_network_name";
    virtualNetwork.state = SubnetState.CREATING;
    virtualNetwork.routingType = RoutingType.ROUTED;
    virtualNetwork.parentId = "parentId";
    virtualNetwork.parentKind = "parentKind";
    virtualNetwork.tier0RouterId = "logical_tier0_router_id";
    virtualNetwork.logicalRouterId = "logical_tier1_router_id";
    virtualNetwork.logicalSwitchId = "logical_switch_id";

    Operation result = testEnvironment.sendPostAndWait(VirtualNetworkService.FACTORY_LINK, virtualNetwork);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    VirtualNetworkService.State createdState = result.getBody(VirtualNetworkService.State.class);
    VirtualNetworkService.State patchState = new VirtualNetworkService.State();
    patchState.state = SubnetState.READY;
    result = testEnvironment.sendPatchAndWait(createdState.documentSelfLink, patchState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(VirtualNetworkService.State.class);
  }

  private static AssignFloatingIpToVmWorkflowDocument buildStartState(
      AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage startStage,
      AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage subStage,
      String networkId,
      int controlFlags) {
    AssignFloatingIpToVmWorkflowDocument startState = new AssignFloatingIpToVmWorkflowDocument();
    startState.taskState = new AssignFloatingIpToVmWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.networkId = networkId;
    startState.nsxAddress = "https://192.168.1.1";
    startState.nsxUsername = "username";
    startState.nsxPassword = "password";
    startState.vmPrivateIpAddress = "1.2.3.4";
    startState.vmFloatingIpAddress = "5.6.7.8";

    return startState;
  }

  private static AssignFloatingIpToVmWorkflowDocument buildPatchState(
      AssignFloatingIpToVmWorkflowDocument.TaskState.TaskStage patchStage,
      AssignFloatingIpToVmWorkflowDocument.TaskState.SubStage patchSubStage) {

    AssignFloatingIpToVmWorkflowDocument patchState = new AssignFloatingIpToVmWorkflowDocument();
    patchState.taskState = new AssignFloatingIpToVmWorkflowDocument.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }
}
