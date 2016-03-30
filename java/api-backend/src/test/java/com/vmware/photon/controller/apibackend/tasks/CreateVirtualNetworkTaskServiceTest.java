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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkTask;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * This class implements tests for the {@link CreateVirtualNetworkTaskService} class.
 */
public class CreateVirtualNetworkTaskServiceTest {

  private TestHost host;
  private CreateVirtualNetworkTaskService service;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private CreateVirtualNetworkTask buildValidStartState() throws Throwable {
    return buildValidStartState(TaskState.TaskStage.CREATED);
  }

  private CreateVirtualNetworkTask buildValidStartState(TaskState.TaskStage stage) throws Throwable {
    CreateVirtualNetworkTask state = ReflectionUtils.buildValidStartState(CreateVirtualNetworkTask.class);
    state.taskState.stage = stage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private CreateVirtualNetworkTask buildValidPatchState(TaskState.TaskStage stage) {
    CreateVirtualNetworkTask patchState = new CreateVirtualNetworkTask();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    return patchState;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new CreateVirtualNetworkTaskService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      service = new CreateVirtualNetworkTaskService();
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
    public void testValidStartState(TaskState.TaskStage stage) throws Throwable {
      CreateVirtualNetworkTask startState = buildValidStartState(stage);

      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVirtualNetworkTask savedState = host.getServiceState(CreateVirtualNetworkTask.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.FAILED},
      };
    }

    /**
     * This test verifies that a service instance which is started in the CREATED state
     * is transitioned to STARTED state as part of start operation handling.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {

      CreateVirtualNetworkTask startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVirtualNetworkTask savedState = host.getServiceState(CreateVirtualNetworkTask.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    /**
     * This test verifies that a service instance which is started in the final states
     * is not transitioned to another state as part of start operation handling.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      CreateVirtualNetworkTask startState = buildValidStartState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVirtualNetworkTask savedState = host.getServiceState(CreateVirtualNetworkTask.class);
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
     * This test verifies that the service handles the missing of the non-null attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      CreateVirtualNetworkTask startState = buildValidStartState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateVirtualNetworkTask.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new CreateVirtualNetworkTaskService();
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
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param patchStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */

    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage,
                                      TaskState.TaskStage patchStage) throws Throwable {

      CreateVirtualNetworkTask startState = buildValidStartState(startStage);
      host.startServiceSynchronously(service, startState);

      CreateVirtualNetworkTask patchState = buildValidPatchState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      CreateVirtualNetworkTask savedState = host.getServiceState(CreateVirtualNetworkTask.class);

      assertThat(savedState.taskState.stage, is(patchStage));
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

    /**
     * This test verifies that illegal stage transitions fail.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param patchStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(expectedExceptions = {XenonRuntimeException.class},
        dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      CreateVirtualNetworkTask startState = buildValidStartState(startStage);
      host.startServiceSynchronously(service, startState,
          CreateVirtualNetworkTaskService.FACTORY_LINK + TestHost.SERVICE_URI);

      CreateVirtualNetworkTask patchState = buildValidPatchState(patchStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host,
              CreateVirtualNetworkTaskService.FACTORY_LINK + TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "illegalStageUpdatesInvalidPatch")
    public Object[][] getInvalidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
      };
    }

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param fieldName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "immutableAttributeNames")
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      CreateVirtualNetworkTask startState = buildValidStartState(TaskState.TaskStage.CREATED);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVirtualNetworkTask patchState = buildValidPatchState(TaskState.TaskStage.STARTED);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI))
          .setBody(patchState);

      host.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "immutableAttributeNames")
    public Object[][] getImmutableFieldNames() {
      List<String> immutableAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateVirtualNetworkTask.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the create virtual network task.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private CreateVirtualNetworkTask startState;

    @BeforeMethod
    public void setUpTest() throws Throwable {

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      machine = new TestEnvironment.Builder()
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      if (machine != null) {
        machine.stop();
        machine = null;
      }

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }

      startState = null;
    }

    /**
     * This test verifies an successful end-to-end scenario.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {

      startState.name = "name";
      startState.description = "desc";
      startState.routingType = RoutingType.ROUTED;

      CreateVirtualNetworkTask savedState = machine.callServiceAndWaitForState(
          CreateVirtualNetworkTaskService.FACTORY_LINK,
          startState,
          CreateVirtualNetworkTask.class,
          (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(savedState.taskState);

      // Verify that a document has been created
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(1));
      VirtualNetworkService.State serviceState = cloudStoreMachine.getServiceState(documentLinks.iterator().next(),
          VirtualNetworkService.State.class);

      assertThat(serviceState.name, is(startState.name));
      assertThat(serviceState.description, is(startState.description));
      assertThat(serviceState.routingType, is(RoutingType.ROUTED));
    }
  }
}
