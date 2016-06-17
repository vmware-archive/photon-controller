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
import com.vmware.photon.controller.api.DeploymentState;
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
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.upgrade.HostTransformationService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.migration.UpgradeInformation;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
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
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskService;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
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
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * This class implements tests for the {@link FinalizeDeploymentMigrationWorkflowService} class.
 */
public class FinalizeDeploymentMigrationWorkflowServiceTest {

  private FinalizeDeploymentMigrationWorkflowService finalizeDeploymentMigrationWorkflowService;
  private TestHost testHost;

  /**
   * This method is a dummy test case which forces IntelliJ to recognize the
   * current class as a test class.
   */
  @Test
  private void dummy() {
  }

  private void startService(FinalizeDeploymentMigrationWorkflowService.State startState) throws Throwable {
    Operation startOperation = testHost.startServiceSynchronously(finalizeDeploymentMigrationWorkflowService
        , startState);
    assertThat(startOperation.getStatusCode(), is(200));
  }

  /**
   * This method creates a new State object which is sufficient to create a new
   * FinishDeploymentWorkflowService instance.
   */
  private FinalizeDeploymentMigrationWorkflowService.State buildValidStartState(
      @Nullable FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
      @Nullable FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage)
      throws Throwable {
    FinalizeDeploymentMigrationWorkflowService.State startState =
        new FinalizeDeploymentMigrationWorkflowService.State();
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    startState.sourceLoadBalancerAddress = "lbLink1";
    startState.destinationDeploymentId = "deployment1";
    startState.taskPollDelay = 1;

    if (null != startStage) {
      startState.taskState = new FinalizeDeploymentMigrationWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;

      if (TaskState.TaskStage.STARTED == startStage) {
        switch (startSubStage) {
          case STOP_MIGRATE_TASKS:
          case REINSTALL_AGENTS:
          case UPGRADE_AGENTS:
          case MIGRATE_FINAL:
          case RESUME_DESTINATION_SYSTEM:
            // fall through
          case PAUSE_SOURCE_SYSTEM:
            startState.sourceDeploymentId = "deployment2";
            startState.sourceZookeeperQuorum = "127.0.0.1";
            break;
        }
      }
    }

    return startState;
  }

  /**
   * This method creates a patch State object which is sufficient to patch a
   * FinalizeDeploymentMigrationWorkflowService instance.
   */
  private FinalizeDeploymentMigrationWorkflowService.State buildValidPatchState(
      TaskState.TaskStage stage,
      FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage patchSubStage) {
    FinalizeDeploymentMigrationWorkflowService.State patchState =
        new FinalizeDeploymentMigrationWorkflowService.State();
    patchState.taskState = new FinalizeDeploymentMigrationWorkflowService.TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = patchSubStage;

    if (TaskState.TaskStage.STARTED == stage) {
      switch (patchSubStage) {
        case PAUSE_SOURCE_SYSTEM:
          patchState.sourceDeploymentId = "deployment2";
          patchState.sourceZookeeperQuorum = "127.0.0.1";
          break;
        case RESUME_DESTINATION_SYSTEM:
        case MIGRATE_FINAL:
        case REINSTALL_AGENTS:
        case UPGRADE_AGENTS:
          // fall through
        case STOP_MIGRATE_TASKS:
          break;
      }
    }
    return patchState;
  }

  /**
   * This class implements tests for the initial service state.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUpTest() {
      finalizeDeploymentMigrationWorkflowService = new FinalizeDeploymentMigrationWorkflowService();
    }

    @AfterMethod
    public void tearDownTest() {
      finalizeDeploymentMigrationWorkflowService = null;
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION);

      assertThat(finalizeDeploymentMigrationWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      finalizeDeploymentMigrationWorkflowService = new FinalizeDeploymentMigrationWorkflowService();
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
    public void testValidStartStages(TaskState.TaskStage startStage,
                                     FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage) throws
        Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      FinalizeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(FinalizeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "TransitionalStartStages")
    public void testTransitionalStartState(TaskState.TaskStage startStage,
                                           FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage
                                               startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      FinalizeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(FinalizeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage, is(FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage
          .PAUSE_SOURCE_SYSTEM));
    }

    @DataProvider(name = "TransitionalStartStages")
    public Object[][] getTransitionalStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartState(FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
                                       FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      FinalizeDeploymentMigrationWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      startState.controlFlags = null;
      startService(startState);

      FinalizeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(FinalizeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "fieldNamesWithMissingValue")
    public void testMissingRequiredStateFieldValue(String fieldName) throws Throwable {
      FinalizeDeploymentMigrationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED,
          null);
      Field declaredField = startState.getClass().getDeclaredField(fieldName);
      declaredField.set(startState, null);

      testHost.startServiceSynchronously(finalizeDeploymentMigrationWorkflowService, startState);
    }

    @DataProvider(name = "fieldNamesWithMissingValue")
    public Object[][] getFieldNamesWithMissingValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              FinalizeDeploymentMigrationWorkflowService.State.class,
              NotNull.class));
    }

    @Test(dataProvider = "TaskPollDelayValues")
    public void testTaskPollDelayValues(Integer taskPollDelay, Integer expectedValue) throws Throwable {
      FinalizeDeploymentMigrationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED,
          null);
      startState.taskPollDelay = taskPollDelay;
      Operation startOperation = testHost.startServiceSynchronously(finalizeDeploymentMigrationWorkflowService,
          startState);
      assertThat(startOperation.getStatusCode(), is(200));
      FinalizeDeploymentMigrationWorkflowService.State savedState =
          testHost.getServiceState(FinalizeDeploymentMigrationWorkflowService.State.class);
      assertThat(savedState.taskPollDelay, is(expectedValue));
    }

    @DataProvider(name = "TaskPollDelayValues")
    public Object[][] getTaskPollDelayValues() {
      DeployerServiceGroup deployerServiceGroup =
          (DeployerServiceGroup) (((PhotonControllerXenonHost) testHost).getDeployer());
      return new Object[][]{
          {null, new Integer(deployerServiceGroup.getDeployerContext().getTaskPollDelay())},
          {new Integer(500), new Integer(500)},
      };
    }

    @Test(dataProvider = "InvalidTaskPollDelayValues")
    public void testFailureInvalidTaskPollDelayValues(int taskPollDelay) throws Throwable {
      FinalizeDeploymentMigrationWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.CREATED,
          null);
      startState.taskPollDelay = taskPollDelay;
      try {
        testHost.startServiceSynchronously(finalizeDeploymentMigrationWorkflowService, startState);
        fail("Service start should throw in response to illegal taskPollDelay values");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), is("taskPollDelay must be greater than zero"));
      }
    }

    @DataProvider(name = "InvalidTaskPollDelayValues")
    public Object[][] getInvalidTaskPollDelayValues() {
      return new Object[][]{
          {0},
          {-10},
      };
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {
    boolean serviceCreated = false;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      finalizeDeploymentMigrationWorkflowService = new FinalizeDeploymentMigrationWorkflowService();
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
    public void testValidStageUpdates(FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage startStage,
                                      FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage startSubStage,
                                      FinalizeDeploymentMigrationWorkflowService.TaskState.TaskStage patchStage,
                                      FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));
      serviceCreated = true;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage, patchSubStage));

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      FinalizeDeploymentMigrationWorkflowService.State serviceState =
          testHost.getServiceState(FinalizeDeploymentMigrationWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM,
              TaskState.TaskStage.FINISHED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM,
              TaskState.TaskStage.FAILED, null},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM,
              TaskState.TaskStage.CANCELLED, null},
      };
    }


    @Test(dataProvider = "InvalidStageUpdates", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(TaskState.TaskStage startStage,
                                           FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage
                                               startSubStage,
                                           TaskState.TaskStage patchStage,
                                           FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage
                                               patchSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));
      serviceCreated = true;

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(buildValidPatchState(patchStage, patchSubStage));

      testHost.sendRequestAndWait(patchOperation);
    }


    @DataProvider(name = "InvalidStageUpdates")
    public Object[][] getInvalidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},

          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.STOP_MIGRATE_TASKS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.REINSTALL_AGENTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.UPGRADE_AGENTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.MIGRATE_FINAL},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED,
              FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.RESUME_DESTINATION_SYSTEM},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }


    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFieldNames")
    public void testInvalidStateFieldValue(String fieldName) throws Throwable {
      startService(buildValidStartState(null, null));
      serviceCreated = true;

      FinalizeDeploymentMigrationWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.STARTED,
          FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.PAUSE_SOURCE_SYSTEM);
      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getFieldNamesWithInvalidValue() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              FinalizeDeploymentMigrationWorkflowService.State.class,
              Immutable.class));
    }
  }

  /**
   * End-to-end tests for the add cloud host task.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";
    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private TestEnvironment sourceEnvironment;
    private TestEnvironment destinationEnvironment;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment sourceCloudStore;
    private com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment destinationCloudStore;
    private DeployerConfig deployerConfig;
    private DeployerContext deployerContext;
    private ListeningExecutorService listeningExecutorService;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private ApiClientFactory apiClientFactory;
    private FinalizeDeploymentMigrationWorkflowService.State startState;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      vibDirectory.mkdirs();
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      TestHelper.createSourceFile("esxcloud-" + UUID.randomUUID().toString() + ".vib", vibDirectory);
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "user-data.template"));
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "meta-data.template"));
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

      deployerConfig = spy(ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()));
      deployerContext = spy(deployerConfig.getDeployerContext());

      startState = buildValidStartState(TaskState.TaskStage.CREATED, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      apiClientFactory = mock(ApiClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (sourceEnvironment != null) {
        sourceEnvironment.stop();
      }
      if (destinationEnvironment != null) {
        destinationEnvironment.stop();
      }

      if (null != sourceCloudStore) {
        sourceCloudStore.stop();
        sourceCloudStore = null;
      }

      if (null != destinationCloudStore) {
        destinationCloudStore.stop();
        destinationCloudStore = null;
      }

      apiClientFactory = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);
    }

    private void createTestEnvironment() throws Throwable {
      String quorum = deployerConfig.getZookeeper().getQuorum();
      deployerContext.setZookeeperQuorum(quorum);

      ZookeeperClientFactory sourceZKFactory = mock(ZookeeperClientFactory.class);
      ZookeeperClientFactory destinationZKFactory = mock(ZookeeperClientFactory.class);
      sourceCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);
      destinationCloudStore = com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment.create(1);

      sourceEnvironment = new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .listeningExecutorService(listeningExecutorService)
          .apiClientFactory(apiClientFactory)
          .cloudServerSet(sourceCloudStore.getServerSet())
          .zookeeperServersetBuilderFactory(sourceZKFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .hostCount(1)
          .build();

      DockerProvisionerFactory dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      destinationEnvironment = new TestEnvironment.Builder()
          .hostCount(1)
          .deployerContext(deployerContext)
          .apiClientFactory(apiClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .cloudServerSet(destinationCloudStore.getServerSet())
          .zookeeperServersetBuilderFactory(destinationZKFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .build();

      when(dockerProvisionerFactory.create(anyString())).thenReturn(mock(DockerProvisioner.class));

      ZookeeperClient sourceZKBuilder = mock(ZookeeperClient.class);
      doReturn(sourceZKBuilder).when(sourceZKFactory).create();
      ZookeeperClient destinationZKBuilder = mock(ZookeeperClient.class);
      doReturn(destinationZKBuilder).when(destinationZKFactory).create();
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1",
          sourceCloudStore.getHosts()[0].getState().httpPort)))
          .when(destinationZKBuilder)
          .getServers(eq("127.0.0.1:2181"), eq("cloudstore"));
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1",
          destinationCloudStore.getHosts()[0].getState().httpPort)))
          .when(destinationZKBuilder)
          .getServers(eq(quorum), eq("cloudstore"));
      ServiceHost sourceHost = sourceEnvironment.getHosts()[0];
      startState.sourceLoadBalancerAddress = sourceHost.getPublicUri().toString();

      List<UpgradeInformation> upgradeInfo = ImmutableList.<UpgradeInformation>builder()
          .add(new UpgradeInformation("/photon/cloudstore/flavors", "/photon/cloudstore/flavors",
              Constants.CLOUDSTORE_SERVICE_NAME, MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
              FlavorService.State.class))
          .add(new UpgradeInformation("/photon/cloudstore/images", "/photon/cloudstore/images",
              Constants.CLOUDSTORE_SERVICE_NAME, MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
              ImageService.State.class))
          .add(new UpgradeInformation("/photon/cloudstore/hosts", "/photon/cloudstore/hosts",
              Constants.CLOUDSTORE_SERVICE_NAME, HostTransformationService.SELF_LINK,
              HostService.State.class))
          .add(new UpgradeInformation("/photon/cloudstore/networks", "/photon/cloudstore/networks",
              Constants.CLOUDSTORE_SERVICE_NAME, MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
              NetworkService.State.class))
          .add(new UpgradeInformation("/photon/cloudstore/datastores", "/photon/cloudstore/datastores",
              Constants.CLOUDSTORE_SERVICE_NAME, MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
              DatastoreService.State.class))
          .add(new UpgradeInformation("/photon/cloudstore/tasks", "/photon/cloudstore/tasks",
              Constants.CLOUDSTORE_SERVICE_NAME, MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
              TaskService.State.class))
          .build();

      when(deployerContext.getUpgradeInformation()).thenReturn(upgradeInfo);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockApiClient(boolean isSuccess) throws Throwable {

      ApiClient apiClient = mock(ApiClient.class);
      DeploymentApi deploymentApi = mock(DeploymentApi.class);
      VmApi vmApi = mock(VmApi.class);
      TasksApi tasksApi = mock(TasksApi.class);

      DeploymentService.State deploymentService = TestHelper.getDeploymentServiceStartState(false, false);
      deploymentService = TestHelper.createDeploymentService(destinationCloudStore, deploymentService);
      Deployment deployment = new Deployment();
      deployment.setId(ServiceUtils.getIDFromDocumentSelfLink(deploymentService.documentSelfLink));
      deployment.setAuth(new AuthInfo());
      final ResourceList<Deployment> deploymentResourceList = new ResourceList<>(Arrays.asList(deployment));
      startState.destinationDeploymentId = deployment.getId();

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
      final Task taskReturnedByResumeSystem = TestHelper.createCompletedApifeTask("RESUME_SYSTEM");

      if (isSuccess) {
        // List all deployments
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Deployment>>) invocation.getArguments()[0]).onSuccess
                (deploymentResourceList);
            return null;
          }
        }).when(deploymentApi).listAllAsync(
            any(FutureCallback.class));

        // Pause system
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByPauseSystem);
            return null;
          }
        }).when(deploymentApi).pauseSystemAsync(any(String.class), any(FutureCallback.class));

        // Resume system
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByResumeSystem);
            return null;
          }
        }).when(deploymentApi).resumeSystemAsync(anyString(), any(FutureCallback.class));

        // List all vms
        doAnswer(new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            ((FutureCallback<ResourceList<Vm>>) invocation.getArguments()[1]).onSuccess
                (vmList);
            return null;
          }
        }).when(deploymentApi).getAllDeploymentVmsAsync(anyString(),
            any(FutureCallback.class));

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
            ((FutureCallback<ResourceList<Deployment>>) invocation.getArguments()[0]).onFailure(new Exception
                ("failed!"));
            return null;
          }
        }).when(deploymentApi).listAllAsync(
            any(FutureCallback.class));
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
      // create images and datastores in the old management plane
      TestHelper.createImageService(sourceCloudStore);

      DatastoreService.State datastore = new DatastoreService.State();
      datastore.name = "ds-name";
      datastore.id = "ds-name1";
      datastore.documentSelfLink = datastore.id;
      datastore.type = DatastoreType.LOCAL_VMFS.name();
      TestHelper.createDatastoreService(sourceCloudStore, datastore);

      DatastoreService.State nonImageDataStore = new DatastoreService.State();
      nonImageDataStore.name = "ds-other-name";
      nonImageDataStore.id = "ds-other-name1";
      nonImageDataStore.documentSelfLink = nonImageDataStore.id;
      nonImageDataStore.type = DatastoreType.LOCAL_VMFS.name();
      TestHelper.createDatastoreService(sourceCloudStore, nonImageDataStore);

      DeploymentService.State deployment = new DeploymentService.State();
      deployment.imageDataStoreNames = new HashSet<>();
      deployment.imageDataStoreNames.add(datastore.name);
      deployment.state = DeploymentState.READY;
      deployment.imageDataStoreUsedForVMs = Boolean.TRUE;
      TestHelper.createDeploymentService(sourceCloudStore, deployment);


      mockApiClient(true);
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.CONFIGURE_SYSLOG_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(),
          ProvisionHostTaskService.INSTALL_VIB_SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateManagementVmTaskService.SCRIPT_NAME,
          true);

      // Create a host on source
      TestHelper.createHostService(sourceCloudStore, Collections.singleton(UsageTag.CLOUD.name()));
      TestHelper.createHostService(sourceCloudStore, Collections.singleton(UsageTag.CLOUD.name()));

      Set<String> hostsSource = getDocuments(HostService.State.class, sourceCloudStore);
      assertThat((hostsSource.size() == 2), is(true));

      FinalizeDeploymentMigrationWorkflowService.State finalState =
          destinationEnvironment.callServiceAndWaitForState(
              FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
              startState,
              FinalizeDeploymentMigrationWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));
      TestHelper.assertTaskStateFinished(finalState.taskState);

      //Make sure that the host is in destination
      Set<String> hosts = getDocuments(HostService.State.class, destinationCloudStore);
      assertThat((hosts.size() == 2), is(true));
    }

    private Set<String> getDocuments(Class<?> kindClass,
                                     com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment cloudStore)
        throws Throwable {

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(kindClass));

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);

      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = cloudStore.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

      return documentLinks;
    }

    @Test
    public void testAPIFEDeploymentApiFailure() throws Throwable {
      createTestEnvironment();
      mockApiClient(false);

      FinalizeDeploymentMigrationWorkflowService.State finalState =
          destinationEnvironment.callServiceAndWaitForState(
              FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
              startState,
              FinalizeDeploymentMigrationWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testSourceEnvironmentStoppedFailure() throws Throwable {
      createTestEnvironment();
      sourceEnvironment.stop();
      sourceEnvironment = null;

      FinalizeDeploymentMigrationWorkflowService.State finalState =
          destinationEnvironment.callServiceAndWaitForState(
              FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK,
              startState,
              FinalizeDeploymentMigrationWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
