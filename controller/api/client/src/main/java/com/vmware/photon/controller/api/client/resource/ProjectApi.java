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

import java.io.IOException;

/**
 * Interface for interacting with Project API.
 */
public interface ProjectApi {
  String getBasePath();

  Project getProject(String projectId) throws IOException;

  void getProjectAsync(String projectId, FutureCallback<Project> responseCallback)
      throws IOException;

  ResourceList<Task> getTasksForProject(String projectId) throws IOException;

  void getTasksForProjectAsync(String projectId, FutureCallback<ResourceList<Task>>
      responseCallback)
      throws
      IOException;

  Task delete(String id) throws IOException;

  void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException;

  Task createDisk(String projectId, DiskCreateSpec diskCreateSpec) throws IOException;

  void createDiskAsync(String projectId, DiskCreateSpec diskCreateSpec, FutureCallback<Task>
      responseCallback)
      throws IOException;

  ResourceList<PersistentDisk> getDisksInProject(String projectId) throws IOException;

  void getDisksInProjectAsync(String projectId,
                              FutureCallback<ResourceList<PersistentDisk>> responseCallback)
      throws IOException;

  Task createVm(String projectId, VmCreateSpec vmCreateSpec) throws IOException;

  void createVmAsync(String projectId, VmCreateSpec vmCreateSpec, FutureCallback<Task>
      responseCallback)
      throws IOException;

  ResourceList<FlavoredCompact> getVmsInProject(String projectId) throws IOException;

  ResourceList<Vm> getVmDetailsInProject(String projectId) throws IOException;

  void getVmsInProjectAsync(String projectId, FutureCallback<ResourceList<FlavoredCompact>>
      responseCallback)
      throws
      IOException;

  Task createCluster(String projectId, ClusterCreateSpec clusterCreateSpec) throws IOException;

  void createClusterAsync(String projectId, ClusterCreateSpec clusterCreateSpec,
                          FutureCallback<Task> responseCallback) throws IOException;

  ResourceList<Cluster> getClustersInProject(String projectId) throws IOException;

  void getClustersInProjectAsync(String projectId, FutureCallback<ResourceList<Cluster>>
      responseCallback) throws IOException;
}
