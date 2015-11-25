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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.AgentStatusCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * This class implements tests for the {@link ProvisionAgentTaskService} class.
 */
public class ProvisionAgentTaskServiceTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private ProvisionAgentTaskService provisionAgentTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionAgentTaskService = new ProvisionAgentTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(provisionAgentTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private ProvisionAgentTaskService provisionAgentTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionAgentTaskService = new ProvisionAgentTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testStartStateValid(@Nullable TaskState.TaskStage startStage) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
      assertThat(serviceState.chairmanServerList, is(Collections.singleton("localhost:13000")));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "StartStagesWhichReturnStarted")
    public void testStartStageReturnsStarted(@Nullable TaskState.TaskStage startStage) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "StartStagesWhichReturnStarted")
    public Object[][] getStartStagesWhichReturnStarted() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(startStage));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "OptionalFieldNames")
    public void testOptionalFieldValuesPersisted(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, getDefaultValue(declaredField));
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(getDefaultValue(declaredField)));
    }

    @DataProvider(name = "OptionalFieldNames")
    public Object[][] getOptionalFieldNames() {
      return new Object[][]{
          {"agentPollDelay"},
          {"maximumPollCount"},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(provisionAgentTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionAgentTaskService.State.class, NotNull.class));
    }

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStartStateRequiredFieldZeroOrNegative(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, -1);
      testHost.startServiceSynchronously(provisionAgentTaskService, startState);
    }

    @DataProvider(name = "PositiveFieldNames")
    public Object[][] getPositiveFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionAgentTaskService.State.class, Positive.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private ProvisionAgentTaskService provisionAgentTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      provisionAgentTaskService = new ProvisionAgentTaskService();
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
    public void testValidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State patchState = ProvisionAgentTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State patchState = ProvisionAgentTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null);
      Operation startOperation = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ProvisionAgentTaskService.State patchState =
          ProvisionAgentTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, getDefaultValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ProvisionAgentTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";

    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private ProvisionAgentTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {

      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      hostClientFactory = mock(HostClientFactory.class);

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      startState = buildValidStartState(null);
      startState.controlFlags = null;
      startState.agentPollDelay = 10;
      startState.maximumPollCount = 3;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (cloudStoreMachine != null) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndSuccessWhenAgentRestartingThenReady() throws Throwable {
      HostClientMock agentRestartigHostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      HostClientMock agentReadyHostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(hostClientFactory.create())
          .thenReturn(agentRestartigHostClientMock)
          .thenReturn(agentRestartigHostClientMock)
          .thenReturn(agentReadyHostClientMock);

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureGetAgentStatusReturnsFailures() throws Throwable {

      HostClientMock provisionSuccessHostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      HostClientMock getAgentStatusTExceptionHostClientMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during getAgentStatus call"))
          .build();

      HostClientMock getAgentStatusFailureHostClientMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.IMAGE_DATASTORE_NOT_CONNECTED)
          .build();


      when(hostClientFactory.create())
          .thenReturn(provisionSuccessHostClientMock)
          .thenReturn(getAgentStatusTExceptionHostClientMock)
          .thenReturn(getAgentStatusFailureHostClientMock);

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailureProvisionCallThrowsRpcException() throws Throwable {

      HostClient hostClientMock = mock(HostClient.class);

      doThrow(new RpcException("Thrift exception during provision call")).when(hostClientMock)
          .provision(anyString(),
              anyListOf(String.class),
              anySetOf(String.class),
              anyBoolean(),
              anyListOf(String.class),
              anyString(),
              anyInt(),
              anyMapOf(String.class, String.class),
              anyListOf(String.class),
              anyDouble(),
              anyString(),
              anyString(),
              anyBoolean(),
              anyString(),
              anyString(),
              any(AsyncMethodCallback.class));

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception during provision call"));
    }

    @Test(dataProvider = "ProvisionFailureResultCodes")
    public void testEndToEndFailureProvisionCallReturnsFailure(ProvisionResultCode provisionResultCode)
        throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(provisionResultCode)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "ProvisionFailureResultCodes")
    public Object[][] getProvisionFailureResultCodes() {
      return new Object[][]{
          {ProvisionResultCode.INVALID_CONFIG},
          {ProvisionResultCode.INVALID_STATE},
          {ProvisionResultCode.SYSTEM_ERROR},
      };
    }

    @Test
    public void testEndToEndFailureProvisionResultThrowsTException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionFailure(new TException("Thrift exception while getting ProvisionResponse result"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception while getting ProvisionResponse result"));
    }

    @Test
    public void testEndToEndFailureGetAgentStatusCallThrowsRpcException() throws Throwable {

      HostClientMock provisionHostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      HostClient getAgentStatusFailureHostClientMock = mock(HostClient.class);

      doThrow(new RpcException("Thrift exception during getAgentStatus call")).when(getAgentStatusFailureHostClientMock)
          .getAgentStatus(any(AsyncMethodCallback.class));

      when(hostClientFactory.create())
          .thenReturn(provisionHostClientMock)
          .thenReturn(getAgentStatusFailureHostClientMock);

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception during getAgentStatus call"));
    }

    @Test(dataProvider = "GetAgentStatusFailureResultCodes")
    public void testEndToEndFailureGetHostConfigCallReturnsFailure(AgentStatusCode agentStatusCode)
        throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .agentStatusCode(agentStatusCode)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "GetAgentStatusFailureResultCodes")
    public Object[][] getGetAgentStatusFailureResultCodes() {
      return new Object[][]{
          {AgentStatusCode.IMAGE_DATASTORE_NOT_CONNECTED},
      };
    }

    @Test
    public void testEndToEndFailureGetAgentStatusResultThrowsTException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .getAgentStatusFailure(new TException("Thrift exception while getting AgentStatusResponse result"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception while getting AgentStatusResponse result"));
    }
  }

  private ProvisionAgentTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    ProvisionAgentTaskService.State startState = new ProvisionAgentTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.chairmanServerList = new HashSet<>(Collections.singleton("localhost:13000"));
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }

  private Object getDefaultValue(Field declaredField) throws Throwable {
    if (declaredField.getType() == Integer.class) {
      return new Integer(1);
    } else if (declaredField.getType() == Boolean.class) {
      return Boolean.FALSE;
    } else if (declaredField.getType() == Set.class) {
      return new HashSet<>();
    }

    return declaredField.getType().newInstance();
  }
}
