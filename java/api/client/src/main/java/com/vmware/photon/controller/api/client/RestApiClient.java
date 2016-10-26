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
import com.vmware.photon.controller.api.client.resource.AuthRestApi;
import com.vmware.photon.controller.api.client.resource.ClusterApi;
import com.vmware.photon.controller.api.client.resource.ClusterRestApi;
import com.vmware.photon.controller.api.client.resource.DeploymentApi;
import com.vmware.photon.controller.api.client.resource.DeploymentRestApi;
import com.vmware.photon.controller.api.client.resource.DisksApi;
import com.vmware.photon.controller.api.client.resource.DisksRestApi;
import com.vmware.photon.controller.api.client.resource.FlavorApi;
import com.vmware.photon.controller.api.client.resource.FlavorRestApi;
import com.vmware.photon.controller.api.client.resource.ImagesApi;
import com.vmware.photon.controller.api.client.resource.ImagesRestApi;
import com.vmware.photon.controller.api.client.resource.ProjectApi;
import com.vmware.photon.controller.api.client.resource.ProjectRestApi;
import com.vmware.photon.controller.api.client.resource.ResourceTicketApi;
import com.vmware.photon.controller.api.client.resource.ResourceTicketRestApi;
import com.vmware.photon.controller.api.client.resource.SystemStatusApi;
import com.vmware.photon.controller.api.client.resource.SystemStatusRestApi;
import com.vmware.photon.controller.api.client.resource.TasksApi;
import com.vmware.photon.controller.api.client.resource.TasksRestApi;
import com.vmware.photon.controller.api.client.resource.TenantsApi;
import com.vmware.photon.controller.api.client.resource.TenantsRestApi;
import com.vmware.photon.controller.api.client.resource.VmApi;
import com.vmware.photon.controller.api.client.resource.VmRestApi;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

/**
 * This class implements the rest client for accessing APIFE.
 */
public class RestApiClient implements ApiClient {

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
  public RestApiClient(@Assisted String endpoint,
                       CloseableHttpAsyncClient httpClient,
                       @SharedSecret String sharedSecret,
                       String protocol) {
    if (!endpoint.startsWith("http")) {
      endpoint = protocol + "://" + endpoint;
    }

    if (sharedSecret == null) {
      this.restClient = new RestClient(endpoint, httpClient);
    } else {
      this.restClient = new RestClient(endpoint, httpClient, sharedSecret);
    }

    this.tasksApi = new TasksRestApi(this.restClient);
    this.tenantsApi = new TenantsRestApi(this.restClient);
    this.resourceTicketApi = new ResourceTicketRestApi(this.restClient);
    this.projectApi = new ProjectRestApi(this.restClient);
    this.flavorApi = new FlavorRestApi(this.restClient);
    this.disksApi = new DisksRestApi(this.restClient);
    this.imagesApi = new ImagesRestApi(this.restClient);
    this.vmApi = new VmRestApi(this.restClient);
    this.systemStatusApi = new SystemStatusRestApi(this.restClient);
    this.clusterApi = new ClusterRestApi(this.restClient);
    this.authApi = new AuthRestApi(this.restClient);
    this.deploymentApi = new DeploymentRestApi(this.restClient);
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
}
