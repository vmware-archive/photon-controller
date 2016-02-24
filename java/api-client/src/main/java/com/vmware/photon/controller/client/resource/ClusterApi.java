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

import com.vmware.photon.controller.api.Cluster;
import com.vmware.photon.controller.api.ClusterResizeOperation;
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
 * Cluster Api.
 */
public class ClusterApi extends ApiBase {
  public ClusterApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/clusters";
  }

  /**
   * Get details about the specified cluster.
   *
   * @param clusterId
   * @return
   * @throws IOException
   */
  public Cluster getCluster(String clusterId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), clusterId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Cluster>() {
        }
    );
  }

  /**
   * Get details about the specified cluster.
   *
   * @param clusterId
   * @param responseCallback
   * @throws IOException
   */
  public void getClusterAsync(final String clusterId, final FutureCallback<Cluster> responseCallback)
      throws IOException {
    final String path = String.format("%s/%s", getBasePath(), clusterId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Cluster>() {
    });
  }

  /**
   * Delete the specified cluster.
   *
   * @param clusterId
   * @return
   * @throws IOException
   */
  public Task delete(String clusterId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), clusterId);

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Delete the specified cluster.
   *
   * @param clusterId
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String clusterId, final FutureCallback<Task> responseCallback)
      throws IOException {
    deleteObjectAsync(clusterId, responseCallback);
  }

  /**
   * Resize the specified cluster.
   *
   * @param clusterId
   * @param size
   * @return
   * @throws IOException
   */
  public Task resize(String clusterId, int size) throws IOException {
    String path = String.format("%s/%s/resize", getBasePath(), clusterId);

    ClusterResizeOperation op = new ClusterResizeOperation();
    op.setNewSlaveCount(size);

    HttpResponse response = this.restClient.perform(RestClient.Method.POST, path, serializeObjectAsJson(op));
    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(response);
  }

  /**
   * Resize the specified cluster.
   *
   * @param clusterId
   * @param size
   * @param responseCallback
   * @throws IOException
   */
  public void resizeAsync(
      final String clusterId, final int size, final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resize", getBasePath(), clusterId);

    ClusterResizeOperation op = new ClusterResizeOperation();
    op.setNewSlaveCount(size);

    createObjectAsync(clusterId, serializeObjectAsJson(op), responseCallback);
  }

  /**
   * Get a list of vms in the specified cluster.
   * @param clusterId
   * @return
   * @throws IOException
   */
  public ResourceList<Vm> getVmsInCluster(String clusterId) throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), clusterId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Vm>>() {
        }
    );
  }

  /**
   * Get a list of vms in the specified cluster.
   *
   * @param clusterId
   * @param responseCallback
   * @throws IOException
   */
  public void getVmsInClusterAsync(final String clusterId, final FutureCallback<ResourceList<Vm>> responseCallback)
    throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), clusterId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<ResourceList<Vm>>() {
    });
  }
}
