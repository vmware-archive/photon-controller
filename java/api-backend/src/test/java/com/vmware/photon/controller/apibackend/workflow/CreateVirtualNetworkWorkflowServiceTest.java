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

package com.vmware.photon.controller.apibackend.workflow;

import com.vmware.photon.controller.apibackend.helpers.ReflectionUtils;
import com.vmware.photon.controller.apibackend.helpers.TestEnvironment;
import com.vmware.photon.controller.apibackend.helpers.TestHelper;
import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableList;
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
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link CreateVirtualNetworkWorkflowService} class.
 */
public class CreateVirtualNetworkWorkflowServiceTest {

  public static final Integer COUNT_ONE = 1;

  private CreateVirtualNetworkWorkflowService createVirtualNetworkWorkflowService;
  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object to create a new CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildValidStartState() {
    return buildValidStartState(TaskState.TaskStage.CREATED, null);
  }

  /**
   * This method creates a new State object to create a new CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildValidStartState(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage) {
    CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
    startState.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    startState.taskState.stage = stage;
    startState.taskState.subStage = subStage;
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.name = "name";
    startState.description = "desc";

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * CreateVirtualNetworkTaskService instance.
   */
  private CreateVirtualNetworkWorkflowDocument buildPatch(
      TaskState.TaskStage stage,
      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage) {
    CreateVirtualNetworkWorkflowDocument state = new CreateVirtualNetworkWorkflowDocument();
    state.taskState = new CreateVirtualNetworkWorkflowDocument.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    return state;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      createVirtualNetworkWorkflowService = new CreateVirtualNetworkWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      createVirtualNetworkWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(createVirtualNetworkWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * handleCreate tests.
   */
  public class HandleCreateTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      startState = buildValidStartState();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    @Test
    public void testHandleCreateSuccessfullyCreateDocuments() throws Throwable {
      testEnvironment = TestEnvironment.create(COUNT_ONE);
      startState.controlFlags = 0;

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.documentSelfLink, is(notNullValue()));
      assertThat(finalState.virtualNetworkServiceState, is(notNullValue()));
      assertThat(finalState.virtualNetworkTaskServiceState, is(notNullValue()));

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(COUNT_ONE));

      kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(TaskService.State.class));

      querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      queryTask = QueryTask.create(querySpecification).setDirect(true);
      queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(COUNT_ONE));
    }

    @Test
    public void testHandleCreateWithMinimumState() throws Throwable {
      testEnvironment = TestEnvironment.create(COUNT_ONE);
      startState = new CreateVirtualNetworkWorkflowDocument();
      startState.name = "network";

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.documentSelfLink, is(notNullValue()));
      assertThat(finalState.virtualNetworkServiceState, is(notNullValue()));
      assertThat(finalState.virtualNetworkTaskServiceState, is(notNullValue()));
    }

    @Test
    public void testHandleCreateWithMissingName() throws Throwable {
      testEnvironment = TestEnvironment.create(COUNT_ONE);
      startState.name = null;

      try {
        testEnvironment.callServiceAndWaitForState(
            CreateVirtualNetworkWorkflowService.FACTORY_LINK,
            startState,
            CreateVirtualNetworkWorkflowDocument.class,
            (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());
        fail("service create did not fail when 'name' was null");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), containsString("name cannot be null"));
      }
    }
  }

  /**
   * Tests for handleStart.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      createVirtualNetworkWorkflowService = new CreateVirtualNetworkWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      createVirtualNetworkWorkflowService = null;
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @Test
    public void testHandleStartValidState() throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState(
          CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED, null);

      Operation startOp = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVirtualNetworkWorkflowDocument  savedState = testHost.getServiceState(
          CreateVirtualNetworkWorkflowDocument.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @Test
    public void testHandleStartValidMinimumState() throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = new CreateVirtualNetworkWorkflowDocument();
      startState.name = "network";

      Operation startOp = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateVirtualNetworkWorkflowDocument  savedState = testHost.getServiceState(
          CreateVirtualNetworkWorkflowDocument.class);
      assertThat(savedState.taskState, notNullValue());
    }

    @Test(dataProvider = "InValidStartStage")
    public void testHandleStartInValidStartStage(final CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage stage,
                                                 final CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState(stage, subStage);

      try {
        testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
        fail("service start did not fail when 'stage' was invalid");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), startsWith("Expected state is CREATED. Cannot proceed in"));
      }
    }

    @DataProvider(name = "InValidStartStage")
    public Object[][] getInValidStartStageTestData() {
      return new Object[][]{
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED, null},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED, null}
      };
    }

    @Test(dataProvider = "InValidStartSubStage")
    public void testHandleStartInValidStartSubStage(
          final CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage stage,
          final CreateVirtualNetworkWorkflowDocument.TaskState.SubStage subStage)
        throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState(stage, subStage);

      try {
        testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
        fail("service start did not fail when 'SubStage' was invalid");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), startsWith("Invalid stage update."));
      }
    }

    @DataProvider(name = "InValidStartSubStage")
    public Object[][] getInValidStartSubStageTestData() {
      return new Object[][]{
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CREATED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.STARTED, null},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FINISHED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.FAILED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER},

          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {CreateVirtualNetworkWorkflowDocument.TaskState.TaskStage.CANCELLED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER}
      };
    }

    @Test
    public void testHandleStartWithMissingName() throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState();
      startState.name = null;

      try {
        testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
        fail("service start did not fail when 'name' was null");
      } catch (XenonRuntimeException ex) {
        assertThat(ex.getMessage(), containsString("name cannot be null"));
      }
    }
  }


  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @BeforeMethod
    public void setUpTest() {
      createVirtualNetworkWorkflowService = new CreateVirtualNetworkWorkflowService();
      testHost.setDefaultServiceUri(UUID.randomUUID().toString());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testHost.deleteServiceSynchronously();
    }

    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage patchSubStage
        ) throws Throwable {

      // start service in desired state
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      // send patch
      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(patchStage, patchSubStage);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost,
              startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class).documentSelfLink,
              null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));

      // check results
      CreateVirtualNetworkWorkflowDocument savedState =
          testHost.getServiceState(CreateVirtualNetworkWorkflowDocument.class);
      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED,
              CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ValidSubStageUpdates")
    public void testValidSubStageUpdates(
        TaskState.TaskStage secondPatchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage secondPatchSubStage
    ) throws Throwable {

      // start service in CREATED state
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      String documentUri = startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class).documentSelfLink;

      // Send patch to move the stage from CREATED to STARTED
      // Simulate the action in handleStart() to sendSelfPatch to move the stage of itself
      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, documentUri, null))
          .setBody(patchState);

      Operation patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));

      patchState = buildPatch(secondPatchStage, secondPatchSubStage);
      patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, documentUri, null))
          .setBody(patchState);

      patchResult = testHost.sendRequestAndWait(patchOperation);
      assertThat(patchResult.getStatusCode(), is(200));

      // Evaluate results
      CreateVirtualNetworkWorkflowDocument savedState = testHost.getServiceState(
          CreateVirtualNetworkWorkflowDocument.class);
      assertThat(savedState.taskState.stage, is(secondPatchStage));
      assertThat(savedState.taskState.subStage, is(secondPatchSubStage));
    }

    @DataProvider(name = "ValidSubStageUpdates")
    public Object[][] getValidSubStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER},
          {TaskState.TaskStage.STARTED, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SETUP_LOGICAL_ROUTER},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidSubStageUpdates")
    public void testInvalidStageUpdates(
        TaskState.TaskStage secondPatchStage,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage secondPatchSubStage)
        throws Throwable {

      // start service in CREATED state
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      String documentUri = startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class).documentSelfLink;

      // Move the stage from CREATED to STARTED
      // Simulate the process in handleStart()
      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, documentUri, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOperation);

      // send invalid patch
      patchState = buildPatch(secondPatchStage, secondPatchSubStage);
      patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, documentUri, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "invalidSubStageUpdates")
    public Object[][] getInvalidSubStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "immutableFieldNames")
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      CreateVirtualNetworkWorkflowDocument startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createVirtualNetworkWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.STARTED,
          CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost,
              startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class).documentSelfLink))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "immutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ImmutableList.of("controlFlags"));
    }
  }

  /**
   * End-to-end tests.
   */
  public class EndToEndTest {

    private CreateVirtualNetworkWorkflowDocument startState;
    private TestEnvironment testEnvironment;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      startState = buildValidStartState();
      startState.controlFlags = 0;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      startState = null;
    }

    @Test(dataProvider = "testSuccessParams")
    public void testSuccess(int hostCount) throws Throwable {
      testEnvironment = TestEnvironment.create(hostCount);

      CreateVirtualNetworkWorkflowDocument finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVirtualNetworkWorkflowService.FACTORY_LINK,
              startState,
              CreateVirtualNetworkWorkflowDocument.class,
              (state) -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      TestHelper.assertTaskStateFinished(finalState.taskState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(VirtualNetworkService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(COUNT_ONE));

      kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(TaskService.State.class));

      querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      queryTask = QueryTask.create(querySpecification).setDirect(true);
      queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      assertThat(QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size(), is(COUNT_ONE));
    }

    @DataProvider(name = "testSuccessParams")
    public Object[][] getTestSuccessParams() {
      return new Object[][]{
          {COUNT_ONE},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }
  }
}
