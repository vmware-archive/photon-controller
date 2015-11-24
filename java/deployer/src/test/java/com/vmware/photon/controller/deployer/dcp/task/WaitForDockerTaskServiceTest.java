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
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * This class implements tests for the {@link WaitForDockerTaskService} class.
 */
public class WaitForDockerTaskServiceTest {

  public static WaitForDockerTaskService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable WaitForDockerTaskService.TaskState.SubStage startSubStage) {
    WaitForDockerTaskService.State startState = new WaitForDockerTaskService.State();
    startState.vmServiceLink = "VM_SERVICE_LINK";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new WaitForDockerTaskService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;
    }

    return startState;
  }

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the service constructor.
   */
  public class InitializationTest {

    private WaitForDockerTaskService waitForDockerTaskService;

    @BeforeMethod
    public void setUpTest() {
      waitForDockerTaskService = new WaitForDockerTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(waitForDockerTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the HandleStart method.
   */
  public class HandleStartTest {

    boolean serviceCreated = false;
    TestHost testHost;
    WaitForDockerTaskService waitForDockerTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      waitForDockerTaskService = new WaitForDockerTaskService();
    }

    private void startService(WaitForDockerTaskService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(waitForDockerTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
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
    public void testValidStartState(@Nullable TaskState.TaskStage startStage,
                                    @Nullable WaitForDockerTaskService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));
      WaitForDockerTaskService.State serviceState = testHost.getServiceState(WaitForDockerTaskService.State.class);
      assertThat(serviceState.taskState.stage, notNullValue());
      assertThat(serviceState.vmServiceLink, is("VM_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartStage(@Nullable TaskState.TaskStage startStage,
                                           @Nullable WaitForDockerTaskService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));
      WaitForDockerTaskService.State serviceState = testHost.getServiceState(WaitForDockerTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(WaitForDockerTaskService.TaskState.SubStage.DELAY));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(TaskState.TaskStage startStage) throws Throwable {
      WaitForDockerTaskService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = null;
      startService(startState);
      WaitForDockerTaskService.State serviceState = testHost.getServiceState(WaitForDockerTaskService.State.class);
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

    @Test(dataProvider = "NotNullAttributeFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStartStateMissingNotNullField(String fieldName) throws Throwable {
      WaitForDockerTaskService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "NotNullAttributeFieldNames")
    public Object[][] getNotNullAttributeFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              WaitForDockerTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the HandlePatch method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private WaitForDockerTaskService waitForDockerTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      waitForDockerTaskService = new WaitForDockerTaskService();
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
    public void testValidStageTransition(TaskState.TaskStage startStage,
                                         @Nullable WaitForDockerTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         @Nullable WaitForDockerTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      WaitForDockerTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(waitForDockerTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(WaitForDockerTaskService.buildPatch(patchStage, patchSubStage, null));

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));
      WaitForDockerTaskService.State serviceState = testHost.getServiceState(WaitForDockerTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           @Nullable WaitForDockerTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           @Nullable WaitForDockerTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      WaitForDockerTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(waitForDockerTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(WaitForDockerTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.DELAY},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, WaitForDockerTaskService.TaskState.SubStage.POLL},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableAttributeFieldNames", expectedExceptions = DcpRuntimeException.class)
    public void testInvalidPatchSetsImmutableField(String fieldName) throws Throwable {
      WaitForDockerTaskService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(waitForDockerTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      WaitForDockerTaskService.State patchState = WaitForDockerTaskService.buildPatch(TaskState.TaskStage.STARTED,
          WaitForDockerTaskService.TaskState.SubStage.DELAY, null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableAttributeFieldNames")
    public Object[][] getImmutableAttributeFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              WaitForDockerTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private DockerProvisionerFactory dockerProvisionerFactory;
    private ListeningExecutorService listeningExecutorService;
    private WaitForDockerTaskService.State startState;
    private TestEnvironment testEnvironment = null;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.delayInterval = 10;
      startState.pollInterval = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
    }

    private void startTestEnvironment() throws Throwable {

      testEnvironment = new TestEnvironment.Builder()
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .listeningExecutorService(listeningExecutorService)
          .hostCount(1)
          .build();

      VmService.State vmStartState = TestHelper.getVmServiceStartState();
      vmStartState.ipAddress = "IP_ADDRESS";
      startState.vmServiceLink = TestHelper.createVmService(testEnvironment, vmStartState).documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable{
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());
      doReturn("Docker info").when(dockerProvisioner).getInfo();
      startTestEnvironment();

      WaitForDockerTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          WaitForDockerTaskFactoryService.SELF_LINK,
          startState,
          WaitForDockerTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndSuccessAfterFailures() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());

      when(dockerProvisioner.getInfo())
          .thenThrow(new RuntimeException(new TimeoutException("Docker call timed out")))
          .thenThrow(new RuntimeException(new TimeoutException("Docker call timed out")))
          .thenReturn("Docker info");

      startTestEnvironment();

      WaitForDockerTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          WaitForDockerTaskFactoryService.SELF_LINK,
          startState,
          WaitForDockerTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testEndToEndFailureAfterRepeatedTimeout() throws Throwable {
      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());

      when(dockerProvisioner.getInfo())
          .thenThrow(new RuntimeException(new TimeoutException("Docker call timed out")));

      startTestEnvironment();

      WaitForDockerTaskService.State serviceState = testEnvironment.callServiceAndWaitForState(
          WaitForDockerTaskFactoryService.SELF_LINK,
          startState,
          WaitForDockerTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(serviceState.taskState.failure.message, containsString("Docker call timed out"));
    }
  }
}
