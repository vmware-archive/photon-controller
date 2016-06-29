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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.xenon.util.MiscUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.github.dockerjava.api.DockerException;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.mockito.Matchers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link CreateContainersWorkflowService} class.
 */
public class CreateContainersWorkflowServiceTest {

  /**
   * Dummy function to make IntelliJ think that this is a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    private CreateContainersWorkflowService createContainersWorkflowService;

    @BeforeMethod
    public void setUp() throws Throwable {
      createContainersWorkflowService = new CreateContainersWorkflowService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);
      assertThat(createContainersWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateContainersWorkflowService createContainersWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      createContainersWorkflowService = new CreateContainersWorkflowService();
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
    public void testValidStartState(
        TaskState.TaskStage startStage,
        CreateContainersWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "StartStagesWhichTransitionToStarted")
    public void testStartStateTransitionsToStarted(
        TaskState.TaskStage startStage,
        CreateContainersWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainersWorkflowService.State savedState =
          testHost.getServiceState(CreateContainersWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "StartStagesWhichTransitionToStarted")
    public Object[][] getStartStagesWhichTransitionToStarted() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
      };
    }

    @Test(dataProvider = "FinalStartStages")
    public void testFinalStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainersWorkflowService.State savedState =
          testHost.getServiceState(CreateContainersWorkflowService.State.class);

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

    /**
     * This test verifies that a service instance cannot be started with a start state
     * in which the required field is null.
     *
     * @param fieldName
     * @throws Throwable
     */
    @Test(dataProvider = "fieldNamesWithMissingValue", expectedExceptions = XenonRuntimeException.class)
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      fail("Expect to throw exception on invalid start state");
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateContainersWorkflowService.State.class,
          NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateContainersWorkflowService createContainersWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createContainersWorkflowService = new CreateContainersWorkflowService();
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
    public void testValidStageTransition(
        TaskState.TaskStage startStage,
        CreateContainersWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        CreateContainersWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);

      CreateContainersWorkflowService.State patchState =
          CreateContainersWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateContainersWorkflowService.State savedState =
          testHost.getServiceState(CreateContainersWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{

          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.FINISHED,
              null},

          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        CreateContainersWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        CreateContainersWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);
      CreateContainersWorkflowService.State patchState =
          CreateContainersWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},

          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},
          {TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},

          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},

          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.PREEMPTIVE_PAUSE_BACKGROUND_TASKS},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_SERVICE_CONTAINERS},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.FINISHED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.FAILED,
              null},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.CANCELLED,
              null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFieldNames")
    private void testInvalidPatchStateValue(String fieldName) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainersWorkflowService.State patchState =
          CreateContainersWorkflowService.buildPatch(
              TaskState.TaskStage.STARTED,
              CreateContainersWorkflowService.TaskState.SubStage.CREATE_CORE_CONTAINERS,
              null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    private Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateContainersWorkflowService.State.class, Immutable.class));
    }

    private void startService(
        TaskState.TaskStage startStage,
        CreateContainersWorkflowService.TaskState.SubStage subStage) throws Throwable {
      CreateContainersWorkflowService.State startState = buildValidStartState(startStage, subStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainersWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * End-to-end tests for the create container task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";
    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private ListeningExecutorService listeningExecutorService;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private CreateContainersWorkflowService.State startState;
    private DeployerConfig deployerConfig;
    private SystemConfig systemConfig;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      this.systemConfig = spy(SystemConfig.createInstance(cloudStoreMachine.getHosts()[0]));
    }

    @BeforeMethod
    public void setUpTest() throws Exception {
      startState = buildValidStartState(null, null);
      startState.controlFlags = 0x0;
      startState.taskPollDelay = 10;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {

      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }


    /**
     * This test verifies the failure scenario inside create container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings("unchecked")
    @Test(dataProvider = "hostCounts")
    public void testTaskFailureInsideCreateContainer(Integer hostCount) throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory,
          healthCheckHelperFactory, hostCount);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          Matchers.<String>anyVararg())).thenThrow(new DockerException("Start container " + "failed", 500));

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);
      createDeploymentServiceDocuments();

      CreateContainersWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainersWorkflowFactoryService.SELF_LINK,
              startState,
              CreateContainersWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    /**
     * This test verifies the success scenario when launching container multiple
     * containers.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @SuppressWarnings("unchecked")
    @Test(dataProvider = "hostCounts")
    public void testTaskSuccess(Integer hostCount) throws Throwable {
      machine = createTestEnvironment(deployerConfig, listeningExecutorService, dockerProvisionerFactory,
          healthCheckHelperFactory, hostCount);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);

      // For create container
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          Matchers.<String>anyVararg())).thenReturn("id");

      // For copydb container
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          Matchers.<String>anyVararg())).thenReturn("id");

      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);

      createHostEntitiesAndAllocateVmsAndContainers(3, 7);
      createDeploymentServiceDocuments();

      CreateContainersWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateContainersWorkflowFactoryService.SELF_LINK,
              startState,
              CreateContainersWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }

    /**
     * This method sets up valid service documents which are needed for test.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    private void createHostEntitiesAndAllocateVmsAndContainers(
        int mgmtCount,
        int cloudCount) throws Throwable {

      for (int i = 0; i < mgmtCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));
      }

      for (int i = 0; i < cloudCount; i++) {
        TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.CLOUD.name()));
      }

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();

      workflowStartState.taskPollDelay = 10;
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      TestHelper.createDeploymentService(cloudStoreMachine);
    }

    private void createDeploymentServiceDocuments() throws Throwable {
      startState.deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
      startState.isAuthEnabled = false;
    }

    private TestEnvironment createTestEnvironment(
        DeployerConfig deployerConfig,
        ListeningExecutorService listeningExecutorService,
        DockerProvisionerFactory dockerProvisionerFactory,
        HealthCheckHelperFactory healthCheckHelperFactory,
        int hostCount)
        throws Throwable {
      return new TestEnvironment.Builder()
          .containersConfig(deployerConfig.getContainersConfig())
          .deployerContext(deployerConfig.getDeployerContext())
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .listeningExecutorService(listeningExecutorService)
          .healthCheckerFactory(healthCheckHelperFactory)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(hostCount)
          .build();
    }
  }

  private CreateContainersWorkflowService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      CreateContainersWorkflowService.TaskState.SubStage substage) {
    CreateContainersWorkflowService.State startState = new CreateContainersWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.isAuthEnabled = false;

    if (null != taskStage) {
      startState.taskState = new CreateContainersWorkflowService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = substage;
    }

    return startState;
  }
}
