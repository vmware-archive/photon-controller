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
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.CreateManagementPlaneLayoutWorkflowService;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.Executors;


/**
 * This class implements test for the
 * {@link RegisterAuthClientTaskService} class.
 */
public class RegisterAuthClientTaskServiceTest {

  @Test(enabled = false)
  public void dummy() {
  }

  private RegisterAuthClientTaskService.State buildValidStartState(@Nullable TaskState.TaskStage startStage) {
    RegisterAuthClientTaskService.State startState = new RegisterAuthClientTaskService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.deploymentServiceLink = "DEPLOYMENT_SERVICE";
    startState.loginRedirectUrlTemplate = "LOGIN_REDIRECT_URL_TEMPLATE %s";
    startState.logoutRedirectUrlTemplate = "LOGOUT_REDIRECT_URL_TEMPLATE %s";

    if (null != startStage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = startStage;
    }

    return startState;
  }

  private RegisterAuthClientTaskService.State buildValidPatchState(TaskState.TaskStage patchStage) {
    RegisterAuthClientTaskService.State patchState = new RegisterAuthClientTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;

    return patchState;
  }

  /**
   * Tests for constructors.
   */
  @Test
  public class InitializationTest {

    @Test
    public void testCapabilities() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION
      );

      RegisterAuthClientTaskService registerAuthClientTaskService = new RegisterAuthClientTaskService();
      assertThat(registerAuthClientTaskService.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {

    private RegisterAuthClientTaskService registerAuthClientTaskService;
    private TestHost testHost;
    private boolean serviceCreated = false;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      registerAuthClientTaskService = new RegisterAuthClientTaskService();
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
    public void testTransitionalStartState(@Nullable TaskState.TaskStage startStage) throws Throwable {
      startService(buildValidStartState(startStage));
      RegisterAuthClientTaskService.State serviceState
          = testHost.getServiceState(RegisterAuthClientTaskService.State.class);
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
    public void testTerminalStartState(TaskState.TaskStage startStage) throws Throwable {
      RegisterAuthClientTaskService.State startState = buildValidStartState(startStage);
      startState.controlFlags = null;
      startService(startState);

      RegisterAuthClientTaskService.State serviceState
          = testHost.getServiceState(RegisterAuthClientTaskService.State.class);
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

    private void startService(RegisterAuthClientTaskService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(registerAuthClientTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private TestHost testHost;
    private RegisterAuthClientTaskService registerAuthClientTaskService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      registerAuthClientTaskService = new RegisterAuthClientTaskService();
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

      RegisterAuthClientTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(registerAuthClientTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

      testHost.sendRequestAndWait(patchOperation);
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

      RegisterAuthClientTaskService.State startState = buildValidStartState(startStage);
      Operation startOperation = testHost.startServiceSynchronously(registerAuthClientTaskService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage));

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
  }

  /**
   * End-to-end tests.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private TestEnvironment testEnvironment = null;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStoreMachine;
    private RegisterAuthClientTaskService.State startState;
    private AuthHelperFactory authHelperFactory;
    private AuthHelper authHelper;
    private ListeningExecutorService listeningExecutorService;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private DeploymentService.State deploymentServiceState;
    private DeployerConfig deployerConfig;
    private AuthClientHandler.ImplicitClient implicitClient;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      implicitClient = new AuthClientHandler.ImplicitClient("client", "http://login", "http://logout");
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      authHelper = mock(AuthHelper.class);

      authHelperFactory = mock(AuthHelperFactory.class);
      doReturn(authHelper).when(authHelperFactory).create();

      DockerProvisioner dockerProvisioner = Mockito.mock(DockerProvisioner.class);
      when(dockerProvisionerFactory.create(anyString())).thenReturn(dockerProvisioner);
      when(dockerProvisioner.launchContainer(anyString(), anyString(), anyInt(), anyLong(), anyMap(), anyMap(),
          anyString(), anyBoolean(), anyMap(), anyBoolean(), anyBoolean(),
          anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("id");

      startTestEnvironment();
      setupDeploymentServiceDocuments();

      startState = buildValidStartState(TaskState.TaskStage.CREATED);
      startState.controlFlags = null;
      startState.deploymentServiceLink = deploymentServiceState.documentSelfLink;
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

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
    }

    @Test
    public void testEndToEndSuccess() throws Throwable {
      setupMgmtServiceDocuments();

      doReturn(implicitClient)
          .when(authHelper)
          .getResourceLoginUri(
              eq(deploymentServiceState.oAuthTenantName),
              eq(deploymentServiceState.oAuthTenantName + "\\" + deploymentServiceState.oAuthUserName),
              eq(deploymentServiceState.oAuthPassword),
              eq(deploymentServiceState.oAuthServerAddress),
              eq(deploymentServiceState.oAuthServerPort),
              anyString(), anyString());

      RegisterAuthClientTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          RegisterAuthClientTaskFactoryService.SELF_LINK,
          startState,
          RegisterAuthClientTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
      assertThat(finalState.loginUrl, is("http://login"));
      assertThat(finalState.logoutUrl, is("http://logout"));
    }

    @Test
    public void testEndToEndFailedToAuthenticate() throws Throwable {
      setupMgmtServiceDocuments();

      doThrow(new AuthException("Failed to authentication."))
          .when(authHelper)
          .getResourceLoginUri(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyString());

      RegisterAuthClientTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          RegisterAuthClientTaskFactoryService.SELF_LINK,
          startState,
          RegisterAuthClientTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testEndToEndFailedToGetLbIp() throws Throwable {
      doReturn(implicitClient)
          .when(authHelper)
          .getResourceLoginUri(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyString());

      RegisterAuthClientTaskService.State finalState = testEnvironment.callServiceAndWaitForState(
          RegisterAuthClientTaskFactoryService.SELF_LINK,
          startState,
          RegisterAuthClientTaskService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void startTestEnvironment() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      testEnvironment = new TestEnvironment.Builder()
          .authHelperFactory(authHelperFactory)
          .deployerContext(deployerConfig.getDeployerContext())
          .containersConfig(deployerConfig.getContainersConfig())
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1)
          .build();
    }

    private void setupDeploymentServiceDocuments() throws Throwable {
      DeploymentService.State deploymentStartState = TestHelper.getDeploymentServiceStartState(false, false);
      deploymentStartState.oAuthServerAddress = "http://lotus";
      deploymentStartState.oAuthServerPort = 433;
      deploymentStartState.oAuthUserName = "user1";
      deploymentStartState.oAuthPassword = "password1";
      deploymentStartState.oAuthTenantName = "tenant1";
      deploymentStartState.loadBalancerEnabled = true;
      deploymentStartState.loadBalancerAddress = "http://lb";
      deploymentServiceState = TestHelper.createDeploymentService(cloudStoreMachine, deploymentStartState);
    }

    private void setupMgmtServiceDocuments() throws Throwable {
      //
      // Creating 2 to accommodate incompatible Lightwave and LoadBalancer containers
      //
      TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));

      CreateManagementPlaneLayoutWorkflowService.State workflowStartState =
          new CreateManagementPlaneLayoutWorkflowService.State();
      workflowStartState.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());

      CreateManagementPlaneLayoutWorkflowService.State finalState =
          testEnvironment.callServiceAndWaitForState(
              CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
              workflowStartState,
              CreateManagementPlaneLayoutWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }
  }
}
