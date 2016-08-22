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

package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.clustermanager.clients.KubernetesClient;
import com.vmware.photon.controller.clustermanager.helpers.ReflectionUtils;
import com.vmware.photon.controller.clustermanager.helpers.TestEnvironment;
import com.vmware.photon.controller.clustermanager.helpers.TestHelper;
import com.vmware.photon.controller.clustermanager.helpers.TestHost;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.statuschecks.StatusCheckHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * This class implements tests for the {@link ClusterWaitTaskService} class.
 */
public class ClusterWaitTaskServiceTest {

  private static final String SERVER_ADDRESS = "10.146.22.40";

  /**
   * This test allows IntelliJ to recognize the current class as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  private ClusterWaitTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    ClusterWaitTaskService.State startState = new ClusterWaitTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    startState.nodeAddresses = new ArrayList();
    startState.nodeAddresses.add("10.146.22.40");
    startState.nodeAddresses.add("10.146.22.41");
    startState.nodeAddresses.add("10.146.22.42");

    startState.serverAddress = SERVER_ADDRESS;
    startState.nodeType = NodeType.KubernetesWorker;

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private ClusterWaitTaskService waitTaskService;

    @BeforeMethod
    public void setUpTest() {
      waitTaskService = new ClusterWaitTaskService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(waitTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private boolean serviceCreated = false;
    private TestHost testHost;
    private ClusterWaitTaskService waitTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      waitTaskService = new ClusterWaitTaskService();
    }

    private void startService(ClusterWaitTaskService.State startState) throws Throwable {
      Operation result = testHost.startServiceSynchronously(waitTaskService, startState);
      assertThat(result.getStatusCode(), is(200));
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
    public void testValidStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      ClusterWaitTaskService.State serviceState = testHost
          .getServiceState(ClusterWaitTaskService.State.class);
      assertThat(serviceState.serverAddress, is(SERVER_ADDRESS));
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
    public void testTransitionalStartStage(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      ClusterWaitTaskService.State serviceState = testHost
          .getServiceState(ClusterWaitTaskService.State.class);
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
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      ClusterWaitTaskService.State serviceState = testHost
          .getServiceState(ClusterWaitTaskService.State.class);
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

    @Test(dataProvider = "NotNullFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      ClusterWaitTaskService.State startState = buildValidStartState(null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "NotNullFieldNames")
    public Object[][] getNotNullFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ClusterWaitTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private ClusterWaitTaskService waitTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      waitTaskService = new ClusterWaitTaskService();
    }

    private void startService(ClusterWaitTaskService.State startState) throws Throwable {
      Operation result = testHost.startServiceSynchronously(waitTaskService, startState);
      assertThat(result.getStatusCode(), is(200));
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
      startService(buildValidStartState(startStage));
      ClusterWaitTaskService.State patchState = ClusterWaitTaskService
          .buildPatch(patchStage, null);

      Operation result = testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));

      assertThat(result.getStatusCode(), is(200));
      ClusterWaitTaskService.State serviceState = testHost
          .getServiceState(ClusterWaitTaskService.State.class);
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

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
        throws Throwable {
      startService(buildValidStartState(startStage));
      ClusterWaitTaskService.State patchState = ClusterWaitTaskService
          .buildPatch(patchStage, null);

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
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

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      startService(buildValidStartState(null));

      ClusterWaitTaskService.State patchState =
          ClusterWaitTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      testHost.sendRequestAndWait(Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState));
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              ClusterWaitTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the task.
   */
  public class EndToEndTest {

    private KubernetesClient kubernetesClient;
    private TestEnvironment testEnvironment;
    private ClusterWaitTaskService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidStartState(null);
      startState.controlFlags = 0x0;
      startState.maxApiCallPollIterations = 3;
      startState.apiCallPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      kubernetesClient = mock(KubernetesClient.class);

      testEnvironment = new TestEnvironment.Builder()
          .kubernetesClient(kubernetesClient)
          .statusCheckHelper(new StatusCheckHelper())
          .hostCount(1)
          .build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      testEnvironment.stop();
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {

      final Set<String> nodeAddresses = new HashSet();
      for (String address : startState.nodeAddresses) {
        nodeAddresses.add(address);
      }

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(nodeAddresses);
          return null;
        }
      }).when(kubernetesClient).getNodeAddressesAsync(
          anyString(), any(FutureCallback.class));

      ClusterWaitTaskService.State serviceState =
          testEnvironment.callServiceAndWaitForState(
              ClusterWaitTaskFactoryService.SELF_LINK,
              startState,
              ClusterWaitTaskService.State.class,
              new Predicate<ClusterWaitTaskService.State>() {
                @Override
                public boolean test(ClusterWaitTaskService.State state) {
                  return TaskUtils.finalTaskStages.contains(state.taskState.stage);
                }
              });

      TestHelper.assertTaskStateFinished(serviceState.taskState);
    }

    @Test
    public void testWaitFailure() throws Throwable {

      final Set<String> nodeAddresses = new HashSet();

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Set<String>>) invocation.getArguments()[1]).onSuccess(nodeAddresses);
          return null;
        }
      }).when(kubernetesClient).getNodeAddressesAsync(
          anyString(), any(FutureCallback.class));

      ClusterWaitTaskService.State serviceState =
          testEnvironment.callServiceAndWaitForState(
              ClusterWaitTaskFactoryService.SELF_LINK,
              startState,
              ClusterWaitTaskService.State.class,
              new Predicate<ClusterWaitTaskService.State>() {
                @Override
                public boolean test(ClusterWaitTaskService.State state) {
                  return TaskUtils.finalTaskStages.contains(state.taskState.stage);
                }
              });

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
