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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantService;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.DeployerModule;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.CreateIsoTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateVmSpecLayoutTaskService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionHostTaskService;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClientFactory;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.helpers.ReflectionUtils;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.MockHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.photon.controller.deployer.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.mockito.Matchers;
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
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import javax.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class implements tests for the {@link DeploymentWorkflowService} class.
 */
public class DeploymentWorkflowServiceTest {

  public static DeploymentWorkflowService.State buildValidStartState(
      @Nullable TaskState.TaskStage startStage,
      @Nullable DeploymentWorkflowService.TaskState.SubStage startSubStage) {

    DeploymentWorkflowService.State startState = new DeploymentWorkflowService.State();
    startState.managementVmImageFile = "ESX_CLOUD_MANAGEMENT_VM_IMAGE_FILE";
    startState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;

    if (null != startStage) {
      startState.taskState = new DeploymentWorkflowService.TaskState();
      startState.taskState.stage = startStage;
      startState.taskState.subStage = startSubStage;

      if (TaskState.TaskStage.CREATED != startStage) {
        startState.taskSubStates = new ArrayList<>(DeploymentWorkflowService.TaskState.SubStage.values().length);
        for (DeploymentWorkflowService.TaskState.SubStage s : DeploymentWorkflowService.TaskState.SubStage.values()) {
          if (null == startSubStage || startSubStage.ordinal() > s.ordinal()) {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.FINISHED);
          } else if (startSubStage.ordinal() == s.ordinal()) {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.STARTED);
          } else {
            startState.taskSubStates.add(s.ordinal(), TaskState.TaskStage.CREATED);
          }
        }
      }
    }

    return startState;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class InitializationTest {

    private DeploymentWorkflowService deploymentWorkflowService;

    @BeforeMethod
    public void setUpTest() {
      deploymentWorkflowService = new DeploymentWorkflowService();
    }

    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE);

      assertThat(deploymentWorkflowService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest {

    private DeploymentWorkflowService deploymentWorkflowService;
    private boolean serviceCreated = false;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      deploymentWorkflowService = new DeploymentWorkflowService();
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

    @Test(dataProvider = "ValidStartStages")
    public void testValidStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable DeploymentWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      DeploymentWorkflowService.State serviceState =
          testHost.getServiceState(DeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState, notNullValue());
      assertThat(serviceState.taskState.stage, notNullValue());
    }

    @DataProvider(name = "ValidStartStages")
    public Object[][] getValidStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "AutoProgressedStartStages")
    public void testAutoProgressedStartStage(
        @Nullable TaskState.TaskStage startStage,
        @Nullable DeploymentWorkflowService.TaskState.SubStage startSubStage)
        throws Throwable {
      startService(buildValidStartState(startStage, startSubStage));

      DeploymentWorkflowService.State serviceState =
          testHost.getServiceState(DeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(TaskState.TaskStage.STARTED));
      assertThat(serviceState.taskState.subStage,
          is(DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS));
    }

    @DataProvider(name = "AutoProgressedStartStages")
    public Object[][] getAutoProgressedStartStages() {
      return new Object[][]{
          {null, null},
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
      };
    }

    @Test(dataProvider = "TerminalStartStages")
    public void testTerminalStartStage(TaskState.TaskStage startStage) throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(startStage, null);
      startState.controlFlags = null;
      startService(startState);

      DeploymentWorkflowService.State serviceState =
          testHost.getServiceState(DeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(startStage));
      assertThat(serviceState.taskState.subStage, nullValue());
    }

    @DataProvider(name = "TerminalStartStages")
    public Object[][] getTerminalStartStages() {
      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED},
      };
    }

    @Test(dataProvider = "RequiredFieldNames", expectedExceptions = XenonRuntimeException.class)
    public void testFailureRequiredFieldMissing(String fieldName) throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(null, null);
      startState.getClass().getDeclaredField(fieldName).set(startState, null);
      startService(startState);
    }

    @DataProvider(name = "RequiredFieldNames")
    public Object[][] getRequiredFieldNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeploymentWorkflowService.State.class, NotNull.class));
    }

    @Test(dataProvider = "InvalidTaskSubStates", expectedExceptions = XenonRuntimeException.class)
    public void testFailureInvalidSubStateList(List<TaskState.TaskStage> taskSubStates) throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(TaskState.TaskStage.STARTED,
          DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS);
      startState.taskSubStates = taskSubStates;
      startService(startState);
    }

    @DataProvider(name = "InvalidTaskSubStates")
    public Object[][] getInvalidTaskSubStates() {
      return new Object[][]{
          {Collections.<TaskState.TaskStage>emptyList()},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.STARTED))},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED,
              TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED))},
          {new ArrayList<>(Arrays.asList(TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED,
              TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED))},
      };
    }

    private void startService(DeploymentWorkflowService.State startState) throws Throwable {
      Operation startOperation = testHost.startServiceSynchronously(deploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));
      serviceCreated = true;
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest {

    private DeploymentWorkflowService deploymentWorkflowService;
    private TestHost testHost;

    @BeforeClass
    public void setUpClass() throws Throwable {
      testHost = TestHost.create();
    }

    @BeforeMethod
    public void setUpTest() {
      deploymentWorkflowService = new DeploymentWorkflowService();
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
        @Nullable DeploymentWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable DeploymentWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(deploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentWorkflowService.State patchState =
          DeploymentWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      Operation result = testHost.sendRequestAndWait(patchOperation);
      assertThat(result.getStatusCode(), is(200));

      DeploymentWorkflowService.State serviceState =
          testHost.getServiceState(DeploymentWorkflowService.State.class);

      assertThat(serviceState.taskState.stage, is(patchStage));
      assertThat(serviceState.taskState.subStage, is(patchSubStage));
    }

    @DataProvider(name = "ValidStageTransitions")
    public Object[][] getValidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},

          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.STARTED,
              DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.STARTED,
              DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.STARTED,
              DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
    public void testInvalidStageTransition(
        TaskState.TaskStage startStage,
        @Nullable DeploymentWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage patchStage,
        @Nullable DeploymentWorkflowService.TaskState.SubStage patchSubStage)
        throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(startStage, startSubStage);
      Operation startOperation = testHost.startServiceSynchronously(deploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentWorkflowService.State patchState =
          DeploymentWorkflowService.buildPatch(patchStage, patchSubStage, null);

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "InvalidStageTransitions")
    public Object[][] getInvalidStageTransitions() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.CREATED, null},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},

          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},

          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED,
              DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.CANCELLED, null},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.CREATE_MANAGEMENT_PLANE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.PROVISION_CLOUD_HOSTS},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.ALLOCATE_CM_RESOURCES},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, DeploymentWorkflowService.TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "ImmutableFieldNames")
    public void testInvalidPatchStateValue(String fieldName) throws Throwable {
      DeploymentWorkflowService.State startState = buildValidStartState(null, null);
      Operation startOperation = testHost.startServiceSynchronously(deploymentWorkflowService, startState);
      assertThat(startOperation.getStatusCode(), is(200));

      DeploymentWorkflowService.State patchState =
          DeploymentWorkflowService.buildPatch(
              TaskState.TaskStage.STARTED,
              DeploymentWorkflowService.TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS,
              null);

      Field declaredField = patchState.getClass().getDeclaredField(fieldName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
          .setBody(patchState);

      testHost.sendRequestAndWait(patchOperation);
    }

    @DataProvider(name = "ImmutableFieldNames")
    public Object[][] getAttributeNames() {
      return TestHelper.toDataProvidersList(
          ReflectionUtils.getAttributeNamesWithAnnotation(
              DeploymentWorkflowService.State.class, Immutable.class));
    }
  }

  /**
   * This class implements end-to-end tests for the deployment workflow.
   */
  public class EndToEndTest {

    private static final String configFilePath = "/config.yml";

    private final File storageDirectory = new File("/tmp/deployAgent");
    private final File scriptDirectory = new File("/tmp/deployAgent/scripts");
    private final File scriptLogDirectory = new File("/tmp/deployAgent/logs");
    private final File vibDirectory = new File("/tmp/deployAgent/vibs");

    private DeployerConfig deployerConfig;
    private ContainersConfig containersConfig;
    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private HttpFileServiceClientFactory httpFileServiceClientFactory;
    private ListeningExecutorService listeningExecutorService;
    private ApiClientFactory apiClientFactory;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private AuthHelperFactory authHelperFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;

    private DeploymentWorkflowService.State startState;
    private TestEnvironment localDeployer;
    private TestEnvironment remoteDeployer;
    private AuthClientHandler.ImplicitClient implicitClient;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment localStore;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment remoteStore;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      vibDirectory.mkdirs();
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      TestHelper.createSourceFile("esxcloud-" + UUID.randomUUID().toString() + ".vib", vibDirectory);
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "user-data.template"));
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "meta-data.template"));

      deployerConfig = spy(ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath()));
      TestHelper.setContainersConfig(deployerConfig);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      startState = buildValidStartState(null, null);
      startState.controlFlags = null;
      startState.taskPollDelay = 10;
      startState.childPollInterval = 10;
      implicitClient = new AuthClientHandler.ImplicitClient("client_id", "http://login", "http://logout");
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {
      agentControlClientFactory = mock(AgentControlClientFactory.class);
      hostClientFactory = mock(HostClientFactory.class);
      httpFileServiceClientFactory = mock(HttpFileServiceClientFactory.class);
      apiClientFactory = mock(ApiClientFactory.class);
      containersConfig = deployerConfig.getContainersConfig();
      authHelperFactory = mock(AuthHelperFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);
      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      MockHelper.mockServiceConfigurator(serviceConfiguratorFactory, true);
    }

    private void createCloudStores() throws Throwable {
      localStore = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      remoteStore = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
    }

    private void createTestEnvironment(int remoteNodeCount) throws Throwable {
      String quorum = deployerConfig.getZookeeper().getQuorum();
      deployerConfig.getDeployerContext().setZookeeperQuorum(quorum);

      DeployerContext context = spy(deployerConfig.getDeployerContext());
      ZookeeperClientFactory zkFactory = mock(ZookeeperClientFactory.class);

      localDeployer = new TestEnvironment.Builder()
          .authHelperFactory(authHelperFactory)
          .containersConfig(containersConfig)
          .deployerContext(context)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(localStore.getServerSet())
          .zookeeperServersetBuilderFactory(zkFactory)
          .hostCount(1)
          .build();

      remoteDeployer = new TestEnvironment.Builder()
          .authHelperFactory(authHelperFactory)
          .containersConfig(containersConfig)
          .deployerContext(context)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .healthCheckerFactory(healthCheckHelperFactory)
          .agentControlClientFactory(agentControlClientFactory)
          .hostClientFactory(hostClientFactory)
          .httpFileServiceClientFactory(httpFileServiceClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(localStore.getServerSet())
          .hostCount(remoteNodeCount)
          .build();

      ZookeeperClient zkBuilder = mock(ZookeeperClient.class);
      doReturn(zkBuilder).when(zkFactory).create();
      doReturn(Collections.singleton(
          new InetSocketAddress("127.0.0.1", localDeployer.getHosts()[0].getState().httpPort - 1)))
          .when(zkBuilder).getServers(eq(quorum), eq("deployer"));
      doReturn(Collections.singleton(
          new InetSocketAddress("127.0.0.1", remoteDeployer.getHosts()[0].getState().httpPort - 1)))
          .when(zkBuilder)
          .getServers(Matchers.startsWith("0.0.0"), eq("deployer"));
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1", localStore.getHosts()[0].getState().httpPort)))
          .when(zkBuilder).getServers(eq(quorum), eq("cloudstore"));
      doReturn(Collections.singleton(new InetSocketAddress("127.0.0.1", remoteStore.getHosts()[0].getState().httpPort)))
          .when(zkBuilder)
          .getServers(Matchers.startsWith("0.0.0"), eq("cloudstore"));
      doReturn(mock(ServiceConfig.class))
          .when(zkBuilder)
          .getServiceConfig(anyString(), anyString());

      InetSocketAddress address = remoteStore.getServerSet().getServers().iterator().next();
      InetSocketAddress adjustedAddress = new InetSocketAddress(address.getHostName(), address.getPort() - 1);
      doReturn(Collections.singleton(adjustedAddress))
        .when(zkBuilder).getServers(anyString(), eq(DeployerModule.HOUSEKEEPER_SERVICE_NAME));
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != localDeployer) {
        localDeployer.stop();
        localDeployer = null;
      }

      if (null != remoteDeployer) {
        remoteDeployer.stop();
        remoteDeployer = null;
      }

      if (null != localStore) {
        localStore.stop();
        localStore = null;
      }

      if (null != remoteStore) {
        remoteStore.stop();
        remoteStore = null;
      }

      authHelperFactory = null;
      containersConfig = null;
      dockerProvisionerFactory = null;
      apiClientFactory = null;
      healthCheckHelperFactory = null;
      agentControlClientFactory = null;
      hostClientFactory = null;
      httpFileServiceClientFactory = null;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      listeningExecutorService.shutdown();
      FileUtils.deleteDirectory(storageDirectory);
    }

    @DataProvider(name = "HostCountsWithAuthInfo")
    public Object[][] getHostCountsWithAuthInfo() {
      return new Object[][]{
          {1, 1, 1, true},
          {1, 1, 1, false},
          {1, 2, 11, true},
          {1, 2, 11, false},
      };
    }

    @Test(dataProvider = "HostCountsWithAuthInfo")
    public void testSuccess(Integer mgmtHostCount, Integer mixedHostCount, Integer cloudHostCount,
                            Boolean isAuthEnabled) throws Throwable {
      createCloudStores();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateIsoTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      for (int i = 0; i < mgmtHostCount; i++) {
        createHostService(Collections.singleton(UsageTag.MGMT.name()), "0.0.0." + i);
      }

      for (int i = 0; i < mixedHostCount; i++) {
        createHostService(new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())), "0.0.0." +
            (mgmtHostCount + i));
      }

      for (int i = 0; i < cloudHostCount; i++) {
        createHostService(Collections.singleton(UsageTag.CLOUD.name()), null);
      }

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, isAuthEnabled);

      DeploymentWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              DeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              DeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);

      verifyDeploymentServiceState(mgmtHostCount + mixedHostCount);
      verifyVmServiceStates(mgmtHostCount + mixedHostCount);
      verifyContainerTemplateServiceStates(isAuthEnabled);
      verifyContainerServiceStates();
      verifyTenantServiceState();
      verifyResourceTicketServiceState();
      verifyProjectServiceState();
      verifyImageServiceState();
      verifyFlavorServiceStates();
    }

    private void createHostService(Set<String> usageTags, String bindAddress) throws Throwable {
      HostService.State hostStartState = TestHelper.getHostServiceStartState(usageTags, HostState.CREATING);
      if (usageTags.contains(UsageTag.MGMT.name())) {
        DeployerXenonServiceHost remoteHost = remoteDeployer.getHosts()[0];
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP,
            bindAddress != null ? bindAddress : remoteHost.getState().bindAddress);
        hostStartState.metadata.put(HostService.State.METADATA_KEY_NAME_DEPLOYER_DCP_PORT,
            Integer.toString(remoteHost.getPort()));
      }
      TestHelper.createHostService(localStore, hostStartState);
    }

    private String createDeploymentServiceLink(
        com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStore,
        boolean isAuthEnabled)
        throws Throwable {
      DeploymentService.State deploymentService = TestHelper.createDeploymentService(cloudStore, isAuthEnabled);
      return deploymentService.documentSelfLink;
    }

    @DataProvider(name = "AuthEnabled")
    public Object[][] getAuthEnabled() {
      return new Object[][]{
          {Boolean.TRUE},
          {Boolean.FALSE},
      };
    }

    @Test(dataProvider = "AuthEnabled")
    public void testProvisionManagementHostFailure(Boolean authEnabled) throws Throwable {
      createCloudStores();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, false);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, false);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateIsoTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, authEnabled);

      DeploymentWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              DeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              DeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test(dataProvider = "AuthEnabled")
    public void testCreateManagementPlaneFailure(Boolean authEnabled) throws Throwable {
      createCloudStores();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, false);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateIsoTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, authEnabled);

      DeploymentWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              DeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              DeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @Test
    public void testAuthClientRegistrationFailure() throws Throwable {
      createCloudStores();
      MockHelper.mockHttpFileServiceClient(httpFileServiceClientFactory, true);
      MockHelper.mockHostClient(agentControlClientFactory, hostClientFactory, true);
      MockHelper.mockApiClient(apiClientFactory, localStore, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), ProvisionHostTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateScriptFile(deployerConfig.getDeployerContext(), CreateIsoTaskService.SCRIPT_NAME, true);
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockAuthHelper(implicitClient, authHelperFactory, false);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
      createTestEnvironment(1);

      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.MGMT.name()));
      TestHelper.createHostService(localStore,
          new HashSet<>(Arrays.asList(UsageTag.CLOUD.name(), UsageTag.MGMT.name())));
      TestHelper.createHostService(localStore, Collections.singleton(UsageTag.CLOUD.name()));

      startState.deploymentServiceLink = createDeploymentServiceLink(localStore, true);

      DeploymentWorkflowService.State finalState =
          localDeployer.callServiceAndWaitForState(
              DeploymentWorkflowFactoryService.SELF_LINK,
              startState,
              DeploymentWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    private void verifyDeploymentServiceState(int mgmtHostCnt) throws Throwable {
      verifySingletonServiceState(
          DeploymentService.State.class,
          (state) -> {
            assertThat(state.imageDataStoreNames.size(), is(1));
            assertThat(state.imageDataStoreNames.iterator().next(), is("IMAGE_DATASTORE_NAME"));
            assertThat(state.imageDataStoreUsedForVMs, is(true));
            assertThat(state.ntpEndpoint, is("NTP_ENDPOINT"));
            if (state.oAuthEnabled) {
              assertThat(state.oAuthServerAddress.startsWith("0.0.0"), is(true));
              assertThat(state.oAuthServerPort, is(443));
            } else {
              assertThat(state.oAuthServerAddress, is("OAUTH_ENDPOINT"));
              assertThat(state.oAuthServerPort, is(500));
            }
            assertThat(state.syslogEndpoint, is("SYSLOG_ENDPOINT"));
            assertThat(state.statsEnabled, is(true));
            assertThat(state.statsStoreEndpoint, is("STATS_STORE_ENDPOINT"));
            assertThat(state.statsStorePort, is(8081));

            assertThat(state.chairmanServerList, is(notNullValue()));

            assertThat(state.zookeeperIdToIpMap.size() == mgmtHostCnt, is(true));
            return true;
          }, remoteStore);
    }

    private void verifyVmServiceStates(int expectedVmEntityNumber) throws Throwable {
      List<VmService.State> states = queryForServiceStates(VmService.State.class, localDeployer);

      // The number of VmService entities that the workflow creates should match
      // the sum of the number of MGMT only hosts and the number of MIXED host.
      assertThat(states.size(), is(expectedVmEntityNumber));

      Set<String> hostServiceLinks = new HashSet<>();
      for (VmService.State state : states) {
        assertThat(state.vmId, is("CREATE_VM_ENTITY_ID"));
        assertThat(state.name, startsWith(CreateVmSpecLayoutTaskService.DOCKER_VM_PREFIX));

        HostService.State hostState = localStore.getServiceState(state.hostServiceLink, HostService.State.class);
        assertThat(state.ipAddress,
            is(hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP)));

        hostServiceLinks.add(state.hostServiceLink);
      }

      // It should be a one-to-one mapping between VmService entity and HostService entity.
      assertThat(hostServiceLinks.size(), is(expectedVmEntityNumber));
    }

    private void verifyContainerTemplateServiceStates(Boolean isAuthEnabled) throws Throwable {
      List<ContainerTemplateService.State> states = queryForServiceStates(ContainerTemplateService.State.class,
          localDeployer);

      // The number of ContainerTemplateService entities that the workflow creates should match
      // the number of the container specs in the config.
      int expectedContainerTemplateEntitynumber = containersConfig.getContainerSpecs().size();
      if (!isAuthEnabled) {
        // if auth is not enabled we will not deploy LightWave
        expectedContainerTemplateEntitynumber -= 1;
      }
      assertThat(states.size(), is(expectedContainerTemplateEntitynumber));

      Map<String, ContainerTemplateService.State> containerTemplateMap = new HashMap<>();
      for (ContainerTemplateService.State state : states) {
        containerTemplateMap.put(state.name, state);
      }

      List<ContainerTemplateService.State> containerTemplateStates = new ArrayList<>();
      for (Map.Entry<String, ContainersConfig.Spec> entry : containersConfig
          .getContainerSpecs().entrySet()) {
        // For each spec, we create one and only one container template entity.
        if (!isAuthEnabled &&
            entry.getValue().getType().equals(ContainersConfig.ContainerType.Lightwave.name())) {
          // if auth is disabled we do not generate the Lightwave container template.
          assertThat(containerTemplateMap.containsKey(entry.getKey()), is(false));
          continue;
        } else {
          assertThat(containerTemplateMap.containsKey(entry.getKey()), is(true));
        }

        ContainersConfig.Spec spec = entry.getValue();
        ContainerTemplateService.State finalState = containerTemplateMap.get(entry.getKey());
        containerTemplateStates.add(finalState);

        assertThat(finalState.name, is(spec.getType()));
        assertThat(finalState.cpuCount, is(spec.getCpuCount()));
        assertThat(finalState.memoryMb, is(spec.getMemoryMb()));
        assertThat(finalState.diskGb, is(spec.getDiskGb()));
        assertThat(finalState.isReplicated, is(spec.getIsReplicated()));
        assertThat(finalState.containerImage, is(spec.getContainerImage()));
      }
    }

    private void verifyContainerServiceStates() throws Throwable {
      List<VmService.State> vmStates = queryForServiceStates(VmService.State.class, localDeployer);
      List<ContainerTemplateService.State> containerTemplateStates =
          queryForServiceStates(ContainerTemplateService.State.class, localDeployer);

      for (ContainerTemplateService.State containerTemplateState : containerTemplateStates) {
        int containerStateNumberIfReplicated = 0;

        for (VmService.State vmState : vmStates) {
          QueryTask.Query kindClause = new QueryTask.Query()
              .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
              .setTermMatchValue(Utils.buildKind(ContainerService.State.class));
          QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
              .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
              .setTermMatchValue(vmState.documentSelfLink);
          QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
              .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
              .setTermMatchValue(containerTemplateState.documentSelfLink);

          QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
          querySpecification.query.addBooleanClause(kindClause);
          querySpecification.query.addBooleanClause(vmServiceLinkClause);
          querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);
          QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

          NodeGroupBroadcastResponse queryResponse = localDeployer.sendBroadcastQueryAndWait(queryTask);
          Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

          // For each VmService entity and each ContainerTemplate entity, there is one and only one
          // ContainerService entity to link them together, if the container is not replicated.
          if (containerTemplateState.isReplicated) {
            assertThat(documentLinks.size(), is(1));
          } else {
            containerStateNumberIfReplicated += documentLinks.size();
          }

          if (documentLinks.size() > 0) {
            for (String documentLink : documentLinks) {
              localDeployer.getServiceState(documentLink, ContainerService.State.class);
              // TODO(ysheng): verify container ID
            }
          }
        }

        // If the container is not replicated, only one VmService entity could be linked to the
        // ContainerTemplateService entity.
        if (!containerTemplateState.isReplicated) {
          assertThat(containerStateNumberIfReplicated, is(1));
        }
      }
    }

    private void verifyTenantServiceState() throws Throwable {
      List<TenantService.State> states = queryForServiceStates(TenantService.State.class, localStore);
      assertThat(states.size(), is(1));
    }

    private void verifyResourceTicketServiceState() throws Throwable {
      List<ResourceTicketService.State> states = queryForServiceStates(ResourceTicketService.State.class, localStore);
      assertThat(states.size(), is(1));
    }

    private void verifyProjectServiceState() throws Throwable {
      List<ProjectService.State> states = queryForServiceStates(ProjectService.State.class, localStore);
      assertThat(states.size(), is(1));
    }

    private void verifyImageServiceState() throws Throwable {
      List<ImageService.State> states = queryForServiceStates(ImageService.State.class, localStore);
      assertThat(states.size(), is(1));
    }

    private void verifyFlavorServiceStates() throws Throwable {
      List<FlavorService.State> states = queryForServiceStates(FlavorService.State.class, localStore);
      List<VmService.State> vmStates = queryForServiceStates(VmService.State.class, localDeployer);

      for (final VmService.State vmState : vmStates) {
        Collection<FlavorService.State> flavorsForVm = states.stream()
            .filter(flavorState -> vmState.vmFlavorServiceLink.equals(flavorState.documentSelfLink))
            .collect(Collectors.toList());

        assertThat(flavorsForVm.size(), is(1));
      }
    }

    private <T extends ServiceDocument> void verifySingletonServiceState(Class<T> classType, Predicate<T> predicate,
                                                                         MultiHostEnvironment<?> multiHostEnvironment)
        throws Throwable {
      List<T> states = queryForServiceStates(classType, multiHostEnvironment);
      assertThat(states.size(), is(1));
      predicate.test(states.get(0));
    }

    private <T extends ServiceDocument> List<T> queryForServiceStates(Class<T> classType,
                                                                      MultiHostEnvironment<?> multiHostEnvironment)
        throws Throwable {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(classType));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      NodeGroupBroadcastResponse queryResponse = multiHostEnvironment.sendBroadcastQueryAndWait(queryTask);
      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
      List<T> states = new ArrayList<>();
      for (String documentLink : documentLinks) {
        states.add(multiHostEnvironment.getServiceState(documentLink, classType));
      }

      return states;
    }
  }
}
