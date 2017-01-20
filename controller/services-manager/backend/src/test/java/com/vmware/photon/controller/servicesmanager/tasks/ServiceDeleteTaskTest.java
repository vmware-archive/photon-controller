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
package com.vmware.photon.controller.servicesmanager.tasks;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.resource.ServiceApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.servicesmanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.servicesmanager.helpers.TestEnvironment;
import com.vmware.photon.controller.servicesmanager.helpers.TestHelper;
import com.vmware.photon.controller.servicesmanager.helpers.TestHost;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServiceDeleteTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import org.hamcrest.Matchers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * This class implements tests for the {@link ServiceDeleteTask} class.
 */
public class ServiceDeleteTaskTest {

  private TestHost host;
  private ServiceDeleteTask taskService;

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  private ServiceDeleteTaskState buildValidStartState(
      TaskState.TaskStage stage, ServiceDeleteTaskState.TaskState.SubStage subStage) throws Throwable {

    ServiceDeleteTaskState state = ReflectionUtils.buildValidStartState(ServiceDeleteTaskState.class);
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    return state;
  }

  private ServiceDeleteTaskState buildValidPatchState(
      TaskState.TaskStage stage, ServiceDeleteTaskState.TaskState.SubStage subStage) {

    ServiceDeleteTaskState patchState = new ServiceDeleteTaskState();
    patchState.taskState = new ServiceDeleteTaskState.TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;

    return patchState;
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      taskService = new ServiceDeleteTask();
    }

    /**
     * Tests that the taskService starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);
      assertThat(taskService.getOptions(), is(expected));
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
      taskService = new ServiceDeleteTask();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test(dataProvider = "validStartStates")
    public void testValidStartState(TaskState.TaskStage stage,
                                    ServiceDeleteTaskState.TaskState.SubStage subStage) throws Throwable {

      ServiceDeleteTaskState startState = buildValidStartState(stage, subStage);

      Operation startOp = host.startServiceSynchronously(taskService, startState);
      assertThat(startOp.getStatusCode(), is(200));
      ServiceDeleteTaskState savedState = host.getServiceState(
          ServiceDeleteTaskState.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.STARTED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.STARTED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.FAILED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "invalidStartStates")
    public void testInvalidStartState(TaskState.TaskStage stage,
                                      ServiceDeleteTaskState.TaskState.SubStage subStage) throws Throwable {

      ServiceDeleteTaskState startState = buildValidStartState(stage, subStage);
      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "invalidStartStates")
    public Object[][] getInvalidStartStates() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED,
              ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.CREATED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.CREATED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},

          {TaskState.TaskStage.STARTED, null},

          {TaskState.TaskStage.FINISHED,
              ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FINISHED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.FINISHED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},

          {TaskState.TaskStage.FAILED,
              ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FAILED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.FAILED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},

          {TaskState.TaskStage.CANCELLED,
              ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.CANCELLED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.CANCELLED,
              ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      ServiceDeleteTaskState startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          ServiceDeleteTaskState.class, NotNull.class);
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
      taskService = new ServiceDeleteTask();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a taskService instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage,
                                      ServiceDeleteTaskState.TaskState.SubStage startSubStage,
                                      TaskState.TaskStage patchStage,
                                      ServiceDeleteTaskState.TaskState.SubStage patchSubStage) throws Throwable {

      ServiceDeleteTaskState startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      ServiceDeleteTaskState patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));
      ServiceDeleteTaskState savedState = host.getServiceState(ServiceDeleteTaskState.class);

      assertThat(savedState.taskState.stage, is(patchStage));
      assertThat(savedState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = {XenonRuntimeException.class, NullPointerException.class},
        dataProvider = "invalidSubStageUpdates")
    public void testInvalidSubStageUpdates(TaskState.TaskStage startStage,
                                           ServiceDeleteTaskState.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           ServiceDeleteTaskState.TaskState.SubStage patchSubStage)
        throws Throwable {

      ServiceDeleteTaskState startState = buildValidStartState(startStage, startSubStage);
      host.startServiceSynchronously(taskService, startState);

      ServiceDeleteTaskState patchState = buildValidPatchState(patchStage, patchSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "invalidSubStageUpdates")
    public Object[][] getInvalidSubStageUpdates()
        throws Throwable {

      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},

          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_VMS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, ServiceDeleteTaskState.TaskState.SubStage.DELETE_SERVICE_DOCUMENT},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "immutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      ServiceDeleteTaskState startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Operation startOperation = host.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      ServiceDeleteTaskState patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          ServiceDeleteTaskState.TaskState.SubStage.UPDATE_SERVICE_DOCUMENT);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI))
          .setBody(patchState);

      host.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "immutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ServiceDeleteTaskState.class, Immutable.class));
    }
  }

  /**
   * End-to-end tests for the Kubernetes service delete task.
   */
  public class EndToEndTest {

    private ApiClient apiClient;
    private ServiceApi serviceApi;
    private VmApi vmApi;
    private Task taskReturnedByStopVm;
    private Task taskReturnedByDeleteVm;

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private ServiceDeleteTaskState startState;

    @BeforeClass
    public void setUpClass() throws Throwable {

      apiClient = mock(ApiClient.class);
      serviceApi = mock(ServiceApi.class);
      vmApi = mock(VmApi.class);
      doReturn(serviceApi).when(apiClient).getServiceApi();
      doReturn(vmApi).when(apiClient).getVmApi();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      machine = new TestEnvironment.Builder()
          .apiClient(apiClient)
          .cloudStoreServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();

      ServiceState.State serviceDocument = ReflectionUtils.buildValidStartState(ServiceState.State.class);
      serviceDocument.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
      serviceDocument = cloudStoreMachine.callServiceSynchronously(
          ServiceStateFactory.SELF_LINK, serviceDocument, ServiceState.State.class);

      taskReturnedByStopVm = new Task();
      taskReturnedByStopVm.setId("startVmTaskId");
      taskReturnedByStopVm.setState("COMPLETED");

      taskReturnedByDeleteVm = new Task();
      taskReturnedByDeleteVm.setId("startVmTaskId");
      taskReturnedByDeleteVm.setState("COMPLETED");

      startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      startState.serviceId = ServiceUtils.getIDFromDocumentSelfLink(serviceDocument.documentSelfLink);
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

    @Test
    public void testEndToEndSuccess() throws Throwable {
      mockGetServiceVms(3, true);
      mockDeleteServiceVms(true);

      ServiceDeleteTaskState savedState = machine.callServiceAndWaitForState(
          ServiceDeleteTaskFactory.SELF_LINK,
          startState,
          ServiceDeleteTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      TestHelper.assertTaskStateFinished(savedState.taskState);
      assertThat(isServiceDeleted(startState.serviceId), is(true));
      assertThat(isTombstoneCreated(startState.serviceId), is(true));
    }

    @Test
    public void testEndToEndSuccessWithZeroVms() throws Throwable {

      mockGetServiceVms(0, true);
      mockDeleteServiceVms(true);

      ServiceDeleteTaskState savedState = machine.callServiceAndWaitForState(
          ServiceDeleteTaskFactory.SELF_LINK,
          startState,
          ServiceDeleteTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      TestHelper.assertTaskStateFinished(savedState.taskState);
      assertThat(isServiceDeleted(startState.serviceId), is(true));
    }

    public void testDeleteServiceForInvalidServiceId() throws Throwable {

      mockGetServiceVms(0, true);
      mockDeleteServiceVms(true);

      startState.serviceId = UUID.randomUUID().toString();
      ServiceDeleteTaskState savedState = machine.callServiceAndWaitForState(
          ServiceDeleteTaskFactory.SELF_LINK,
          startState,
          ServiceDeleteTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal(),
          60000,
          3
      );

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailureGetServiceVmsFails() throws Throwable {

      mockGetServiceVms(3, false);
      mockDeleteServiceVms(true);

      ServiceDeleteTaskState savedState = machine.callServiceAndWaitForState(
          ServiceDeleteTaskFactory.SELF_LINK,
          startState,
          ServiceDeleteTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal()
      );

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(savedState.taskState.failure.message, Matchers.containsString("get service vms failed"));

      ServiceState.State document = cloudStoreMachine.getServiceState(
          ServiceStateFactory.SELF_LINK + "/" + startState.serviceId, ServiceState.State.class);
      assertThat(document.serviceState, is(com.vmware.photon.controller.api.model.ServiceState.PENDING_DELETE));
    }

    @Test
    public void testEndToEndFailureDeleteServiceVmsFails() throws Throwable {

      mockGetServiceVms(3, true);
      mockDeleteServiceVms(false);

      ServiceDeleteTaskState savedState = machine.callServiceAndWaitForState(
          ServiceDeleteTaskFactory.SELF_LINK,
          startState,
          ServiceDeleteTaskState.class,
          state -> TaskState.TaskStage.STARTED.ordinal() < state.taskState.stage.ordinal());

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.FAILED));

      ServiceState.State document = cloudStoreMachine.getServiceState(
          ServiceStateFactory.SELF_LINK + "/" + startState.serviceId, ServiceState.State.class);
      assertThat(document.serviceState, is(com.vmware.photon.controller.api.model.ServiceState.PENDING_DELETE));
    }

    private boolean isServiceDeleted(String serviceId) throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ServiceState.State.class));

      QueryTask.Query idClause = new QueryTask.Query()
          .setTermPropertyName(ServiceState.State.FIELD_NAME_SELF_LINK)
          .setTermMatchValue(ServiceStateFactory.SELF_LINK + "/" + serviceId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(idClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      return (QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size() == 0);
    }

    private boolean isTombstoneCreated(String serviceId) throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(TombstoneService.State.class));

      QueryTask.Query serviceIdClause = new QueryTask.Query()
          .setTermPropertyName("entityId")
          .setTermMatchValue(serviceId);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(serviceIdClause);
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStoreMachine.sendBroadcastQueryAndWait(queryTask);
      return (QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse).size() == 1);
    }

    private void mockGetServiceVms(int nodeCount, boolean isSuccess) throws Throwable {

      if (isSuccess) {
        final List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < nodeCount; ++i) {
          Vm vm = new Vm();
          vm.setId(String.format("vm-%d", i));
          vmList.add(vm);
        }

        doAnswer(invocation -> {
          ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(new ResourceList<>(vmList));
          return null;
        }).when(serviceApi).getVmsInServiceAsync(any(String.class), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("get service vms failed"))
            .when(serviceApi).getVmsInServiceAsync(any(String.class), any(FutureCallback.class));
      }
    }

    private void mockDeleteServiceVms(boolean isSuccess) throws Throwable {
      if (isSuccess) {
        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByStopVm);
          return null;
        }).when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));

        doAnswer(invocation -> {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByDeleteVm);
          return null;
        }).when(vmApi).deleteAsync(anyString(), any(FutureCallback.class));
      } else {
        doThrow(new RuntimeException("stop vm failed"))
            .when(vmApi).performStopOperationAsync(anyString(), any(FutureCallback.class));
      }
    }
  }
}
