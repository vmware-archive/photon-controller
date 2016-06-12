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
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements tests for {@link CreateVmSpecLayoutTaskService}.
 */
public class CreateVmSpecLayoutTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private CreateVmSpecLayoutTaskService createVmSpecLayoutTaskService;

    @BeforeMethod
    public void setUpTest() {
      createVmSpecLayoutTaskService = new CreateVmSpecLayoutTaskService();
    }

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);

      assertThat(createVmSpecLayoutTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateVmSpecLayoutTaskService createVmSpecLayoutTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createVmSpecLayoutTaskService = new CreateVmSpecLayoutTaskService();
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
      CreateVmSpecLayoutTaskService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecLayoutTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecLayoutTaskService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecLayoutTaskService, startState);
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
      CreateVmSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecLayoutTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecLayoutTaskService.State.class);

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
      CreateVmSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecLayoutTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateVmSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecLayoutTaskService.State.class);

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
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateVmSpecLayoutTaskService createVmSpecLayoutTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createVmSpecLayoutTaskService = new CreateVmSpecLayoutTaskService();
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
      CreateVmSpecLayoutTaskService.State patchState =
          createVmSpecLayoutTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateVmSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateVmSpecLayoutTaskService.State.class);

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
      CreateVmSpecLayoutTaskService.State patchState =
          createVmSpecLayoutTaskService.buildPatch(patchStage, null);

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

    @Test(dataProvider = "InvalidPatchStateAttributes", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateInvalidAttributeSet(String attributeName, Object value) throws Throwable {
      startService(TaskState.TaskStage.CREATED);

      CreateVmSpecLayoutTaskService.State patchState =
          createVmSpecLayoutTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

      patchState.getClass().getDeclaredField(attributeName).set(patchState, value);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidPatchStateAttributes")
    public Object[][] getInvalidPatchStateAttributes() {
      return new Object[][]{
          {"controlFlags", new Integer(0)},
      };
    }

    private void startService(TaskState.TaskStage startStage) throws Throwable {
      CreateVmSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createVmSpecLayoutTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * End to end tests for {@link CreateVmSpecLayoutTaskService}.
   */
  public class EndToEndTest {

    private CreateVmSpecLayoutTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    private ContainersConfig containersConfig;

    private List<VmService.State> dockerVms = new ArrayList<>();

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidStartState();
      startState.controlFlags = 0x0;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
      startState = buildValidStartState();
      startState.controlFlags = 0x0;
      startState.taskPollDelay = 10;
      dockerVms.clear();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void testEndToEndSuccessWithNodesMarkedMgmtExclusively() throws Throwable {
      int mgmtHosts = 3;
      setupHosts(10, mgmtHosts);

      CreateVmSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVmSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateVmSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      validateCreateManagementVmTaskServices(mgmtHosts);
    }

    @Test
    public void testEndToEndSuccessWithNodesMarkedAsCloudAndMgmt() throws Throwable {
      int mgmtHosts = 3;
      int mgmtHostsTaggedAsCloud = 2;
      setupHosts(10, mgmtHosts, mgmtHostsTaggedAsCloud);

      CreateVmSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVmSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateVmSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      validateCreateManagementVmTaskServices(mgmtHosts);
    }

    @Test
    public void testFailureDueToNoManagementHosts() throws Throwable {
      setupHosts(5, 0);

      CreateVmSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateVmSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateVmSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void validateCreateManagementVmTaskServices(int mgmtHosts) throws Throwable {

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(CreateVmSpecTaskService.State.class));

      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      assertThat(documentLinks.size(), is(mgmtHosts));

      Set<String> hostServiceLinks = new HashSet<>();
      for (String documentLink : documentLinks) {
        CreateVmSpecTaskService.State state = testEnvironment.getServiceState(documentLink,
            CreateVmSpecTaskService.State.class);
        hostServiceLinks.add(state.hostServiceLink);
      }

      // Verify 1 vm allocated per mgmt host
      assertThat(hostServiceLinks.size(), is(mgmtHosts));
    }

    private void setupHosts(int totalHosts, int mgmtHosts) throws Throwable {
      setupHosts(totalHosts, mgmtHosts, 0 /* mgmtHostsTaggedAsCloud */);
    }

    private void setupHosts(int totalHosts, int mgmtHosts, int mgmtHostsTaggedAsCloud) throws Throwable {
      if (mgmtHosts > totalHosts) {
        throw new RuntimeException(
            String.format("#mgmtHosts(%s) should be <= #totalHosts (%s)", mgmtHosts, totalHosts));
      }

      if (mgmtHostsTaggedAsCloud > mgmtHosts) {
        throw new RuntimeException(
            String.format("#mgmtHostsTaggedAsCloud(%s) should be <= #mgmtHosts (%s)", mgmtHostsTaggedAsCloud,
                mgmtHosts));
      }

      for (int i = 0; i < totalHosts; i++) {
        if (i < mgmtHostsTaggedAsCloud) {
          TestHelper.createHostService(cloudStoreMachine,
              ImmutableSet.of(UsageTag.MGMT.name(), UsageTag.CLOUD.name()));
        } else if (i < mgmtHosts) {
          TestHelper.createHostService(cloudStoreMachine,
              Collections.singleton(UsageTag.MGMT.name()));
        } else {
          TestHelper.createHostService(cloudStoreMachine,
              Collections.singleton(UsageTag.CLOUD.name()));
        }
      }
    }
  }

  private CreateVmSpecLayoutTaskService.State buildValidStartState() {
    CreateVmSpecLayoutTaskService.State startState = new CreateVmSpecLayoutTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;
    startState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());
    return startState;
  }

  private CreateVmSpecLayoutTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    CreateVmSpecLayoutTaskService.State startState = buildValidStartState();
    startState.taskState = new TaskState();
    startState.taskState.stage = taskStage;
    return startState;
  }
}
