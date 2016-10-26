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
package com.vmware.photon.controller.api.client.resource;

import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with Deployment API.
 */
public interface DeploymentApi {
  String getBasePath();

  ResourceList<Deployment> listAll() throws IOException;

  void listAllAsync(FutureCallback<ResourceList<Deployment>> responseCallback) throws IOException;

  Task pauseSystem(String deploymentId) throws IOException;

  void pauseSystemAsync(String deploymentId, FutureCallback<Task> responseCallback)
      throws IOException;

  Task resumeSystem(String deploymentId) throws IOException;

  void resumeSystemAsync(String deploymentId, FutureCallback<Task> responseCallback)
      throws IOException;

  ResourceList<Vm> getAllDeploymentVms(String deploymentId) throws IOException;

  void getAllDeploymentVmsAsync(String deploymentId, FutureCallback<ResourceList<Vm>> responseCallback)
      throws
      IOException;
}
