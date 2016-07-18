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

package com.vmware.photon.controller.api.client;

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

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * Esxcloud client base.
 */
public class ApiClient {

  private final RestClient restClient;

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

  @Inject
  public ApiClient(@Assisted String endpoint,
                   CloseableHttpAsyncClient httpClient,
                   @SharedSecret String sharedSecret) {
    if (!endpoint.startsWith("http")) {
      endpoint = "http://" + endpoint;
    }

    if (sharedSecret == null) {
      this.restClient = new RestClient(endpoint, httpClient);
    } else {
      this.restClient = new RestClient(endpoint, httpClient, sharedSecret);
    }

    this.tasksApi = new TasksApi(this.restClient);
    this.tenantsApi = new TenantsApi(this.restClient);
    this.resourceTicketApi = new ResourceTicketApi(this.restClient);
    this.projectApi = new ProjectApi(this.restClient);
    this.flavorApi = new FlavorApi(this.restClient);
    this.disksApi = new DisksApi(this.restClient);
    this.imagesApi = new ImagesApi(this.restClient);
    this.vmApi = new VmApi(this.restClient);
    this.systemStatusApi = new SystemStatusApi(this.restClient);
    this.clusterApi = new ClusterApi(this.restClient);
    this.authApi = new AuthApi(this.restClient);
    this.deploymentApi = new DeploymentApi(this.restClient);
  }

  public TasksApi getTasksApi() {
    return tasksApi;
  }

  public TenantsApi getTenantsApi() {
    return tenantsApi;
  }

  public ResourceTicketApi getResourceTicketApi() {
    return resourceTicketApi;
  }

  public ProjectApi getProjectApi() {
    return projectApi;
  }

  public FlavorApi getFlavorApi() {
    return flavorApi;
  }

  public DisksApi getDisksApi() {
    return disksApi;
  }

  public ImagesApi getImagesApi() {
    return imagesApi;
  }

  public VmApi getVmApi() {
    return vmApi;
  }

  public SystemStatusApi getSystemStatusApi() {
    return systemStatusApi;
  }

  public ClusterApi getClusterApi() {
    return clusterApi;
  }

  public AuthApi getAuthApi() {
    return authApi;
  }

  public DeploymentApi getDeploymentApi() {
    return deploymentApi;
  }
}
