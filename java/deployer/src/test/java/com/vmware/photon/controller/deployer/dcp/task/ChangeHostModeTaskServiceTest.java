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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.host.gen.SetHostModeResultCode;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;

/**
 * This class implements tests for the {@link ChangeHostModeTaskService} class.
 */
public class ChangeHostModeTaskServiceTest {

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

    private ChangeHostModeTaskService changeHostModeTaskService;

    @BeforeMethod
    public void setUpTest() {
      changeHostModeTaskService = new ChangeHostModeTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(changeHostModeTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the DeployerContextProvider.
   */
  public class DeployerContextProviderTest {

    private ChangeHostModeTaskService changeHostModeTaskService;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      changeHostModeTaskService = spy(new ChangeHostModeTaskService());
      doReturn(mock(ServiceHost.class)).when(changeHostModeTaskService).getHost();
      HostUtils.getDeployerContext(changeHostModeTaskService);
    }
  }

  /**
   * This class implements tests for the AgentControlClientProvider.
   */
  public class AgentControlClientProviderTest {

    private ChangeHostModeTaskService changeHostModeTaskService;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      changeHostModeTaskService = spy(new ChangeHostModeTaskService());
      doReturn(mock(ServiceHost.class)).when(changeHostModeTaskService).getHost();
      HostUtils.getAgentControlClient(changeHostModeTaskService);
    }
  }

  /**
   * This class implements tests for the HostClientProvider.
   */
  public class HostClientProviderTest {

    private ChangeHostModeTaskService changeHostModeTaskService;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      changeHostModeTaskService = spy(new ChangeHostModeTaskService());
      doReturn(mock(ServiceHost.class)).when(changeHostModeTaskService).getHost();
      HostUtils.getHostClient(changeHostModeTaskService);
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private ChangeHostModeTaskService changeHostModeTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      changeHostModeTaskService = new ChangeHostModeTaskService();
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
      ChangeHostModeTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State serviceState =
          testHost.getServiceState(ChangeHostModeTaskService.State.class);
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
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
      ChangeHostModeTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State serviceState =
          testHost.getServiceState(ChangeHostModeTaskService.State.class);
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
      ChangeHostModeTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State serviceState =
          testHost.getServiceState(ChangeHostModeTaskService.State.class);
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

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      ChangeHostModeTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(changeHostModeTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ChangeHostModeTaskService.State.class, NotNull.class));
    }

    @Test(dataProvider = "PositiveFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateRequiredFieldZeroOrNegative(String fieldName) throws Throwable {
      ChangeHostModeTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, -1);
      testHost.startServiceSynchronously(changeHostModeTaskService, startState);
    }

    @DataProvider(name = "PositiveFieldNames")
    public Object[][] getPositiveFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ChangeHostModeTaskService.State.class, Positive.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private ChangeHostModeTaskService changeHostModeTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      changeHostModeTaskService = new ChangeHostModeTaskService();
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
      ChangeHostModeTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State patchState = ChangeHostModeTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      ChangeHostModeTaskService.State serviceState =
          testHost.getServiceState(ChangeHostModeTaskService.State.class);
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

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      ChangeHostModeTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State patchState = ChangeHostModeTaskService.buildPatch(patchStage, null);

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

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ChangeHostModeTaskService.State startState = buildValidStartState(null);
      Operation startOperation = testHost.startServiceSynchronously(changeHostModeTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ChangeHostModeTaskService.State patchState =
          ChangeHostModeTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ChangeHostModeTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";
    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private ChangeHostModeTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()).getDeployerContext();

      hostClientFactory = mock(HostClientFactory.class);

      startState = buildValidStartState(null);
      startState.controlFlags = null;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .hostClientFactory(hostClientFactory)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

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
      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .setHostModeResultCode(SetHostModeResultCode.OK)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ChangeHostModeTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ChangeHostModeTaskFactoryService.SELF_LINK,
              startState,
              ChangeHostModeTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureChangeHostModeCallThrowsRpcException() throws Throwable {

      HostClient hostClientMock = mock(HostClient.class);

      doThrow(new RpcException("Thrift exception during change host mode call")).when(hostClientMock)
          .setHostMode(any(HostMode.class), any(AsyncMethodCallback.class));

      doReturn(hostClientMock).when(hostClientFactory).create();

      ChangeHostModeTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ChangeHostModeTaskFactoryService.SELF_LINK,
              startState,
              ChangeHostModeTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception during change host mode call"));
    }

    @Test(dataProvider = "ChangeHostModeFailureResultCodes")
    public void testEndToEndFailureChangeHostModeCallReturnsFailure(
        SetHostModeResultCode setHostModeResultCode)
        throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .setHostModeResultCode(setHostModeResultCode)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ChangeHostModeTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ChangeHostModeTaskFactoryService.SELF_LINK,
              startState,
              ChangeHostModeTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "ChangeHostModeFailureResultCodes")
    public Object[][] getChangeHostModeFailureResultCodes() {
      return new Object[][]{
          {SetHostModeResultCode.SYSTEM_ERROR}
      };
    }

    @Test
    public void testEndToEndFailureChangeHostModeResultThrowsTException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .setHostModeFailure(new TException("Thrift exception while getting Change Host Mode result"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ChangeHostModeTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ChangeHostModeTaskFactoryService.SELF_LINK,
              startState,
              ChangeHostModeTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception while getting Change Host Mode result"));
    }
  }

  private ChangeHostModeTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    ChangeHostModeTaskService.State startState = new ChangeHostModeTaskService.State();
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.hostMode = HostMode.ENTERING_MAINTENANCE;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }
}
