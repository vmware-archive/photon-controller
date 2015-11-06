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

package com.vmware.photon.controller.client.resource;

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

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
   * @return List of flavors
   * @throws IOException
   */
  public ResourceList<Deployment> listAll() throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, getBasePath(), null);
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
    getObjectByPathAsync(getBasePath(), responseCallback, new TypeReference<ResourceList<Deployment>>() {
    });
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
    getObjectByPathAsync(path, responseCallback, new TypeReference<ResourceList<Vm>>() {
    });
  }
}
