/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.backend.workflows;

import com.vmware.photon.controller.api.backend.helpers.ReflectionUtils;
import com.vmware.photon.controller.api.backend.helpers.TestEnvironment;
import com.vmware.photon.controller.api.backend.helpers.TestHelper;
import com.vmware.photon.controller.api.backend.helpers.mocks.MockSubnetConfigurationService;
import com.vmware.photon.controller.api.backend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.api.backend.utils.TaskStateHelper;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This class implements tests for the {@link CreateVirtualNetworkWorkflowService} class.
 */
public class CreateVirtualNetworkWorkflowServiceTest {
  private static final TaskStateHelper<CreateVirtualNetworkWorkflowDocument.TaskState.SubStage> taskStateHelper =
      new TaskStateHelper<>(CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.class);

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object to create a new CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildValidStartState(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage,
      int controlFlags) {
    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.name = "name";
    startState.description = "desc";
    startState.size = 16;
    startState.reservedStaticIpSize = 4;
    startState.routingType = RoutingType.ROUTED;
    startState.parentId = "project-id";
    startState.parentKind = Project.KIND;

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildPatch(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage) {
    CreateVirtualNetworkWorkflowDocument state = new CreateVirtualNetworkWorkflowDocument();
    state.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    return state;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    private CreateVirtualNetworkWorkflowService createVirtualNetworkWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      createVirtualNetworkWorkflowService = new CreateVirtualNetworkWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      createVirtualNetworkWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(createVirtualNetworkWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link CreateVirtualNetworkWorkflowService#handleCreate} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandleCreateTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleCreate()
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
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void failsWithNullMandatoryFields() throws Throwable {

      startState.name = null;
      testEnvironment.callServiceAndWaitForState(
          CreateVirtualNetworkWorkflowService.FACTORY_LINK,
          startState,
          CreateVirtualNetworkWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    /**
     * Verifies that the workflow will create a {@link VirtualNetworkService.State} entity in cloud-store.
     */
    @Test
    public void succeedsToCreateVirtualNetworkServiceState() throws Throwable {

      startState.controlFlags = new ControlFlags.Builder()
          .disableOperationProcessingOnHandleStart()
          .disableOperationProcessingOnHandlePatch()
          .build();
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskServiceEntity, notNullValue());
      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.taskServiceEntity;
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.state, expectedVirtualNetworkServiceState.state);
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);
    }
  }

  /**
   * Test that when {@link CreateVirtualNetworkWorkflowService#handleStart} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandleStartTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
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

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(TaskState.TaskStage stage,
                                           CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildValidStartState(stage, subStage, new ControlFlags.Builder()
          .disableOperationProcessingOnHandleStart()
          .disableOperationProcessingOnHandlePatch()
          .build());

      testEnvironment.callServiceAndWaitForState(
          CreateVirtualNetworkWorkflowService.FACTORY_LINK,
          startState,
          CreateVirtualNetworkWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() throws Throwable {
      return taskStateHelper.getInvalidStartState();
    }
  }

  /**
   * Tests that when {@link CreateVirtualNetworkWorkflowService#handlePatch} is called, the workflow will
   * validate the state object and behave correctly.
   */
  public class HandlePatchTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
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
        TaskState.TaskStage currentStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage currentSubStage,
        TaskState.TaskStage patchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      if (!(currentStage == TaskState.TaskStage.STARTED &&
        currentSubStage == CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatch(currentStage, currentSubStage));
      }

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() throws Throwable {
      return taskStateHelper.getValidPatchState();
    }

    /**
     * Verifies that when an invalid stage/sub-stage patch state is given, the workflow will validate
     * the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidStageAndSubStagePatch")
    public void failsWithInvalidStageAndSubStagePatch(
        TaskState.TaskStage currentStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage currentSubStage,
        TaskState.TaskStage patchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      if (!(currentStage == TaskState.TaskStage.STARTED &&
          currentSubStage == CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatch(currentStage, currentSubStage));
      }

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(patchStage, patchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch() throws Throwable {
      return taskStateHelper.getInvalidPatchState();
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION
                      == state.taskState.subStage);

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(CreateVirtualNetworkWorkflowDocument.class);
    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateVirtualNetworkWorkflowDocument.class, Immutable.class));
    }
  }

  /**
   * Tests end-to-end scenarios of the {@link CreateVirtualNetworkWorkflowService}.
   */
  public class EndToEndTest {

    private static final String NETWORK_MANAGER_ADDRESS = "networkManagerAddress";
    private static final String NETWORK_MANAGER_USERNAME = "networkManagerUsername";
    private static final String NETWORK_MANAGER_PASSWORD = "networkManagerPassword";
    private static final String NETWORK_ZONE_ID = "networkZoneId";
    private static final String NETWORK_TOP_ROUTER_ID = "networkTopRouterId";
    private static final String NETWORK_EDGE_CLUSTER_ID = "networkEdgeClusterId";
    private static final String LOGICAL_SWITCH_ID = "logicalSwitchId";
    private static final String LOGICAL_ROUTER_ID = "logicalRouterId";
    private static final String LOGICAL_SWITCH_UPLINK_PORT_ID = "logicalSwitchUplinkPortId";
    private static final String LOGICAL_ROUTER_DONWLINK_PORT_ID = "logicalRouterDownlinkPortId";
    private static final String LOGICAL_ROUTER_UPLINK_PORT_ID = "logicalRouterUplinkPortId";
    private static final String TIER0_ROUTER_DOWNLINK_PORT_ID = "tier0RouterDownlinkPortId";
    private static final String DHCP_RELAY_SERVICE_ID = "dhcpRelayServiceId";
    private static final String SNAT_RULE_ID = "snatRuleId";

    private CreateVirtualNetworkWorkflowDocument startState;
    private NsxClientFactory nsxClientFactory;
    private NsxClientMock nsxClientMock;
    private TestEnvironment testEnvironment;
    private Map<Class<? extends Service>, Supplier<FactoryService>> testFactoryServiceMap;

    @BeforeMethod
    public void setUpTest() throws Throwable {

      startState = buildValidStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .build());

      nsxClientFactory = mock(NsxClientFactory.class);

      testFactoryServiceMap = new HashMap<>();
      testFactoryServiceMap.put(MockSubnetConfigurationService.class, MockSubnetConfigurationService::createFactory);

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .testFactoryServiceMap(testFactoryServiceMap)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    /**
     * Verifies that the workflow succeeds to create a public virtual network.
     */
    @Test
    public void succeedsToCreatePublicVirtualNetwork() throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createLogicalSwitch(true, LOGICAL_SWITCH_ID)
          .getLogicalSwitchState(true, LOGICAL_SWITCH_ID)
          .createLogicalRouter(true, LOGICAL_ROUTER_ID)
          .createLogicalPort(true, LOGICAL_SWITCH_UPLINK_PORT_ID)
          .createLogicalRouterDownLinkPort(true, LOGICAL_ROUTER_DONWLINK_PORT_ID)
          .createLogicalLinkPortOnTier0Router(true, TIER0_ROUTER_DOWNLINK_PORT_ID)
          .createLogicalLinkPortOnTier1Router(true, LOGICAL_ROUTER_UPLINK_PORT_ID)
          .getRoutingAdvertisement(true)
          .configureRoutingAdvertisement(true)
          .createNatRule(true, SNAT_RULE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      startDeploymentService(testEnvironment);
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);
      startDhcpSubnetService(testEnvironment);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ProjectService.State.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ProjectService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      testEnvironment.waitForQuery(queryTask, qt -> qt.results.documentLinks.size() > 0);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that one and only one virtual network entity is created in cloud-store.
      assertThat(finalState.taskServiceEntity, notNullValue());
      assertEquals(finalState.taskServiceEntity.routingType, RoutingType.ROUTED);
      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.taskServiceEntity;
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.state, expectedVirtualNetworkServiceState.state,
          String.format("Actual state: %s\n Expected state: %s",
              actualVirtualNetworkServiceState.toString(),
              expectedVirtualNetworkServiceState.toString()));
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);
      assertEquals(actualVirtualNetworkServiceState.parentId, expectedVirtualNetworkServiceState.parentId);
      assertEquals(actualVirtualNetworkServiceState.parentKind, expectedVirtualNetworkServiceState.parentKind);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));

      querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(1));

      ResourceTicketService.State finalResourceTicket = testEnvironment.getServiceState(ResourceTicketServiceFactory
              .SELF_LINK + "/resource-ticket-id",
          ResourceTicketService.State.class);
      assertThat(finalResourceTicket.usageMap.get(CreateVirtualNetworkWorkflowService.SDN_RESOURCE_TICKET_KEY),
          notNullValue());
      assertThat(finalResourceTicket.usageMap.get(CreateVirtualNetworkWorkflowService.SDN_RESOURCE_TICKET_KEY)
          .getValue(), is(16.0));

      // Verifies that NSX configuration is cached in the service document.
      assertThat(finalState.nsxAddress, is(NETWORK_MANAGER_ADDRESS));
      assertThat(finalState.nsxUsername, is(NETWORK_MANAGER_USERNAME));
      assertThat(finalState.nsxPassword, is(NETWORK_MANAGER_PASSWORD));
      assertThat(finalState.transportZoneId, is(NETWORK_ZONE_ID));
      assertThat(finalState.tier0RouterId, is(NETWORK_TOP_ROUTER_ID));
      assertThat(finalState.dhcpRelayServiceId, is(DHCP_RELAY_SERVICE_ID));

      // Verifies that logical switch ID is cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchId, is(LOGICAL_SWITCH_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalSwitchId,
          expectedVirtualNetworkServiceState.logicalSwitchId);

      // Verifies that logical router ID is cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(finalState.taskServiceEntity.logicalRouterId, is(LOGICAL_ROUTER_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalRouterId,
          expectedVirtualNetworkServiceState.logicalRouterId);

      // Verifies that logical port IDs are cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(expectedVirtualNetworkServiceState.isSizeQuotaConsumed, is(true));
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchUplinkPortId, is(LOGICAL_SWITCH_UPLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalRouterDownlinkPortId, is(LOGICAL_ROUTER_DONWLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalRouterUplinkPortId, is(LOGICAL_ROUTER_UPLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.tier0RouterDownlinkPortId, is(TIER0_ROUTER_DOWNLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.tier0RouterId, is(NETWORK_TOP_ROUTER_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalSwitchUplinkPortId,
          expectedVirtualNetworkServiceState.logicalSwitchUplinkPortId);
      assertEquals(actualVirtualNetworkServiceState.logicalRouterDownlinkPortId,
          expectedVirtualNetworkServiceState.logicalRouterDownlinkPortId);
      assertEquals(actualVirtualNetworkServiceState.logicalRouterUplinkPortId,
          expectedVirtualNetworkServiceState.logicalRouterUplinkPortId);
      assertEquals(actualVirtualNetworkServiceState.tier0RouterDownlinkPortId,
          expectedVirtualNetworkServiceState.tier0RouterDownlinkPortId);
      assertEquals(actualVirtualNetworkServiceState.tier0RouterId,
          expectedVirtualNetworkServiceState.tier0RouterId);
      assertEquals(actualVirtualNetworkServiceState.cidr, expectedVirtualNetworkServiceState.cidr);
      assertEquals(actualVirtualNetworkServiceState.lowIpDynamic, expectedVirtualNetworkServiceState.lowIpDynamic);
      assertEquals(actualVirtualNetworkServiceState.highIpDynamic, expectedVirtualNetworkServiceState.highIpDynamic);
      assertEquals(actualVirtualNetworkServiceState.lowIpStatic, expectedVirtualNetworkServiceState.lowIpStatic);
      assertEquals(actualVirtualNetworkServiceState.highIpStatic, expectedVirtualNetworkServiceState.highIpStatic);
      assertEquals(actualVirtualNetworkServiceState.reservedIpList, expectedVirtualNetworkServiceState.reservedIpList);
      assertEquals(actualVirtualNetworkServiceState.size, expectedVirtualNetworkServiceState.size);
    }

    /**
     * Verifies that the workflow succeeds to create a private virtual network.
     */
    @Test
    public void succeedsToCreatePrivateVirtualNetwork() throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createLogicalSwitch(true, LOGICAL_SWITCH_ID)
          .getLogicalSwitchState(true, LOGICAL_SWITCH_ID)
          .createLogicalRouter(true, LOGICAL_ROUTER_ID)
          .createLogicalPort(true, LOGICAL_SWITCH_UPLINK_PORT_ID)
          .createLogicalRouterDownLinkPort(true, LOGICAL_ROUTER_DONWLINK_PORT_ID)
          .createLogicalLinkPortOnTier0Router(true, TIER0_ROUTER_DOWNLINK_PORT_ID)
          .createLogicalLinkPortOnTier1Router(true, LOGICAL_ROUTER_UPLINK_PORT_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      startDeploymentService(testEnvironment);
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);
      startDhcpSubnetService(testEnvironment);

      startState.routingType = RoutingType.ISOLATED;
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that one and only one virtual network entity is created in cloud-store.
      assertThat(finalState.taskServiceEntity, notNullValue());
      assertEquals(finalState.taskServiceEntity.routingType, RoutingType.ISOLATED);
      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.taskServiceEntity;
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.state, expectedVirtualNetworkServiceState.state,
          String.format("Actual state: %s\n Expected state: %s",
              actualVirtualNetworkServiceState.toString(),
              expectedVirtualNetworkServiceState.toString()));
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);
      assertEquals(actualVirtualNetworkServiceState.parentId, expectedVirtualNetworkServiceState.parentId);
      assertEquals(actualVirtualNetworkServiceState.parentKind, expectedVirtualNetworkServiceState.parentKind);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(1));

      // Verifies that NSX configuration is cached in the service document.
      assertThat(finalState.nsxAddress, is(NETWORK_MANAGER_ADDRESS));
      assertThat(finalState.nsxUsername, is(NETWORK_MANAGER_USERNAME));
      assertThat(finalState.nsxPassword, is(NETWORK_MANAGER_PASSWORD));
      assertThat(finalState.transportZoneId, is(NETWORK_ZONE_ID));
      assertThat(finalState.tier0RouterId, is(NETWORK_TOP_ROUTER_ID));
      assertThat(finalState.dhcpRelayServiceId, is(DHCP_RELAY_SERVICE_ID));

      // Verifies that logical switch ID is cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchId, is(LOGICAL_SWITCH_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalSwitchId,
          expectedVirtualNetworkServiceState.logicalSwitchId);

      // Verifies that logical router ID is cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(finalState.taskServiceEntity.logicalRouterId, is(LOGICAL_ROUTER_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalRouterId,
          expectedVirtualNetworkServiceState.logicalRouterId);

      // Verifies that logical port IDs are cached in the service document, and persisted in the
      // virtual network entity.
      assertThat(expectedVirtualNetworkServiceState.isSizeQuotaConsumed, is(true));
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchUplinkPortId, is(LOGICAL_SWITCH_UPLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalRouterDownlinkPortId, is(LOGICAL_ROUTER_DONWLINK_PORT_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalRouterUplinkPortId, nullValue());
      assertThat(expectedVirtualNetworkServiceState.tier0RouterDownlinkPortId, nullValue());
      assertThat(expectedVirtualNetworkServiceState.tier0RouterId, is(NETWORK_TOP_ROUTER_ID));
      assertEquals(actualVirtualNetworkServiceState.logicalSwitchUplinkPortId,
          expectedVirtualNetworkServiceState.logicalSwitchUplinkPortId);
      assertEquals(actualVirtualNetworkServiceState.logicalRouterDownlinkPortId,
          expectedVirtualNetworkServiceState.logicalRouterDownlinkPortId);
      assertEquals(actualVirtualNetworkServiceState.logicalRouterUplinkPortId,
          expectedVirtualNetworkServiceState.logicalRouterUplinkPortId);
      assertEquals(actualVirtualNetworkServiceState.tier0RouterDownlinkPortId,
          expectedVirtualNetworkServiceState.tier0RouterDownlinkPortId);
      assertEquals(actualVirtualNetworkServiceState.tier0RouterId,
          expectedVirtualNetworkServiceState.tier0RouterId);
      assertEquals(actualVirtualNetworkServiceState.cidr, expectedVirtualNetworkServiceState.cidr);
      assertEquals(actualVirtualNetworkServiceState.lowIpDynamic, expectedVirtualNetworkServiceState.lowIpDynamic);
      assertEquals(actualVirtualNetworkServiceState.highIpDynamic, expectedVirtualNetworkServiceState.highIpDynamic);
      assertEquals(actualVirtualNetworkServiceState.lowIpStatic, expectedVirtualNetworkServiceState.lowIpStatic);
      assertEquals(actualVirtualNetworkServiceState.highIpStatic, expectedVirtualNetworkServiceState.highIpStatic);
      assertEquals(actualVirtualNetworkServiceState.reservedIpList, expectedVirtualNetworkServiceState.reservedIpList);
      assertEquals(actualVirtualNetworkServiceState.size, expectedVirtualNetworkServiceState.size);
    }

    /**
     * Verifies that when GET_NSX_CONFIGURATION sub-stage fails, the workflow will progress to FAILED state,
     * and no NSX configuration is cached in the service document. We simulate the failure by not creating
     * a deployment service entity in cloud-store.
     */
    @Test
    public void failsToGetNsxConfiguration() throws Throwable {
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that NSX configuration is empty in the service document.
      assertThat(finalState.nsxAddress, nullValue());
      assertThat(finalState.nsxUsername, nullValue());
      assertThat(finalState.nsxUsername, nullValue());
      assertThat(finalState.transportZoneId, nullValue());
      assertThat(finalState.tier0RouterId, nullValue());
    }

    /**
     * Verifies that when ALLOCATE_IP_ADDRESS_SPACE sub-stage fails, the workflow will progress to FAILED state.
     */
    @Test
    public void failsToAllocateIpAddressSpace() throws Throwable {
      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that IP Range information is empty in the service document.
      assertThat(finalState.taskServiceEntity, notNullValue());
      assertThat(finalState.taskServiceEntity.cidr, nullValue());
      assertThat(finalState.taskServiceEntity.lowIpDynamic, nullValue());
      assertThat(finalState.taskServiceEntity.highIpDynamic, nullValue());
      assertThat(finalState.taskServiceEntity.lowIpStatic, nullValue());
      assertThat(finalState.taskServiceEntity.highIpStatic, nullValue());
      assertThat(finalState.taskServiceEntity.reservedIpList, nullValue());
    }

    /**
     * Verifies that when CREATE_LOGICAL_SWITCH sub-stage fails, the workflow will progress to FAILED state,
     * and no logical switch ID is cached in the service document. We simulate the failure by failing the
     * NSX API call in the NsxClientMock.
     */
    @Test
    public void failsToCreateLogicalSwitch() throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createLogicalSwitch(false, LOGICAL_SWITCH_ID)
          .getLogicalSwitchState(false, LOGICAL_SWITCH_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      startDeploymentService(testEnvironment);
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that logical switch ID is empty in the service document.
      assertThat(finalState.taskServiceEntity.logicalSwitchId, nullValue());
    }

    /**
     * Verifies that when CREATE_LOGICAL_ROUTER sub-stage fails, the workflow will progress to FAILED state,
     * and no logical router ID is cached in the service document. We simulate the failure by failing the
     * NSX API call in the NsxClientMock.
     */
    @Test
    public void failsToCreateLogicalRouter() throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createLogicalSwitch(true, LOGICAL_SWITCH_ID)
          .getLogicalSwitchState(true, LOGICAL_SWITCH_ID)
          .createLogicalRouter(false, LOGICAL_ROUTER_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

      startDeploymentService(testEnvironment);
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that logical router ID is empty in the service document.
      assertThat(finalState.taskServiceEntity.logicalRouterId, nullValue());
    }

    /**
     * Verifies that when SET_UP_LOGICAL_ROUTER sub-stage fails, the workflow will progress to FAILED state,
     * and no logical port IDs are cached in the service document. We simulate the failure by failing the
     * NSX API call in the NsxClientMock.
     */
    @Test
    public void failsToSetUpLogicalRouter() throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createLogicalSwitch(true, LOGICAL_SWITCH_ID)
          .getLogicalSwitchState(true, LOGICAL_SWITCH_ID)
          .createLogicalRouter(true, LOGICAL_ROUTER_ID)
          .createLogicalPort(false, LOGICAL_SWITCH_UPLINK_PORT_ID)
          .createLogicalRouterDownLinkPort(false, LOGICAL_ROUTER_DONWLINK_PORT_ID)
          .createLogicalLinkPortOnTier0Router(false, TIER0_ROUTER_DOWNLINK_PORT_ID)
          .createLogicalLinkPortOnTier1Router(false, LOGICAL_ROUTER_UPLINK_PORT_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));


      startDeploymentService(testEnvironment);
      startProjectService(testEnvironment);
      startResourceTicketService(testEnvironment);
      startSubnetAllocatorService(testEnvironment);
      startDhcpSubnetService(testEnvironment);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      VirtualNetworkService.State expectedVirtualNetworkServiceState = finalState.taskServiceEntity;
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchId, is(LOGICAL_SWITCH_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalRouterId, is(LOGICAL_ROUTER_ID));
      assertThat(expectedVirtualNetworkServiceState.logicalSwitchUplinkPortId, nullValue());
      assertThat(expectedVirtualNetworkServiceState.logicalRouterDownlinkPortId, nullValue());
      assertThat(expectedVirtualNetworkServiceState.logicalRouterUplinkPortId, nullValue());
      assertThat(expectedVirtualNetworkServiceState.tier0RouterDownlinkPortId, nullValue());

      // Verifies that the virtual network entity is updated in cloud-store.
      VirtualNetworkService.State actualVirtualNetworkServiceState = testEnvironment.getServiceState(
          expectedVirtualNetworkServiceState.documentSelfLink,
          VirtualNetworkService.State.class);

      assertThat(actualVirtualNetworkServiceState, notNullValue());
      assertEquals(actualVirtualNetworkServiceState.name, expectedVirtualNetworkServiceState.name);
      assertEquals(actualVirtualNetworkServiceState.description, expectedVirtualNetworkServiceState.description);
      assertEquals(actualVirtualNetworkServiceState.routingType, expectedVirtualNetworkServiceState.routingType);
      assertEquals(actualVirtualNetworkServiceState.parentId, expectedVirtualNetworkServiceState.parentId);
      assertEquals(actualVirtualNetworkServiceState.parentKind, expectedVirtualNetworkServiceState.parentKind);
      assertEquals(actualVirtualNetworkServiceState.logicalSwitchId,
          expectedVirtualNetworkServiceState.logicalSwitchId);
      assertEquals(actualVirtualNetworkServiceState.logicalRouterId,
          expectedVirtualNetworkServiceState.logicalRouterId);
      assertThat(actualVirtualNetworkServiceState.logicalSwitchUplinkPortId, nullValue());
      assertThat(actualVirtualNetworkServiceState.logicalRouterDownlinkPortId, nullValue());
      assertThat(actualVirtualNetworkServiceState.logicalRouterUplinkPortId, nullValue());
      assertThat(actualVirtualNetworkServiceState.tier0RouterDownlinkPortId, nullValue());
    }

    private void startDeploymentService(TestEnvironment testEnvironment) throws Throwable {
      DeploymentService.State deploymentStartState =
          ReflectionUtils.buildValidStartState(DeploymentService.State.class);
      deploymentStartState.sdnEnabled = true;
      deploymentStartState.networkManagerAddress = NETWORK_MANAGER_ADDRESS;
      deploymentStartState.networkManagerUsername = NETWORK_MANAGER_USERNAME;
      deploymentStartState.networkManagerPassword = NETWORK_MANAGER_PASSWORD;
      deploymentStartState.networkZoneId = NETWORK_ZONE_ID;
      deploymentStartState.networkTopRouterId = NETWORK_TOP_ROUTER_ID;
      deploymentStartState.edgeClusterId = NETWORK_EDGE_CLUSTER_ID;
      deploymentStartState.dhcpRelayServiceId = DHCP_RELAY_SERVICE_ID;

      URI hostUri = testEnvironment.getHosts()[0].getUri();
      deploymentStartState.dhcpServers = new ArrayList<>();
      deploymentStartState.dhcpServers.add(hostUri.getHost());

      testEnvironment.callServiceAndWaitForState(
          DeploymentServiceFactory.SELF_LINK,
          deploymentStartState,
          DeploymentService.State.class,
          (state) -> true);
    }

    private void startProjectService(TestEnvironment testEnvironment) throws Throwable {
      ProjectService.State projectState = new ProjectService.State();
      projectState.resourceTicketId = "resource-ticket-id";
      projectState.name = "project-name";
      projectState.tenantId = "tenant-id";
      projectState.documentSelfLink = "project-id";

      testEnvironment.callServiceAndWaitForState(
          ProjectServiceFactory.SELF_LINK,
          projectState,
          ProjectService.State.class,
          (state) -> true);
    }

    private void startResourceTicketService(TestEnvironment testEnvironment) throws Throwable {
      ResourceTicketService.State resourceTicketState = new ResourceTicketService.State();
      resourceTicketState.name = "resource-ticket-name";
      resourceTicketState.tenantId = "tenant-id";
      resourceTicketState.parentId = "parent-id";
      resourceTicketState.documentSelfLink = "resource-ticket-id";
      resourceTicketState.limitMap = new HashMap<>();
      QuotaLineItem costItem = new QuotaLineItem();
      costItem.setKey(CreateVirtualNetworkWorkflowService.SDN_RESOURCE_TICKET_KEY);
      costItem.setValue(20);
      costItem.setUnit(QuotaUnit.COUNT);
      resourceTicketState.limitMap.put(costItem.getKey(), costItem);

      testEnvironment.callServiceAndWaitForState(
          ResourceTicketServiceFactory.SELF_LINK,
          resourceTicketState,
          ResourceTicketService.State.class,
          (state) -> true);
    }

    private void startSubnetAllocatorService(TestEnvironment testEnvironment) throws Throwable {
      SubnetAllocatorService.State subnetAllocatorServiceState = new SubnetAllocatorService.State();
      subnetAllocatorServiceState.rootCidr = "192.168.1.1/24";
      subnetAllocatorServiceState.documentSelfLink = SubnetAllocatorService.SINGLETON_LINK;

      URI hostUri = testEnvironment.getHosts()[0].getUri();
      subnetAllocatorServiceState.dhcpAgentEndpoint =
          hostUri.getScheme() + "://" + hostUri.getHost() + ":" + hostUri.getPort();

      testEnvironment.callServiceAndWaitForState(
          SubnetAllocatorService.FACTORY_LINK,
          subnetAllocatorServiceState,
          SubnetAllocatorService.State.class,
          (state) -> true);
    }

    private DhcpSubnetService.State startDhcpSubnetService(TestEnvironment testEnvironment) throws Throwable {
      DhcpSubnetService.State dhcpSubnetServiceState = new DhcpSubnetService.State();
      dhcpSubnetServiceState.lowIp = 1L;
      dhcpSubnetServiceState.highIp = 100L;
      dhcpSubnetServiceState.lowIpDynamic = 1L;
      dhcpSubnetServiceState.highIpDynamic = 100L;
      dhcpSubnetServiceState.lowIpStatic = 1L;
      dhcpSubnetServiceState.highIpDynamic = 100L;
      dhcpSubnetServiceState.size = 128L;
      URI hostUri = testEnvironment.getHosts()[0].getUri();
      dhcpSubnetServiceState.dhcpAgentEndpoint =
          hostUri.getScheme() + "://" + hostUri.getHost() + ":" + hostUri.getPort();
      dhcpSubnetServiceState.documentSelfLink = DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK;
      dhcpSubnetServiceState.subnetId = ServiceUtils.getIDFromDocumentSelfLink(
          DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK);
      dhcpSubnetServiceState.isFloatingIpSubnet = true;

      return testEnvironment.callServiceAndWaitForState(
          DhcpSubnetService.FACTORY_LINK,
          dhcpSubnetServiceState,
          DhcpSubnetService.State.class,
          (state) -> true);
    }
  }
}
