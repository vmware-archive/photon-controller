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
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerContextTest;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.powermock.api.mockito.PowerMockito.mock;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link DeleteVibTaskService} class.
 */
public class DeleteVibTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructors.
   */
  public class InitializationTest {

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);

      DeleteVibTaskService deleteVibTaskService = new DeleteVibTaskService();
      assertThat(deleteVibTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private DeleteVibTaskService deleteVibTaskService;
    private Boolean serviceCreated = false;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      deleteVibTaskService = new DeleteVibTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
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

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      DeleteVibTaskService.State serviceState = testHost.getServiceState(DeleteVibTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(TaskState.TaskStage startStage) throws Throwable {
      DeleteVibTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = null;
      startService(startState);
      DeleteVibTaskService.State serviceState = testHost.getServiceState(DeleteVibTaskService.State.class);
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

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      DeleteVibTaskService.State startState = buildValidStartState(null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeleteVibTaskService.State.class, NotNull.class));
    }

    private void startService(DeleteVibTaskService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(deleteVibTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private DeleteVibTaskService deleteVibTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      deleteVibTaskService = new DeleteVibTaskService();
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

      DeleteVibTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(deleteVibTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      DeleteVibTaskService.State serviceState = testHost.getServiceState(DeleteVibTaskService.State.class);
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

      DeleteVibTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(deleteVibTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

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
      DeleteVibTaskService.State startState = buildValidStartState(null);
      Operation startOperation = testHost.startServiceSynchronously(deleteVibTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteVibTaskService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
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
              DeleteVibTaskService.State.class, Immutable.class));
    }

    private DeleteVibTaskService.State buildValidPatchState(TaskState.TaskStage patchStage) {
      DeleteVibTaskService.State patchState = new DeleteVibTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = patchStage;
      return patchState;
    }
  }

  /**
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private ListeningExecutorService executorService;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private DeployerContext deployerContext;
    private DeleteVibTaskService.State startState;
    private TestEnvironment testEnvironment = null;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine = null;

    @BeforeClass
    public void setUpClass() throws Throwable {
      executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      startState = buildValidStartState(null);
      startState.controlFlags = null;
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      deployerContext = ConfigBuilder.build(DeployerConfig.class,
          DeployerContextTest.class.getResource("/config.yml").getPath()).getDeployerContext();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
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
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      startTestEnvironment();

      DeleteVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVibTaskFactoryService.SELF_LINK,
              startState,
              DeleteVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureDeleteThrowsIOException() throws Throwable {
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, false);
      startTestEnvironment();

      DeleteVibTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              DeleteVibTaskFactoryService.SELF_LINK,
              startState,
              DeleteVibTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void startTestEnvironment() throws Throwable {
      testEnvironment = new TestEnvironment.Builder()
          .listeningExecutorService(executorService)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .deployerContext(deployerContext)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;
    }
  }

  private DeleteVibTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    DeleteVibTaskService.State startState = new DeleteVibTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.vibPath = "VIB_PATH";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }
}
