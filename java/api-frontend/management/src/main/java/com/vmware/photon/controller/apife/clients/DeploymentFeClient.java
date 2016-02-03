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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.ClusterConfiguration;
import com.vmware.photon.controller.api.ClusterConfigurationSpec;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentCreateSpec;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Tenant;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.ProjectBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.backends.TenantBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.Constants;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      TaskCommandFactory commandFactory,
      @BackendTaskExecutor ExecutorService executor) {
    this.taskBackend = taskBackend;
    this.deploymentBackend = deploymentBackend;
    this.vmBackend = vmBackend;
    this.hostBackend = hostBackend;
    this.tenantBackend = tenantBackend;
    this.projectBackend = projectBackend;
    this.commandFactory = commandFactory;
    this.executor = executor;
  }

  public Task create(DeploymentCreateSpec deploymentCreateSpec) throws
      InternalException, ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareCreateDeployment(deploymentCreateSpec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task perform(String deploymentId) throws InternalException, ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareDeploy(deploymentId);
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

  public Task initializeDeploymentMigration(String sourceLoadBalancerAddress, String destinationDeploymentId)
      throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareInitializeMigrateDeployment(sourceLoadBalancerAddress,
        destinationDeploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task finalizeDeploymentMigration(String sourceLoadBalancerAddress, String destinationDeploymentId)
      throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.prepareFinalizeMigrateDeployment(sourceLoadBalancerAddress,
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

  public ResourceList<Vm> listVms(String id) throws ExternalException {
    deploymentBackend.findById(id);
    ResourceList<Tenant> tenantList = tenantBackend.filter(Optional.of(Constants.TENANT_NAME),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE));
    if (tenantList.getItems() == null || 1 != tenantList.getItems().size()) {
      logger.info("Did not find the expected management tenants {}", tenantList);
      return new ResourceList<>(new ArrayList<Vm>());
    }

    List<Project> projectList = projectBackend.filter(
        tenantList.getItems().get(0).getId(), Optional.of(Constants.PROJECT_NAME));
    if (1 != projectList.size()) {
      logger.info("Did not find the expected management projects {}", projectList);
      return new ResourceList<>(new ArrayList<Vm>());
    }

    return vmBackend.filterByProject(projectList.get(0).getId());
  }

  public ResourceList<Host> listHosts(String id, Optional<Integer> pageSize) throws ExternalException {
    deploymentBackend.findById(id);
    return hostBackend.listAll(pageSize);
  }


  public ResourceList<Host> getHostsPage(String pageLink) throws PageExpiredException{
    return hostBackend.getHostsPage(pageLink);
  }

  public ClusterConfiguration configureCluster(String id, ClusterConfigurationSpec spec) throws ExternalException {
    deploymentBackend.findById(id);
    return deploymentBackend.configureCluster(spec);
  }

  public Task deleteClusterConfiguration(String id, ClusterType clusterType) throws ExternalException {
    deploymentBackend.findById(id);
    TaskEntity taskEntity =  deploymentBackend.deleteClusterConfiguration(clusterType);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public Task setImageDatastores(String id, List<String> imageDatastores) throws ExternalException {
    TaskEntity taskEntity = deploymentBackend.updateImageDatastores(id, imageDatastores);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }
}
