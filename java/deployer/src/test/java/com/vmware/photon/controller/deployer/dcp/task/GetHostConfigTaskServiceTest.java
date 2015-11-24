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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceHost;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.deployer.dcp.HostConfigTestUtils;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.Network;

import org.apache.thrift.TException;
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
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link GetHostConfigTaskService} class.
 */
public class GetHostConfigTaskServiceTest {

  private TestHost testHost;
  private GetHostConfigTaskService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new GetHostConfigTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new GetHostConfigTaskService();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
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

    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState(stage);
      Operation startOp = testHost.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      GetHostConfigTaskService.State savedState = testHost.getServiceState(
          GetHostConfigTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.hostServiceLink, is("hostServiceLink"));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState();
      Operation startOp = testHost.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      GetHostConfigTaskService.State savedState = testHost.getServiceState(GetHostConfigTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.hostServiceLink, is("hostServiceLink"));
    }

    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState(stage);
      Operation startOp = testHost.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      GetHostConfigTaskService.State savedState = testHost.getServiceState(GetHostConfigTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.hostServiceLink, is("hostServiceLink"));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(GetHostConfigTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new GetHostConfigTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState(startStage);
      testHost.startServiceSynchronously(service, startState);

      GetHostConfigTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = testHost.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      GetHostConfigTaskService.State savedState = testHost.getServiceState(GetHostConfigTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

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

    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      GetHostConfigTaskService.State startState = buildValidStartState(startStage);
      testHost.startServiceSynchronously(service, startState);

      GetHostConfigTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (DcpRuntimeException e) {
      }
    }

    @DataProvider(name = "illegalStageUpdatesInvalidPatch")
    public Object[][] getIllegalStageUpdatesInvalidPatch() {

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

    @Test(expectedExceptions = DcpRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      GetHostConfigTaskService.State startState = buildValidStartState();
      testHost.startServiceSynchronously(service, startState);

      GetHostConfigTaskService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      if (declaredField.getType() == Boolean.class) {
        declaredField.set(patchState, Boolean.FALSE);
      } else if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(GetHostConfigTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the get host config task.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private HostClientFactory hostClientFactory;
    private GetHostConfigTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      startState = buildValidStartState();
      startState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
      }

      machine = null;
      startState = null;
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
          .getConfigResultCode(GetConfigResultCode.OK)
          .hostConfig(HostConfigTestUtils.createHostConfig(2, 2))
          .build();
      doReturn(hostClientMock).when(hostClientFactory).create();

      machine = createTestEnvironment(hostClientFactory, cloudStoreMachine.getServerSet(), 1);
      setUpServiceDocuments();

      GetHostConfigTaskService.State finalState =
          machine.callServiceAndWaitForState(
              GetHostConfigTaskFactoryService.SELF_LINK,
              startState,
              GetHostConfigTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.hostConfig, notNullValue());

      List<Datastore> datastores = finalState.hostConfig.getDatastores();
      List<Network> networks = finalState.hostConfig.getNetworks();
      assertThat(datastores, notNullValue());
      assertThat(networks, notNullValue());

      for (int i = 0; i < 2; ++i) {
        Datastore datastore = datastores.get(i);
        assertThat(datastore.getName(), is(HostConfigTestUtils.getDatastoreFieldValue("name", i)));
        assertThat(datastore.getId(), is(HostConfigTestUtils.getDatastoreFieldValue("id", i)));
        assertThat(datastore.getType(), is(DatastoreType.EXT3));

        Network network = networks.get(i);
        assertThat(network.getId(), is(HostConfigTestUtils.getNetworkFieldValue("id", i)));
      }
    }

    @Test
    public void testEndToEndFailureHostClientThrowsException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigFailure(new TException("Thrift exception during getHostConfig"))
          .build();
      doReturn(hostClientMock).when(hostClientFactory).create();

      machine = createTestEnvironment(hostClientFactory, cloudStoreMachine.getServerSet(), 1);
      setUpServiceDocuments();

      GetHostConfigTaskService.State finalState =
          machine.callServiceAndWaitForState(
              GetHostConfigTaskFactoryService.SELF_LINK,
              startState,
              GetHostConfigTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.hostConfig, nullValue());
      assertThat(finalState.taskState.failure.message, containsString("Thrift exception during getHostConfig"));
    }

    @Test
    public void testEndToEndFailureHostClientReturnsError() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.SYSTEM_ERROR)
          .build();
      doReturn(hostClientMock).when(hostClientFactory).create();

      machine = createTestEnvironment(hostClientFactory, cloudStoreMachine.getServerSet(), 1);
      setUpServiceDocuments();

      GetHostConfigTaskService.State finalState =
          machine.callServiceAndWaitForState(
              GetHostConfigTaskFactoryService.SELF_LINK,
              startState,
              GetHostConfigTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.hostConfig, nullValue());
    }

    private void setUpServiceDocuments() throws Throwable {
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
    }
  }

  private GetHostConfigTaskService.State buildValidStartState() {
    return buildValidStartState(TaskState.TaskStage.CREATED);
  }

  private GetHostConfigTaskService.State buildValidStartState(TaskState.TaskStage stage) {
    GetHostConfigTaskService.State state = new GetHostConfigTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.hostServiceLink = "hostServiceLink";

    if (stage == TaskState.TaskStage.FINISHED) {
      state.hostConfig = HostConfigTestUtils.createHostConfig(2, 2);
    }

    return state;
  }

  private GetHostConfigTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private GetHostConfigTaskService.State buildValidPatchState(TaskState.TaskStage stage) {

    GetHostConfigTaskService.State state = new GetHostConfigTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (stage == TaskState.TaskStage.FINISHED) {
      state.hostConfig = HostConfigTestUtils.createHostConfig(2, 2);
    }

    return state;
  }

  private TestEnvironment createTestEnvironment(
      HostClientFactory hostClientFactory,
      ServerSet cloudServerSet,
      int hostCount) throws Throwable {
    return new TestEnvironment.Builder().hostClientFactory(hostClientFactory).cloudServerSet(cloudServerSet)
        .hostCount(hostCount)
        .build();
  }
}
