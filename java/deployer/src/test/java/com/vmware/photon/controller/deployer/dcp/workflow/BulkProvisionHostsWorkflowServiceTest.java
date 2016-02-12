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

import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.mock.AgentControlClientMock;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionHostTaskService;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.AgentStatusCode;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.collect.ImmutableSet;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link BulkProvisionHostsWorkflowService} class.
 */
public class BulkProvisionHostsWorkflowServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private BulkProvisionHostsWorkflowService bulkProvisionHostsWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      bulkProvisionHostsWorkflowService = new BulkProvisionHostsWorkflowService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(bulkProvisionHostsWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private BulkProvisionHostsWorkflowService bulkProvisionHostsWorkflowService;
    private Boolean serviceCreated = false;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      bulkProvisionHostsWorkflowService = new BulkProvisionHostsWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
        serviceCreated = false;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage,
                                    BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      BulkProvisionHostsWorkflowService.State serviceState =
          testHost.getServiceState(BulkProvisionHostsWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.querySpecification, notNullValue());
      assertThat(serviceState.chairmanServerList, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage startStage,
                                           BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      BulkProvisionHostsWorkflowService.State serviceState =
          testHost.getServiceState(BulkProvisionHostsWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(TaskState.TaskStage startStage,
                                       BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      BulkProvisionHostsWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      startState.controlFlags = null;
      startService(startState);

      BulkProvisionHostsWorkflowService.State serviceState =
          testHost.getServiceState(BulkProvisionHostsWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      BulkProvisionHostsWorkflowService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BulkProvisionHostsWorkflowService.State.class, NotNull.class));
    }

    private void startService(BulkProvisionHostsWorkflowService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(bulkProvisionHostsWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * This class implements tests for the handlePatch test.
   */
  public class HandlePatchTest {

    private BulkProvisionHostsWorkflowService bulkProvisionHostsWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      bulkProvisionHostsWorkflowService = new BulkProvisionHostsWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         BulkProvisionHostsWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      BulkProvisionHostsWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(bulkProvisionHostsWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage, patchSubStage));

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      BulkProvisionHostsWorkflowService.State serviceState =
          testHost.getServiceState(BulkProvisionHostsWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           BulkProvisionHostsWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      BulkProvisionHostsWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(bulkProvisionHostsWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage, patchSubStage));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      BulkProvisionHostsWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(bulkProvisionHostsWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      BulkProvisionHostsWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          BulkProvisionHostsWorkflowService.TaskState.SubStage.UPLOAD_VIB);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else if (declaredField.getType() == Set.class) {
        declaredField.set(patchState, new HashSet<>());
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BulkProvisionHostsWorkflowService.State.class, Immutable.class));
    }

    private BulkProvisionHostsWorkflowService.State buildValidPatchState(
        TaskState.TaskStage patchStage,
        BulkProvisionHostsWorkflowService.TaskState.SubStage patchSubStage) {

      BulkProvisionHostsWorkflowService.State patchState = new BulkProvisionHostsWorkflowService.State();
      patchState.taskState = new BulkProvisionHostsWorkflowService.TaskState();
      patchState.taskState.stage = patchStage;
      patchState.taskState.subStage = patchSubStage;

      if (TaskState.TaskStage.STARTED == patchStage) {
        switch (patchSubStage) {
          case UPLOAD_VIB:
            break;
        }
      }

      return patchState;
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File destinationDirectory = new File("/tmp/deployAgent/output");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private DeployerConfig deployerConfig;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private BulkProvisionHostsWorkflowService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private File vibSourceFile;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      FileUtils.deleteDirectory(storageDirectory);
      vibDirectory.mkdirs();
      vibSourceFile = TestHelper.createSourceFile(null, vibDirectory);

      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartState(null, null);
      startState.querySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      destinationDirectory.mkdirs();
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
    }

    private void createTestEnvironment(int hostCount) throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .containersConfig(deployerConfig.getContainersConfig())
          .deployerContext(deployerConfig.getDeployerContext())
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(hostCount)
          .build();

      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .hostConfig(new HostConfig())
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

    }

    private void createHostEntities(int mgmtCount, int cloudCount, int mixedCount) throws Throwable {

      for (int i = 0; i < mgmtCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));
      }

      for (int i = 0; i < cloudCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()));
      }

      for (int i = 0; i < mixedCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, ImmutableSet.of(UsageTag.CLOUD.name(), UsageTag.MGMT.name()));
      }

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      FileUtils.deleteDirectory(destinationDirectory);
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @DataProvider(name = "HostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          // hostCount, mgmtHostCount, cloudHostCount, mixedHostCount
          {new Integer(1), new Integer(3), new Integer(7), new Integer(2)},
          {new Integer(1), new Integer(3), new Integer(0), new Integer(2)},
          {new Integer(1), new Integer(3), new Integer(7), new Integer(0)},
          {new Integer(1), new Integer(1), new Integer(0), new Integer(0)},
          {new Integer(1), new Integer(0), new Integer(7), new Integer(2)},
          {new Integer(1), new Integer(0), new Integer(0), new Integer(2)},
      };
    }

    @Test(dataProvider = "HostCounts")
    public void testEndToEndSuccess(
        Integer hostCount,
        Integer mgmtHostCount,
        Integer cloudHostCout,
        Integer mixedHostCount) throws Throwable {
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      createTestEnvironment(hostCount);
      createHostEntities(mgmtHostCount, cloudHostCout, mixedHostCount);
      startState.querySpecification = null;

      for (String usageTage : Arrays.asList(UsageTag.MGMT.name(), UsageTag.CLOUD.name())) {
        startState.usageTag = usageTage;

        BulkProvisionHostsWorkflowService.State finalState =
            testEnvironment.callServiceAndWaitForState(
                BulkProvisionHostsWorkflowFactoryService.SELF_LINK,
                startState,
                BulkProvisionHostsWorkflowService.State.class,
                (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

        TestHelper.assertTaskStateFinished(finalState.taskState);
      }
    }

    @Test(enabled = false)
    public void testEndToEndFailNoMgmtHost() throws Throwable {
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      MockHelper.mockProvisionAgent(agentControlClientFactory, hostClientFactory, true);
      createTestEnvironment(1);
      createHostEntities(0, 2, 0);

      BulkProvisionHostsWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              BulkProvisionHostsWorkflowFactoryService.SELF_LINK,
              startState,
              BulkProvisionHostsWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }
  }

  private BulkProvisionHostsWorkflowService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable BulkProvisionHostsWorkflowService.TaskState.SubStage startSubStage)
      throws Throwable {

    BulkProvisionHostsWorkflowService.State startState = new BulkProvisionHostsWorkflowService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.usageTag = UsageTag.MGMT.name();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.chairmanServerList = Collections.singleton("IP_ADDRESS:PORT");

    if (null != startStage) {
      startState.taskState = new BulkProvisionHostsWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;

      if (TaskState.TaskStage.STARTED == startStage) {
        switch (startSubStage) {
          case UPLOAD_VIB:
            break;
        }
      }
    }

    return startState;
  }
}
