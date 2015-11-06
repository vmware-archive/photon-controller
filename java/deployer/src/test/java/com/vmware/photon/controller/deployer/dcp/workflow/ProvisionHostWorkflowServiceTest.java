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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.task.DeployAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.HostConfig;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Tests {@link ProvisionHostWorkflowService}.
 */
public class ProvisionHostWorkflowServiceTest {

  private TestHost host;
  private ProvisionHostWorkflowService service;

  public static ProvisionHostWorkflowService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable ProvisionHostWorkflowService.TaskState.SubStage startSubStage) {
    ProvisionHostWorkflowService.State startState = new ProvisionHostWorkflowService.State();
    startState.vibPath = "VIB_PATH";
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.chairmanServerList = new HashSet<>(Collections.singleton("localhost:13000"));
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new ProvisionHostWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;
    }

    return startState;
  }

  public static ProvisionHostWorkflowService.State buildValidPatchState(
      TaskState.TaskStage patchStage,
      ProvisionHostWorkflowService.TaskState.SubStage patchSubStage) {
    ProvisionHostWorkflowService.State patchState = new ProvisionHostWorkflowService.State();
    patchState.taskState = new ProvisionHostWorkflowService.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;
    return patchState;
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() {
      service = new ProvisionHostWorkflowService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStart {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUp() {
      service = new ProvisionHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that service instances can be created with specific
     * start states.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(
        TaskState.TaskStage stage,
        ProvisionHostWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(stage, subStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state is transitioned to
     * STARTED state as part of the start operation handling.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "StartedStages")
    public void testMinimalStartStateChanged(
        @Nullable TaskState.TaskStage startStage,
        @Nullable ProvisionHostWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.CREATED));
      assertThat(savedState.taskState.subStage, is(nullValue()));
    }

    @DataProvider(name = "StartedStages")
    public Object[][] getStartedStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
      };
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(stage, null);
      startState.controlFlags = null;
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that we create a unique id.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testCreatesUniqueId() throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(null, null);
      startState.uniqueID = null;
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);

      assertThat(savedState.uniqueID, notNullValue());
    }

    /**
     * This test verifies that the unique id provided is not changed.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testUnchangedUniqueId() throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(null, null);
      startState.uniqueID = "unique id";
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);

      assertThat(savedState.uniqueID, is("unique id"));
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostWorkflowService.State.class, NotNull.class));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatch {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new ProvisionHostWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that legal stage and substage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        ProvisionHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        ProvisionHostWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {

      ProvisionHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      ProvisionHostWorkflowService.State savedState = host.getServiceState(ProvisionHostWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the patch state is invalid.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(expectedExceptions = IllegalStateException.class)
    public void testIllegalStageUpdatesInvalidPatch() throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(null, null);
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.CREATED, null);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidStart", expectedExceptions = IllegalStateException.class)
    public void testIllegalStageUpdatesInvalidStart(
        TaskState.TaskStage startStage,
        ProvisionHostWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        ProvisionHostWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "illegalStageUpdatesInvalidStart")
    public Object[][] getIllegalStageUpdatesInvalidStart() {

      return new Object[][]{
          {TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT},
      };
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the patch state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      ProvisionHostWorkflowService.State startState = buildValidStartState(null, null);
      host.startServiceSynchronously(service, startState);

      ProvisionHostWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          ProvisionHostWorkflowService.TaskState.SubStage.DEPLOY_AGENT);

      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      if (declaredField.getType() == Boolean.class) {
        declaredField.set(patchState, Boolean.FALSE);
      } else if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else if (declaredField.getType() == List.class) {
        declaredField.set(patchState, new ArrayList());
      } else if (declaredField.getType() == Set.class) {
        declaredField.set(patchState, new HashSet<>());
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionHostWorkflowService.State.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the iso creation task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");

    private DeployerContext deployerContext;
    private HostClient hostClient;
    private HostClientFactory hostClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private ProvisionHostWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      hostClientFactory = mock(HostClientFactory.class);
    }

    private void createTestEnvironment(int hostCount) throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(hostCount)
          .build();

      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .getConfigResultCode(GetConfigResultCode.OK)
          .hostConfig(new HostConfig())
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      FileUtils.deleteDirectory(scriptLogDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "HostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {
      MockHelper.mockCreateScriptFile(deployerContext, DeployAgentTaskService.SCRIPT_NAME, true);
      MockHelper.mockProvisionAgent(hostClientFactory, true);
      createTestEnvironment(hostCount);

      ProvisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              ProvisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    /**
     * This test verifies that the workflow fails when the deploy agent task fails.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "HostCounts")
    public void testEndToEndFailsWhenDeployAgentFails(Integer hostCount) throws Throwable {
      MockHelper.mockCreateScriptFile(deployerContext, DeployAgentTaskService.SCRIPT_NAME, false);
      createTestEnvironment(hostCount);

      ProvisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              ProvisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(DeployAgentTaskService.SCRIPT_NAME +
          " returned 1"));
    }

    @Test(dataProvider = "HostCounts", enabled = false)
    public void testEndToEndFailureWhenProvisionAgentFails(Integer hostCount) throws Throwable {
      MockHelper.mockCreateScriptFile(deployerContext, DeployAgentTaskService.SCRIPT_NAME, true);
      MockHelper.mockProvisionAgent(hostClientFactory, false);
      createTestEnvironment(hostCount);

      ProvisionHostWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionHostWorkflowFactoryService.SELF_LINK,
              startState,
              ProvisionHostWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("SystemErrorException"));
    }

    @DataProvider(name = "HostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }
  }
}
