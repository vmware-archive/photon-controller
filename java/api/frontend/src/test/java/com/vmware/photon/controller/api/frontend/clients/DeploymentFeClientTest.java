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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.ProjectBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskCommandExecutorService;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Auth;
import com.vmware.photon.controller.api.model.AuthInfo;
import com.vmware.photon.controller.api.model.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentDeployOperation;
import com.vmware.photon.controller.api.model.DeploymentSize;
import com.vmware.photon.controller.api.model.DhcpConfigurationSpec;
import com.vmware.photon.controller.api.model.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.model.InitializeMigrationOperation;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.common.Constants;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.mockito.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Tests {@link DeploymentFeClient}.
 */
public class DeploymentFeClientTest {
  private DeploymentFeClient feClient;

  private TaskBackend taskBackend;
  private DeploymentBackend deploymentBackend;
  private VmBackend vmBackend;
  private HostBackend hostBackend;
  private TenantBackend tenantBackend;
  private ProjectBackend projectBackend;
  private AuthConfig authConfig;
  private TaskCommandFactory commandFactory;
  private ExecutorService executorService;

  private void setUpCommon() {
    taskBackend = mock(TaskBackend.class);
    deploymentBackend = mock(DeploymentBackend.class);
    vmBackend = mock(VmBackend.class);
    hostBackend = mock(HostBackend.class);
    tenantBackend = mock(TenantBackend.class);
    projectBackend = mock(ProjectBackend.class);
    authConfig = new AuthConfig();

    commandFactory = mock(TaskCommandFactory.class);
    executorService = mock(TaskCommandExecutorService.class);

    feClient = new DeploymentFeClient(
        taskBackend, deploymentBackend, vmBackend, hostBackend, tenantBackend, projectBackend, authConfig,
        commandFactory, executorService);
  }

  /**
   * dummy test to keep IntelliJ happy.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests the create method.
   */
  public class CreateTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testTaskIsCreateAndSubmitted() throws Throwable {
      DeploymentCreateSpec spec = new DeploymentCreateSpec();
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareCreateDeployment(spec);

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      Task resp = feClient.create(spec);
      assertThat(resp, is(task));
    }
  }

  /**
   * Tests the perform method.
   */
  public class PerformDeploymentTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testTaskIsCreated() throws Throwable {
      String deploymentId = "deployment-id";
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareDeploy(eq(deploymentId),
          any(DeploymentDeployOperation.class));

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.perform("deployment-id", new DeploymentDeployOperation());
      assertThat(resp, is(task));
      verify(executorService).submit(command);
    }
  }

  /**
   * Tests the pauseBackgroundTasks method.
   */
  public class PauseBackgroundTasksTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testTaskIsCreated() throws Throwable {
      String deploymentId = "deployment-id";
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).pauseBackgroundTasks(deploymentId);

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.pauseBackgroundTasks("deployment-id");
      assertThat(resp, is(task));
      verify(executorService).submit(command);
    }
  }

  /**
   * Tests the delete and destroy methods.
   */
  public class DeleteTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testDelete() throws Throwable {
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareDeleteDeployment(any(String.class));

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.delete("dummy-deployment-id");
      assertThat(resp, is(task));
      //delete creates a completed task so no more execution is required.
      verifyNoMoreInteractions(executorService);
    }

    @Test
    public void testDestroy() throws Throwable {
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareDestroy(any(String.class));

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.destroy("dummy-deployment-id");
      assertThat(resp, is(task));
      verify(executorService).submit(command);
    }
  }

  /**
   * Tests the listVms method.
   */
  public class ListVmsTest {
    String deploymentId;
    Tenant tenant;
    Project project;
    Vm vm;
    String pageLink;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();

      deploymentId = "deployment-id";
      doReturn(null).when(deploymentBackend).findById(deploymentId);

      tenant = new Tenant();
      tenant.setId("mgmt-tenant-id");
      tenant.setName(Constants.TENANT_NAME);
      doReturn(new ResourceList<>(ImmutableList.of(tenant))).when(tenantBackend).filter(
          Optional.of(Constants.TENANT_NAME), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      project = new Project();
      project.setId("mgmt-project-id");
      project.setName(Constants.PROJECT_NAME);
      doReturn(new ResourceList<>(ImmutableList.of(project))).when(projectBackend).filter(tenant.getId(),
          Optional.of(Constants.PROJECT_NAME), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      vm = new Vm();
      vm.setId("mgmt-vm-id");
      doReturn(new ResourceList<>(ImmutableList.of(vm)))
          .when(vmBackend)
          .filterByProject(project.getId(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));

      pageLink = UUID.randomUUID().toString();
      doReturn(new ResourceList<>(ImmutableList.of(vm))).when(vmBackend).getVmsPage(pageLink);
    }

    /**
     * Test a successful invocation of the method.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccess() throws Throwable {
      ResourceList<Vm> list = feClient.listVms(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(list.getItems().size(), is(1));
      assertThat(list.getItems().get(0), is(vm));
    }

    /**
     * Test scenario when either no management tenant could be found or
     * more than one tenant matched the name.
     *
     * @param message
     * @param tenantList
     * @throws Throwable
     */
    @Test(dataProvider = "NotFoundTenantData")
    public void testNotFoundTenant(String message, ResourceList<Tenant> tenantList) throws Throwable {
      doReturn(tenantList).when(tenantBackend).filter(Matchers.<Optional<String>>any(),
          Matchers.<Optional<Integer>>any());
      ResourceList list = feClient.listVms(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(list.getItems().size(), is(0));
    }

    @DataProvider(name = "NotFoundTenantData")
    Object[][] getNotFoundTenantData() {
      return new Object[][]{
          {"No tenants", new ResourceList<Tenant>(ImmutableList.of())},
          {"Multiple tenants", new ResourceList<Tenant>(ImmutableList.of(new Tenant(), new Tenant()))}
      };
    }

    /**
     * Test scenario when either management project could not be found or
     * more than one project matched the name.
     *
     * @param message
     * @param projectList
     * @throws Throwable
     */
    @Test(dataProvider = "NotFoundProjectData")
    public void testNotFoundProject(String message, ResourceList<Project> projectList) throws Throwable {
      doReturn(projectList).when(projectBackend).filter(tenant.getId(), Optional.of(Constants.PROJECT_NAME),
          Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      ResourceList list = feClient.listVms(deploymentId, Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
      assertThat(list.getItems().size(), is(0));
    }

    @DataProvider(name = "NotFoundProjectData")
    Object[][] getNotFoundProjectData() {
      return new Object[][]{
          {"No projects", new ResourceList<>(ImmutableList.of())},
          {"Multiple projects", new ResourceList<>(ImmutableList.of(new Project(), new Project()))}
      };
    }

    @Test
    public void testGetVmsPage() throws ExternalException {
      ResourceList<Vm> resourceList = feClient.getVmsPage(pageLink);
      assertThat(resourceList.getItems().size(), is(1));
      assertThat(resourceList.getItems().get(0), is(vm));
    }
  }

  /**
   * Tests the migration methods.
   */
  public class PerformDeploymentMigrationTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testInitializeMigrationTaskIsCreated() throws Throwable {
      InitializeMigrationOperation op = new InitializeMigrationOperation();
      op.setSourceLoadBalancerAddress("sourceAddress");

      String deploymentId = "deployment-id";
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareInitializeMigrateDeployment(op, deploymentId);

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.initializeDeploymentMigration(op, deploymentId);
      assertThat(resp, is(task));
      verify(executorService).submit(command);
    }

    @Test
    public void testFinalizeMigrationTaskIsCreated() throws Throwable {
      FinalizeMigrationOperation op = new FinalizeMigrationOperation();
      op.setSourceLoadBalancerAddress("address");

      String deploymentId = "deployment-id";
      TaskEntity taskEntity = new TaskEntity();
      doReturn(taskEntity).when(deploymentBackend).prepareFinalizeMigrateDeployment(op, deploymentId);

      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.finalizeDeploymentMigration(op, deploymentId);
      assertThat(resp, is(task));
      verify(executorService).submit(command);
    }
  }

  /**
   * Tests the configure cluster method.
   */
  public class ConfigureClusterTest {
    String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      deploymentId = "deployment-id";
      doReturn(null).when(deploymentBackend).findById(deploymentId);
      TaskEntity taskEntity = new TaskEntity();

      doReturn(taskEntity).when(deploymentBackend).configureCluster(any(ClusterConfigurationSpec.class));
      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      TaskCommand command = mock(TaskCommand.class);
      doReturn(command).when(commandFactory).create(taskEntity);

      Task resp = feClient.configureCluster(deploymentId, new ClusterConfigurationSpec());
      assertThat(resp, is(task));

    }
  }

  /**
   * Tests the configure DHCP method.
   */
  public class ConfigureDhcpTest {
    String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      deploymentId = "deployment-id";
      DeploymentEntity deploymentEntity = new DeploymentEntity();
      deploymentEntity.setNetworkManagerAddress("1.2.3.4");
      deploymentEntity.setNetworkManagerUsername("username");
      deploymentEntity.setNetworkManagerPassword("password");
      doReturn(deploymentEntity).when(deploymentBackend).findById(deploymentId);
      TaskEntity taskEntity = new TaskEntity();

      doReturn(taskEntity).when(deploymentBackend).configureDhcp(any(DhcpConfigurationSpec.class));
      Task task = new Task();
      doReturn(task).when(taskBackend).getApiRepresentation(taskEntity);

      Task resp = feClient.configureDhcp(deploymentId, new DhcpConfigurationSpec());
      assertThat(resp, is(task));
    }
  }

  /**
   * Tests the getAuth method.
   */
  public class GetAuthTest {
    @BeforeMethod
    public void setUp() {
      setUpCommon();
    }

    @Test
    public void testAuthDisabled() {
      Auth auth = feClient.getAuth();
      assertThat(auth.getEnabled(), is(false));
      assertThat(auth.getEndpoint(), nullValue());
      assertThat(auth.getPort(), nullValue());
    }

    @Test
    public void testAuthEnabled() {
      AuthInfo authInfo = new AuthInfo();
      authInfo.setEnabled(true);
      authInfo.setEndpoint("10.146.1.1");
      authInfo.setPort(443);

      Deployment deployment = new Deployment();
      deployment.setAuth(authInfo);

      authConfig.setEnableAuth(true);
      doReturn(ImmutableList.of(deployment)).when(deploymentBackend).getAll();

      Auth auth = feClient.getAuth();
      assertThat(auth.getEnabled(), is(authInfo.getEnabled()));
      assertThat(auth.getEndpoint(), is(authInfo.getEndpoint()));
      assertThat(auth.getPort(), is(authInfo.getPort()));
    }

    @Test(expectedExceptions = IllegalStateException.class,
        expectedExceptionsMessageRegExp = "Must have one or more deployments present to display auth info.")
    public void testAuthEnabledInConfigNoDeployment() {
      authConfig.setEnableAuth(true);
      doReturn(new ArrayList<>()).when(deploymentBackend).getAll();

      feClient.getAuth();
    }
  }

  /**
   * Tests the getDeploymentSize method.
   */
  public class GetDeploymentSizeTest {
    String deploymentId;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpCommon();
    }

    @Test
    public void testSuccess() throws Exception {
      deploymentId = "deployment-id";
      doReturn(null).when(deploymentBackend).findById(deploymentId);

      DeploymentSize deploymentSize = new DeploymentSize();
      deploymentSize.setNumberHosts(2);
      deploymentSize.setNumberTenants(7);

      doReturn(deploymentSize.getNumberHosts()).when(hostBackend).getNumberHosts();
      doReturn(deploymentSize.getNumberTenants()).when(tenantBackend).getNumberTenants();

      DeploymentSize deploymentRetrievedSize = feClient.getDeploymentSize(deploymentId);
      assertThat(deploymentRetrievedSize.getNumberHosts(), is(deploymentSize.getNumberHosts()));
      assertThat(deploymentRetrievedSize.getNumberTenants(), is(deploymentSize.getNumberTenants()));
    }

    @Test(expectedExceptions = DeploymentNotFoundException.class,
        expectedExceptionsMessageRegExp = "Deployment #.* not found")
    public void testFailure() throws Exception {
      deploymentId = "invalid-deployment-id";
      doThrow(new DeploymentNotFoundException(deploymentId)).when(deploymentBackend).findById(deploymentId);

      feClient.getDeploymentSize(deploymentId);
    }

  }
}
