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

import com.vmware.photon.controller.api.client.resource.DeploymentApi;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * This class implements Deployment API for communicating with APIFE locally.
 */
public class DeploymentLocalApi implements DeploymentApi {
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public ResourceList<Deployment> listAll() throws IOException {
    return null;
  }

  @Override
  public void listAllAsync(FutureCallback<ResourceList<Deployment>> responseCallback) throws IOException {

  }

  @Override
  public Task pauseSystem(String deploymentId) throws IOException {
    return null;
  }

  @Override
  public void pauseSystemAsync(String deploymentId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task resumeSystem(String deploymentId) throws IOException {
    return null;
  }

  @Override
  public void resumeSystemAsync(String deploymentId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Vm> getAllDeploymentVms(String deploymentId) throws IOException {
    return null;
  }

  @Override
  public void getAllDeploymentVmsAsync(String deploymentId, FutureCallback<ResourceList<Vm>> responseCallback)
      throws IOException {

  }
}
