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

import com.vmware.photon.controller.api.client.resource.ProjectApi;
import com.vmware.photon.controller.api.frontend.clients.VmFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ClusterCreateSpec;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.base.FlavoredCompact;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;

import java.io.IOException;

/**
 * This class implements Project API for communicating with APIFE locally.
 */
public class ProjectLocalApi implements ProjectApi {
  @Inject VmFeClient vmFeClient;

  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Project getProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public void getProjectAsync(String projectId, FutureCallback<Project> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Task> getTasksForProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public void getTasksForProjectAsync(String projectId, FutureCallback<ResourceList<Task>> responseCallback)
      throws IOException {

  }

  @Override
  public Task delete(String id) throws IOException {
    return null;
  }

  @Override
  public void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task createDisk(String projectId, DiskCreateSpec diskCreateSpec) throws IOException {
    return null;
  }

  @Override
  public void createDiskAsync(String projectId, DiskCreateSpec diskCreateSpec, FutureCallback<Task> responseCallback)
      throws IOException {

  }

  @Override
  public ResourceList<PersistentDisk> getDisksInProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public void getDisksInProjectAsync(String projectId, FutureCallback<ResourceList<PersistentDisk>> responseCallback)
      throws IOException {

  }

  @Override
  public Task createVm(String projectId, VmCreateSpec vmCreateSpec) throws IOException {
    try {
      return vmFeClient.create(projectId, vmCreateSpec);
    } catch (ExternalException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void createVmAsync(String projectId, VmCreateSpec vmCreateSpec, FutureCallback<Task> responseCallback)
      throws IOException {
    try {
      Task task = createVm(projectId, vmCreateSpec);
      responseCallback.onSuccess(task);
    } catch (Exception e) {
      responseCallback.onFailure(e);
    }
  }

  @Override
  public ResourceList<FlavoredCompact> getVmsInProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public ResourceList<Vm> getVmDetailsInProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public void getVmsInProjectAsync(String projectId, FutureCallback<ResourceList<FlavoredCompact>> responseCallback)
      throws IOException {

  }

  @Override
  public Task createCluster(String projectId, ClusterCreateSpec clusterCreateSpec) throws IOException {
    return null;
  }

  @Override
  public void createClusterAsync(String projectId, ClusterCreateSpec clusterCreateSpec,
                                 FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Cluster> getClustersInProject(String projectId) throws IOException {
    return null;
  }

  @Override
  public void getClustersInProjectAsync(String projectId, FutureCallback<ResourceList<Cluster>> responseCallback)
      throws IOException {

  }
}
