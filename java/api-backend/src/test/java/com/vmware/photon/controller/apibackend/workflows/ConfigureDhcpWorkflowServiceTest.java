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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.StatsStoreType;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
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
      int controlFlag,
      String deploymentId) {

    ConfigureDhcpWorkflowDocument startState = new ConfigureDhcpWorkflowDocument();

    startState.taskState = new ConfigureDhcpWorkflowDocument.TaskState();
    startState.taskState.stage = startStage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = controlFlag;

    startState.nsxManagerEndpoint = "https://192.168.1.1";
    startState.username = "username";
    startState.password = "password";
    startState.deploymentId = deploymentId;
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
    DeploymentService.State startState = new DeploymentService.State();
    startState.imageDataStoreNames = Collections.singleton("IMAGE_DATASTORE_NAME");
    startState.imageDataStoreUsedForVMs = true;
    startState.state = DeploymentState.READY;
    startState.ntpEndpoint = "NTP_ENDPOINT";
    startState.syslogEndpoint = "SYSLOG_ENDPOINT";
    startState.statsEnabled = true;
    startState.statsStoreEndpoint = "STATS_STORE_ENDPOINT";
    startState.statsStorePort = 8081;
    startState.statsStoreType = StatsStoreType.GRAPHITE;
    startState.oAuthEnabled = false;
    startState.oAuthServerAddress = "OAUTH_ENDPOINT";
    startState.virtualNetworkEnabled = false;

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

      DeploymentService.State deploymentState = createDeploymentDocumentInCloudStore(testEnvironment);
      startState = buildStartState(
          TaskState.TaskStage.CREATED,
          null,
          new ControlFlags.Builder()
              .disableOperationProcessingOnHandleStart()
              .disableOperationProcessingOnHandlePatch()
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink));
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
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink));
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
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink));

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
              .build(),
          ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink));
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
}
