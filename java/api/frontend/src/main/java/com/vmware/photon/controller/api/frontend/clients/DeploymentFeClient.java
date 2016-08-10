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

import com.vmware.photon.controller.api.frontend.BackendTaskExecutor;
import com.vmware.photon.controller.api.frontend.backends.DeploymentBackend;
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.ProjectBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.Auth;
import com.vmware.photon.controller.api.model.AuthInfo;
import com.vmware.photon.controller.api.model.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.model.ClusterType;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.DeploymentCreateSpec;
import com.vmware.photon.controller.api.model.DeploymentDeployOperation;
import com.vmware.photon.controller.api.model.DeploymentSize;
import com.vmware.photon.controller.api.model.DhcpConfigurationSpec;
import com.vmware.photon.controller.api.model.FinalizeMigrationOperation;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.InitializeMigrationOperation;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.common.Constants;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Frontend client for Deployment used by {@link DeploymentsResource}.
 */
@Singleton
public class DeploymentFeClient {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentFeClient.class);

  private final TaskBackend taskBackend;
  private final DeploymentBackend deploymentBackend;
  private final VmBackend vmBackend;
  private final HostBackend hostBackend;
  private final TenantBackend tenantBackend;
  private final ProjectBackend projectBackend;

  private final AuthConfig authConfig;

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;

  @Inject
  public DeploymentFeClient(
      TaskBackend taskBackend,
      DeploymentBackend deploymentBackend,
      VmBackend vmBackend,
      HostBackend hostBackend,
      TenantBackend tenantBackend,
      ProjectBackend projectBackend,
      AuthConfig authConfig,
      TaskCommandFactory commandFactory,
      @BackendTaskExecutor ExecutorService executor) {
    this.taskBackend = taskBackend;
    this.deploymentBackend = deploymentBackend;
    this.vmBackend = vmBackend;
    this.hostBackend = hostBackend;
    this.tenantBackend = tenantBackend;
    this.projectBackend = projectBackend;
    this.authConfig = authConfig;
    this.commandFactory = commandFactory;
    this.executor = executor;
  }

  public Task create(DeploymentCreateSpec deploymentCreateSpec) throws
      InternalException, ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task perform(String deploymentId, DeploymentDeployOperation config)
      throws InternalException, ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareDeploy(deploymentId, config);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task pauseSystem(String deploymentId) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.pauseSystem(deploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task pauseBackgroundTasks(String deploymentId) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.pauseBackgroundTasks(deploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task resumeSystem(String deploymentId) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.resumeSystem(deploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Deployment get(String id) throws ExternalException {
    Deployment deployment = deploymentBackend.toApiRepresentation(id);
    deployment.setClusterConfigurations(deploymentBackend.getClusterConfigurations());
    return deployment;
  }

  public Auth getAuth() {
    if (!authConfig.isAuthEnabled()) {
      return new Auth();
    }

    List<Deployment> deploymentList = deploymentBackend.getAll();
    checkState(deploymentList.size() > 0, "Must have one or more deployments present to display auth info.");

    // deployment object exists
    Deployment deployment = deploymentList.get(0);

    AuthInfo authInfo = deployment.getAuth();
    Auth auth = new Auth();
    auth.setEnabled(authInfo.getEnabled());
    auth.setEndpoint(authInfo.getEndpoint());
    auth.setPort(authInfo.getPort());
    return auth;
  }

  public Task setSecurityGroups(String id, List<String> securityGroups) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.updateSecurityGroups(id, securityGroups);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public ResourceList<Deployment> listAllDeployments() {
    return new ResourceList<>(deploymentBackend.getAll());
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareDeleteDeployment(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public Task initializeDeploymentMigration(InitializeMigrationOperation initializeMigrationOperation,
                                            String destinationDeploymentId) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareInitializeMigrateDeployment(initializeMigrationOperation,
        destinationDeploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task finalizeDeploymentMigration(FinalizeMigrationOperation finalizeMigrationOperation,
                                          String destinationDeploymentId) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareFinalizeMigrateDeployment(finalizeMigrationOperation,
        destinationDeploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task destroy(String id) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareDestroy(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public ResourceList<Vm> listVms(String id, Optional<Integer> pageSize) throws ExternalException {
    deploymentBackend.findById(id);
    ResourceList<Tenant> tenantList = tenantBackend.filter(Optional.of(Constants.TENANT_NAME),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    if (tenantList.getItems() == null || 1 != tenantList.getItems().size()) {
      logger.info("Did not find the expected management tenants {}", tenantList);
      return new ResourceList<>(new ArrayList<Vm>());
    }

    ResourceList<Project> projectList = projectBackend.filter(
        tenantList.getItems().get(0).getId(), Optional.of(Constants.PROJECT_NAME),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    if (projectList.getItems() == null || 1 != projectList.getItems().size()) {
      logger.info("Did not find the expected management projects {}", projectList);
      return new ResourceList<>(new ArrayList<Vm>());
    }

    return vmBackend.filterByProject(projectList.getItems().get(0).getId(), pageSize);
  }

  public ResourceList<Host> listHosts(String id, Optional<Integer> pageSize) throws ExternalException {
    deploymentBackend.findById(id);
    return hostBackend.listAll(pageSize);
  }


  public ResourceList<Host> getHostsPage(String pageLink) throws PageExpiredException {
    return hostBackend.getHostsPage(pageLink);
  }

  public Task configureCluster(String id, ClusterConfigurationSpec spec) throws ExternalException {
    deploymentBackend.findById(id);
    TaskEntity taskEntity =  deploymentBackend.configureCluster(spec);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public Task deleteClusterConfiguration(String id, ClusterType clusterType) throws ExternalException {
    deploymentBackend.findById(id);
    TaskEntity taskEntity = deploymentBackend.deleteClusterConfiguration(clusterType);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public Task setImageDatastores(String id, List<String> imageDatastores) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareUpdateImageDatastores(id, imageDatastores);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return vmBackend.getVmsPage(pageLink);
  }

  public Task configureDhcp(String id, DhcpConfigurationSpec spec) throws ExternalException {
    deploymentBackend.findById(id);
    TaskEntity taskEntity = deploymentBackend.configureDhcp(spec);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public DeploymentSize getDeploymentSize(String id) throws ExternalException {
    deploymentBackend.findById(id);

    DeploymentSize deploymentSize = new DeploymentSize();
    deploymentSize.setNumberHosts(hostBackend.getNumberHosts());
    deploymentSize.setNumberTenants(tenantBackend.getNumberTenants());

    return deploymentSize;

  }
}
