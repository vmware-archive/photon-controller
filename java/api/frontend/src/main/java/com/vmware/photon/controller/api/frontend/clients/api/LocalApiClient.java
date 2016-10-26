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

import com.google.inject.Inject;

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

  public LocalApiClient() {
    this.tasksApi = new TasksLocalApi();
    this.tenantsApi = new TenantsLocalApi();
    this.resourceTicketApi = new ResourceTicketLocalApi();
    this.projectApi = new ProjectLocalApi();
    this.flavorApi = new FlavorLocalApi();
    this.disksApi = new DisksLocalApi();
    this.imagesApi = new ImagesLocalApi();
    this.vmApi = new VmLocalApi();
    this.systemStatusApi = new SystemStatusLocalApi();
    this.clusterApi = new ClusterLocalApi();
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
    return getVmApi();
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
}
