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

import com.vmware.photon.controller.api.client.resource.ServiceApi;
import com.vmware.photon.controller.api.frontend.clients.ServiceFeClient;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * This class implements Service API for communicating with APIFE locally.
 */
public class ServiceLocalApi implements ServiceApi {
  private final ServiceFeClient serviceFeClient;
  private final PaginationConfig paginationConfig;
  private final ExecutorService executorService;

  public ServiceLocalApi(ServiceFeClient serviceFeClient, PaginationConfig paginationConfig,
                         ExecutorService executorService) {
    this.serviceFeClient = serviceFeClient;
    this.paginationConfig = paginationConfig;
    this.executorService = executorService;
  }

  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Service getService(String serviceId) throws IOException {
    return null;
  }

  @Override
  public void getServiceAsync(String serviceId, FutureCallback<Service> responseCallback) throws IOException {

  }

  @Override
  public Task delete(String serviceId) throws IOException {
    return null;
  }

  @Override
  public void deleteAsync(String serviceId, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task resize(String serviceId, int size) throws IOException {
    return null;
  }

  @Override
  public void resizeAsync(String serviceId, int size, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Vm> getVmsInService(String serviceId) throws IOException {
    try {
      ResourceList<Vm> vmResourceList = new ResourceList<>();
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, Optional.absent());
      ResourceList<Vm> resourceList = serviceFeClient.findVms(serviceId, adjustedPageSize);
      vmResourceList.setItems(resourceList.getItems());
      while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
        resourceList = serviceFeClient.getVmsPage(resourceList.getNextPageLink());
        vmResourceList.getItems().addAll(resourceList.getItems());
      }

      return vmResourceList;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void getVmsInServiceAsync(String serviceId, FutureCallback<ResourceList<Vm>> responseCallback)
      throws IOException {
    executorService.submit(() -> {
      try {
        ResourceList<Vm> vmResourceList = getVmsInService(serviceId);
        responseCallback.onSuccess(vmResourceList);
      } catch (Exception e) {
        responseCallback.onFailure(e);
      }
    });
  }
}
