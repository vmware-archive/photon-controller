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

/**
 * ApiClient is the common interface that needs to be implemented to communicate with API.
 */
public interface ApiClient {
  TasksApi getTasksApi();

  TenantsApi getTenantsApi();

  ResourceTicketApi getResourceTicketApi();

  ProjectApi getProjectApi();

  FlavorApi getFlavorApi();

  DisksApi getDisksApi();

  ImagesApi getImagesApi();

  VmApi getVmApi();

  SystemStatusApi getSystemStatusApi();

  ClusterApi getClusterApi();

  AuthApi getAuthApi();

  DeploymentApi getDeploymentApi();
}
