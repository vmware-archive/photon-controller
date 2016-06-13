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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements tests for {@link CreateContainerSpecLayoutTaskService}.
 */
public class CreateContainerSpecLayoutTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private CreateContainerSpecLayoutTaskService allocateVmsAndContainersTaskService;

    @BeforeMethod
    public void setUpTest() {
      allocateVmsAndContainersTaskService = new CreateContainerSpecLayoutTaskService();
    }

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);

      assertThat(allocateVmsAndContainersTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateContainerSpecLayoutTaskService allocateVmsAndContainersTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateVmsAndContainersTaskService = new CreateContainerSpecLayoutTaskService();
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
      CreateContainerSpecLayoutTaskService.State startState = buildValidStartState();
      Operation startOperation = testHost.startServiceSynchronously(allocateVmsAndContainersTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecLayoutTaskService.State.class);

      assertThat(savedState.taskState.stage, is(TaskState.TaskStage.STARTED));
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(TaskState.TaskStage startStage) throws Throwable {
      CreateContainerSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateVmsAndContainersTaskService, startState);
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
      CreateContainerSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateVmsAndContainersTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecLayoutTaskService.State.class);

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
      CreateContainerSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = 0;
      Operation startOperation = testHost.startServiceSynchronously(allocateVmsAndContainersTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      CreateContainerSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecLayoutTaskService.State.class);

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

    private CreateContainerSpecLayoutTaskService allocateVmsAndContainersTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      allocateVmsAndContainersTaskService = new CreateContainerSpecLayoutTaskService();
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
      CreateContainerSpecLayoutTaskService.State patchState =
          allocateVmsAndContainersTaskService.buildPatch(patchStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      CreateContainerSpecLayoutTaskService.State savedState =
          testHost.getServiceState(CreateContainerSpecLayoutTaskService.State.class);

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
      CreateContainerSpecLayoutTaskService.State patchState =
          allocateVmsAndContainersTaskService.buildPatch(patchStage, null);

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

      CreateContainerSpecLayoutTaskService.State patchState =
          allocateVmsAndContainersTaskService.buildPatch(TaskState.TaskStage.STARTED, null);

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
      CreateContainerSpecLayoutTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(allocateVmsAndContainersTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * End to end tests for {@link CreateContainerSpecLayoutTaskService}.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private DeployerConfig deployerConfig;
    private ContainersConfig containersConfig;
    private CreateContainerSpecLayoutTaskService.State startState;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStore;

    private Map<String, ContainerTemplateService.State> containerTypeStateMap = new HashMap<>();

    private ContainersConfig getContainersConfig() {
      return containersConfig;
    }

    @BeforeClass
    public void setUpClass() throws Throwable {

      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      containersConfig = deployerConfig.getContainersConfig();

      startState = buildValidStartState();
      startState.controlFlags = 0x0;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      testEnvironment = new TestEnvironment.Builder()
          .containersConfig(containersConfig)
          .cloudServerSet(cloudStore.getServerSet())
          .hostCount(1)
          .build();
      containerTypeStateMap.clear();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != testEnvironment) {
        testEnvironment.stop();
        testEnvironment = null;
      }

      if (null != cloudStore) {
        cloudStore.stop();
        cloudStore = null;
      }
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      setupHostsAndVms(null, 3);
      setupContainerTemplates();
      setupVms(3);

      CreateContainerSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      Set<String> containerTemplateServiceDocumentLinks = new HashSet<>();
      for (ContainerTemplateService.State containerTemplateService : containerTypeStateMap.values()) {
        containerTemplateServiceDocumentLinks.add(containerTemplateService.documentSelfLink);
      }
      validateAllocateContainerTaskServices(3, containerTemplateServiceDocumentLinks, Collections.EMPTY_SET);
    }

    @Test
    public void testEndToEndSuccessServicePlacement() throws Throwable {
      setupHostsAndVms(null, 2);
      setupHostAndVm(ImmutableSet.of("ManagementApi"), 3);
      setupContainerTemplates();
      setupVms(3);

      CreateContainerSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      Set<String> containerTemplateServiceDocumentLinks = new HashSet<>();
      for (ContainerTemplateService.State containerTemplateService : containerTypeStateMap.values()) {
        containerTemplateServiceDocumentLinks.add(containerTemplateService.documentSelfLink);
      }
      validateAllocateContainerTaskServices(
          2,
          containerTemplateServiceDocumentLinks,
          Collections.singleton("ManagementApi"));
    }

    @Test
    public void testFailureDueToNoContainerTemplates() throws Throwable {
      setupHostsAndVms(null, 3);
      setupVms(3);

      CreateContainerSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testFailureDueToNoDockerVms() throws Throwable {
      setupHosts(3);
      setupContainerTemplates();

      CreateContainerSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testFailureDueToInsufficientDockerVms() throws Throwable {
      setupHostsAndVms(null, 1);
      setupContainerTemplates();

      CreateContainerSpecLayoutTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerSpecLayoutTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testValidateIncompatibleContainerTypesMatrix() throws Throwable {
      for (String containerType : CreateContainerSpecLayoutTaskService
          .INCOMPATIBLE_CONTAINER_TYPES.keySet()) {
        for (String incompatibleContainerType :
            CreateContainerSpecLayoutTaskService.INCOMPATIBLE_CONTAINER_TYPES.get(containerType)) {
          if (!CreateContainerSpecLayoutTaskService
              .INCOMPATIBLE_CONTAINER_TYPES.containsKey(incompatibleContainerType) ||
              !CreateContainerSpecLayoutTaskService
                  .INCOMPATIBLE_CONTAINER_TYPES.get(incompatibleContainerType).contains(containerType)) {

            fail("Container compatibility matrix does not contain two-way condition for container types " +
                containerType + " and " + incompatibleContainerType);

          }
        }
      }
    }

    private void validateAllocateContainerTaskServices(int numDockerVms, Set<String> containerTemplateDocumentLinks,
                                                       Set<String> singletonServices) throws Throwable {

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(CreateContainerSpecTaskService.State.class));

      QueryTask query = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = testEnvironment.sendBroadcastQueryAndWait(query);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      // Verify that we created the exact number of AllocateContainerTaskService instances as there are
      // containerTemplate document links.
      assertThat(documentLinks.size(), is(containerTemplateDocumentLinks.size()));

      for (String documentLink : documentLinks) {
        CreateContainerSpecTaskService.State state = testEnvironment.getServiceState(documentLink,
            CreateContainerSpecTaskService.State.class);
        ContainerTemplateService.State template = testEnvironment.getServiceState(state.containerTemplateDocumentLink,
            ContainerTemplateService.State.class);
        if (singletonServices.contains(template.name)) {
          assertThat(state.dockerVmDocumentLinks.size(), is(1));
        } else {
          assertThat(state.dockerVmDocumentLinks.size(), is(numDockerVms));
        }

        containerTemplateDocumentLinks.remove(state.containerTemplateDocumentLink);
      }

      // Verify that all containerTemplateDocument links were used.
      assertThat(containerTemplateDocumentLinks.size(), is(0));
    }

    private HostService.State setupHost(Set<String> allowedService, int index) throws Throwable {
      HostService.State hostService =
          TestHelper.getHostServiceStartState(Collections.singleton(UsageTag.MGMT.name()), HostState.CREATING);
      hostService.hostAddress = "1.2.3." + index;
      hostService.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "3.2.1." + index);
      if (allowedService != null && !allowedService.isEmpty()) {
        hostService.metadata
            .put(HostService.State.METADATA_KEY_NAME_ALLOWED_SERVICES, Joiner.on(",").join(allowedService));
      }

      return TestHelper.createHostService(cloudStore, hostService);
    }

    private void setupHosts(int count) throws Throwable {
      for (int i = 0; i < count; ++i) {
        setupHost(Collections.EMPTY_SET, i);
      }
    }

    private VmService.State setupVm(HostService.State hostService, int index) throws Throwable {
      VmService.State vm = TestHelper.getVmServiceStartState();
      vm.name = "vm_name_" + index;
      if (hostService != null) {
        vm.hostServiceLink = hostService.documentSelfLink;
      }

      return TestHelper.createVmService(testEnvironment, vm);
    }

    private void setupVms(int count) throws Throwable {
      for (int i = 0; i < count; i++) {
        setupVm(null, i);
      }
    }

    private void setupHostAndVm(Set<String> allowedService, int index) throws Throwable {
      HostService.State hostService = setupHost(
          allowedService == null ? Collections.EMPTY_SET : allowedService,
          index);
      setupVm(hostService, index);

    }

    private void setupHostsAndVms(Set<String> allowedService, int count) throws Throwable {
      for (; count > 0; count--) {
        setupHostAndVm(allowedService, count);
      }
    }

    private void setupContainerTemplates() throws Throwable {
      for (ContainersConfig.Spec spec : containersConfig.getContainerSpecs().values()) {
        ContainerTemplateService.State state = TestHelper.createContainerTemplateService(testEnvironment, spec);
        containerTypeStateMap.put(spec.getType(), state);
      }
    }
  }

  private CreateContainerSpecLayoutTaskService.State buildValidStartState() {
    CreateContainerSpecLayoutTaskService.State startState = new CreateContainerSpecLayoutTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.taskPollDelay = DeployerDefaults.DEFAULT_TASK_POLL_DELAY;
    startState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null,
        UsageTag.MGMT.name());
    return startState;
  }

  private CreateContainerSpecLayoutTaskService.State buildValidStartState(TaskState.TaskStage taskStage) {
    CreateContainerSpecLayoutTaskService.State startState = buildValidStartState();
    startState.taskState = new TaskState();
    startState.taskState.stage = taskStage;
    startState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null,
        UsageTag.MGMT.name());
    return startState;
  }
}
