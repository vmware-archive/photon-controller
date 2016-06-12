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

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServiceFileConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelper;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

/**
 * This class implements tests for the {@link CreateContainerTaskService} class.
 */
public class CreateContainerTaskServiceTest {

  /**
   * This dummy test enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for object initialization.
   */
  public class InitializationTest {

    private CreateContainerTaskService createContainerTaskService;

    @BeforeMethod
    public void setUpTest() {
      createContainerTaskService = new CreateContainerTaskService();
    }

    @Test
    public void testServiceOptions() {
      assertThat(createContainerTaskService.getOptions(), is(EnumSet.noneOf(Service.ServiceOption.class)));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private CreateContainerTaskService createContainerTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createContainerTaskService = new CreateContainerTaskService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(TaskState.TaskStage taskStage,
                                    CreateContainerTaskService.TaskState.SubStage subStage)
        throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State serviceState =
          testHost.getServiceState(CreateContainerTaskService.State.class);
      assertThat(serviceState.deploymentServiceLink, is("DEPLOYMENT_SERVICE_LINK"));
      assertThat(serviceState.containerServiceLink, is("CONTAINER_SERVICE_LINK"));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage taskStage,
                                           CreateContainerTaskService.TaskState.SubStage subStage)
        throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(taskStage, subStage);
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State serviceState =
          testHost.getServiceState(CreateContainerTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage taskStage,
                                       CreateContainerTaskService.TaskState.SubStage subStage)
        throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(taskStage, subStage);
      startState.controlFlags = null;
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State serviceState =
          testHost.getServiceState(CreateContainerTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(taskStage));
      assertThat(serviceState.taskState.subStage, is(subStage));
      assertThat(serviceState.controlFlags, is(0));
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredFieldName(String fieldName) throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(null, null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);
      testHost.startServiceSynchronously(createContainerTaskService, startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateContainerTaskService.State.class, NotNull.class));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private CreateContainerTaskService createContainerTaskService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      createContainerTaskService = new CreateContainerTaskService();
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
                                         CreateContainerTaskService.TaskState.SubStage startSubStage,
                                         TaskState.TaskStage patchStage,
                                         CreateContainerTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(CreateContainerTaskService.buildPatch(patchStage, patchSubStage, null));

      assertThat(testHost.sendRequestAndWait(patchOp).getStatusCode(), is(200));

      CreateContainerTaskService.State serviceState =
          testHost.getServiceState(CreateContainerTaskService.State.class);
      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
      assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           CreateContainerTaskService.TaskState.SubStage startSubStage,
                                           TaskState.TaskStage patchStage,
                                           CreateContainerTaskService.TaskState.SubStage patchSubStage)
        throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(CreateContainerTaskService.buildPatch(patchStage, patchSubStage, null));

      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, CreateContainerTaskService.TaskState.SubStage.WAIT_FOR_SERVICE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldSet(String fieldName) throws Throwable {
      CreateContainerTaskService.State startState = buildValidStartState(null, null);
      Operation startOp = testHost.startServiceSynchronously(createContainerTaskService, startState);
      assertThat(startOp.getStatusCode(), is(200));

      CreateContainerTaskService.State patchState =
          CreateContainerTaskService.buildPatch(TaskState.TaskStage.STARTED,
              CreateContainerTaskService.TaskState.SubStage.CREATE_CONTAINER, null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));
      Operation patchOp = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);
      testHost.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getImmutableFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              CreateContainerTaskService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the service.
   */
  public class EndToEndTest {

    private static final String SWAGGER_LOGIN_URL = "http://1.2.3.4/swagger_login";
    private static final String SWAGGER_LOGOUT_URL = "http://1.2.3.4/swagger_logout";
    private static final String MGMT_UI_LOGIN_URL = "http://1.2.3.4/mgmt_ui_login";
    private static final String MGMT_UI_LOGOUT_URL = "http://1.2.3.4/mgmt_ui_logout";

    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreEnvironment;
    private DeployerConfig deployerConfig;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private CreateContainerTaskService.State startState;
    private TestEnvironment testEnvironment;

    private class CaptorHolder {

      @Captor
      ArgumentCaptor<Map<String, String>> captor;

      public CaptorHolder() {
        MockitoAnnotations.initMocks(this);
      }
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      cloudStoreEnvironment = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      deployerConfig = ConfigBuilder.build(DeployerConfig.class, getClass().getResource("/config.yml").getPath());
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);

      TestHelper.setContainersConfig(deployerConfig);

      testEnvironment = new TestEnvironment.Builder()
          .cloudServerSet(cloudStoreEnvironment.getServerSet())
          .containersConfig(deployerConfig.getContainersConfig())
          .deployerContext(deployerConfig.getDeployerContext())
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .hostCount(1)
          .build();
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      TestHelper.assertNoServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.assertNoServicesOfType(testEnvironment, VmService.State.class);

      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.requiredPollCount = 3;
      startState.maximumPollCount = 10;
      startState.taskPollDelay = 10;
    }

    public void createTestDocuments(ContainersConfig.ContainerType containerType, boolean authEnabled)
        throws Throwable {

      DeploymentService.State deploymentStartState = TestHelper.getDeploymentServiceStartState(
          authEnabled, false);

      if (authEnabled) {
        deploymentStartState.oAuthSwaggerLoginEndpoint = SWAGGER_LOGIN_URL;
        deploymentStartState.oAuthSwaggerLogoutEndpoint = SWAGGER_LOGOUT_URL;
        deploymentStartState.oAuthMgmtUiLoginEndpoint = MGMT_UI_LOGIN_URL;
        deploymentStartState.oAuthMgmtUiLogoutEndpoint = MGMT_UI_LOGOUT_URL;
      }

      DeploymentService.State deploymentState =
          TestHelper.createDeploymentService(cloudStoreEnvironment, deploymentStartState);

      ContainerTemplateService.State templateState =
          TestHelper.createContainerTemplateService(testEnvironment,
              deployerConfig.getContainersConfig().getContainerSpecs().get(containerType.name()));

      VmService.State vmState = TestHelper.createVmService(testEnvironment);

      ContainerService.State containerState =
          TestHelper.createContainerService(testEnvironment, templateState, vmState);

      startState.deploymentServiceLink = deploymentState.documentSelfLink;
      startState.containerServiceLink = containerState.documentSelfLink;
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      TestHelper.deleteServicesOfType(cloudStoreEnvironment, DeploymentService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, ContainerTemplateService.State.class);
      TestHelper.deleteServicesOfType(testEnvironment, VmService.State.class);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      testEnvironment.stop();
      cloudStoreEnvironment.stop();
    }

    @Test(dataProvider = "ContainerTypes")
    public void testSuccess(ContainersConfig.ContainerType containerType) throws Throwable {
      testSuccess(containerType, false);
    }

    @Test(dataProvider = "ContainerTypes")
    public void testSuccessWithAuthEnabled(ContainersConfig.ContainerType containerType) throws Throwable {
      testSuccess(containerType, true);
    }

    private void testSuccess(ContainersConfig.ContainerType containerType, Boolean authEnabled) throws Throwable {

      createTestDocuments(containerType, authEnabled);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());
      doReturn("CONTAINER_ID")
          .when(dockerProvisioner)
          .launchContainer(anyString(),
              anyString(),
              anyInt(),
              anyLong(),
              Matchers.<Map<String, String>>any(),
              Matchers.<Map<Integer, Integer>>any(),
              anyString(),
              anyBoolean(),
              Matchers.<Map<String, String>>any(),
              anyBoolean(),
              anyBoolean(),
              anyVararg());

      HealthChecker successfulHealthChecker = () -> true;
      HealthChecker failureHealthChecker = () -> false;
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);

      doReturn(successfulHealthChecker)
          .doReturn(successfulHealthChecker)
          .doReturn(failureHealthChecker)
          .doReturn(successfulHealthChecker)
          .when(healthCheckHelper).getHealthChecker();

      doReturn(healthCheckHelper).when(healthCheckHelperFactory)
          .create(any(Service.class), any(ContainersConfig.ContainerType.class), anyString());

      CreateContainerTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.taskState.subStage, nullValue());

      ContainersConfig.Spec containerSpec =
          deployerConfig.getContainersConfig().getContainerSpecs().get(containerType.name());

      CaptorHolder volumeBindingsCaptor = new CaptorHolder();
      CaptorHolder environmentVariablesCaptor = new CaptorHolder();

      verify(dockerProvisioner, times(1)).launchContainer(
          Matchers.eq(containerSpec.getServiceName()),
          Matchers.eq(containerSpec.getContainerImage()),
          anyInt(),
          anyLong(),
          volumeBindingsCaptor.captor.capture(),
          Matchers.eq(containerSpec.getPortBindings()),
          Matchers.eq(containerSpec.getVolumesFrom()),
          Matchers.eq(containerSpec.getIsPrivileged()),
          environmentVariablesCaptor.captor.capture(),
          Matchers.eq(true),
          Matchers.eq(containerSpec.getUseHostNetwork()),
          anyVararg());

      String hostVolumeKeyName = ServiceFileConstants.VM_MUSTACHE_DIRECTORY +
          ServiceFileConstants.CONTAINER_CONFIG_ROOT_DIRS.get(containerType);

      Map<String, String> volumeBindings = volumeBindingsCaptor.captor.getValue();
      assertThat(volumeBindings, hasKey(hostVolumeKeyName));
      String volumeBindingValue = volumeBindings.get(hostVolumeKeyName);
      assertThat(volumeBindingValue, containsString(ServiceFileConstants.CONTAINER_CONFIG_DIRECTORY));
      switch (containerType) {
        case Zookeeper:
          assertThat(volumeBindingValue, containsString(CreateContainerTaskService.ZOOKEEPER_CONF_DIR));
          assertThat(volumeBindingValue, containsString(CreateContainerTaskService.ZOOKEEPER_DATA_DIR));
          break;
        case LoadBalancer:
          assertThat(volumeBindingValue, containsString(CreateContainerTaskService.HAPROXY_CONF_DIR));
          break;
        case Lightwave:
          assertThat(volumeBindingValue, containsString(CreateContainerTaskService.LIGHTWAVE_CONF_DIR));
          break;
      }

      Map<String, String> environmentVariables = environmentVariablesCaptor.captor.getValue();
      for (Map.Entry<String, String> entry : containerSpec.getDynamicParameters().entrySet()) {
        assertThat(environmentVariables, hasEntry(entry.getKey(), entry.getValue()));
      }

      if (authEnabled) {
        assertThat(environmentVariables,
            hasEntry(CreateContainerTaskService.ENV_MGMT_API_SWAGGER_LOGIN_URL, SWAGGER_LOGIN_URL));
        assertThat(environmentVariables,
            hasEntry(CreateContainerTaskService.ENV_MGMT_API_SWAGGER_LOGOUT_URL, SWAGGER_LOGOUT_URL));
        assertThat(environmentVariables,
            hasEntry(CreateContainerTaskService.ENV_MGMT_UI_LOGIN_URL, MGMT_UI_LOGIN_URL));
        assertThat(environmentVariables,
            hasEntry(CreateContainerTaskService.ENV_MGMT_UI_LOGOUT_URL, MGMT_UI_LOGOUT_URL));
      }

      verify(healthCheckHelper, times(6)).getHealthChecker();
    }

    @DataProvider(name = "ContainerTypes")
    public Object[][] getEndToEndTestConfig() {
      return TestHelper.toDataProvidersList(Arrays.asList(ContainersConfig.ContainerType.values()));
    }

    @Test(dataProvider = "OneContainerType")
    public void testFailureContainerCreationException(ContainersConfig.ContainerType containerType) throws Throwable {

      createTestDocuments(containerType, false);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());
      doThrow(new RuntimeException("Exception during launchContainer call"))
          .when(dockerProvisioner)
          .launchContainer(
              anyString(),
              anyString(),
              anyInt(),
              anyLong(),
              Matchers.<Map<String, String>>any(),
              Matchers.<Map<Integer, Integer>>any(),
              anyString(),
              anyBoolean(),
              Matchers.<Map<String, String>>any(),
              anyBoolean(),
              anyBoolean(),
              anyVararg());

      CreateContainerTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Exception during launchContainer call"));
    }

    @Test(dataProvider = "OneContainerType")
    public void testFailureContainerCreationNullResult(ContainersConfig.ContainerType containerType) throws Throwable {

      createTestDocuments(containerType, false);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());
      doReturn(null)
          .when(dockerProvisioner)
          .launchContainer(
              anyString(),
              anyString(),
              anyInt(),
              anyLong(),
              Matchers.<Map<String, String>>any(),
              Matchers.<Map<Integer, Integer>>any(),
              anyString(),
              anyBoolean(),
              Matchers.<Map<String, String>>any(),
              anyBoolean(),
              anyBoolean(),
              anyVararg());

      CreateContainerTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Create container returned null"));
    }

    @Test(dataProvider = "OneContainerType")
    public void testFailureServiceNotReady(ContainersConfig.ContainerType containerType) throws Throwable {

      createTestDocuments(containerType, false);

      DockerProvisioner dockerProvisioner = mock(DockerProvisioner.class);
      doReturn(dockerProvisioner).when(dockerProvisionerFactory).create(anyString());
      doReturn("CONTAINER_ID")
          .when(dockerProvisioner)
          .launchContainer(
              anyString(),
              anyString(),
              anyInt(),
              anyLong(),
              Matchers.<Map<String, String>>any(),
              Matchers.<Map<Integer, Integer>>any(),
              anyString(),
              anyBoolean(),
              Matchers.<Map<String, String>>any(),
              anyBoolean(),
              anyBoolean(),
              anyVararg());

      HealthChecker successfulHealthChecker = () -> true;
      HealthChecker failureHealthChecker = () -> false;
      HealthCheckHelper healthCheckHelper = mock(HealthCheckHelper.class);

      doReturn(successfulHealthChecker)
          .doReturn(successfulHealthChecker)
          .doReturn(failureHealthChecker)
          .when(healthCheckHelper).getHealthChecker();

      doReturn(healthCheckHelper).when(healthCheckHelperFactory)
          .create(any(Service.class), any(ContainersConfig.ContainerType.class), anyString());

      CreateContainerTaskService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateContainerTaskFactoryService.SELF_LINK,
              startState,
              CreateContainerTaskService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
      assertThat(finalState.taskState.subStage, nullValue());
      assertThat(finalState.taskState.failure.statusCode, is(400));
      assertThat(finalState.taskState.failure.message, containsString("Container CONTAINER_ID of type " +
          containerType + " on VM IP_ADDRESS failed to become ready after 10 iterations"));
    }

    @DataProvider(name = "OneContainerType")
    public Object[][] getOneContainerType() {
      return new Object[][]{
          {ContainersConfig.ContainerType.values()[0]},
      };
    }
  }

  private CreateContainerTaskService.State buildValidStartState(
      TaskState.TaskStage taskStage,
      CreateContainerTaskService.TaskState.SubStage subStage) {
    CreateContainerTaskService.State startState = new CreateContainerTaskService.State();
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE_LINK";
    startState.containerServiceLink = "CONTAINER_SERVICE_LINK";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    if (taskStage != null) {
      startState.taskState = new CreateContainerTaskService.TaskState();
      startState.taskState.stage = taskStage;
      startState.taskState.subStage = subStage;
    }

    return startState;
  }
}
