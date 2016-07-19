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

import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.util.MiscUtils;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link CreateManagementPlaneLayoutWorkflowService}.
 */
public class CreateManagementPlaneLayoutWorkflowServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private CreateManagementPlaneLayoutWorkflowService createManagementPlaneLayoutWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      createManagementPlaneLayoutWorkflowService = new CreateManagementPlaneLayoutWorkflowService();
    }

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);

      assertThat(createManagementPlaneLayoutWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateManagementPlaneLayoutWorkflowService createManagementPlaneLayoutWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createManagementPlaneLayoutWorkflowService = new CreateManagementPlaneLayoutWorkflowService();
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
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      CreateManagementPlaneLayoutWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(createManagementPlaneLayoutWorkflowService,
          startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "StartStagesWhichTransitionToStarted")
    public void testStartStateTransitionsToStarted(
        TaskState.TaskStage startStage,
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      CreateManagementPlaneLayoutWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(createManagementPlaneLayoutWorkflowService,
          startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateManagementPlaneLayoutWorkflowService.State savedState =
          testHost.getServiceState(CreateManagementPlaneLayoutWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @DataProvider(name = "StartStagesWhichTransitionToStarted")
    public Object[][] getStartStagesWhichTransitionToStarted() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
      };
    }

    @Test(dataProvider = "FinalStartStages")
    public void testFinalStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateManagementPlaneLayoutWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(createManagementPlaneLayoutWorkflowService,
          startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateManagementPlaneLayoutWorkflowService.State savedState =
          testHost.getServiceState(CreateManagementPlaneLayoutWorkflowService.State.class);

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
      CreateManagementPlaneLayoutWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED,
          null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(createManagementPlaneLayoutWorkflowService, startState);
      fail("Expect to throw exception on invalid start state");
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      List<String> notNullAttributes = ReflectionUtils.getAttributeNamesWithAnnotation(
          CreateManagementPlaneLayoutWorkflowService.State.class,
          NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateManagementPlaneLayoutWorkflowService createManagementPlaneLayoutWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createManagementPlaneLayoutWorkflowService = new CreateManagementPlaneLayoutWorkflowService();
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
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);

      CreateManagementPlaneLayoutWorkflowService.State patchState =
          createManagementPlaneLayoutWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateManagementPlaneLayoutWorkflowService.State savedState =
          testHost.getServiceState(CreateManagementPlaneLayoutWorkflowService.State.class);

      assertThat(savedState.taskState.stage, is(patchStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{

          {TaskState.TaskStage.CREATED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.FINISHED,
              null},

          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(startStage, startSubStage);
      CreateManagementPlaneLayoutWorkflowService.State patchState =
          createManagementPlaneLayoutWorkflowService.buildPatch(patchStage, patchSubStage, null);

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
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              TaskState.TaskStage.CREATED,
              null},

          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},

          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},

          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.CREATED,
              null},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},
          {TaskState.TaskStage.FINISHED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION},
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
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},
          {TaskState.TaskStage.FAILED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION},
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
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.ALLOCATE_DOCKER_VMS},
          {TaskState.TaskStage.CANCELLED,
              null,
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION},
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

    @Test(dataProvider = "InvalidPatchStateAttributes", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchStateInvalidAttributeSet(String attributeName, Object value) throws Throwable {
      startService(TaskState.TaskStage.CREATED, null);

      CreateManagementPlaneLayoutWorkflowService.State patchState =
          createManagementPlaneLayoutWorkflowService.buildPatch(
              TaskState.TaskStage.STARTED,
              CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage.CREATE_CONTAINER_TEMPLATES,
              null);

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

    private void startService(
        TaskState.TaskStage startStage,
        CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage subStage) throws Throwable {
      CreateManagementPlaneLayoutWorkflowService.State startState = buildValidStartState(startStage, subStage);
      Operation startOperation = testHost.startServiceSynchronously(createManagementPlaneLayoutWorkflowService,
          startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * This class implements end-to-end tests for the workflow.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final Set<String> sharedUsageTags = new HashSet<>(
        Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name()));

    private DeployerTestConfig deployerTestConfig;
    private ContainersConfig containersConfig;
    private CreateManagementPlaneLayoutWorkflowService.State startState;
    private TestEnvironment testEnvironment = null;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerTestConfig = ConfigBuilder.build(DeployerTestConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerTestConfig);

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
      startState.isAuthEnabled = true;
    }

    @BeforeMethod
    public void setUpTest() throws Exception {
      containersConfig = deployerTestConfig.getContainersConfig();
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

    @Test(dataProvider = "hostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount);
      createHostServices(Collections.singleton(UsageTag.MGMT.name()), 4);
      createHostServices(sharedUsageTags, 3);
      createHostServices(Collections.singleton(UsageTag.CLOUD.name()), 3);

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
      finalState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      TestHelper.assertTaskStateFinished(finalState.taskState);
      validateContainerServices();
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateContainerTemplates(Integer hostCount) throws Throwable {
      populateInvalidContainersConfig();
      startTestEnvironment(hostCount);

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
      finalState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("containerImage cannot be null"));
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailureCreateVmSpecLayout(Integer hostCount) throws Throwable {
      startTestEnvironment(hostCount);
      createHostServices(Collections.singleton(UsageTag.CLOUD.name()), 10);

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              startState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
      finalState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.failure.message, containsString("Found 0 hosts with usageTag: MGMT"));
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }

    private void validateContainerServices() throws Throwable {

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = kindClause;
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
      assertThat(documentLinks.size(), is(containersConfig.getContainerSpecs().size()));

      Set<String> jobsToCreate = new HashSet<>(containersConfig.getContainerSpecs().keySet());
      for (String documentLink : documentLinks) {
        jobsToCreate.remove(testEnvironment.getServiceState(documentLink, ContainerTemplateService.State.class).name);
      }
      assertThat(jobsToCreate.size(), is(0));
    }

    private void populateInvalidContainersConfig() {
      ContainersConfig.Spec spec = new ContainersConfig.Spec();
      spec.setCpuCount(-1);
      spec.setType(ContainersConfig.ContainerType.PhotonControllerCore.name());

      containersConfig = new ContainersConfig();
      containersConfig.setContainers(Collections.singletonList(spec));
    }

    private void startTestEnvironment(Integer hostCount) throws Throwable {
      testEnvironment = new TestEnvironment.Builder().containersConfig(containersConfig).cloudServerSet
          (cloudStoreMachine.getServerSet()).hostCount(hostCount)
          .build();
    }

    private void createHostServices(Set<String> usageTags, int count) throws Throwable {
      for (int i = 0; i < count; i++) {
        TestHelper.createHostService(cloudStoreMachine, usageTags);
      }
    }
  }

  private CreateManagementPlaneLayoutWorkflowService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      CreateManagementPlaneLayoutWorkflowService.TaskState.SubStage substage) {

    CreateManagementPlaneLayoutWorkflowService.State startState =
        new CreateManagementPlaneLayoutWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

    if (null != taskStage) {
      startState.taskState = new CreateManagementPlaneLayoutWorkflowService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = substage;
    }

    return startState;
  }
}
