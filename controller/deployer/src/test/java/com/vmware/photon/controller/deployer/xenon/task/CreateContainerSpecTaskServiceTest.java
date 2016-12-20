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
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements tests for {@link CreateContainerSpecTaskService}.
 */
public class CreateContainerSpecTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private CreateContainerSpecTaskService createContainerSpecTaskService;

    @BeforeMethod
    public void setUpTest() {
      createContainerSpecTaskService = new CreateContainerSpecTaskService();
    }

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(createContainerSpecTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateContainerSpecTaskService createContainerSpecTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createContainerSpecTaskService = new CreateContainerSpecTaskService();
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
      CreateContainerSpecTaskService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecTaskService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateContainerSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
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
      CreateContainerSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecTaskService.State.class);

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
      CreateContainerSpecTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecTaskService.State.class);

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

    @Test(dataProvider = "InvalidStartStateAttributes", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateInvalidAttributeSet(String attributeName, Object value) throws Throwable {
      CreateContainerSpecTaskService.State startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.getClass().getDeclaredField(attributeName).set(startState, value);

      testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
      fail("Expect to throw exception on invalid start state");
    }

    @DataProvider(name = "InvalidStartStateAttributes")
    public Object[][] getInvalidStartStateAttributes() {
      return new Object[][]{
          {"containerTemplateDocumentLink", null},
          {"dockerVmDocumentLinks", null}
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateContainerSpecTaskService createContainerSpecTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createContainerSpecTaskService = new CreateContainerSpecTaskService();
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
      CreateContainerSpecTaskService.State patchState =
          createContainerSpecTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateContainerSpecTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecTaskService.State.class);

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
      CreateContainerSpecTaskService.State patchState =
          createContainerSpecTaskService.buildPatch(patchStage, null);

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

      CreateContainerSpecTaskService.State patchState =
          createContainerSpecTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

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
          {"containerTemplateDocumentLink", "NEW_LINK"},
          {"dockerVmDocumentLinks", new ArrayList<String>()}
      };
    }

    private void startService(TaskState.TaskStage startStage) throws Throwable {
      CreateContainerSpecTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(createContainerSpecTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * End to end tests for {@link CreateContainerSpecTaskService}.
   */
  public class EndToEndTest {

    private CreateContainerSpecTaskService.State startState;
    private TestEnvironment testEnvironment;

    private List<String> dockerVms = Arrays.asList("VM_1", "VM_2", "VM_3");

    @BeforeClass
    public void setUpClass() throws Throwable {
      startState = buildValidStartState();
      startState.controlFlags = 0x0;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      testEnvironment = new TestEnvironment.Builder().hostCount(1).build();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }
    }

    @Test(dataProvider = "SuccessCombinations")
    public void testEndToEndSuccess(boolean isReplicated, int existingContainers) throws Throwable {
      ContainerTemplateService.State templateStartState = TestHelper.getContainerTemplateServiceStartState();
      templateStartState.isReplicated = isReplicated;

      ContainerTemplateService.State containerTemplate =
          TestHelper.createContainerTemplateService(testEnvironment, templateStartState);

      VmService.State vmState = new VmService.State();

      for (int i = 0; i < existingContainers; i++) {
        vmState.documentSelfLink = dockerVms.get(i);
        TestHelper.createContainerService(testEnvironment, containerTemplate, vmState);
      }

      startState.containerTemplateDocumentLink = containerTemplate.documentSelfLink;
      startState.dockerVmDocumentLinks.clear();
      startState.dockerVmDocumentLinks.addAll(dockerVms);
      startState.singletonVmServiceLink = dockerVms.get(0);

      CreateContainerSpecTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      validateAllocatedContainers(isReplicated, containerTemplate.documentSelfLink);
    }

    @DataProvider(name = "SuccessCombinations")
    public Object[][] getSuccessCombinations() {
      return new Object[][]{
          {true, 0},
          {true, dockerVms.size() - 1},
          {true, dockerVms.size()},
          {false, 0},
          {false, 1},
      };
    }

    // TODO(hgadgil): This test does not run due to how Xenon handles queries.
    // If a document link is specified and we try to retrieve it when the object does not exist, Xenon assumes that
    // the document is still being replicated so waits forever rather than returning failure "NOT_FOUND".
    // Filed tracker story to enable this test when Xenon enables the required behavior:
    // https://www.pivotaltracker.com/story/show/94686222
    @Test(enabled = false)
    public void testContainerTemplateNotFound() throws Throwable {
      CreateContainerSpecTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void validateAllocatedContainers(boolean isReplicated, String containerTemplateLink) throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

      QueryTask.Query containerTemplateClause = new QueryTask.Query()
          .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
          .setTermMatchValue(containerTemplateLink);

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(containerTemplateClause);

      QueryTask query = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(query);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      // Verify that count(replicas) == count(dockerVms) i.e. 1 container per vm
      int expectedReplicaCount = isReplicated ? dockerVms.size() : 1;
      assertThat(documentLinks.size(), is(expectedReplicaCount));

      // Verify that each container was assigned to a unique docker vm
      Set<String> uniqueVmLinks = new HashSet<>();
      for (String documentLink : documentLinks) {
        ContainerService.State state = testEnvironment.getServiceState(documentLink, ContainerService.State.class);
        uniqueVmLinks.add(state.vmServiceLink);
      }
      assertThat(uniqueVmLinks.size(), is(expectedReplicaCount));
    }

    private void setupContainer(String containerTemplateServiceLink, String vmServiceLink) throws Throwable {
      ContainerService.State container = new ContainerService.State();
      container.containerTemplateServiceLink = containerTemplateServiceLink;
      container.vmServiceLink = vmServiceLink;

      testEnvironment.callServiceSynchronously(
          ContainerFactoryService.SELF_LINK,
          container,
          ContainerService.State.class);
    }
  }

  private CreateContainerSpecTaskService.State buildValidStartState() {
    CreateContainerSpecTaskService.State startState = new CreateContainerSpecTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.containerTemplateDocumentLink = "CONTAINER_TEMPLATE_DOCUMENT_LINK";
    startState.dockerVmDocumentLinks = new ArrayList<>();
    startState.dockerVmDocumentLinks.add("VM_1");
    startState.dockerVmDocumentLinks.add("VM_2");
    startState.dockerVmDocumentLinks.add("VM_3");
    return startState;
  }

  private CreateContainerSpecTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    CreateContainerSpecTaskService.State startState = buildValidStartState();
    startState.taskState = new TaskState();
    startState.taskState.stage = taskStage;
    return startState;
  }
}
