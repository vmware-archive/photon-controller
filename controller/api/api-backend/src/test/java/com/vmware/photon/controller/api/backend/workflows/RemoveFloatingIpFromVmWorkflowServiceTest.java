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

package com.vmware.photon.controller.api.backend.workflows;

import com.vmware.photon.controller.api.backend.helpers.ReflectionUtils;
import com.vmware.photon.controller.api.backend.helpers.TestEnvironment;
import com.vmware.photon.controller.api.backend.helpers.TestHelper;
import com.vmware.photon.controller.api.backend.servicedocuments.RemoveFloatingIpFromVmWorkflowDocument;
import com.vmware.photon.controller.api.backend.utils.TaskStateHelper;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.IpHelper;
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

import org.apache.commons.net.util.SubnetUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link RemoveFloatingIpFromVmWorkflowService}.
 */
public class RemoveFloatingIpFromVmWorkflowServiceTest {
  private static final TaskStateHelper<RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage> taskStateHelper =
      new TaskStateHelper<>(RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.class);

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
          "vmId",
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
          "vmId",
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
          "vmId",
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
    public Object[][] getInvalidStartStateTestData() throws Throwable {
      return taskStateHelper.getInvalidStartState();
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
          "vmId",
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
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.GET_VM_MAC
                      == state.taskState.subStage);

      if (!(currentStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED &&
          currentSubStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.GET_VM_MAC)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatchState(currentStage, currentSubStage));
      }

      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
          buildPatchState(patchStage, patchSubStage))
          .getBody(RemoveFloatingIpFromVmWorkflowDocument.class);

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
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage currentStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage currentSubStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage patchStage,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      RemoveFloatingIpFromVmWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
              startState,
              RemoveFloatingIpFromVmWorkflowDocument.class,
              (state) -> RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED == state.taskState.stage &&
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.GET_VM_MAC
                      == state.taskState.subStage);

      if (!(currentStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.STARTED &&
          currentSubStage == RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.GET_VM_MAC)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatchState(currentStage, currentSubStage));
      }

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
          buildPatchState(patchStage, patchSubStage))
          .getBody(RemoveFloatingIpFromVmWorkflowDocument.class);

    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch() throws Throwable {
      return taskStateHelper.getInvalidPatchState();
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
      assertThat(virtualNetwork.vmIdToDnatRuleIdMap.size(), is(1));
      assertThat(virtualNetwork.vmIdToDnatRuleIdMap, equalTo(expectedVmIdToNatRuleIdMap));
      assertThat(virtualNetwork.vmIdToSnatRuleIdMap.size(), is(1));
      assertThat(virtualNetwork.vmIdToSnatRuleIdMap, equalTo(expectedVmIdToNatRuleIdMap));

      VmService.State vm = testEnvironment.getServiceState(
          VmServiceFactory.SELF_LINK + "/" + savedState.vmId,
          VmService.State.class);
      assertThat(vm.networkInfo.size(), is(1));
      assertThat(vm.networkInfo.get(savedState.networkId).floatingIpAddress, nullValue());
      assertThat(vm.networkInfo.get(savedState.networkId).isFloatingIpQuotaConsumed, is(false));
    }

    private RemoveFloatingIpFromVmWorkflowDocument startService() throws Throwable {
      createDeploymentInCloudStore(testEnvironment);
      createDhcpRootSubnetServiceInCloudStore(testEnvironment);
      ResourceTicketService.State resourceTicketState = createResourceTicketInCloudStore(testEnvironment);
      ProjectService.State projectState = createProjectInCloudStore(testEnvironment,
          ServiceUtils.getIDFromDocumentSelfLink(resourceTicketState.documentSelfLink));
      VirtualNetworkService.State virtualNetworkState = createVirtualNetworkInCloudStore(testEnvironment);
      String networkId = ServiceUtils.getIDFromDocumentSelfLink(virtualNetworkState.documentSelfLink);
      VmService.State vmState = createVmInCloudStore(testEnvironment,
          networkId,
          ServiceUtils.getIDFromDocumentSelfLink(projectState.documentSelfLink));
      String vmId = ServiceUtils.getIDFromDocumentSelfLink(vmState.documentSelfLink);

      Map<String, String> vmIdToNatRuleIdMap = new HashMap<>();
      vmIdToNatRuleIdMap.put(vmId, "natRuleId");
      vmIdToNatRuleIdMap.put("vmId2", "natRuleId2");
      updateVirtualNetworkInCloudStore(testEnvironment, networkId, vmIdToNatRuleIdMap);

      return testEnvironment.callServiceAndWaitForState(
          RemoveFloatingIpFromVmWorkflowService.FACTORY_LINK,
          buildStartState(
              RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage.CREATED,
              null,
              networkId,
              vmId,
              0),
          RemoveFloatingIpFromVmWorkflowDocument.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage)
      );
    }
  }

  private static VmService.State createVmInCloudStore(TestEnvironment testEnvironment,
                                                      String networkId,
                                                      String projectId)
      throws Throwable {
    VmService.State startState = ReflectionUtils.buildValidStartState(VmService.State.class);
    startState.projectId = projectId;

    VmService.NetworkInfo networkInfo = new VmService.NetworkInfo();
    networkInfo.id = networkId;
    networkInfo.macAddress = "macAddress";
    networkInfo.privateIpAddress = "1.2.3.4";
    networkInfo.floatingIpAddress = "4.5.6.7";
    networkInfo.isFloatingIpQuotaConsumed = true;

    startState.networkInfo = new HashMap<>();
    startState.networkInfo.put(networkId, networkInfo);

    Operation result = testEnvironment.sendPostAndWait(VmServiceFactory.SELF_LINK, startState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(VmService.State.class);
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
    virtualNetwork.vmIdToDnatRuleIdMap = new HashMap<>();
    virtualNetwork.vmIdToDnatRuleIdMap.put("vmId1", "natRuleId1");
    virtualNetwork.vmIdToDnatRuleIdMap.put("vmId2", "natRuleId2");
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

  private static VirtualNetworkService.State updateVirtualNetworkInCloudStore(
      TestEnvironment testEnvironment,
      String networkId,
      Map<String, String> vmIdToNatRuleIdMap) throws Throwable {
    VirtualNetworkService.State patchState = new VirtualNetworkService.State();
    patchState.vmIdToDnatRuleIdMap = vmIdToNatRuleIdMap;
    patchState.vmIdToSnatRuleIdMap = vmIdToNatRuleIdMap;

    Operation result = testEnvironment.sendPatchAndWait(VirtualNetworkService.FACTORY_LINK + "/" + networkId,
        patchState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(VirtualNetworkService.State.class);
  }

  private static DhcpSubnetService.State createDhcpRootSubnetServiceInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    String cidr = "192.168.1.0/24";
    SubnetUtils subnetUtils = new SubnetUtils(cidr);
    SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();

    DhcpSubnetService.State state = new DhcpSubnetService.State();
    state.cidr = cidr;
    state.lowIp = IpHelper.ipStringToLong(subnetInfo.getLowAddress());
    state.highIp = IpHelper.ipStringToLong(subnetInfo.getHighAddress());
    state.documentSelfLink = DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK;
    state.isFloatingIpSubnet = true;

    Operation result = testEnvironment.sendPostAndWait(DhcpSubnetService.FACTORY_LINK, state);
    return result.getBody(DhcpSubnetService.State.class);
  }

  private static DeploymentService.State createDeploymentInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    DeploymentService.State deployment = ReflectionUtils.buildValidStartState(DeploymentService.State.class);
    deployment.sdnEnabled = true;
    deployment.networkManagerAddress = "networkManagerAddress";
    deployment.networkManagerUsername = "networkManagerUsername";
    deployment.networkManagerPassword = "networkManagerPassword";
    deployment.networkZoneId = "networkZoneId";
    deployment.networkTopRouterId = "networkTopRouterId";
    deployment.dhcpRelayServiceId = "dhcpRelayServiceId";

    Operation result = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, deployment);
    return result.getBody(DeploymentService.State.class);
  }

  private static ResourceTicketService.State createResourceTicketInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    ResourceTicketService.State resourceTicketState = new ResourceTicketService.State();
    resourceTicketState.name = "resource-ticket-name";
    resourceTicketState.tenantId = "tenant-id";
    resourceTicketState.parentId = "parent-id";
    resourceTicketState.documentSelfLink = "resource-ticket-id";
    resourceTicketState.limitMap = new HashMap<>();
    QuotaLineItem costItem = new QuotaLineItem();
    costItem.setKey(AssignFloatingIpToVmWorkflowService.SDN_FLOATING_IP_RESOURCE_TICKET_KEY);
    costItem.setValue(20);
    costItem.setUnit(QuotaUnit.COUNT);
    resourceTicketState.limitMap.put(costItem.getKey(), costItem);

    Operation result = testEnvironment.sendPostAndWait(ResourceTicketServiceFactory.SELF_LINK, resourceTicketState);
    resourceTicketState = result.getBody(ResourceTicketService.State.class);

    ResourceTicketService.Patch consumePatchState = new ResourceTicketService.Patch();
    consumePatchState.patchtype = ResourceTicketService.Patch.PatchType.USAGE_CONSUME;
    consumePatchState.cost = new HashMap<>();

    QuotaLineItem consumeCostType = new QuotaLineItem();
    consumeCostType.setKey(AssignFloatingIpToVmWorkflowService.SDN_FLOATING_IP_RESOURCE_TICKET_KEY);
    consumeCostType.setValue(1);
    consumeCostType.setUnit(QuotaUnit.COUNT);
    consumePatchState.cost.put(consumeCostType.getKey(), consumeCostType);

    testEnvironment.sendPatchAndWait(resourceTicketState.documentSelfLink, consumePatchState);

    return resourceTicketState;
  }

  private static ProjectService.State createProjectInCloudStore(TestEnvironment testEnvironment,
                                                                String resourceTicketId) throws Throwable {
    ProjectService.State projectState = new ProjectService.State();
    projectState.name = "projectName";
    projectState.tenantId = "tenantId";
    projectState.resourceTicketId = resourceTicketId;

    Operation result = testEnvironment.sendPostAndWait(ProjectServiceFactory.SELF_LINK, projectState);
    return result.getBody(ProjectService.State.class);
  }

  private static RemoveFloatingIpFromVmWorkflowDocument buildStartState(
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.TaskStage startStage,
      RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage subStage,
      String networkId,
      String vmId,
      int controlFlags) {
    RemoveFloatingIpFromVmWorkflowDocument startState = new RemoveFloatingIpFromVmWorkflowDocument();
    startState.taskState = new RemoveFloatingIpFromVmWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlags;
    startState.networkId = networkId;
    startState.vmId = vmId;

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
