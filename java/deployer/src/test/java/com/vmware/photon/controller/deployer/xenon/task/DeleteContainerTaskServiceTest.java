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

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.github.dockerjava.api.DockerException;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link DeleteContainerTaskService} class.
 */
public class DeleteContainerTaskServiceTest {

  private TestHost host;
  private DeleteContainerTaskService service;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private DeleteContainerTaskService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED);
  }

  private DeleteContainerTaskService.State buildValidStartupState(TaskState.TaskStage stage) {
    DeleteContainerTaskService.State state = new DeleteContainerTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.containerServiceLink = "CONTAINER_SERVICE_LINK";
    state.removeVolumes = true;
    state.force = true;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    return state;
  }

  private DeleteContainerTaskService.State buildValidPatchState() {
    return buildValidPatchState(TaskState.TaskStage.STARTED);
  }

  private DeleteContainerTaskService.State buildValidPatchState(TaskState.TaskStage stage) {
    DeleteContainerTaskService.State state = new DeleteContainerTaskService.State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    return state;
  }

  private TestEnvironment createTestEnvironment(
      ListeningExecutorService listeningExecutorService,
      DockerProvisionerFactory dockerProvisionerFactory,
      int hostCount)
      throws Throwable {

    return new TestEnvironment.Builder()
        .dockerProvisionerFactory(dockerProvisionerFactory)
        .listeningExecutorService(listeningExecutorService)
        .hostCount(hostCount)
        .build();
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      service = new DeleteContainerTaskService();
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
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      service = new DeleteContainerTaskService();
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
    public void testMinimalStartState(TaskState.TaskStage stage) throws Throwable {

      DeleteContainerTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DeleteContainerTaskService.State savedState = host.getServiceState(
          DeleteContainerTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
      assertThat(savedState.removeVolumes, is(true));
      assertThat(savedState.force, is(true));
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStatesWithoutUploadImageId() {

      return new Object[][]{
          {TaskState.TaskStage.CREATED},
          {TaskState.TaskStage.STARTED},
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {

      DeleteContainerTaskService.State startState = buildValidStartupState(stage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      DeleteContainerTaskService.State savedState =
          host.getServiceState(DeleteContainerTaskService.State.class);
      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
      assertThat(savedState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
      assertThat(savedState.removeVolumes, is(true));
      assertThat(savedState.force, is(true));
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
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      DeleteContainerTaskService.State startState = buildValidStartupState();
      Field declaredField = startState.getClass().getDeclaredField(attributeName);
      declaredField.set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes
          = ReflectionUtils.getAttributeNamesWithAnnotation(DeleteContainerTaskService.State.class, NotNull.class);
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
      service = new DeleteContainerTaskService();
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
     * This test verifies that legal stage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      DeleteContainerTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteContainerTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      DeleteContainerTaskService.State savedState =
          host.getServiceState(DeleteContainerTaskService.State.class);
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

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidPatch")
    public void testIllegalStageUpdatesInvalidPatch(
        TaskState.TaskStage startStage,
        TaskState.TaskStage targetStage)
        throws Throwable {

      DeleteContainerTaskService.State startState = buildValidStartupState(startStage);
      host.startServiceSynchronously(service, startState);

      DeleteContainerTaskService.State patchState = buildValidPatchState(targetStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      try {
        host.sendRequestAndWait(patchOp);
        fail("Patch handling should throw in response to invalid start state");
      } catch (XenonRuntimeException e) {
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

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      DeleteContainerTaskService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      DeleteContainerTaskService.State patchState = buildValidPatchState();
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
          = ReflectionUtils.getAttributeNamesWithAnnotation(DeleteContainerTaskService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End-to-end tests for the create container task.
   */
  public class EndToEndTest {

    private TestEnvironment machine;
    private ListeningExecutorService listeningExecutorService;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private DeleteContainerTaskService.State startState;
    private ContainerService.State containerStartState;

    @BeforeClass
    public void setUpClass() throws Exception {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);

      containerStartState = new ContainerService.State();
      containerStartState.containerId = "id";
      containerStartState.containerTemplateServiceLink = "containerTemplateServiceLink";
    }

    @BeforeMethod
    public void setUpTest() throws Exception {

      startState = buildValidStartupState();
      startState.controlFlags = 0x0;
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
    public void tearDownClass() throws Exception {
      listeningExecutorService.shutdown();
    }

    /**
     * This test verifies the failure scenario when deleting a container returns a null.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskFailureWithNullResult() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.deleteContainer(anyString(), anyBoolean(), anyBoolean())).thenReturn(null);

      machine = createTestEnvironment(listeningExecutorService, dockerProvisionerFactory, 1);

      VmService.State vmServiceState = TestHelper.createVmService(machine);
      containerStartState.vmServiceLink = vmServiceState.documentSelfLink;

      ContainerService.State containerServiceState = TestHelper.createContainerService(machine, vmServiceState);
      startState.containerServiceLink = containerServiceState.documentSelfLink;

      DeleteContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteContainerTaskFactoryService.SELF_LINK,
              startState,
              DeleteContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Delete container returned null"));
    }

    /**
     * This test verifies the failure scenario when deleting a container with internal docker exception.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskFailureInsideDocker() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.deleteContainer(anyString(), anyBoolean(), anyBoolean())).
          thenThrow(new DockerException("Delete container failed", 500));

      machine = createTestEnvironment(listeningExecutorService, dockerProvisionerFactory, 1);

      VmService.State vmServiceState = TestHelper.createVmService(machine);
      containerStartState.vmServiceLink = vmServiceState.documentSelfLink;

      ContainerService.State containerServiceState = TestHelper.createContainerService(machine, vmServiceState);
      startState.containerServiceLink = containerServiceState.documentSelfLink;

      DeleteContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteContainerTaskFactoryService.SELF_LINK,
              startState,
              DeleteContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Delete container failed"));
    }

    /**
     * This test verifies the success scenario when deleting a container.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test
    public void testTaskSuccess() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.deleteContainer(anyString(), anyBoolean(), anyBoolean())).thenReturn("id");

      machine = createTestEnvironment(listeningExecutorService, dockerProvisionerFactory, 1);

      VmService.State vmServiceState = TestHelper.createVmService(machine);
      containerStartState.vmServiceLink = vmServiceState.documentSelfLink;

      ContainerService.State containerServiceState = TestHelper.createContainerService(machine, vmServiceState);
      startState.containerServiceLink = containerServiceState.documentSelfLink;

      DeleteContainerTaskService.State finalState =
          machine.callServiceAndWaitForState(
              DeleteContainerTaskFactoryService.SELF_LINK,
              startState,
              DeleteContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }
  }
}
