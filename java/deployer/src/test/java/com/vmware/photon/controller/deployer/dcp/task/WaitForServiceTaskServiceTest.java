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
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

/**
 * Implements tests for {@link WaitForServiceTaskService}.
 */
public class WaitForServiceTaskServiceTest {

  private WaitForServiceTaskService service;
  private TestHost host;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  private WaitForServiceTaskService.State buildValidStartupState(TaskState.TaskStage stage) {
    WaitForServiceTaskService.State state = new WaitForServiceTaskService.State();
    if (null != stage) {
      state.taskState = new TaskState();
      state.taskState.stage = stage;
    }

    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    state.vmServiceLink = "vmServiceLink";
    state.containerType = ContainersConfig.ContainerType.Zookeeper.name();
    state.maxRetries = 1;
    state.taskPollDelay = 10; // 10msec for testing
    return state;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @AfterMethod
    public void tearDownTest() {
      service = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      service = new WaitForServiceTaskService();
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new WaitForServiceTaskService();
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
     * @param taskStage
     * @throws Throwable
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(TaskState.TaskStage taskStage) throws Throwable {

      WaitForServiceTaskService.State startState = buildValidStartupState(taskStage);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      WaitForServiceTaskService.State savedState = host.getServiceState(WaitForServiceTaskService.State.class);

      if (taskStage == TaskState.TaskStage.CREATED || taskStage == TaskState.TaskStage.STARTED) {
        assertThat(savedState.vmServiceLink, notNullValue());
      }
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {null},
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that a service instance which is started in the
     * CREATED state is transitioned to the STARTED state as part of start
     * operation processing.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartStateChanged() throws Throwable {
      WaitForServiceTaskService.State startState = buildValidStartupState(TaskState.TaskStage.CREATED);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      WaitForServiceTaskService.State savedState = host.getServiceState(WaitForServiceTaskService.State.class);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.vmServiceLink, notNullValue());
    }

    /**
     * This test verifies that a service instance which is started in the
     * specified state does not transition to another state when operation
     * processing is enabled.
     *
     * @param stage
     * @throws Throwable
     */
    @Test(dataProvider = "StartStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {
      WaitForServiceTaskService.State startState = buildValidStartupState(stage);
      startState.controlFlags = 0;
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      WaitForServiceTaskService.State savedState = host.getServiceState(WaitForServiceTaskService.State.class);
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "StartStateNotChanged")
    public Object[][] getStartStateNotChanged() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    /**
     * This test verifies that an exception is thrown when a service instance
     * is created with vmId field set to null.
     *
     * @throws Throwable
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      WaitForServiceTaskService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED);
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(WaitForServiceTaskService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      service = new WaitForServiceTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      host.deleteServiceSynchronously();
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(host);
    }

    /**
     * This test verifies that legal state transitions succeed.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage targetStage) throws
        Throwable {

      WaitForServiceTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      WaitForServiceTaskService.State savedState = host.getServiceState(WaitForServiceTaskService.State.class);
      TaskState.TaskStage expectedStartStage = startStage;
      if (startStage == TaskState.TaskStage.CREATED) {
        startStage = TaskState.TaskStage.STARTED;
      }
      assertThat(savedState.taskState.stage, is(startStage));

      WaitForServiceTaskService.State patchState = new WaitForServiceTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = targetStage;
      if (targetStage == TaskState.TaskStage.STARTED) {
        patchState.vmServiceLink = "vmId";
      }

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOperation = host.sendRequestAndWait(patchOperation);
      assertThat(resultOperation.getStatusCode(), is(200));

      savedState = host.getServiceState(WaitForServiceTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          // N.B. Services created in the CREATED state will transition to
          //      STARTED as part of service creation
          // Also, ONLY way to transition to FINISHED state is to go from STARTED to FINISHED. Because we transition
          // to STARTED on creation, we ignore these 2 cases.
          //  {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          //  {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},

          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
      };
    }

    /**
     * This test verifies that exceptions are thrown on illegal state
     * transitions.
     *
     * @param startStage
     * @param targetStage
     * @throws Throwable
     */
    @Test(dataProvider = "InvalidStageUpdates")
    public void testInvalidStageUpdates(TaskState.TaskStage startStage, TaskState.TaskStage targetStage) throws
        Throwable {

      WaitForServiceTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      WaitForServiceTaskService.State patchState = new WaitForServiceTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = targetStage;
      if (targetStage == TaskState.TaskStage.STARTED) {
        patchState.vmServiceLink = "vmServiceLink";
      }

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOperation);
        fail("Transition from " + startStage + " to " + targetStage + " succeeded unexpectedly");
      } catch (IllegalStateException e) {
        // N.B. An assertion can be added here if an error message is added to
        //      the checkState calls in validatePatch.
      }
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},

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

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      WaitForServiceTaskService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED);
      host.startServiceSynchronously(service, startState);

      WaitForServiceTaskService.State patchState = buildValidStartupState(TaskState.TaskStage.FINISHED);
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      if (declaredField.getType() == Boolean.class) {
        declaredField.set(patchState, Boolean.FALSE);
      } else if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(WaitForServiceTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * This class implements end-to-end tests for the task service.
   */
  public class EndToEndTest {

    private final String configFilePath = "/config.yml";

    private DeployerContext deployerContext;

    private TestEnvironment testEnvironment;

    private WaitForServiceTaskService.State startState;

    private HealthCheckHelperFactory healthCheckHelperFactory;

    @BeforeClass
    public void setUpClass() throws Throwable {
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      deployerContext = ConfigBuilder.build(
          DeployerConfig.class,
          getClass().getResource(configFilePath).getPath()
      ).getDeployerContext();

      startState = buildValidStartupState(TaskState.TaskStage.CREATED);
      startState.controlFlags = 0;
    }

    @AfterClass
    public void tearDownClass() {
      deployerContext = null;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .healthCheckerFactory(healthCheckHelperFactory)
          .hostCount(1)
          .build();

      VmService.State vmServiceState = TestHelper.createVmService(testEnvironment);
      startState.vmServiceLink = vmServiceState.documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
      }

      testEnvironment = null;
    }

    @Test
    public void testSuccess() throws Throwable {

      HealthChecker healthChecker = new HealthChecker() {
        @Override
        public boolean isReady() {
          return true;
        }
      };
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
      when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

      when(healthCheckHelperFactory.create(any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).
          thenReturn(healthCheckHelper);

      WaitForServiceTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          WaitForServiceTaskFactoryService.SELF_LINK,
          startState,
          WaitForServiceTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testSuccessAfterRetries() throws Throwable {

      HealthChecker healthChecker = mock(HealthChecker.class);
      when(healthChecker.isReady())
          .thenReturn(false)
          .thenReturn(false)
          .thenReturn(true);

      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
      when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

      when(healthCheckHelperFactory.create(any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).
          thenReturn(healthCheckHelper);

      startState.taskPollDelay = 500;
      startState.maxRetries = 10;

      WaitForServiceTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          WaitForServiceTaskFactoryService.SELF_LINK,
          startState,
          WaitForServiceTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testFailureWhenServiceNotReady() throws Throwable {
      HealthChecker healthChecker = new HealthChecker() {
        @Override
        public boolean isReady() {
          return false;
        }
      };
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);
      when(healthCheckHelper.getHealthChecker()).thenReturn(healthChecker);

      when(healthCheckHelperFactory.create(any(Service.class), any(ContainersConfig.ContainerType.class), anyString())).
          thenReturn(healthCheckHelper);

      startState.maxRetries = 5;
      startState.taskPollDelay = 100; // 100 ms

      WaitForServiceTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          WaitForServiceTaskFactoryService.SELF_LINK,
          startState,
          WaitForServiceTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
