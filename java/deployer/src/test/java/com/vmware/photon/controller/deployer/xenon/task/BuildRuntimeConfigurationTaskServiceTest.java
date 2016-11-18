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

import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
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
import com.vmware.xenon.common.Utils;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements tests for the {@link BuildRuntimeConfigurationTaskService} class.
 */
public class BuildRuntimeConfigurationTaskServiceTest {

  /**
   * This dummy test case enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = true)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private BuildRuntimeConfigurationTaskService buildRuntimeConfigurationTaskService;

    @BeforeClass
    public void setUpClass() {
      buildRuntimeConfigurationTaskService = new BuildRuntimeConfigurationTaskService();
    }

    @Test
    public void testOptions() {
      assertThat(buildRuntimeConfigurationTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the {@link BuildRuntimeConfigurationTaskService#handleStart(Operation)}
   * method.
   */
  public class HandleStartTest {

    private BuildRuntimeConfigurationTaskService buildRuntimeConfigurationTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      buildRuntimeConfigurationTaskService = new BuildRuntimeConfigurationTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Nothing
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(
        TaskState.TaskStage taskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage subStage) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation op = testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State serviceState =
          testHost.getServiceState(BuildRuntimeConfigurationTaskService.State.class);

      if (taskStage == null) {
        assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.CREATED));
      } else {
        assertThat(serviceState.taskState.stage, is(taskStage));
      }

      assertThat(serviceState.taskState.subStage, is(subStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return TestHelper.getValidStartStages(BuildRuntimeConfigurationTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStartStages", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStage(
        TaskState.TaskStage taskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage subStage) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(taskStage, subStage);
      testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
    }

    @DataProvider(name = "InvalidStartStages")
    public Object[][] getInvalidStartStages() {
      return TestHelper.getInvalidStartStages(BuildRuntimeConfigurationTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidStartStateRequiredFieldMissing(String fieldName) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildRuntimeConfigurationTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the {@link BuildRuntimeConfigurationTaskService#handlePatch(Operation)}
   * method.
   */
  public class HandlePatchTest {

    private BuildRuntimeConfigurationTaskService buildRuntimeConfigurationTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      buildRuntimeConfigurationTaskService = new BuildRuntimeConfigurationTaskService();
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
        TaskState.TaskStage startTaskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchTaskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage patchSubStage) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(startTaskStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(BuildRuntimeConfigurationTaskService.buildPatch(patchTaskStage, patchSubStage));

      op = testHost.sendRequestAndWait(patchOp);
      assertThat(op.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State serviceState =
          testHost.getServiceState(BuildRuntimeConfigurationTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchTaskStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return TestHelper.getValidStageTransitions(BuildRuntimeConfigurationTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = BadRequestException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startTaskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchTaskStage,
        BuildRuntimeConfigurationTaskService.TaskState.SubStage patchSubStage) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(startTaskStage, startSubStage);
      Operation op = testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(BuildRuntimeConfigurationTaskService.buildPatch(patchTaskStage, patchSubStage));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return TestHelper.getInvalidStageTransitions(BuildRuntimeConfigurationTaskService.TaskState.SubStage.class);
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State patchState = BuildRuntimeConfigurationTaskService.buildPatch(
          TaskState.TaskStage.STARTED, BuildRuntimeConfigurationTaskService.TaskState.SubStage.BUILD_COMMON_STATE);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildRuntimeConfigurationTaskService.State.class, Immutable.class));
    }

    @Test(dataProvider = "WriteOnceFieldNames", expectedExceptions = BadRequestException.class)
    public void testInvalidPatchWriteOnceFieldWrittenTwice(String fieldName) throws Throwable {
      BuildRuntimeConfigurationTaskService.State startState = buildValidStartState(null, null);
      Operation op = testHost.startServiceSynchronously(buildRuntimeConfigurationTaskService, startState);
      assertThat(op.getStatusCode(), is(200));

      BuildRuntimeConfigurationTaskService.State patchState = BuildRuntimeConfigurationTaskService.buildPatch(
          TaskState.TaskStage.STARTED, BuildRuntimeConfigurationTaskService.TaskState.SubStage.BUILD_COMMON_STATE);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);

      try {
        testHost.sendRequestAndWait(patchOp);
      } catch (BadRequestException e) {
        throw new RuntimeException(e);
      }

      BuildRuntimeConfigurationTaskService.State serviceState =
          testHost.getServiceState(BuildRuntimeConfigurationTaskService.State.class);
      assertThat(declaredField.get(serviceState), is(ReflectionUtils.getDefaultAttributeValue(declaredField)));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "WriteOnceFieldNames")
    public Object[][] getWriteOnceFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              BuildRuntimeConfigurationTaskService.State.class, WriteOnce.class));
    }
  }

  /**
   * This class implements end-to-end tests for the {@link BuildRuntimeConfigurationTaskService}
   * task.
   */
  public class EndToEndTest {

    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerTestConfig deployerTestConfig;
    private BuildRuntimeConfigurationTaskService.State startState;
    private TestEnvironment testEnvironment;

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerTestConfig =
          ConfigBuilder.build(DeployerTestConfig.class, this.getClass().getResource("/config.yml").getPath());
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

    @Test(dataProvider = "ContainerTypesAuthEnabled")
    public void testOneHostRuntimeStateAuthEnabled(ContainersConfig.ContainerType containerType) throws Throwable {
      testRuntimeStateFromFile(containerType, true, 1, getExpectedParameters("1host-auth", containerType));
    }

    @Test(dataProvider = "ContainerTypesAuthDisabled")
    public void testOneHostRuntimeStateAuthDisabled(ContainersConfig.ContainerType containerType) throws Throwable {
      testRuntimeStateFromFile(containerType, false, 1, getExpectedParameters("1host", containerType));
    }


    @Test(dataProvider = "ContainerTypesAuthEnabled")
    public void testThreeHostRuntimeStateAuthEnabled(ContainersConfig.ContainerType containerType) throws Throwable {
      testRuntimeStateFromFile(containerType, true, 3, getExpectedParameters("3host-auth", containerType));
    }

    @Test(dataProvider = "ContainerTypesAuthDisabled")
    public void testThreeHostRuntimeStateAuthDisabled(ContainersConfig.ContainerType containerType) throws Throwable {
      testRuntimeStateFromFile(containerType, false, 3, getExpectedParameters("3host", containerType));
    }

    @DataProvider(name = "ContainerTypesAuthEnabled")
    public Object[][] getContainerTypesAuthEnabled() {
      return TestHelper.toDataProvidersList(Arrays.asList(ContainersConfig.ContainerType.values()));
    }

    @DataProvider(name = "ContainerTypesAuthDisabled")
    public Object[][] getContainerTypesAuthDisabled() {

      //
      // N.B. Lightwave is deployed only when authentication is enabled.
      //

      return TestHelper.toDataProvidersList(Stream.of(ContainersConfig.ContainerType.values())
          .filter((type) -> type != ContainersConfig.ContainerType.Lightwave)
          .collect(Collectors.toList()));
    }

    private Map<String, String> getExpectedParameters(String testCase, ContainersConfig.ContainerType containerType)
        throws Throwable {
      Path fixturePath = Paths.get("/fixtures/dynamic-parameters", testCase, containerType.name() + ".json");
      File fixture = new File(this.getClass().getResource(fixturePath.toString()).getPath());

      // from Json is trying to do proper object conversion which results in some objects
      // being mapped to Integer or Boolean
      Map<?, ?> fromJson = Utils.fromJson(FileUtils.readFileToString(fixture), Map.class);

      HashMap<String, String> map = new HashMap<>();
      for (Map.Entry<?, ?> entry : fromJson.entrySet()) {
        map.put(entry.getKey().toString(), entry.getValue().toString());
      }

      return map;
    }

    private void testRuntimeStateFromFile(ContainersConfig.ContainerType containerType,
                                          boolean authEnabled,
                                          int hostCount,
                                          Map<String, String> expectedParameters) throws Throwable {

      createDeploymentService(authEnabled);

      Map<ContainersConfig.ContainerType, ContainerTemplateService.State> templateMap = new HashMap<>();
      for (ContainersConfig.ContainerType type : ContainersConfig.ContainerType.values()) {
        templateMap.put(type, TestHelper.createContainerTemplateService(testEnvironment, type));
      }

      for (int i = 0; i < hostCount; i++) {
        HostService.State hostState = TestHelper.createHostService(cloudStoreEnvironment, UsageTag.MGMT);
        VmService.State vmStartState = TestHelper.getVmServiceStartState(hostState);
        vmStartState.ipAddress = "0.0.0." + i;
        VmService.State vmState = TestHelper.createVmService(testEnvironment, vmStartState);
        for (ContainersConfig.ContainerType currentType : ContainersConfig.ContainerType.values()) {
          int desiredIndex = hostCount - 1;
          switch (currentType) {

            //
            // N.B. Lightwave is deployed as a singleton container, and only when authentication is
            // enabled.
            //

            case Lightwave:
              if (!authEnabled || i != 0) {
                continue;
              }

              desiredIndex = 0;
              break;

            //
            // N.B. HAProxy is deployed as a singleton container.
            //

            case LoadBalancer:
              if (i != 0) {
                continue;
              }

              desiredIndex = 0;
              break;
          }

          ContainerService.State containerState =
              TestHelper.createContainerService(testEnvironment, templateMap.get(currentType), vmState);

          if (currentType == containerType && i == desiredIndex) {
            startState.containerServiceLink = containerState.documentSelfLink;
          }
        }
      }

      BuildRuntimeConfigurationTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              BuildRuntimeConfigurationTaskFactoryService.SELF_LINK,
              startState,
              BuildRuntimeConfigurationTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());
      expectedParameters = new HashMap<>(expectedParameters);
      assertThat(expectedParameters.size(), is(finalState.dynamicParameters.size()));
      assertThat(expectedParameters.entrySet(), everyItem(isIn(finalState.dynamicParameters.entrySet())));
      assertThat(finalState.dynamicParameters.entrySet(), everyItem(isIn(expectedParameters.entrySet())));

      ContainerService.State containerState =
          testEnvironment.getServiceState(startState.containerServiceLink, ContainerService.State.class);

      assertThat(containerState.dynamicParameters, is(expectedParameters));

      DeploymentService.State deploymentState =
          cloudStoreEnvironment.getServiceState(startState.deploymentServiceLink, DeploymentService.State.class);

      switch (containerType) {
        case Lightwave:
          assertThat(deploymentState.oAuthServerAddress, is("0.0.0.0"));
          assertThat(deploymentState.oAuthServerPort, is(443));
          break;
        case LoadBalancer:
          assertThat(deploymentState.loadBalancerAddress, is("0.0.0.0"));
          break;
      }
    }

    private void createDeploymentService(boolean authEnabled) throws Throwable {
      DeploymentService.State deploymentStartState = TestHelper.getDeploymentServiceStartState(authEnabled, false);
      deploymentStartState.oAuthServerAddress = null;
      if (authEnabled) {
        deploymentStartState.oAuthMgmtUiLoginEndpoint = "MGMT_UI_LOGIN_URL";
        deploymentStartState.oAuthMgmtUiLogoutEndpoint = "MGMT_UI_LOGOUT_URL";
        deploymentStartState.oAuthPassword = "PASSWORD";
        deploymentStartState.oAuthSwaggerLoginEndpoint = "SWAGGER_LOGIN_URL";
        deploymentStartState.oAuthSwaggerLogoutEndpoint = "SWAGGER_LOGOUT_URL";
        deploymentStartState.oAuthTenantName = "TENANT_NAME";
      }

      startState.deploymentServiceLink =
          TestHelper.createDeploymentService(cloudStoreEnvironment, deploymentStartState).documentSelfLink;
    }
  }

  private BuildRuntimeConfigurationTaskService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      BuildRuntimeConfigurationTaskService.TaskState.SubStage subStage) {
    BuildRuntimeConfigurationTaskService.State startState = new BuildRuntimeConfigurationTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.containerServiceLink = "CONTAINER_SERVICE_LINK";

    if (taskStage != null) {
      startState.taskState = new BuildRuntimeConfigurationTaskService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
