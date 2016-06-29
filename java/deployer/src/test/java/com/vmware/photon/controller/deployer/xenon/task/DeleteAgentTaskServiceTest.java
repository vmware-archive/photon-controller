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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link DeleteAgentTaskService} class.
 */
public class DeleteAgentTaskServiceTest {

  private static final String configFilePath = "/config.yml";

  /**
   * This method creates a new State object which is sufficient to create a new
   * service instance.
   *
   * @param startStage
   * @return
   */
  public static DeleteAgentTaskService.State buildValidStartupState(@Nullable TaskState.TaskStage startStage) {
    DeleteAgentTaskService.State startState = new DeleteAgentTaskService.State();
    startState.hostServiceLink = "hostServiceLink";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }

  private DeleteAgentTaskService service;
  private TestHost host;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  private TestEnvironment createTestEnvironment(
      DeployerContext deployerContext,
      ListeningExecutorService listeningExecutorService,
      ServerSet cloudServerSet,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .deployerContext(deployerContext)
        .listeningExecutorService(listeningExecutorService)
        .cloudServerSet(cloudServerSet)
        .hostCount(hostCount)
        .build();
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
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      service = new DeleteAgentTaskService();
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
      service = spy(new DeleteAgentTaskService());
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
     * This test verifies that a service instance can be created in the
     * specified start state.
     *
     * @param taskStage
     * @throws Throwable
     */
    @Test(dataProvider = "ValidStartStates")
    public void testMinimalStartState(TaskState.TaskStage taskStage) throws Throwable {
      DeleteAgentTaskService.State startState = buildValidStartupState(taskStage);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State savedState = host.getServiceState(DeleteAgentTaskService.State.class);
      assertThat(savedState.uniqueID, notNullValue());
    }

    @DataProvider(name = "ValidStartStates")
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
      DeleteAgentTaskService.State startState = buildValidStartupState(null);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State savedState = host.getServiceState(DeleteAgentTaskService.State.class);
      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(savedState.uniqueID, notNullValue());
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
      DeleteAgentTaskService.State startState = buildValidStartupState(stage);
      startState.controlFlags = null;
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State savedState = host.getServiceState(DeleteAgentTaskService.State.class);
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.uniqueID, notNullValue());
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
     * This test verifies that an override value for the unique task ID is
     * persisted, if specified.
     *
     * @throws Throwable
     */
    @Test
    public void testUniqueIDOverride() throws Throwable {
      DeleteAgentTaskService.State startState = buildValidStartupState(null);
      startState.uniqueID = "uniqueID";
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State savedState = host.getServiceState(DeleteAgentTaskService.State.class);
      assertThat(savedState.uniqueID, is("uniqueID"));
    }

    /**
     * This test verifies that service start fails if an ESX hypervisor service
     * link is not specified.
     *
     * @throws Throwable
     */
    @Test(dataProvider = "RequiredAttributeNames", expectedExceptions = XenonRuntimeException.class)
    public void testFailureRequiredStateNull(String fieldName) throws Throwable {
      DeleteAgentTaskService.State startState = buildValidStartupState(null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "RequiredAttributeNames")
    public Object[][] getRequiredAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeleteAgentTaskService.State.class, NotNull.class));
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
      service = new DeleteAgentTaskService();
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
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage) throws Throwable {

      DeleteAgentTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteAgentTaskService.State patchState = new DeleteAgentTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = targetStage;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOperation = host.sendRequestAndWait(patchOperation);
      assertThat(resultOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State savedState = host.getServiceState(DeleteAgentTaskService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{

          // N.B. Services created in the CREATED state will transition to
          //      STARTED as part of service creation, but any listeners will
          //      subsequently get a STARTED patch too, so cover this case.

          {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},

          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
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
    public void testInvalidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage) throws Throwable {

      DeleteAgentTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteAgentTaskService.State patchState = new DeleteAgentTaskService.State();
      patchState.taskState = new TaskState();
      patchState.taskState.stage = targetStage;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOperation);
        fail("Transition from " + startStage + " to " + targetStage + " succeeded unexpectedly");

      } catch (XenonRuntimeException e) {
        // N.B. An assertion can be added here if an error message is added to
        //      the checkState calls in validatePatch.
      }
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},

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

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchUpdateImmutableField(String fieldName) throws Throwable {
      DeleteAgentTaskService.State startState = buildValidStartupState(null);
      Operation startOperation = host.startServiceSynchronously(service, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeleteAgentTaskService.State patchState = service.buildPatch(TaskState.TaskStage.STARTED, null);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, getDefaultValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI))
          .setBody(patchState);

      host.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeleteAgentTaskService.State.class, Immutable.class));
    }

    private Object getDefaultValue(Field declaredField) throws Throwable {
      if (declaredField.getType() == Integer.class) {
        return new Integer(1);
      }

      return declaredField.getType().newInstance();
    }
  }

  /**
   * This class implements end-to-end tests for the delete agent task service.
   */
  public class EndToEndTest {

    private final File storageDirectory = new File("/tmp/deployAgent");

    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");

    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private DeployerContext deployerContext;
    private ListeningExecutorService listeningExecutorService;
    private DeleteAgentTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      FileUtils.deleteDirectory(storageDirectory);

      deployerContext = ConfigBuilder.build(DeployerTestConfig.class,
          DeleteAgentTaskService.class.getResource(configFilePath).getPath()).getDeployerContext();

      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    }

    @BeforeMethod
    public void setUpTest() {
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      startState = buildValidStartupState(TaskState.TaskStage.CREATED);
      startState.controlFlags = null;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      FileUtils.deleteDirectory(storageDirectory);
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
     * This test verifies that the end-to-end scenario completes successfully
     * when the delete agent script succeeds.
     *
     * @throws Throwable
     */
    @Test
    public void testEndToEndSuccess() throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, DeleteAgentTaskService.SCRIPT_NAME, true);

      machine = createTestEnvironment(deployerContext,
          listeningExecutorService, cloudStoreMachine.getServerSet(), 1);

      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;

      DeleteAgentTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteAgentTaskFactoryService.SELF_LINK,
              startState,
              DeleteAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    /**
     * This test verifies that the end-to-end scenario completes successfully
     * when the delete agent script succeeds.
     *
     * @throws Throwable
     */
    @Test
    public void testEndToEndFailureScriptRunnerFailureNonZeroExit() throws Throwable {

      MockHelper.mockCreateScriptFile(deployerContext, DeleteAgentTaskService.SCRIPT_NAME, false);

      machine = createTestEnvironment(deployerContext,
          listeningExecutorService, cloudStoreMachine.getServerSet(), 1);

      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;

      DeleteAgentTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteAgentTaskFactoryService.SELF_LINK,
              startState,
              DeleteAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    /**
     * This test verifies that the end-to-end scenario completes successfully
     * when the delete agent script succeeds.
     *
     * @throws Throwable
     */
    @Test
    public void testEndToEndFailureScriptRunnerFailureException() throws Throwable {

      // do not create script file

      machine = createTestEnvironment(deployerContext,
          listeningExecutorService, cloudStoreMachine.getServerSet(), 1);

      startState.hostServiceLink = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name())).documentSelfLink;

      DeleteAgentTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteAgentTaskFactoryService.SELF_LINK,
              startState,
              DeleteAgentTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
