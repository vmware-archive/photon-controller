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

import com.vmware.photon.controller.api.AuthInfo;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.NetworkConnection;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmNetworks;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.DeploymentApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.MockHelper;
import com.vmware.photon.controller.deployer.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.xenon.TestHost;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.Matchers;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the
 * {@link InitializeDeploymentMigrationWorkflowService} class.
 */
public class InitializeDeploymentMigrationWorkflowServiceTest {

  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This method creates a new State object which is sufficient to create a new
   * FinishDeploymentWorkflowService instance.
   */
  private InitializeDeploymentMigrationWorkflowService.State buildValidStartState(
      @Nullable InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
      @Nullable InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
    InitializeDeploymentMigrationWorkflowService.State startState =
        new InitializeDeploymentMigrationWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.sourceLoadBalancerAddress = "lbLink1";
    startState.destinationDeploymentId = "deployment1";
    startState.taskPollDelay = 1;

    if (null != startStage) {
      startState.taskState = new InitializeDeploymentMigrationWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;

      if (TaskState.TaskStage.STARTED == startStage) {
        switch (startSubStage) {
          case CONTINOUS_MIGRATE_DATA:
          case UPLOAD_VIBS:
            startState.sourceZookeeperQuorum = "quorum";
            // fall through
          case PAUSE_DESTINATION_SYSTEM:
            startState.sourceDeploymentId = "deployment1";
            break;
        }
      }
    }

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * InitializeDeploymentMigrationWorkflowService instance.
   */
  private InitializeDeploymentMigrationWorkflowService.State buildValidPatchState(
      TaskState.TaskStage patchStage,
      InitializeDeploymentMigrationWorkflowService.TaskState.SubStage patchSubStage) {

    InitializeDeploymentMigrationWorkflowService.State patchState =
        new InitializeDeploymentMigrationWorkflowService.State();
    patchState.taskState = new InitializeDeploymentMigrationWorkflowService.TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    if (TaskState.TaskStage.STARTED == patchStage) {
      switch (patchSubStage) {
        case CONTINOUS_MIGRATE_DATA:
        case UPLOAD_VIBS:
          patchState.sourceZookeeperQuorum = "quorum";
          break;
        case PAUSE_DESTINATION_SYSTEM:
          patchState.sourceDeploymentId = "deployment1";
          break;
      }
    }

    return patchState;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {
    private InitializeDeploymentMigrationWorkflowService initializeDeploymentMigrationWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      initializeDeploymentMigrationWorkflowService = new InitializeDeploymentMigrationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      initializeDeploymentMigrationWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(initializeDeploymentMigrationWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {
    private InitializeDeploymentMigrationWorkflowService initializeDeploymentMigrationWorkflowService;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      initializeDeploymentMigrationWorkflowService = new InitializeDeploymentMigrationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      try {
        testHost.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not
        // successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartState(
        TaskState.TaskStage startStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      InitializeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(InitializeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][] { { null, null }, { TaskState.TaskStage.CREATED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA },
          { TaskState.TaskStage.FINISHED, null }, { TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.CANCELLED, null }, };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(
        TaskState.TaskStage startStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      InitializeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(InitializeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(
          serviceState.taskState.subStage,
          is(InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][] { { null, null }, { TaskState.TaskStage.CREATED, null }, { TaskState.TaskStage.STARTED,
          InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM }, };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(
        InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage) throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      startState.controlFlags = null;
      startService(startState);

      InitializeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(InitializeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][] { { TaskState.TaskStage.FINISHED, null }, { TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.CANCELLED, null }, };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStartStateMissingRequiredField(String fieldName) throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              InitializeDeploymentMigrationWorkflowService.State.class,
              NotNull.class));
    }

    private void startService(InitializeDeploymentMigrationWorkflowService.State startState) throws Throwable {
      Operation startOperation =
          testHost.startServiceSynchronously(initializeDeploymentMigrationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {
    private InitializeDeploymentMigrationWorkflowService initializeDeploymentMigrationWorkflowService;
    boolean serviceCreated = false;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      initializeDeploymentMigrationWorkflowService = new InitializeDeploymentMigrationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (serviceCreated) {
        testHost.deleteServiceSynchronously();
        serviceCreated = false;
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      TestHost.destroy(testHost);
    }

    @Test(dataProvider = "ValidStageUpdates")
    public void testValidStageUpdates(
        InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage patchStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage patchSubStage) throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation =
          testHost.startServiceSynchronously(initializeDeploymentMigrationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;

      Operation patchOperation = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(
          buildValidPatchState(patchStage, patchSubStage));

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      InitializeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(InitializeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][] {
          { TaskState.TaskStage.CREATED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM,
              TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },
          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS,
              TaskState.TaskStage.FINISHED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA,
              TaskState.TaskStage.FINISHED, null },

          { TaskState.TaskStage.CREATED, null, TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM,
              TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS,
              TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA,
              TaskState.TaskStage.FAILED, null },

          { TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CANCELLED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM,
              TaskState.TaskStage.CANCELLED, null },
          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS,
              TaskState.TaskStage.CANCELLED, null },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA,
              TaskState.TaskStage.CANCELLED, null }, };
    }

    @Test(dataProvider = "InvalidStageUpdates", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        InitializeDeploymentMigrationWorkflowService.TaskState.SubStage patchSubStage) throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation =
          testHost.startServiceSynchronously(initializeDeploymentMigrationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;

      Operation patchOperation = Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(
          buildValidPatchState(patchStage, patchSubStage));

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][] { { TaskState.TaskStage.CREATED, null, TaskState.TaskStage.CREATED, null },

          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM,
              TaskState.TaskStage.CREATED, null },

          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS,
              TaskState.TaskStage.CREATED, null },
          { TaskState.TaskStage.STARTED, InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS,
              TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA,
              TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },

          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CREATED, null },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FINISHED, null },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.FINISHED, null, TaskState.TaskStage.CANCELLED, null },

          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CREATED, null },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FINISHED, null },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.FAILED, null, TaskState.TaskStage.CANCELLED, null },

          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CREATED, null },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.UPLOAD_VIBS },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.STARTED,
              InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.CONTINOUS_MIGRATE_DATA },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FINISHED, null },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.FAILED, null },
          { TaskState.TaskStage.CANCELLED, null, TaskState.TaskStage.CANCELLED, null }, };
    }

    @Test(dataProvider = "ImmutableFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidPatchImmutableFieldChanged(String fieldName) throws Throwable {
      InitializeDeploymentMigrationWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation =
          testHost.startServiceSynchronously(initializeDeploymentMigrationWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;

      InitializeDeploymentMigrationWorkflowService.State patchState = buildValidPatchState(
          TaskState.TaskStage.STARTED,
          InitializeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_DESTINATION_SYSTEM);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      if (declaredField.getType() == Integer.class) {
        declaredField.set(patchState, new Integer(0));
      } else if (declaredField.getType() == Set.class) {
        declaredField.set(patchState, new HashSet<>());
      } else {
        declaredField.set(patchState, declaredField.getType().newInstance());
      }

      Operation patchOperation =
          Operation.createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI)).setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getFieldNamesWithInvalidValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              InitializeDeploymentMigrationWorkflowService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the add cloud host task.
   */
  public class EndToEndTest {

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");
    private static final String configFilePath = "/config.yml";

    private TestEnvironment sourceEnvironment;
    private TestEnvironment destinationEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment sourceCloudStore;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment destinationCloudStore;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private InitializeDeploymentMigrationWorkflowService.State startState;
    private DeployerConfig deployerConfig;
    private DeployerContext deployerContext;

    @BeforeClass
    public void setUpClass() throws Throwable {
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      startState = buildValidStartState(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.CREATED, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;

      sourceCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      destinationCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      FileUtils.deleteDirectory(storageDirectory);
      vibDirectory.mkdirs();
      TestHelper.createSourceFile(null, vibDirectory);

      deployerConfig = spy(ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()));
      deployerContext = spy(deployerConfig.getDeployerContext());
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (sourceEnvironment != null) {
        sourceEnvironment.stop();
      }
      if (destinationEnvironment != null) {
        destinationEnvironment.stop();
      }
      apiClientFactory = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);

      if (sourceCloudStore != null) {
        sourceCloudStore.stop();
        sourceCloudStore = null;
      }

      if (destinationCloudStore != null) {
        destinationCloudStore.stop();
        destinationCloudStore = null;
      }
    }

    private void createTestEnvironment() throws Throwable {
      String quorum = deployerConfig.getZookeeper().getQuorum();
      deployerContext.setZookeeperQuorum(quorum);

      ZookeeperClientFactory zkFactory = mock(ZookeeperClientFactory.class);
      sourceEnvironment = new TestEnvironment.Builder()
          .listeningExecutorService(listeningExecutorService)
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(sourceCloudStore.getServerSet())
          .hostCount(1)
          .build();

      destinationEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .deployerContext(deployerContext)
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(destinationCloudStore.getServerSet())
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .zookeeperServersetBuilderFactory(zkFactory)
          .build();

      ZookeeperClient zkBuilder = mock(ZookeeperClient.class);
      doReturn(zkBuilder).when(zkFactory).create();
      doReturn(
          Collections
              .singleton(new InetSocketAddress("127.0.0.1", sourceEnvironment.getHosts()[0].getState().httpPort)))
                  .when(zkBuilder)
                  .getServers(Matchers.startsWith("127.0.0.1:2181"), eq("cloudstore"));


      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1",
          destinationCloudStore.getHosts()[0].getState().httpPort)))
          .when(zkBuilder)
          .getServers(eq(quorum), eq("cloudstore"));

      ServiceHost sourceHost = sourceEnvironment.getHosts()[0];
      startState.sourceLoadBalancerAddress = sourceHost.getPublicUri().toString();

      TestHelper.createHostService(sourceCloudStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(sourceCloudStore, Collections.singleton(UsageTag.CLOUD.name()));
      DeploymentService.State deploymentService = TestHelper.createDeploymentService(destinationCloudStore);
      startState.destinationDeploymentId = ServiceUtils.getIDFromDocumentSelfLink(deploymentService.documentSelfLink);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void mockApiClient(boolean isSuccess) throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);
      DeploymentApi deploymentApi = mock(DeploymentApi.class);
      VmApi vmApi = mock(VmApi.class);
      TasksApi tasksApi = mock(TasksApi.class);

      Deployment deployment = new Deployment();
      deployment.setId("deploymentId1");
      deployment.setAuth(new AuthInfo());
      final ResourceList<Deployment> deploymentResourceList = new ResourceList<>(Arrays.asList(deployment));

      Vm vm1 = new Vm();
      vm1.setId("vm1");
      Map<String, String> metadata = new HashMap<String, String>();
      metadata.put("key1", ContainersConfig.ContainerType.Zookeeper.name());
      vm1.setMetadata(metadata);
      final ResourceList<Vm> vmList = new ResourceList<>(Arrays.asList(vm1));

      NetworkConnection networkConnection = new NetworkConnection();
      networkConnection.setNetwork("VM VLAN");
      networkConnection.setIpAddress("127.0.0.1");

      VmNetworks vmNetworks = new VmNetworks();
      vmNetworks.setNetworkConnections(Collections.singleton(networkConnection));

      final Task getNetworksTaskResult = new Task();
      getNetworksTaskResult.setId("taskId");
      getNetworksTaskResult.setState("COMPLETED");
      getNetworksTaskResult.setResourceProperties(vmNetworks);

      final Task taskReturnedByPauseSystem = TestHelper.createCompletedApifeTask("PAUSE_SYSTEM");

      if (isSuccess) {
        // List all deployments
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Deployment>>) invocation.getArguments()[0]).onSuccess(deploymentResourceList);
            return null;
          }
        }).when(deploymentApi).listAllAsync(any(FutureCallback.class));

        // Pause system
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByPauseSystem);
            return null;
          }
        }).when(deploymentApi).pauseSystemAsync(any(String.class), any(FutureCallback.class));

        // List all vms
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess(vmList);
            return null;
          }
        }).when(deploymentApi).getAllDeploymentVmsAsync(anyString(), any(FutureCallback.class));

        // Get vm networks
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(getNetworksTaskResult);
            return null;
          }
        }).when(vmApi).getNetworksAsync(any(String.class), any(FutureCallback.class));

      } else {
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Deployment>>) invocation.getArguments()[0])
                .onFailure(new Exception("failed!"));
            return null;
          }
        }).when(deploymentApi).listAllAsync(any(FutureCallback.class));
      }

      doReturn(deploymentApi).when(apiClient).getDeploymentApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      doReturn(apiClient).when(apiClientFactory).create();
      doReturn(apiClient).when(apiClientFactory).create(any(String.class));
    }

    @Test
    public void testSuccess() throws Throwable {
      createTestEnvironment();
      mockApiClient(true);
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);

      InitializeDeploymentMigrationWorkflowService.State finalState = destinationEnvironment.callServiceAndWaitForState(
          InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
          startState,
          InitializeDeploymentMigrationWorkflowService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test
    public void testAPIFEDeploymentApiFailure() throws Throwable {
      createTestEnvironment();
      mockApiClient(false);

      InitializeDeploymentMigrationWorkflowService.State finalState = destinationEnvironment.callServiceAndWaitForState(
          InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
          startState,
          InitializeDeploymentMigrationWorkflowService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(
          finalState.taskState.stage,
          is(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.FAILED));
    }

    @Test
    public void testSourceEnvironmentStoppedFailure() throws Throwable {
      createTestEnvironment();
      sourceEnvironment.stop();
      sourceEnvironment = null;

      InitializeDeploymentMigrationWorkflowService.State finalState = destinationEnvironment.callServiceAndWaitForState(
          InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
          startState,
          InitializeDeploymentMigrationWorkflowService.State.class,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(
          finalState.taskState.stage,
          is(InitializeDeploymentMigrationWorkflowService.TaskState.TaskStage.FAILED));
    }
  }
}
