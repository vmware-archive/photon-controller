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
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Deployment Api.
 */
public class DeploymentApi extends ApiBase {
  public DeploymentApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/deployments";
  }


  /**
   * List all deployments.
   *
   * @return List of deployments
   * @throws IOException
   */
  public ResourceList<Deployment> listAll() throws IOException {
    ResourceList<Deployment> deploymentResourceList = new ResourceList<>();
    ResourceList<Deployment> resourceList = getDeploymentResourceList(getBasePath());
    deploymentResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getDeploymentResourceList(resourceList.getNextPageLink());
      deploymentResourceList.getItems().addAll(resourceList.getItems());
    }

    return deploymentResourceList;
  }

  /**
   * Get all deployments at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Deployment> getDeploymentResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Deployment>>() {
        }
    );
  }

  /**
   * Lists all deployments.
   *
   * @param responseCallback
   * @throws IOException
   */
  public void listAllAsync(final FutureCallback<ResourceList<Deployment>> responseCallback) throws IOException {
    ResourceList<Deployment> deploymentResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Deployment>> callback = new FutureCallback<ResourceList<Deployment>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Deployment> result) {
        if (deploymentResourceList.getItems() == null) {
          deploymentResourceList.setItems(result.getItems());
        } else {
          deploymentResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Deployment>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(deploymentResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(getBasePath(), callback, new TypeReference<ResourceList<Deployment>>() {});
  }

  /**
   * Pause system.
   *
   * @param deploymentId
   * @return
   * @throws IOException
   */
  public Task pauseSystem(String deploymentId) throws IOException {
    String path = String.format("%s/%s/pause_system", getBasePath(), deploymentId);

    HttpResponse response = this.restClient.perform(RestClient.Method.POST, path, null);
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Pause system.
   *
   * @param deploymentId
   * @param responseCallback
   * @throws IOException
   */
  public void pauseSystemAsync(final String deploymentId, final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/pause_system", getBasePath(), deploymentId);
    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Resume system.
   *
   * @param deploymentId
   * @return
   * @throws IOException
   */
  public Task resumeSystem(String deploymentId) throws IOException {
    String path = String.format("%s/%s/resume_system", getBasePath(), deploymentId);

    HttpResponse response = this.restClient.perform(RestClient.Method.POST, path, null);
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Pause system.
   *
   * @param deploymentId
   * @param responseCallback
   * @throws IOException
   */
  public void resumeSystemAsync(final String deploymentId, final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resume_system", getBasePath(), deploymentId);
    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Get all deployment vms.
   *
   * @param deploymentId
   * @return
   * @throws IOException
   */
  public ResourceList<Vm> getAllDeploymentVms(final String deploymentId) throws IOException{
    String path = String.format("%s/%s/vms", getBasePath(), deploymentId);
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
   * Get all vms at specified path.
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
   * Get all deployment vms.
   *
   * @param deploymentId
   * @return
   * @throws IOException
   */
  public void getAllDeploymentVmsAsync(String deploymentId, final FutureCallback<ResourceList<Vm>> responseCallback)
      throws
      IOException {
    String path = String.format("%s/%s/vms", getBasePath(), deploymentId);
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
