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

import com.vmware.photon.controller.api.client.RestClient;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Service Api.
 */
public class ServiceRestApi extends ApiBase implements ServiceApi {
  public ServiceRestApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/services";
  }

  /**
   * Get details about the specified service.
   *
   * @param serviceId
   * @return
   * @throws IOException
   */
  @Override
  public Service getService(String serviceId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), serviceId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Service>() {
        }
    );
  }

  /**
   * Get details about the specified service.
   *
   * @param serviceId
   * @param responseCallback
   * @throws IOException
   */
  @Override
  public void getServiceAsync(final String serviceId, final FutureCallback<Service> responseCallback)
      throws IOException {
    final String path = String.format("%s/%s", getBasePath(), serviceId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Service>() {
    });
  }

  /**
   * Delete the specified service.
   *
   * @param serviceId
   * @return
   * @throws IOException
   */
  @Override
  public Task delete(String serviceId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), serviceId);

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Delete the specified service.
   *
   * @param serviceId
   * @param responseCallback
   * @throws IOException
   */
  @Override
  public void deleteAsync(final String serviceId, final FutureCallback<Task> responseCallback)
      throws IOException {
    deleteObjectAsync(serviceId, responseCallback);
  }

  /**
   * Resize the specified service.
   *
   * @param serviceId
   * @param size
   * @return
   * @throws IOException
   */
  @Override
  public Task resize(String serviceId, int size) throws IOException {
    String path = String.format("%s/%s/resize", getBasePath(), serviceId);

    ServiceResizeOperation op = new ServiceResizeOperation();
    op.setNewWorkerCount(size);

    HttpResponse response = this.restClient.perform(RestClient.Method.POST, path, serializeObjectAsJson(op));
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Resize the specified service.
   *
   * @param serviceId
   * @param size
   * @param responseCallback
   * @throws IOException
   */
  @Override
  public void resizeAsync(
      final String serviceId, final int size, final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resize", getBasePath(), serviceId);

    ServiceResizeOperation op = new ServiceResizeOperation();
    op.setNewWorkerCount(size);

    createObjectAsync(path, serializeObjectAsJson(op), responseCallback);
  }

  /**
   * Get a list of vms in the specified service.
   * @param serviceId
   * @return
   * @throws IOException
   */
  @Override
  public ResourceList<Vm> getVmsInService(String serviceId) throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), serviceId);

    ResourceList<Vm> vmResourceList = new ResourceList<>();
    ResourceList<Vm> resourceList = getVmResourceList(path);
    vmResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getVmResourceList(resourceList.getNextPageLink());
      vmResourceList.getItems().addAll(resourceList.getItems());
    }

    return vmResourceList;
  }

  /**
   * Get all Vms at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Vm> getVmResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Vm>>() {
        }
    );
  }

  /**
   * Get a list of vms in the specified service.
   *
   * @param serviceId
   * @param responseCallback
   * @throws IOException
   */
  @Override
  public void getVmsInServiceAsync(final String serviceId, final FutureCallback<ResourceList<Vm>> responseCallback)
    throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), serviceId);

    ResourceList<Vm> vmResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Vm>> callback = new FutureCallback<ResourceList<Vm>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Vm> result) {
        if (vmResourceList.getItems() == null) {
          vmResourceList.setItems(result.getItems());
        } else {
          vmResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Vm>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(vmResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<Vm>>() {});
  }
}
