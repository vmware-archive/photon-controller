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
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link CreateVmSpecTaskService} class.
 */
public class CreateVmSpecTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private CreateVmSpecTaskService createVmSpecTaskService;

    @BeforeMethod
    public void setUpTest() {
      createVmSpecTaskService = new CreateVmSpecTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(createVmSpecTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateVmSpecTaskService createVmSpecTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createVmSpecTaskService = new CreateVmSpecTaskService();
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

    @Test
    public void testMinimalStartState() throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecTaskService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "StartStagesWhichTransitionToStarted")
    public void testStartStateTransitionsToStarted(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecTaskService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "StartStagesWhichTransitionToStarted")
    public Object[][] getStartStagesWhichTransitionToStarted() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
      };
    }

    @Test(dataProvider = "FinalStartStages")
    public void testFinalStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecTaskService.State.class);

      assertThat(savedState.taskState.stage, is(startStage));
    }

    @DataProvider(name = "FinalStartStages")
    public Object[][] getFinalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String attributeName) throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState();
      startState.getClass().getDeclaredField(attributeName).set(startState, null);
      testHost.startServiceSynchronously(createVmSpecTaskService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateVmSpecTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateVmSpecTaskService createVmSpecTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createVmSpecTaskService = new CreateVmSpecTaskService();
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
      startService(startStage);
      CreateVmSpecTaskService.State patchState = createVmSpecTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateVmSpecTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecTaskService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
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
      startService(startStage);
      CreateVmSpecTaskService.State patchState = createVmSpecTaskService.buildPatch(patchStage, null);

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

    @Test(dataProvider = "fieldNamesWithIllegalValue", expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStateFieldValue(String attributeName) throws Throwable {
      startService(TaskState.TaskStage.CREATED);

      CreateVmSpecTaskService.State patchState =
          createVmSpecTaskService.buildPatch(TaskState.TaskStage.STARTED, null);
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

    @DataProvider(name = "fieldNamesWithIllegalValue")
    public Object[][] getFieldNamesWithIllegalValue() {
      List<String> immutableAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(CreateVmSpecTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }

    private void startService(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * End to end tests for the create management vm task.
   */
  public class EndToEndTest {

    private CreateVmSpecTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      startState = buildValidStartState();
      startState.controlFlags = null;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder().
          cloudServerSet(cloudStoreMachine.getServerSet()).
          hostCount(1).build();
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

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()));

      startState.hostServiceLink = hostServiceState.documentSelfLink;

      CreateVmSpecTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVmSpecTaskFactoryService.SELF_LINK,
              startState,
              CreateVmSpecTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndSuccessVmServiceExists() throws Throwable {

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()));

      TestHelper.createVmService(testEnvironment, hostServiceState);
      startState.hostServiceLink = hostServiceState.documentSelfLink;

      CreateVmSpecTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVmSpecTaskFactoryService.SELF_LINK,
              startState,
              CreateVmSpecTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }
  }

  private CreateVmSpecTaskService.State buildValidStartState() {
    CreateVmSpecTaskService.State startState = new CreateVmSpecTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.hostServiceLink = "HOST_SERVICE_LINK";
    startState.name = "NAME";
    return startState;
  }

  private CreateVmSpecTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    CreateVmSpecTaskService.State startState = buildValidStartState();
    startState.taskState = new TaskState();
    startState.taskState.stage = taskStage;
    return startState;
  }
}
