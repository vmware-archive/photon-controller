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
package com.vmware.photon.controller.api.frontend.clients.api;

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.api.client.resource.AuthApi;
import com.vmware.photon.controller.api.client.resource.ClusterApi;
import com.vmware.photon.controller.api.client.resource.DeploymentApi;
import com.vmware.photon.controller.api.client.resource.DisksApi;
import com.vmware.photon.controller.api.client.resource.FlavorApi;
import com.vmware.photon.controller.api.client.resource.ImagesApi;
import com.vmware.photon.controller.api.client.resource.ProjectApi;
import com.vmware.photon.controller.api.client.resource.ResourceTicketApi;
import com.vmware.photon.controller.api.client.resource.SystemStatusApi;
import com.vmware.photon.controller.api.client.resource.TasksApi;
import com.vmware.photon.controller.api.client.resource.TenantsApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.frontend.clients.ClusterFeClient;
import com.vmware.photon.controller.api.frontend.clients.TaskFeClient;
import com.vmware.photon.controller.api.frontend.clients.VmFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;

import com.google.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class implements the API client to communicate with APIFE internally by other Photon Controller components.
 */
public class LocalApiClient implements ApiClient {
  private final TasksApi tasksApi;
  private final TenantsApi tenantsApi;
  private final ResourceTicketApi resourceTicketApi;
  private final ProjectApi projectApi;
  private final FlavorApi flavorApi;
  private final DisksApi disksApi;
  private final ImagesApi imagesApi;
  private final VmApi vmApi;
  private final SystemStatusApi systemStatusApi;
  private final ClusterApi clusterApi;
  private final AuthApi authApi;
  private final DeploymentApi deploymentApi;

  private final ClusterFeClient clusterFeClient;
  private final PaginationConfig paginationConfig;
  private final TaskFeClient taskFeClient;
  private final VmFeClient vmFeClient;

  // Executor service which will be used to execute all the local API calls asynchronously
  private final ExecutorService executorService;

  @Inject
  public LocalApiClient(ClusterFeClient clusterFeClient, VmFeClient vmFeClient, TaskFeClient taskFeClient,
                        PaginationConfig paginationConfig) {
    this.clusterFeClient = clusterFeClient;
    this.vmFeClient = vmFeClient;
    this.taskFeClient = taskFeClient;
    this.paginationConfig = paginationConfig;

    this.executorService = Executors.newFixedThreadPool(DeployerDefaults.CORE_POOL_SIZE);
    this.tasksApi = new TasksLocalApi(taskFeClient, executorService);
    this.tenantsApi = new TenantsLocalApi();
    this.resourceTicketApi = new ResourceTicketLocalApi();
    this.projectApi = new ProjectLocalApi(vmFeClient, executorService);
    this.flavorApi = new FlavorLocalApi();
    this.disksApi = new DisksLocalApi();
    this.imagesApi = new ImagesLocalApi();
    this.vmApi = new VmLocalApi(vmFeClient, executorService);
    this.systemStatusApi = new SystemStatusLocalApi();
    this.clusterApi = new ClusterLocalApi(clusterFeClient, paginationConfig, executorService);
    this.authApi = new AuthLocalApi();
    this.deploymentApi = new DeploymentLocalApi();
  }

  @Override
  public TasksApi getTasksApi() {
    return tasksApi;
  }

  @Override
  public TenantsApi getTenantsApi() {
    return tenantsApi;
  }

  @Override
  public ResourceTicketApi getResourceTicketApi() {
    return resourceTicketApi;
  }

  @Override
  public ProjectApi getProjectApi() {
    return projectApi;
  }

  @Override
  public FlavorApi getFlavorApi() {
    return flavorApi;
  }

  @Override
  public DisksApi getDisksApi() {
    return disksApi;
  }

  @Override
  public ImagesApi getImagesApi() {
    return imagesApi;
  }

  @Override
  public VmApi getVmApi() {
    return vmApi;
  }

  @Override
  public SystemStatusApi getSystemStatusApi() {
    return systemStatusApi;
  }

  @Override
  public ClusterApi getClusterApi() {
    return clusterApi;
  }

  @Override
  public AuthApi getAuthApi() {
    return authApi;
  }

  @Override
  public DeploymentApi getDeploymentApi() {
    return deploymentApi;
  }

  public String toString() {
    return "LocalApiClient = ClusterFeClient " + clusterFeClient + " PaginationConfig " + paginationConfig;
  }
}
