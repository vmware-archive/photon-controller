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

import com.vmware.photon.controller.api.client.resource.ClusterApi;
import com.vmware.photon.controller.api.frontend.clients.ClusterFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.Cluster;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * This class implements Cluster API for communicating with APIFE locally.
 */
public class ClusterLocalApi implements ClusterApi {
  private final ClusterFeClient clusterFeClient;
  private final PaginationConfig paginationConfig;
  private final ExecutorService executorService;

  public ClusterLocalApi(ClusterFeClient clusterFeClient, PaginationConfig paginationConfig,
                         ExecutorService executorService) {
    this.clusterFeClient = clusterFeClient;
    this.paginationConfig = paginationConfig;
    this.executorService = executorService;
  }

  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Cluster getCluster(String clusterId) throws IOException {
    return null;
  }

  @Override
  public void getClusterAsync(String clusterId, FutureCallback<Cluster> responseCallback) throws IOException {

  }

  @Override
  public Task delete(String clusterId) throws IOException {
    return null;
  }

  @Override
  public void deleteAsync(String clusterId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task resize(String clusterId, int size) throws IOException {
    return null;
  }

  @Override
  public void resizeAsync(String clusterId, int size, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Vm> getVmsInCluster(String clusterId) throws IOException {
    try {
      ResourceList<Vm> vmResourceList = new ResourceList<>();
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, Optional.absent());
      ResourceList<Vm> resourceList = clusterFeClient.findVms(clusterId, adjustedPageSize);
      vmResourceList.setItems(resourceList.getItems());
      while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
        resourceList = clusterFeClient.getVmsPage(resourceList.getNextPageLink());
        vmResourceList.getItems().addAll(resourceList.getItems());
      }

      return vmResourceList;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void getVmsInClusterAsync(String clusterId, FutureCallback<ResourceList<Vm>> responseCallback)
      throws IOException {
    executorService.submit(() -> {
      try {
        ResourceList<Vm> vmResourceList = getVmsInCluster(clusterId);
        responseCallback.onSuccess(vmResourceList);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }
}
