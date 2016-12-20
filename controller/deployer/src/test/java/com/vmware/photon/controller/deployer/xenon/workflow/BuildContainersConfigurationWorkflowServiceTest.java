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
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.DeployerTestConfig;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements tests for the {@link BuildContainersConfigurationWorkflowService} class.
 */
public class BuildContainersConfigurationWorkflowServiceTest {

  /**
   * This dummy test case enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private BuildContainersConfigurationWorkflowService buildContainersConfigurationWorkflowService;

    @BeforeClass
    public void setUpClass() {
      buildContainersConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
    }

    @Test
    public void testOptions() {
      assertThat(buildContainersConfigurationWorkflowService.getOptions(),
          is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link BuildContainersConfigurationWorkflowService#handleStart(Operation)}
   * method.
   */
  public class HandleStartTest {

    private BuildContainersConfigurationWorkflowService buildContainersConfigurationWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      buildContainersConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(
        TaskState.TaskStage taskStage,
        BuildContainersConfigurationWorkflowService.TaskState.SubStage subStage) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
      assertThat(op.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State serviceState =
          testHost.getServiceState(BuildContainersConfigurationWorkflowService.State.class);

      if (taskStage == null) {
        assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.CREATED));
      } else {
        assertThat(serviceState.taskState.stage, is(taskStage));
      }

      assertThat(serviceState.taskState.subStage, is(subStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return TestHelper.getValidStartStages(BuildContainersConfigurationWorkflowService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStartStages", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStage(
        TaskState.TaskStage taskStage,
        BuildContainersConfigurationWorkflowService.TaskState.SubStage subStage) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(taskStage, subStage);
      testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
    }

    @DataProvider(name = "InvalidStartStages")
    public Object[][] getInvalidStartStages() {
      return TestHelper.getInvalidStartStages(BuildContainersConfigurationWorkflowService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldNameMissing(String fieldName) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildContainersConfigurationWorkflowService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link BuildContainersConfigurationWorkflowService#handlePatch(Operation)}
   * method.
   */
  public class HandlePatchTest {

    private BuildContainersConfigurationWorkflowService buildContainersConfigurationWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      buildContainersConfigurationWorkflowService = new BuildContainersConfigurationWorkflowService();
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
        BuildContainersConfigurationWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        BuildContainersConfigurationWorkflowService.TaskState.SubStage patchSubStage) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(BuildContainersConfigurationWorkflowService.buildPatch(patchStage, patchSubStage));
      op = testHost.sendRequestAndWait(patchOp);
      assertThat(op.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State serviceState =
          testHost.getServiceState(BuildContainersConfigurationWorkflowService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(BuildContainersConfigurationWorkflowService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        BuildContainersConfigurationWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        BuildContainersConfigurationWorkflowService.TaskState.SubStage patchSubStage) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(BuildContainersConfigurationWorkflowService.buildPatch(patchStage, patchSubStage));
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(
          BuildContainersConfigurationWorkflowService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      BuildContainersConfigurationWorkflowService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(buildContainersConfigurationWorkflowService, startState);
      assertThat(op.getStatusCode(), is(200));

      BuildContainersConfigurationWorkflowService.State patchState =
          BuildContainersConfigurationWorkflowService.buildPatch(TaskState.TaskStage.STARTED,
              BuildContainersConfigurationWorkflowService.TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildContainersConfigurationWorkflowService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link BuildContainersConfigurationWorkflowService}
   * task.
   */
  public class EndToEndTest {

    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerTestConfig deployerTestConfig;
    private BuildContainersConfigurationWorkflowService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerTestConfig = ConfigBuilder.build(
          DeployerTestConfig.class, this.getClass().getResource("/config.yml").getPath());
      TestHelper.setContainersConfig(deployerTestConfig);
      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .containersConfig(deployerTestConfig.getContainersConfig())
          .deployerContext(deployerTestConfig.getDeployerContext())
          .hostCount(1)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, HostService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
    }

    @Test
    public void testOneHostAuthEnabled() throws Throwable {
      testEndToEndSuccess(true, 1);
    }

    @Test
    public void testOneHostAuthDisabled() throws Throwable {
      testEndToEndSuccess(false, 1);
    }

    @Test
    public void testThreeHostsAuthEnabled() throws Throwable {
      testEndToEndSuccess(true, 3);
    }

    @Test
    public void testThreeHostsAuthDisabled() throws Throwable {
      testEndToEndSuccess(false, 3);
    }

    private void testEndToEndSuccess(boolean authEnabled, int hostCount) throws Throwable {
      DeploymentService.State deploymentStartState = TestHelper.createDeploymentService(cloudStoreEnvironment,
          authEnabled, false);
      startState.deploymentServiceLink = deploymentStartState.documentSelfLink;

      Map<ContainersConfig.ContainerType, ContainerTemplateService.State> templateMap = new HashMap<>();
      for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {
        templateMap.put(containerType, TestHelper.createContainerTemplateService(testEnvironment, containerType));
      }

      for (int i = 0; i < hostCount; i++) {
        HostService.State hostState = TestHelper.createHostService(cloudStoreEnvironment, UsageTag.MGMT);
        VmService.State vmStartState = TestHelper.getVmServiceStartState(hostState);
        vmStartState.ipAddress = "0.0.0." + i;
        VmService.State vmState = TestHelper.createVmService(testEnvironment, vmStartState);

        for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {
          switch (containerType) {
            case Lightwave:
            case LoadBalancer:
              if (i != 0) {
                continue;
              }
          }

          TestHelper.createContainerService(testEnvironment, templateMap.get(containerType), vmState);
        }
      }

      BuildContainersConfigurationWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              BuildContainersConfigurationWorkflowFactoryService.SELF_LINK,
              startState,
              BuildContainersConfigurationWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());

      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(ContainerService.State.class)
              .addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK,
                  templateMap.get(ContainersConfig.ContainerType.PhotonControllerCore).documentSelfLink)
              .build())
          .addOptions(EnumSet.of(
              QueryTask.QuerySpecification.QueryOption.BROADCAST,
              QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .build();

      QueryTask result = testEnvironment.sendQueryAndWait(queryTask);
      assertThat(result.results.documentLinks.size(), is(hostCount));
    }
  }

  private BuildContainersConfigurationWorkflowService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      BuildContainersConfigurationWorkflowService.TaskState.SubStage subStage) throws Throwable {

    BuildContainersConfigurationWorkflowService.State startState =
        new BuildContainersConfigurationWorkflowService.State();

    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.hostServiceLink = null;

    if (taskStage != null) {
      startState.taskState = new BuildContainersConfigurationWorkflowService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
