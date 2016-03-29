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

import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmMetadata;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.client.resource.FlavorApi;
import com.vmware.photon.controller.client.resource.ImagesApi;
import com.vmware.photon.controller.client.resource.ProjectApi;
import com.vmware.photon.controller.client.resource.TasksApi;
import com.vmware.photon.controller.client.resource.TenantsApi;
import com.vmware.photon.controller.client.resource.VmApi;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TenantService;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.DeployerModule;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ServiceConfiguratorFactory;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.deployengine.ApiClientFactory;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisionerFactory;
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
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.io.FileUtils;
import org.apache.http.entity.mime.content.FileBody;
import org.mockito.ArgumentMatcher;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Tests {@link BatchCreateManagementWorkflowService}.
 */
public class BatchCreateManagementWorkflowServiceTest {

  private TestHost host;
  private BatchCreateManagementWorkflowService service;

  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructor.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() {
      service = new BatchCreateManagementWorkflowService();
    }

    /**
     * Tests that the service starts with the expected capabilities.
     */
    @Test
    public void testCapabilities() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStart {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new BatchCreateManagementWorkflowService());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * This test verifies that service instances can be created with specific
     * start states.
     *
     * @param stage Supplies the stage of state.
     * @throws Throwable Throws exception if any error is encountered.
     */
    @Test(dataProvider = "validStartStates")
    public void testMinimalStartState(
        TaskState.TaskStage stage,
        BatchCreateManagementWorkflowService.TaskState.SubStage subStage)
        throws Throwable {
      BatchCreateManagementWorkflowService.State startState = buildValidStartupState(stage, subStage);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      BatchCreateManagementWorkflowService.State savedState =
          host.getServiceState(BatchCreateManagementWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
    }

    @DataProvider(name = "validStartStates")
    public Object[][] getValidStartStates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS},
          {TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that the task state of a service instance which is started
     * in a terminal state is not modified on startup when state transitions are
     * enabled.
     *
     * @param stage Supplies the stage of the state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "startStateNotChanged")
    public void testMinimalStartStateNotChanged(TaskState.TaskStage stage) throws Throwable {
      BatchCreateManagementWorkflowService.State startState = buildValidStartupState(stage, null);
      Operation startOp = host.startServiceSynchronously(service, startState);
      assertThat(startOp.getStatusCode(), is(200));

      BatchCreateManagementWorkflowService.State savedState
          = host.getServiceState(BatchCreateManagementWorkflowService.State.class);

      assertThat(savedState.taskState, notNullValue());
      assertThat(savedState.taskState.stage, is(stage));
    }

    @DataProvider(name = "startStateNotChanged")
    public Object[][] getStartStateNotChanged() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED},
          {TaskState.TaskStage.FAILED},
          {TaskState.TaskStage.CANCELLED}
      };
    }

    /**
     * This test verifies that the service handles the missing of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testMissingStateValue(String attributeName) throws Throwable {
      BatchCreateManagementWorkflowService.State startState = buildValidStartupState();
      startState.getClass().getDeclaredField(attributeName).set(startState, null);

      host.startServiceSynchronously(service, startState);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> notNullAttributes = ReflectionUtils
          .getAttributeNamesWithAnnotation(BatchCreateManagementWorkflowService.State.class, NotNull.class);
      return TestHelper.toDataProvidersList(notNullAttributes);
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatch {

    @BeforeClass
    public void setUpClass() throws Throwable {
      host = TestHost.create();
    }

    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new BatchCreateManagementWorkflowService());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      try {
        host.deleteServiceSynchronously();
      } catch (ServiceHost.ServiceNotFoundException e) {
        // Exceptions are expected in the case where a service instance was not successfully created.
      }
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }
    }

    /**
     * This test verifies that legal stage and substage transitions succeed.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "validStageUpdates")
    public void testValidStageUpdates(
        TaskState.TaskStage startStage,
        BatchCreateManagementWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        BatchCreateManagementWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {

      BatchCreateManagementWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      BatchCreateManagementWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      Operation resultOp = host.sendRequestAndWait(patchOp);
      assertThat(resultOp.getStatusCode(), is(200));

      BatchCreateManagementWorkflowService.State savedState
          = host.getServiceState(BatchCreateManagementWorkflowService.State.class);
      assertThat(savedState.taskState.stage, is(targetStage));
      assertThat(savedState.taskState.subStage, is(targetSubStage));
    }

    @DataProvider(name = "validStageUpdates")
    public Object[][] getValidStageUpdates() {
      return new Object[][]{
          {TaskState.TaskStage.CREATED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS,
              TaskState.TaskStage.FINISHED, null},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS,
              TaskState.TaskStage.FAILED, null},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE,
              TaskState.TaskStage.CANCELLED, null},
          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS,
              TaskState.TaskStage.CANCELLED, null},
      };
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the patch state is invalid.
     *
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStageUpdatesInvalidPatch() throws Throwable {

      BatchCreateManagementWorkflowService.State startState = buildValidStartupState(TaskState.TaskStage.STARTED, null);
      host.startServiceSynchronously(service, startState);

      BatchCreateManagementWorkflowService.State patchState = buildValidPatchState(TaskState.TaskStage.CREATED, null);

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    /**
     * This test verifies that illegal stage transitions fail, where
     * the start state is invalid.
     *
     * @param startStage  Supplies the stage of the start state.
     * @param targetStage Supplies the stage of the target state.
     * @throws Throwable Throws an exception if any error is encountered.
     */
    @Test(dataProvider = "illegalStageUpdatesInvalidStart", expectedExceptions = XenonRuntimeException.class)
    public void testIllegalStageUpdatesInvalidStart(
        TaskState.TaskStage startStage,
        BatchCreateManagementWorkflowService.TaskState.SubStage startSubStage,
        TaskState.TaskStage targetStage,
        BatchCreateManagementWorkflowService.TaskState.SubStage targetSubStage)
        throws Throwable {
      BatchCreateManagementWorkflowService.State startState = buildValidStartupState(startStage, startSubStage);
      host.startServiceSynchronously(service, startState);

      BatchCreateManagementWorkflowService.State patchState = buildValidPatchState(targetStage, targetSubStage);
      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);

      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "illegalStageUpdatesInvalidStart")
    public Object[][] getIllegalStageUpdatesInvalidStart() {

      return new Object[][]{
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
          {TaskState.TaskStage.FINISHED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS},

          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
          {TaskState.TaskStage.FAILED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS},

          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
          {TaskState.TaskStage.CANCELLED, null,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS},

          {TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.CREATE_VMS,
              TaskState.TaskStage.STARTED, BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE},
      };
    }

    /**
     * This test verifies that the service handles the presence of the specified list of attributes
     * in the start state.
     *
     * @param attributeName Supplies the attribute name.
     * @throws Throwable
     */
    @Test(expectedExceptions = XenonRuntimeException.class, dataProvider = "attributeNames")
    public void testInvalidPatchStateValue(String attributeName) throws Throwable {
      BatchCreateManagementWorkflowService.State startState = buildValidStartupState();
      host.startServiceSynchronously(service, startState);

      BatchCreateManagementWorkflowService.State patchState = buildValidPatchState();
      Field declaredField = patchState.getClass().getDeclaredField(attributeName);
      declaredField.set(patchState, ReflectionUtils.getDefaultAttributeValue(declaredField));

      Operation patchOp = Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody(patchState);
      host.sendRequestAndWait(patchOp);
    }

    @DataProvider(name = "attributeNames")
    public Object[][] getAttributeNames() {
      List<String> immutableAttributes = ReflectionUtils
          .getAttributeNamesWithAnnotation(BatchCreateManagementWorkflowService.State.class, Immutable.class);
      return TestHelper.toDataProvidersList(immutableAttributes);
    }
  }

  /**
   * End to end tests.
   */
  public class EndToEnd {

    private File scriptDirectory;
    private File scriptLogDirectory;

    private static final String configFilePath = "/config.yml";
    private final File storageDirectory = new File("/tmp/createIso");

    private static final String scriptFileName = "untar-image";

    private TestEnvironment machine;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;
    private ListeningExecutorService listeningExecutorService;
    private DockerProvisionerFactory dockerProvisionerFactory;
    private HealthCheckHelperFactory healthCheckHelperFactory;

    private BatchCreateManagementWorkflowService.State startState;

    private ContainersConfig containersConfig;
    private DeployerContext deployerContext;
    private ApiClientFactory apiClientFactory;
    private ServiceConfiguratorFactory serviceConfiguratorFactory;
    private ServiceConfigurator serviceConfigurator;
    private ApiClient apiClient;
    private ProjectApi projectApi;
    private TasksApi tasksApi;
    private VmApi vmApi;
    private FlavorApi flavorApi;
    private ImagesApi imagesApi;
    private TenantsApi tenantsApi;

    private Task taskReturnedByCreateVm;
    private Task taskReturnedBySetMetadata;
    private Task taskReturnedByAttachIso;
    private Task taskReturnedByGetTask;
    private Task taskReturnedByGetUploadImageTask;
    private Task taskReturnedByCreateVmFlavor;
    private Task taskReturnedByGetCreateVmFlavorTask;
    private Task taskReturnedByCreateDiskFlavor;
    private Task taskReturnedByGetCreateDiskFlavorTask;
    private Task taskReturnedByUploadImage;
    private Task taskReturnedByCreateTenant;
    private Task taskReturnedByCreateResourceTicket;
    private Task taskReturnedByCreateProject;

    private String deploymentServiceLink;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);

      dockerProvisionerFactory = mock(DockerProvisionerFactory.class);
      healthCheckHelperFactory = mock(HealthCheckHelperFactory.class);

      deploymentServiceLink = TestHelper.createDeploymentService(cloudStoreMachine).documentSelfLink;
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      listeningExecutorService.shutdown();

      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @BeforeMethod
    public void setUp() throws Throwable {

      apiClient = mock(ApiClient.class);
      projectApi = mock(ProjectApi.class);
      tasksApi = mock(TasksApi.class);
      vmApi = mock(VmApi.class);
      flavorApi = mock(FlavorApi.class);
      imagesApi = mock(ImagesApi.class);
      tenantsApi = mock(TenantsApi.class);
      serviceConfigurator = mock(ServiceConfigurator.class);

      apiClientFactory = mock(ApiClientFactory.class);
      serviceConfiguratorFactory = mock(ServiceConfiguratorFactory.class);
      doReturn(serviceConfigurator).when(serviceConfiguratorFactory).create();
      doReturn(apiClient).when(apiClientFactory).create();
      doReturn(projectApi).when(apiClient).getProjectApi();
      doReturn(tasksApi).when(apiClient).getTasksApi();
      doReturn(vmApi).when(apiClient).getVmApi();
      doReturn(flavorApi).when(apiClient).getFlavorApi();
      doReturn(imagesApi).when(apiClient).getImagesApi();
      doReturn(tenantsApi).when(apiClient).getTenantsApi();

      taskReturnedByCreateVm = new Task();
      taskReturnedByCreateVm.setId("taskId");
      taskReturnedByCreateVm.setState("STARTED");

      taskReturnedBySetMetadata = new Task();
      taskReturnedBySetMetadata.setId("taskId");
      taskReturnedBySetMetadata.setState("STARTED");

      taskReturnedByGetTask = new Task();
      taskReturnedByGetTask.setId("taskId");
      taskReturnedByGetTask.setState("COMPLETED");

      taskReturnedByGetUploadImageTask = new Task();
      taskReturnedByGetUploadImageTask.setId("taskId");
      taskReturnedByGetUploadImageTask.setState("COMPLETED");

      Task.Entity entity = new Task.Entity();
      entity.setId("vmId");
      taskReturnedByCreateVm.setEntity(entity);
      taskReturnedBySetMetadata.setEntity(entity);
      taskReturnedByGetTask.setEntity(entity);

      Task.Entity vmEntity = new Task.Entity();
      vmEntity.setId("vmId");

      taskReturnedByAttachIso = new Task();
      taskReturnedByAttachIso.setId("taskId");
      taskReturnedByAttachIso.setState("STARTED");
      taskReturnedByAttachIso.setEntity(vmEntity);

      taskReturnedByCreateVmFlavor = new Task();
      taskReturnedByCreateVmFlavor.setId("createVmFlavorTaskId");
      taskReturnedByCreateVmFlavor.setState("STARTED");

      taskReturnedByGetCreateVmFlavorTask = new Task();
      taskReturnedByGetCreateVmFlavorTask.setId("createVmFlavorTaskId");
      taskReturnedByGetCreateVmFlavorTask.setState("COMPLETED");
      Task.Entity taskEntity = new Task.Entity();
      taskEntity.setId("VM_FLAVOR_ID");
      taskReturnedByGetCreateVmFlavorTask.setEntity(taskEntity);

      taskReturnedByCreateDiskFlavor = new Task();
      taskReturnedByCreateDiskFlavor.setId("createDiskFlavorTaskId");
      taskReturnedByCreateDiskFlavor.setState("STARTED");

      taskReturnedByGetCreateDiskFlavorTask = new Task();
      taskReturnedByGetCreateDiskFlavorTask.setId("createDiskFlavorTaskId");
      taskReturnedByGetCreateDiskFlavorTask.setState("COMPLETED");
      Task.Entity diskTaskEntity = new Task.Entity();
      diskTaskEntity.setId("DISK_FLAVOR_ID");
      taskReturnedByGetCreateDiskFlavorTask.setEntity(diskTaskEntity);

      taskReturnedByCreateTenant = new Task();
      taskReturnedByCreateTenant.setId("createTenantTaskId");
      taskReturnedByCreateTenant.setState("COMPLETED");
      TenantService.State tenantState = TestHelper.createTenant(cloudStoreMachine);
      String tenantId = ServiceUtils.getIDFromDocumentSelfLink(tenantState.documentSelfLink);
      Task.Entity tenantEntity = new Task.Entity();
      tenantEntity.setId(tenantId);
      taskReturnedByCreateTenant.setEntity(tenantEntity);

      taskReturnedByCreateResourceTicket = new Task();
      taskReturnedByCreateResourceTicket.setId("createResourceTicketTaskId");
      taskReturnedByCreateResourceTicket.setState("COMPLETED");
      ResourceTicketService.State resourceState = TestHelper.createResourceTicket(tenantId, cloudStoreMachine);
      String rtId = ServiceUtils.getIDFromDocumentSelfLink(resourceState.documentSelfLink);
      Task.Entity resourceTicketEntity = new Task.Entity();
      resourceTicketEntity.setId(rtId);
      taskReturnedByCreateResourceTicket.setEntity(resourceTicketEntity);

      taskReturnedByCreateProject = new Task();
      taskReturnedByCreateProject.setId("createProjectTaskId");
      taskReturnedByCreateProject.setState("COMPLETED");
      Task.Entity projectEntity = new Task.Entity();
      projectEntity.setId("PROJECT_ID");
      taskReturnedByCreateProject.setEntity(projectEntity);

      Task.Entity taskEntityUpload = new Task.Entity();
      taskEntityUpload.setId("IMAGE_ID");

      taskReturnedByUploadImage = new Task();
      taskReturnedByUploadImage.setId("taskId");
      taskReturnedByUploadImage.setState("STARTED");
      taskReturnedByUploadImage.setEntity(taskEntityUpload);
      taskReturnedByGetUploadImageTask.setEntity(taskEntityUpload);

      startState = buildValidStartupState();
      startState.controlFlags = 0;
      startState.childPollInterval = 10;
      startState.taskPollDelay = 10;
      startState.deploymentServiceLink = deploymentServiceLink;

      DeployerConfig deployerConfig = ConfigBuilder.build(DeployerConfig.class,
          this.getClass().getResource(configFilePath).getPath());
      TestHelper.setContainersConfig(deployerConfig);
      containersConfig = deployerConfig.getContainersConfig();
      deployerContext = deployerConfig.getDeployerContext();
      TestHelper.setContainersConfig(deployerConfig);

      scriptDirectory = new File(deployerContext.getScriptDirectory());
      scriptLogDirectory = new File(deployerContext.getScriptLogDirectory());
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
      scriptDirectory.mkdirs();
      scriptLogDirectory.mkdirs();
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "user-data.template"));
      Files.createFile(Paths.get(scriptDirectory.getAbsolutePath(), "meta-data.template"));
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      if (null != machine) {
        machine.stop();
        machine = null;
      }

      startState = null;
      FileUtils.deleteDirectory(scriptDirectory);
      FileUtils.deleteDirectory(scriptLogDirectory);
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndSuccess(Integer hostCount) throws Throwable {
      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, healthCheckHelperFactory, serviceConfiguratorFactory, containersConfig, hostCount);

      mockSuccessFulCreateManagementVmWorkFlow();

      BatchCreateManagementWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              BatchCreateManagementWorkflowFactoryService.SELF_LINK,
              startState,
              BatchCreateManagementWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      TestHelper.assertTaskStateFinished(finalState.taskState);
    }

    @Test(dataProvider = "hostCounts")
    public void testEndToEndFailsWhenUploadImageFails(Integer hostCount) throws Throwable {
      machine = createTestEnvironment(deployerContext, listeningExecutorService, apiClientFactory,
          dockerProvisionerFactory, healthCheckHelperFactory, serviceConfiguratorFactory, containersConfig, hostCount);

      BatchCreateManagementWorkflowService.State finalState =
          machine.callServiceAndWaitForState(
              BatchCreateManagementWorkflowFactoryService.SELF_LINK,
              startState,
              BatchCreateManagementWorkflowService.State.class,
              (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

      assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
    }

    @DataProvider(name = "hostCounts")
    public Object[][] getHostCounts() {
      return new Object[][]{
          {1},
      };
    }

    private void mockSuccessFulCreateManagementVmWorkFlow() throws Throwable {
      mockSuccessfulUploadImage();
      mockSuccessfulAllocateResources();
      mockSuccessfulCreateIso();
      mockSuccessfulStartVm();
      mockSuccessfulVmCreate();
      MockHelper.mockCreateContainer(dockerProvisionerFactory, true);
      MockHelper.mockHealthChecker(healthCheckHelperFactory, true);
    }

    private void mockSuccessfulUploadImage() throws Throwable {
      doReturn(taskReturnedByUploadImage).when(imagesApi).uploadImage(any(FileBody.class), anyString());
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetUploadImageTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(anyString(), any(FutureCallback.class));
      doAnswer(MockHelper.mockGetImageAsync("IMAGE_ID", "100.0%"))
          .when(imagesApi)
          .getImageAsync(anyString(), Matchers.<FutureCallback<Image>>any());
    }

    private void mockSuccessfulAllocateResources() throws Throwable {

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateProject);
          return null;
        }
      }).when(tenantsApi).createProjectAsync(
          any(String.class), any(ProjectCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateResourceTicket);
          return null;
        }
      }).when(tenantsApi).createResourceTicketAsync(
          any(String.class), any(ResourceTicketCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateTenant);
          return null;
        }
      }).when(tenantsApi).createAsync(any(String.class), any(FutureCallback.class));

      ArgumentMatcher<FlavorCreateSpec> vmFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().equals("mgmt-vm-NAME");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateVmFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(vmFlavorSpecMatcher), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetCreateVmFlavorTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(eq("createVmFlavorTaskId"), any(FutureCallback.class));

      ArgumentMatcher<FlavorCreateSpec> diskFlavorSpecMatcher = new ArgumentMatcher<FlavorCreateSpec>() {
        @Override
        public boolean matches(Object o) {
          FlavorCreateSpec spec = (FlavorCreateSpec) o;
          return spec.getName().equals("mgmt-vm-disk-NAME");
        }
      };

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByCreateDiskFlavor);
          return null;
        }
      }).when(flavorApi).createAsync(argThat(diskFlavorSpecMatcher), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(taskReturnedByGetCreateDiskFlavorTask);
          return null;
        }
      }).when(tasksApi).getTaskAsync(eq("createDiskFlavorTaskId"), any(FutureCallback.class));
    }

    private void mockSuccessfulStartVm() throws Throwable {
      final Task performOperationReturnValue = new Task();
      performOperationReturnValue.setState("COMPLETED");

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[1]).onSuccess(performOperationReturnValue);
          return null;
        }
      }).when(vmApi).performStartOperationAsync(anyString(), any(FutureCallback.class));
    }

    private void mockSuccessfulCreateIso() throws Throwable {
      String scriptFileName = "esx-create-vm-iso";
      TestHelper.createSuccessScriptFile(deployerContext, scriptFileName);
      doReturn(taskReturnedByAttachIso).when(vmApi).uploadAndAttachIso(anyString(), anyString());
      doReturn(taskReturnedByGetTask).when(tasksApi).getTask(any(String.class));
      when(serviceConfigurator.generateContainersConfig(any(String.class))).thenReturn(containersConfig);
      doNothing().when(serviceConfigurator).copyDirectory(any(String.class), any(String.class));
      doNothing().when(serviceConfigurator).applyDynamicParameters(any(String.class), any(ContainersConfig
          .ContainerType.class), any(Map.class));
    }

    private void mockSuccessfulVmCreate() throws Throwable {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedByCreateVm);
          return null;
        }
      }).when(projectApi).createVmAsync(any(String.class), any(VmCreateSpec.class), any(FutureCallback.class));

      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((FutureCallback<Task>) invocation.getArguments()[2]).onSuccess(taskReturnedBySetMetadata);
          return null;
        }
      }).when(vmApi).setMetadataAsync(any(String.class), any(VmMetadata.class), any(FutureCallback.class));

      HostService.State hostServiceState = TestHelper.createHostService(cloudStoreMachine,
          Collections.singleton(UsageTag.MGMT.name()));

      VmService.State vmServiceStartState = TestHelper.getVmServiceStartState(hostServiceState);
      vmServiceStartState.ipAddress = "1.1.1.1";
      VmService.State vmServiceState = TestHelper.createVmService(machine, vmServiceStartState);
      for (ContainersConfig.ContainerType containerType : ContainersConfig.ContainerType.values()) {

        //
        // Lightwave and the load balancer can't be placed on the same VM, so skip Lightwave here.
        //

        if (containerType == ContainersConfig.ContainerType.Lightwave) {
          continue;
        }

        ContainerTemplateService.State containerTemplateSavedState = TestHelper.createContainerTemplateService(machine,
            containersConfig.getContainerSpecs().get(containerType.name()));
        TestHelper.createContainerService(machine, containerTemplateSavedState, vmServiceState);
      }
    }

    public TestEnvironment createTestEnvironment(
        DeployerContext deployerContext,
        ListeningExecutorService listeningExecutorService,
        ApiClientFactory apiClientFactory,
        DockerProvisionerFactory dockerProvisionerFactory,
        HealthCheckHelperFactory healthCheckHelperFactory,
        ServiceConfiguratorFactory serviceConfiguratorFactory,
        ContainersConfig containersConfig,
        int hostCount)
        throws Throwable {
      ZookeeperClientFactory zkFactory = mock(ZookeeperClientFactory.class);
      ZookeeperClient zkBuilder = mock(ZookeeperClient.class);
      doReturn(zkBuilder).when(zkFactory).create();
      doReturn(mock(ServiceConfig.class))
          .when(zkBuilder)
          .getServiceConfig(anyString(), anyString());

      InetSocketAddress address = cloudStoreMachine.getServerSet().getServers().iterator().next();
      doReturn(Collections.singleton(address))
          .when(zkBuilder).getServers(anyString(), eq(DeployerModule.CLOUDSTORE_SERVICE_NAME));
      InetSocketAddress adjustedAddress = new InetSocketAddress(address.getHostName(), address.getPort() - 1);
      doReturn(Collections.singleton(adjustedAddress))
          .when(zkBuilder).getServers(anyString(), eq(DeployerModule.DEPLOYER_SERVICE_NAME));
      doReturn(Collections.singleton(adjustedAddress))
          .when(zkBuilder).getServers(anyString(), eq(DeployerModule.HOUSEKEEPER_SERVICE_NAME));

      return new TestEnvironment.Builder()
          .deployerContext(deployerContext)
          .dockerProvisionerFactory(dockerProvisionerFactory)
          .apiClientFactory(apiClientFactory)
          .listeningExecutorService(listeningExecutorService)
          .healthCheckerFactory(healthCheckHelperFactory)
          .serviceConfiguratorFactory(serviceConfiguratorFactory)
          .cloudServerSet(cloudStoreMachine.getServerSet())
          .containersConfig(containersConfig)
          .zookeeperServersetBuilderFactory(zkFactory)
          .hostCount(hostCount)
          .build();
    }
  }

  private BatchCreateManagementWorkflowService.State buildValidStartupState() {
    return buildValidStartupState(TaskState.TaskStage.CREATED, null);
  }

  private BatchCreateManagementWorkflowService.State buildValidStartupState(
      TaskState.TaskStage stage,
      BatchCreateManagementWorkflowService.TaskState.SubStage subStage) {
    BatchCreateManagementWorkflowService.State state = new BatchCreateManagementWorkflowService.State();
    state.taskState = new BatchCreateManagementWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    state.imageFile = "imageFile";
    state.deploymentServiceLink = "deploymentServiceLink";
    state.isAuthEnabled = false;
    state.isPhotonDHCPEnabled = false;
    return state;
  }

  private BatchCreateManagementWorkflowService.State buildValidPatchState() {
    return buildValidPatchState(
        TaskState.TaskStage.STARTED,
        BatchCreateManagementWorkflowService.TaskState.SubStage.UPLOAD_IMAGE);
  }

  private BatchCreateManagementWorkflowService.State buildValidPatchState(
      TaskState.TaskStage stage,
      BatchCreateManagementWorkflowService.TaskState.SubStage subStage) {

    BatchCreateManagementWorkflowService.State state = new BatchCreateManagementWorkflowService.State();
    state.taskState = new BatchCreateManagementWorkflowService.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    return state;
  }
}
