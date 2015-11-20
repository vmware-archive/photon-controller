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
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.mock.HostClientMock;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.photon.controller.host.gen.GetConfigResultCode;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;

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
import static org.mockito.Mockito.doReturn;
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
public class UpdateHostDatastoresServiceTest {

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

    private UpdateHostDatastoresTaskService taskService;

    @BeforeMethod
    public void setUpTest() {
      taskService = new UpdateHostDatastoresTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(taskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private UpdateHostDatastoresTaskService taskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new UpdateHostDatastoresTaskService();
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
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State serviceState
        = testHost.getServiceState(UpdateHostDatastoresTaskService.State.class);
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
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State serviceState
        = testHost.getServiceState(UpdateHostDatastoresTaskService.State.class);
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
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State serviceState
        = testHost.getServiceState(UpdateHostDatastoresTaskService.State.class);
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

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = IllegalStateException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(taskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              UpdateHostDatastoresTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private UpdateHostDatastoresTaskService taskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      taskService = new UpdateHostDatastoresTaskService();
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

    @Test(dataProvider = "ValidStageTransitions")
    public void testValidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State patchState = UpdateHostDatastoresTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State serviceState
        = testHost.getServiceState(UpdateHostDatastoresTaskService.State.class);
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

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = IllegalStateException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State patchState = UpdateHostDatastoresTaskService.buildPatch(patchStage, null);

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

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = IllegalStateException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      UpdateHostDatastoresTaskService.State startState = buildValidStartState(null);
      Operation startOperation = testHost.startServiceSynchronously(taskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      UpdateHostDatastoresTaskService.State patchState =
          UpdateHostDatastoresTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

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
              UpdateHostDatastoresTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";

    private DeployerContext deployerContext;
    private HostClientFactory hostClientFactory;
    private UpdateHostDatastoresTaskService.State startState;
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
      if (cloudStoreMachine != null) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      HostConfig hostConfig = new HostConfig();
      hostConfig.setCpu_count(2);
      hostConfig.setMemory_mb(4096);
      Datastore imageDatastore = new Datastore();
      imageDatastore.setId("imageDatastore");
      imageDatastore.setType(DatastoreType.SHARED_VMFS);
      imageDatastore.setName("imageDatastore");
      Datastore datastore = new Datastore();
      datastore.setId("datastore");
      datastore.setName("datastore");
      datastore.setType(DatastoreType.LOCAL_VMFS);
      hostConfig.addToDatastores(imageDatastore);
      hostConfig.addToDatastores(datastore);
      hostConfig.addToImage_datastore_ids(imageDatastore.getId());

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .getConfigResultCode(GetConfigResultCode.OK)
          .hostConfig(hostConfig)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      UpdateHostDatastoresTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UpdateHostDatastoresTaskFactoryService.SELF_LINK,
              startState,
              UpdateHostDatastoresTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      DatastoreService.State imageDatastoreService = cloudStoreMachine.getServiceState(DatastoreServiceFactory.SELF_LINK + "/" + imageDatastore.getId(), DatastoreService.State.class);
      assertThat(imageDatastoreService.isImageDatastore, is(true));
      assertThat(imageDatastoreService.name, is(imageDatastore.getName()));
      assertThat(imageDatastoreService.id, is(imageDatastore.getId()));
      DatastoreService.State datastoreService = cloudStoreMachine.getServiceState(DatastoreServiceFactory.SELF_LINK + "/" + datastore.getId(), DatastoreService.State.class);
      assertThat(datastoreService.isImageDatastore, is(false));
      assertThat(datastoreService.name, is(datastore.getName()));
      assertThat(datastoreService.id, is(datastore.getId()));

      HostService.State host = cloudStoreMachine.getServiceState(startState.hostServiceLink, HostService.State.class);
      assertThat(host.reportedDatastores.size(), is(1));
      assertThat(host.reportedDatastores.iterator().next(), is(datastoreService.name));
      assertThat(host.reportedImageDatastores.size(), is(1));
      assertThat(host.reportedImageDatastores.iterator().next(), is(imageDatastoreService.name));
      assertThat(host.datastoreServiceLinks, notNullValue());
      assertThat(host.datastoreServiceLinks.size(), is(2));
      assertThat(host.datastoreServiceLinks.get(datastore.getName()), is(datastoreService.documentSelfLink));
      assertThat(host.datastoreServiceLinks.get(imageDatastore.getName()), is(imageDatastoreService.documentSelfLink));
    }

    @Test
    public void testEndToEndFailureGetConfigReturnsFailures() throws Throwable {

      HostClientMock provisionSuccessHostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .build();

      HostClientMock getHostConfigTExceptionHostClientMock = new HostClientMock.Builder()
          .getConfigFailure(new TException("Thrift exception during getHostConfig call"))
          .build();

      HostClientMock getHostConfigFailureHostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.SYSTEM_ERROR)
          .build();

      HostClientMock getHostConfigSuccessHostClientMock = new HostClientMock.Builder()
          .getConfigResultCode(GetConfigResultCode.OK)
          .build();

      when(hostClientFactory.create())
          .thenReturn(provisionSuccessHostClientMock)
          .thenReturn(getHostConfigTExceptionHostClientMock)
          .thenReturn(getHostConfigFailureHostClientMock)
          .thenReturn(getHostConfigSuccessHostClientMock);

      UpdateHostDatastoresTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UpdateHostDatastoresTaskFactoryService.SELF_LINK,
              startState,
              UpdateHostDatastoresTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "GetConfigFailureResultCodes")
    public void testEndToEndFailureGetHostConfigCallReturnsFailure(GetConfigResultCode getConfigResultCode)
        throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .getConfigResultCode(getConfigResultCode)
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      UpdateHostDatastoresTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UpdateHostDatastoresTaskFactoryService.SELF_LINK,
              startState,
              UpdateHostDatastoresTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "GetConfigFailureResultCodes")
    public Object[][] getGetConfigFailureResultCodes() {
      return new Object[][]{
          {GetConfigResultCode.SYSTEM_ERROR},
      };
    }

    @Test
    public void testEndToEndFailureGetHostConfigResultThrowsTException() throws Throwable {

      HostClientMock hostClientMock = new HostClientMock.Builder()
          .provisionResultCode(ProvisionResultCode.OK)
          .getConfigFailure(new TException("Thrift exception while getting GetHostConfigResponse result"))
          .build();

      doReturn(hostClientMock).when(hostClientFactory).create();

      UpdateHostDatastoresTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              UpdateHostDatastoresTaskFactoryService.SELF_LINK,
              startState,
              UpdateHostDatastoresTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString(
          "Thrift exception while getting GetHostConfigResponse result"));
    }
  }

  private UpdateHostDatastoresTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    UpdateHostDatastoresTaskService.State startState = new UpdateHostDatastoresTaskService.State();
    startState.hostServiceLink = "HOST_SERVICE_LINK";
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
