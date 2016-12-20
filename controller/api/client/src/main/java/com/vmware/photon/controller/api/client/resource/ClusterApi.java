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
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * Interface for interacting with Cluster API.
 */
public interface ClusterApi {
  String getBasePath();

  Cluster getCluster(String clusterId) throws IOException;

  void getClusterAsync(String clusterId, FutureCallback<Cluster> responseCallback)
      throws IOException;

  Task delete(String clusterId) throws IOException;

  void deleteAsync(String clusterId, FutureCallback<Task> responseCallback)
      throws IOException;

  Task resize(String clusterId, int size) throws IOException;

  void resizeAsync(
      String clusterId, int size, FutureCallback<Task> responseCallback)
      throws IOException;

  ResourceList<Vm> getVmsInCluster(String clusterId) throws IOException;

  void getVmsInClusterAsync(String clusterId, FutureCallback<ResourceList<Vm>> responseCallback)
    throws IOException;
}
