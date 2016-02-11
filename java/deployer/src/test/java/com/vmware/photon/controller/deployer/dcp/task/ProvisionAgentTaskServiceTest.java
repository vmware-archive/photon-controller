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
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentConfigurationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.mock.AgentControlClientMock;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
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
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;

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
    public void testServiceOptions() {
      assertThat(provisionAgentTaskService.getOptions(), is(EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION)));
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
        // Exceptions are expected in the case where a service was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage taskStage,
                                    ProvisionAgentTaskService.TaskState.SubStage subStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.hostServiceLink, is("HOST_SERVICE_LINK"));
      assertThat(serviceState.chairmanServerList, contains("CHAIRMAN_SERVER_1"));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage taskStage,
                                           ProvisionAgentTaskService.TaskState.SubStage subStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTermainalStartStage(TaskState.TaskStage taskStage,
                                        ProvisionAgentTaskService.TaskState.SubStage subStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(taskStage, subStage);
      startState.controlFlags = null;
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
      assertThat(serviceState.taskState.subStage, is(subStage));
      assertThat(serviceState.controlFlags, is(0));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "OptionalFieldNames")
    public void testOptionalFieldValuePersisted(String fieldName, Object defaultValue) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, getDefaultAttributeFieldValue(declaredField, defaultValue));
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(getDefaultAttributeFieldValue(declaredField, defaultValue)));
    }

    @DataProvider(name = "OptionalFieldNames")
    public Object[][] getOptionalFieldNames() {
      return new Object[][]{
          {"maximumPollCount", new Integer(1)},
          {"pollInterval", new Integer(1)},
          {"pollCount", null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null, null);
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
      if (testHost != null) {
        TestHost.destroy(testHost);
        testHost = null;
      }
    }

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         ProvisionAgentTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         ProvisionAgentTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionAgentTaskService.buildPatch(patchStage, patchSubStage, null));

      op = testHost.sendRequestAndWait(patchOperation);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State serviceState = testHost.getServiceState(ProvisionAgentTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           ProvisionAgentTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           ProvisionAgentTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(ProvisionAgentTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      ProvisionAgentTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(provisionAgentTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      ProvisionAgentTaskService.State patchState = ProvisionAgentTaskService.buildPatch(TaskState.TaskStage.STARTED,
          ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, getDefaultAttributeFieldValue(declaredField, null));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
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
   * This class implements tests for the PROVISION_AGENT sub-stage.
   */
  public class ProvisionAgentTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private ProvisionAgentTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (cloudStoreEnvironment != null) {
        cloudStoreEnvironment.stop();
        cloudStoreEnvironment = null;
      }
    }

    @Test
    public void testProvisionAgentSuccess() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(finalState.taskState.subStage, is(ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT));
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test(dataProvider = "ProvisionFailureCodes")
    public void testProvisionAgentFailureWithResult(ProvisionResultCode resultCode, Class<DcpRuntimeException> clazz)
        throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(resultCode)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "Provisioning the agent on host hostAddress failed with error"));
      assertThat(finalState.taskState.failure.message, containsString(clazz.getName()));
    }

    @DataProvider(name = "ProvisionFailureCodes")
    public Object[][] getProvisionFailureCodes() {
      return new Object[][]{
          {ProvisionResultCode.INVALID_CONFIG, InvalidAgentConfigurationException.class},
          {ProvisionResultCode.INVALID_STATE, InvalidAgentStateException.class},
          {ProvisionResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test
    public void testProvisionAgentFailureWithTException() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionFailure(new TException("Thrift exception during provision call"))
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.PROVISION_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "Provisioning the agent on host hostAddress failed with error"));
      assertThat(finalState.taskState.failure.message, containsString(TException.class.getName()));
    }
  }

  /**
   * This class implements tests for the WAIT_FOR_AGENT sub-stage.
   */
  public class WaitForAgentTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private HostClientFactory hostClientFactory;
    private ProvisionAgentTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.STARTED,
          ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT);
      startState.controlFlags = ControlFlags.CONTROL_FLAG_DISABLE_OPERATION_PROCESSING_ON_STAGE_TRANSITION;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
      startState.maximumPollCount = 3;
      startState.pollInterval = 10;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (cloudStoreEnvironment != null) {
        cloudStoreEnvironment.stop();
        cloudStoreEnvironment = null;
      }
    }

    @Test
    public void testWaitForAgentSuccess() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentSuccessAfterFailures() throws Throwable {

      HostClientMock exceptionMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during getAgentStatus call"))
          .build();

      HostClientMock restartingMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      HostClientMock readyMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(hostClientFactory.create())
          .thenReturn(exceptionMock)
          .thenReturn(restartingMock)
          .thenReturn(readyMock);

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @Test
    public void testWaitForAgentFailureWithResult() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }

    @Test
    public void testWaitForAgentFailureWithTException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during getAgentStatus call"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> state.taskState.subStage != ProvisionAgentTaskService.TaskState.SubStage.WAIT_FOR_AGENT);

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreEnvironment;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private ProvisionAgentTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreEnvironment).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreEnvironment,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
      startState.maximumPollCount = 3;
      startState.pollInterval = 10;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (testEnvironment != null) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (cloudStoreEnvironment != null) {
        cloudStoreEnvironment.stop();
        cloudStoreEnvironment = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      HostClientMock hostClientMock = new HostClientMock.Builder()
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
      assertThat(finalState.taskState.subStage, nullValue());
    }

    @Test
    public void testEndToEndSuccessAfterAgentStatusFailures() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      HostClientMock agentStatusExceptionMock = new HostClientMock.Builder()
          .getAgentStatusFailure(new TException("Thrift exception during getAgentStatus call"))
          .build();

      HostClientMock agentStatusRestartingMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      HostClientMock agentStatusReadyMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.OK)
          .build();

      when(hostClientFactory.create())
          .thenReturn(agentStatusExceptionMock)
          .thenReturn(agentStatusRestartingMock)
          .thenReturn(agentStatusReadyMock);

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
    }

    @Test
    public void testProvisionFailure() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.SYSTEM_ERROR)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "Provisioning the agent on host hostAddress failed with error"));
      assertThat(finalState.taskState.failure.message, containsString(SystemErrorException.class.getName()));
    }

    @Test
    public void testWaitForAgentFailure() throws Throwable {

      AgentControlClientMock agentControlClientMock = new AgentControlClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      doReturn(agentControlClientMock).when(agentControlClientFactory).create();

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .agentStatusCode(AgentStatusCode.RESTARTING)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      ProvisionAgentTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              ProvisionAgentTaskFactoryService.SELF_LINK,
              startState,
              ProvisionAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString(
          "The agent on host hostAddress failed to become ready after provisioning after 3 retries"));
    }
  }

  private Object getDefaultAttributeFieldValue(Field declaredField, Object defaultValue) throws Throwable {
    return (defaultValue != null) ? defaultValue : ReflectionUtils.getDefaultAttributeValue(declaredField);
  }

  private ProvisionAgentTaskService.State buildValidStartState(TaskState.TaskStage taskStage,
                                                               ProvisionAgentTaskService.TaskState.SubStage subStage) {
    ProvisionAgentTaskService.State startState = new ProvisionAgentTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.chairmanServerList = Collections.singleton("CHAIRMAN_SERVER_1");
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (taskStage != null) {
      startState.taskState = new ProvisionAgentTaskService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
