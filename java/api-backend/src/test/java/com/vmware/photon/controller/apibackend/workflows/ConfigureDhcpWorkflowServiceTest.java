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

import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;

import org.hamcrest.Matchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Tests for {@link com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService}.
 */
public class ConfigureDhcpWorkflowServiceTest {

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Creates a new ConfigureDhcpWorkflowDocument object to create a new ConfigureDhcpWorkflowService instance.
   */
  private static ConfigureDhcpWorkflowDocument buildStartState(
      ConfigureDhcpWorkflowDocument.TaskState.TaskStage startStage,
      ConfigureDhcpWorkflowDocument.TaskState.SubStage subStage,
      int controlFlag) {

    ConfigureDhcpWorkflowDocument startState = new ConfigureDhcpWorkflowDocument();

    startState.taskState = new ConfigureDhcpWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlag;

    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.dhcpServerAddresses = new ArrayList<>();
    startState.dhcpServerAddresses.add("1.2.3.4");

    return startState;
  }

  /**
   * Creates a patch object which is sufficient to patch a ConfigureDhcpWorkflowService instance.
   */
  private static ConfigureDhcpWorkflowDocument buildPatchState(
      ConfigureDhcpWorkflowDocument.TaskState.TaskStage patchStage,
      ConfigureDhcpWorkflowDocument.TaskState.SubStage patchSubStage) {

    ConfigureDhcpWorkflowDocument patchState = new ConfigureDhcpWorkflowDocument();
    patchState.taskState = new ConfigureDhcpWorkflowDocument.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    return patchState;
  }

  /**
   * Creates a DeploymentService.State object in cloud-store.
   */
  private DeploymentService.State createDeploymentDocumentInCloudStore(TestEnvironment testEnvironment)
      throws Throwable {
    return createDeploymentDocumentInCloudStore(testEnvironment, "nsxAddress", "username", "password");
  }

  private DeploymentService.State createDeploymentDocumentInCloudStore(TestEnvironment testEnvironment,
                                                                       String nsxServerAddress,
                                                                       String username,
                                                                       String password) throws Throwable {
    DeploymentService.State startState = ReflectionUtils.buildValidStartState(DeploymentService.State.class);
    startState.virtualNetworkEnabled = true;
    startState.networkManagerAddress = nsxServerAddress;
    startState.networkManagerUsername = username;
    startState.networkManagerPassword = password;

    Operation result = testEnvironment.sendPostAndWait(DeploymentServiceFactory.SELF_LINK, startState);
    assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));

    return result.getBody(DeploymentService.State.class);
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

      ConfigureDhcpWorkflowService service = new ConfigureDhcpWorkflowService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService#handleCreate}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandleCreateTest {

    private ConfigureDhcpWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      createDeploymentDocumentInCloudStore(testEnvironment);
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
      ConfigureDhcpWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureDhcpWorkflowService.FACTORY_LINK,
              startState,
              ConfigureDhcpWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when a field of the initial state has null value but is annotated as mandatory,
     * the workflow will validate the state and fail.
     * @throws Throwable
     */
    @Test
    public void failsWithNullMandatoryFields() throws Throwable {

      startState.dhcpServerAddresses = null;
      try {
        testEnvironment.callServiceAndWaitForState(
            ConfigureDhcpWorkflowService.FACTORY_LINK,
            startState,
            ConfigureDhcpWorkflowDocument.class,
            (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), containsString("dhcpServerAddresses cannot be null"));
      }

    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService#handleStart}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandleStartTest {

    private ConfigureDhcpWorkflowDocument startState;
    private TestEnvironment testEnvironment;
    private DeploymentService.State deploymentState;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      deploymentState = createDeploymentDocumentInCloudStore(testEnvironment);
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

      ConfigureDhcpWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureDhcpWorkflowService.FACTORY_LINK,
              startState,
              ConfigureDhcpWorkflowDocument.class,
              (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);

      assertThat(finalState.taskState, notNullValue());
    }

    /**
     * Verifies that when an invalid start state is given, the workflow will validate the state and fail.
     */
    @Test(dataProvider = "InvalidStartState", expectedExceptions = XenonRuntimeException.class)
    public void failsWithInvalidStartState(TaskState.TaskStage stage,
                                           ConfigureDhcpWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {

      startState = buildStartState(stage,
          subStage,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build());

      testEnvironment.callServiceAndWaitForState(
          ConfigureDhcpWorkflowService.FACTORY_LINK,
          startState,
          ConfigureDhcpWorkflowDocument.class,
          (state) -> TaskState.TaskStage.CREATED == state.taskState.stage);
    }

    @DataProvider(name = "InvalidStartState")
    public Object[][] getInvalidStartStateTestData() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.CREATED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.STARTED, null},
          {TaskState.TaskStage.STARTED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.FINISHED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.FAILED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.CANCELLED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.CANCELLED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},
      };
    }
  }

  /**
   * Tests that when {@link com.vmware.photon.controller.apibackend.workflows.ConfigureDhcpWorkflowService#handlePatch}
   * is called, the workflow will validate the state object and behave correctly.
   */
  public class HandlePatchTest {

    private ConfigureDhcpWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .cloudStoreHelper(new CloudStoreHelper())
          .build();

      DeploymentService.State deploymentState = createDeploymentDocumentInCloudStore(testEnvironment);
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
        TaskState.TaskStage patchStage,
        ConfigureDhcpWorkflowDocument.TaskState.SubStage patchSubStage
    ) throws Throwable {

      ConfigureDhcpWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureDhcpWorkflowService.FACTORY_LINK,
              startState,
              ConfigureDhcpWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE
                      == state.taskState.subStage);

      ConfigureDhcpWorkflowDocument patchState = buildPatchState(patchStage, patchSubStage);
      finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(ConfigureDhcpWorkflowDocument.class);

      assertThat(finalState.taskState.stage, is(patchStage));
      assertThat(finalState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageAndSubStagePatch")
    public Object[][] getValidStageAndSubStagePatch() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.STARTED,
              ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * Verifies that when an invalid stage/sub-stage patch state is given, the workflow will validate
     * the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "InvalidStageAndSubStagePatch")
    public void failsWithInvalidStageAndSubStagePatch(
        TaskState.TaskStage firstPatchStage,
        ConfigureDhcpWorkflowDocument.TaskState.SubStage firstPatchSubStage,
        TaskState.TaskStage secondPatchStage,
        ConfigureDhcpWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      ConfigureDhcpWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureDhcpWorkflowService.FACTORY_LINK,
              startState,
              ConfigureDhcpWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE
                      == state.taskState.subStage);

      ConfigureDhcpWorkflowDocument patchState = buildPatchState(firstPatchStage, firstPatchSubStage);
      if (firstPatchStage != TaskState.TaskStage.STARTED ||
          firstPatchSubStage != ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE) {
        finalState = testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
            .getBody(ConfigureDhcpWorkflowDocument.class);
      }

      patchState = buildPatchState(secondPatchStage, secondPatchSubStage);
      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(ConfigureDhcpWorkflowDocument.class);
    }

    @DataProvider(name = "InvalidStageAndSubStagePatch")
    public Object[][] getInvalidStageAndSubStagePatch()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE},

      };
    }

    /**
     * Verifies that when a immutable field is set to non-null value in the patch state, the workflow will
     * validate the state and fail.
     */
    @Test(expectedExceptions = XenonRuntimeException.class,
        expectedExceptionsMessageRegExp = "^.* is immutable",
        dataProvider = "ImmutableFields")
    public void failsWithNonNullImmutableFieldPatch(String fieldName) throws Throwable {
      ConfigureDhcpWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              ConfigureDhcpWorkflowService.FACTORY_LINK,
              startState,
              ConfigureDhcpWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED == state.taskState.stage &&
                  ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE
                      == state.taskState.subStage);

      ConfigureDhcpWorkflowDocument patchState = buildPatchState(TaskState.TaskStage.STARTED,
          ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testEnvironment.sendPatchAndWait(finalState.documentSelfLink, patchState)
          .getBody(ConfigureDhcpWorkflowDocument.class);

    }

    @DataProvider(name = "ImmutableFields")
    public Object[][] getImmutableFields() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ConfigureDhcpWorkflowDocument.class, Immutable.class));
    }
  }

  /**
   * Tests end-to-end scenarios of the {@link ConfigureDhcpWorkflowService}.
   */
  public class EndToEndTest {

    private static final String NETWORK_MANAGER_ADDRESS = "networkManagerAddress";
    private static final String NETWORK_MANAGER_USERNAME = "networkManagerUsername";
    private static final String NETWORK_MANAGER_PASSWORD = "networkManagerPassword";
    private static final String DHCP_SERVER_ADDRESS = "1.2.3.4";
    private static final String DHCP_RELAY_PROFILE_ID = "dhcpRelayProfileId";
    private static final String DHCP_RELAY_SERVICE_ID = "dhcpRelayServiceId";

    private ConfigureDhcpWorkflowDocument startState;
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
    public void succeedsToConfigureDhcp(int hostCount) throws Throwable {
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

      createDeploymentDocumentInCloudStore(testEnvironment,
          NETWORK_MANAGER_ADDRESS,
          NETWORK_MANAGER_USERNAME,
          NETWORK_MANAGER_PASSWORD);

      ConfigureDhcpWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureDhcpWorkflowService.FACTORY_LINK,
          startState,
          ConfigureDhcpWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FINISHED == state.taskState.stage);

      // Verifies that NSX configuration is cached in the service document.
      assertThat(finalState.nsxManagerEndpoint, Matchers.is(NETWORK_MANAGER_ADDRESS));
      assertThat(finalState.username, Matchers.is(NETWORK_MANAGER_USERNAME));
      assertThat(finalState.password, Matchers.is(NETWORK_MANAGER_PASSWORD));

      // Verifies that the DHCP relay profile ID and service ID are cached in the service document,
      // and persisted in the deployment entity.
      DeploymentService.State expectedDeploymentState = finalState.taskServiceEntity;
      DeploymentService.State actualDeploymentState = testEnvironment.getServiceState(
          finalState.taskServiceEntity.documentSelfLink,
          DeploymentService.State.class);
      assertThat(expectedDeploymentState.dhcpRelayProfileId, is(DHCP_RELAY_PROFILE_ID));
      assertThat(expectedDeploymentState.dhcpRelayServiceId, is(DHCP_RELAY_SERVICE_ID));
      assertEquals(actualDeploymentState.dhcpRelayProfileId, expectedDeploymentState.dhcpRelayProfileId);
      assertEquals(actualDeploymentState.dhcpRelayServiceId, expectedDeploymentState.dhcpRelayServiceId);
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

      createDeploymentDocumentInCloudStore(testEnvironment,
          NETWORK_MANAGER_ADDRESS,
          NETWORK_MANAGER_USERNAME,
          NETWORK_MANAGER_PASSWORD);

      ConfigureDhcpWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureDhcpWorkflowService.FACTORY_LINK,
          startState,
          ConfigureDhcpWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that DHCP relay profile ID is empty in the service document.
      assertThat(finalState.taskServiceEntity.dhcpRelayProfileId, nullValue());
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

      createDeploymentDocumentInCloudStore(testEnvironment,
          NETWORK_MANAGER_ADDRESS,
          NETWORK_MANAGER_USERNAME,
          NETWORK_MANAGER_PASSWORD);

      ConfigureDhcpWorkflowDocument finalState = testEnvironment.callServiceAndWaitForState(
          ConfigureDhcpWorkflowService.FACTORY_LINK,
          startState,
          ConfigureDhcpWorkflowDocument.class,
          (state) -> TaskState.TaskStage.FAILED == state.taskState.stage);

      // Verifies that DHCP relay service ID is empty in the service document.
      assertThat(finalState.taskServiceEntity.dhcpRelayServiceId, nullValue());
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
