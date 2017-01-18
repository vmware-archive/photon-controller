/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
import com.vmware.photon.controller.api.backend.servicedocuments.ConfigureNsxWorkflowDocument;
import com.vmware.photon.controller.api.backend.utils.TaskStateHelper;
import com.vmware.photon.controller.api.model.IpRange;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;

/**
 * Tests for {@link ConfigureNsxWorkflowService}.
 */
public class ConfigureNsxWorkflowServiceTest {
  private static final TaskStateHelper<ConfigureNsxWorkflowDocument.TaskState.SubStage> taskStateHelper =
      new TaskStateHelper<>(ConfigureNsxWorkflowDocument.TaskState.SubStage.class);

  private static final String NETWORK_MANAGER_ADDRESS = "networkManagerAddress";
  private static final String NETWORK_MANAGER_USERNAME = "networkManagerUsername";
  private static final String NETWORK_MANAGER_PASSWORD = "networkManagerPassword";
  private static final String DHCP_PRIVATE_IP = "192.168.1.1";
  private static final String DHCP_PUBLIC_IP = "10.35.7.33";
  private static final String DHCP_RELAY_PROFILE_ID = "dhcpRelayProfileId";
  private static final String DHCP_RELAY_SERVICE_ID = "dhcpRelayServiceId";
  private static final String PRIVATE_IP_ROOT_CIDR = "192.168.2.0/16";
  private static final String FLOATING_IP_ROOT_RANGE_START = "22.3.5.1";
  private static final String FLOATING_IP_ROOT_RANGE_END = "22.3.5.100";
  private static final String T0_ROUTER_ID = "t0RouterId";
  private static final String EDGE_CLUSTER_ID = "edgeClusterId";
  private static final String OVERLAY_TRANSPORT_ZONE_ID = "overlayTransportZoneId";
  private static final String TUNNEL_IP_POOL_ID = "tunnelIpPoolId";
  private static final String HOST_UPLINK_PNIC = "hostUplinkPnic";

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Creates a new ConfigureNsxWorkflowDocument object to create a new ConfigureNsxWorkflowService instance.
   */
  private static ConfigureNsxWorkflowDocument buildStartState(
      ConfigureNsxWorkflowDocument.TaskState.TaskStage startStage,
      ConfigureNsxWorkflowDocument.TaskState.SubStage subStage,
      int controlFlag) {

    ConfigureNsxWorkflowDocument startState = new ConfigureNsxWorkflowDocument();

    startState.taskState = new ConfigureNsxWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlag;

    startState.nsxAddress = NETWORK_MANAGER_ADDRESS;
    startState.nsxUsername = NETWORK_MANAGER_USERNAME;
    startState.nsxPassword = NETWORK_MANAGER_PASSWORD;
    startState.dhcpServerAddresses = new HashMap<>();
    startState.dhcpServerAddresses.put(DHCP_PRIVATE_IP, DHCP_PUBLIC_IP);
    startState.privateIpRootCidr = PRIVATE_IP_ROOT_CIDR;
    startState.floatingIpRootRange = new IpRange();
    startState.floatingIpRootRange.setStart(FLOATING_IP_ROOT_RANGE_START);
    startState.floatingIpRootRange.setEnd(FLOATING_IP_ROOT_RANGE_END);
    startState.t0RouterId = T0_ROUTER_ID;
    startState.edgeClusterId = EDGE_CLUSTER_ID;
    startState.overlayTransportZoneId = OVERLAY_TRANSPORT_ZONE_ID;
    startState.tunnelIpPoolId = TUNNEL_IP_POOL_ID;
    startState.hostUplinkPnic = HOST_UPLINK_PNIC;

    return startState;
  }

  /**
   * Creates a patch object which is sufficient to patch a ConfigureNsxWorkflowService instance.
   */
  private static ConfigureNsxWorkflowDocument buildPatchState(
      ConfigureNsxWorkflowDocument.TaskState.TaskStage patchStage,
      ConfigureNsxWorkflowDocument.TaskState.SubStage patchSubStage) {

    ConfigureNsxWorkflowDocument patchState = new ConfigureNsxWorkflowDocument();
    patchState.taskState = new ConfigureNsxWorkflowDocument.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }

  private DeploymentService.State createDeploymentStartState() throws Throwable {
    DeploymentService.State startState = ReflectionUtils.buildValidStartState(DeploymentService.State.class);
    startState.sdnEnabled = true;
    startState.nsxConfigured = false;
    return startState;
  }

  private DeploymentService.State createDeployment(TestEnvironment testEnvironment)
      throws Throwable {
    return createDeployment(testEnvironment, createDeploymentStartState());
  }

  private DeploymentService.State createDeployment(
      TestEnvironment testEnvironment,
      DeploymentService.State deploymentState) throws Throwable {
    Operation result = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, deploymentState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(DeploymentService.State.class);
  }

  private SubnetAllocatorService.State createGlobalSubnetAllocator(
      TestEnvironment testEnvironment) throws Throwable {
    SubnetAllocatorService.State startState = ReflectionUtils.buildValidStartState(SubnetAllocatorService.State.class);
    startState.rootCidr = PRIVATE_IP_ROOT_CIDR;
    startState.dhcpAgentEndpoint = String.format(
        "http://%s:%d",
        DHCP_PUBLIC_IP,
        Constants.DHCP_AGENT_PORT);
    startState.documentSelfLink = SubnetAllocatorService.SINGLETON_LINK;

    Operation result = testEnvironment.sendPostAndWait(SubnetAllocatorService.FACTORY_LINK, startState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(SubnetAllocatorService.State.class);
  }

  private DhcpSubnetService.State createGlobalFloatingIpAllocator(
      TestEnvironment testEnvironment) throws Throwable {
    DhcpSubnetService.State startState = ReflectionUtils.buildValidStartState(DhcpSubnetService.State.class);
    startState.isFloatingIpSubnet = true;
    startState.lowIp = IpHelper.ipStringToLong(FLOATING_IP_ROOT_RANGE_START);
    startState.highIp = IpHelper.ipStringToLong(FLOATING_IP_ROOT_RANGE_END);
    startState.documentSelfLink = DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK;

    Operation result = testEnvironment.sendPostAndWait(DhcpSubnetService.FACTORY_LINK, startState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(DhcpSubnetService.State.class);
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

      ConfigureNsxWorkflowService service = new ConfigureNsxWorkflowService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link ConfigureNsxWorkflowService#handleCreate}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandleCreateTest {

    private ConfigureNsxWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      createDeployment(testEnvironment);
      startState = buildStartState(
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
     * Verifies that when a field of the initial state has null value and is annotated with
     * default value, the workflow will initialize the state with the default value and succeed.
     */
    @Test
    public void succeedsWithNullDefaultFields() throws Throwable {

      startState.taskState = null;
      ConfigureNsxWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureNsxWorkflowService.FACTORY_LINK,
              startState,
              ConfigureNsxWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* cannot be null",
        dataProvider = "NotNullFields")
    public void failsWithNullMandatoryFields(String fieldName) throws Throwable {

      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "NotNullFields")
    public Object[][] getNotNullFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ConfigureNsxWorkflowDocument.class, NotBlank.class));
    }
  }

  /**
   * Tests that when {@link ConfigureNsxWorkflowService#handleStart}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandleStartTest {

    private ConfigureNsxWorkflowDocument startState;
    private TestEnvironment testEnvironment;
    private DeploymentService.State deploymentState;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      deploymentState = createDeployment(testEnvironment);
      startState = buildStartState(
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

      ConfigureNsxWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureNsxWorkflowService.FACTORY_LINK,
              startState,
              ConfigureNsxWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(TaskState.TaskStage stage,
                                           ConfigureNsxWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildStartState(stage,
          subStage,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());

      testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() throws Throwable {
      return taskStateHelper.getInvalidStartState();
    }
  }

  /**
   * Tests that when {@link ConfigureNsxWorkflowService#handlePatch}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandlePatchTest {

    private ConfigureNsxWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      createDeployment(testEnvironment);
      startState = buildStartState(
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
        ConfigureNsxWorkflowDocument.TaskState.SubStage currentSubStage,
        TaskState.TaskStage patchStage,
        ConfigureNsxWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      ConfigureNsxWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureNsxWorkflowService.FACTORY_LINK,
              startState,
              ConfigureNsxWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED
                      == state.taskState.subStage);


      if (!(currentStage == ConfigureNsxWorkflowDocument.TaskState.TaskStage.STARTED &&
          currentSubStage == ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatchState(currentStage, currentSubStage));
      }

      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
          buildPatchState(patchStage, patchSubStage))
          .getBody(ConfigureNsxWorkflowDocument.class);

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
        ConfigureNsxWorkflowDocument.TaskState.SubStage currentSubStage,
        TaskState.TaskStage patchStage,
        ConfigureNsxWorkflowDocument.TaskState.SubStage patchSubStage) throws Throwable {

      ConfigureNsxWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureNsxWorkflowService.FACTORY_LINK,
              startState,
              ConfigureNsxWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED
                      == state.taskState.subStage);

      if (!(currentStage == ConfigureNsxWorkflowDocument.TaskState.TaskStage.STARTED &&
          currentSubStage == ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED)) {
        testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
            buildPatchState(currentStage, currentSubStage));
      }

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink,
          buildPatchState(patchStage, patchSubStage))
          .getBody(ConfigureNsxWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch() throws Throwable {
      return taskStateHelper.getInvalidPatchState();
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {
      ConfigureNsxWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureNsxWorkflowService.FACTORY_LINK,
              startState,
              ConfigureNsxWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED
                      == state.taskState.subStage);

      ConfigureNsxWorkflowDocument patchState = buildPatchState(TaskState.TaskStage.STARTED,
          ConfigureNsxWorkflowDocument.TaskState.SubStage.CHECK_NSX_CONFIGURED);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(ConfigureNsxWorkflowDocument.class);

    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ConfigureNsxWorkflowDocument.class, Immutable.class));
    }
  }

  /**
   * Tests end-to-end scenarios of the {@link ConfigureNsxWorkflowService}.
   */
  public class EndToEndTest {

    private ConfigureNsxWorkflowDocument startState;
    private NsxClientFactory nsxClientFactory;
    private NsxClientMock nsxClientMock;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {

      startState = buildStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder().build());

      nsxClientFactory = mock(NsxClientFactory.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test(dataProvider = "hostCount")
    public void succeedsToConfigureNsx(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      createDeployment(testEnvironment);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that deployment entity has been updated.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.nsxConfigured, is(true));
      assertThat(deploymentState.networkManagerAddress, is(NETWORK_MANAGER_ADDRESS));
      assertThat(deploymentState.networkManagerUsername, is(NETWORK_MANAGER_USERNAME));
      assertThat(deploymentState.networkManagerPassword, is(NETWORK_MANAGER_PASSWORD));
      assertThat(deploymentState.dhcpServers.size(), is(1));
      assertThat(deploymentState.dhcpServers.contains(DHCP_PUBLIC_IP), is(true));
      assertThat(deploymentState.dhcpRelayProfileId, is(DHCP_RELAY_PROFILE_ID));
      assertThat(deploymentState.dhcpRelayServiceId, is(DHCP_RELAY_SERVICE_ID));
      assertThat(deploymentState.ipRange, is(PRIVATE_IP_ROOT_CIDR));
      assertThat(deploymentState.floatingIpRange.getStart(), is(FLOATING_IP_ROOT_RANGE_START));
      assertThat(deploymentState.floatingIpRange.getEnd(), is(FLOATING_IP_ROOT_RANGE_END));
      assertThat(deploymentState.networkTopRouterId, is(T0_ROUTER_ID));
      assertThat(deploymentState.edgeClusterId, is(EDGE_CLUSTER_ID));
      assertThat(deploymentState.networkZoneId, is(OVERLAY_TRANSPORT_ZONE_ID));
      assertThat(deploymentState.networkEdgeIpPoolId, is(TUNNEL_IP_POOL_ID));
      assertThat(deploymentState.networkHostUplinkPnic, is(HOST_UPLINK_PNIC));

      // Verifies that global subnet allocator has been created.
      SubnetAllocatorService.State globalSubnetAllocatorState = testEnvironment.getServiceState(
          SubnetAllocatorService.SINGLETON_LINK,
          SubnetAllocatorService.State.class);
      assertThat(globalSubnetAllocatorState.rootCidr, is(PRIVATE_IP_ROOT_CIDR));

      // Verifies that global floating IP allocator has been created.
      DhcpSubnetService.State globalFloatingIpAllocatorState = testEnvironment.getServiceState(
          DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK,
          DhcpSubnetService.State.class);
      assertThat(globalFloatingIpAllocatorState.isFloatingIpSubnet, is(true));
      assertThat(globalFloatingIpAllocatorState.lowIp, is(IpHelper.ipStringToLong(FLOATING_IP_ROOT_RANGE_START)));
      assertThat(globalFloatingIpAllocatorState.highIp, is(IpHelper.ipStringToLong(FLOATING_IP_ROOT_RANGE_END)));
    }

    @Test(dataProvider = "hostCount")
    public void succeedsToSkipCreatingGlobalSubnetAllocator(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      createDeployment(testEnvironment);
      createGlobalSubnetAllocator(testEnvironment);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that deployment entity has been updated.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.nsxConfigured, is(true));
    }

    @Test(dataProvider = "hostCount")
    public void succeedsToSkipCreatingGlobalFloatingIpAllocator(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      createDeployment(testEnvironment);
      createGlobalFloatingIpAllocator(testEnvironment);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that deployment entity has been updated.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.nsxConfigured, is(true));
    }

    @Test(dataProvider = "hostCount")
    public void succeedsToSkipCreatingDhcpRelayProfile(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      DeploymentService.State deploymentStartState = createDeploymentStartState();
      deploymentStartState.dhcpRelayProfileId = "existingDhcpRelayProfileId";
      createDeployment(testEnvironment, deploymentStartState);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that the DHCP relay profile ID has not been overwritten in deployment entity.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.nsxConfigured, is(true));
      assertThat(deploymentState.dhcpRelayProfileId, is("existingDhcpRelayProfileId"));
    }

    @Test(dataProvider = "hostCount")
    public void failsToCreateDhcpRelayProfile(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(false, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      createDeployment(testEnvironment);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that DHCP relay profile ID is empty in the service document.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.dhcpRelayProfileId, is(nullValue()));
    }

    @Test(dataProvider = "hostCount")
    public void succeedsToSkipCreatingDhcpRelayService(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(true, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      DeploymentService.State deploymentStartState = createDeploymentStartState();
      deploymentStartState.dhcpRelayServiceId = "existingDhcpRelayServiceId";
      createDeployment(testEnvironment, deploymentStartState);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that the DHCP relay service ID has not been overwritten in deployment entity.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.nsxConfigured, is(true));
      assertThat(deploymentState.dhcpRelayServiceId, is("existingDhcpRelayServiceId"));
    }

    @Test(dataProvider = "hostCount")
    public void failsToCreateDhcpRelayService(int hostCount) throws Throwable {
      nsxClientMock = new NsxClientMock.Builder()
          .createDhcpRelayProfile(true, DHCP_RELAY_PROFILE_ID)
          .createDhcpRelayService(false, DHCP_RELAY_SERVICE_ID)
          .build();
      doReturn(nsxClientMock).when(nsxClientFactory).create(anyString(), anyString(), anyString());

      testEnvironment = new TestEnvironment.Builder()
          .hostCount(hostCount)
          .cloudStoreHelper(new CloudStoreHelper())
          .nsxClientFactory(nsxClientFactory)
          .build();

      createDeployment(testEnvironment);

      ConfigureNsxWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureNsxWorkflowService.FACTORY_LINK,
          startState,
          ConfigureNsxWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that DHCP relay service ID is empty in the service document.
      DeploymentService.State deploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(deploymentState.dhcpRelayServiceId, is(nullValue()));
    }

    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }
  }
}
