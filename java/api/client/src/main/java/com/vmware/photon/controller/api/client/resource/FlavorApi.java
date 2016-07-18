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
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Flavors api.
 */
public class FlavorApi extends ApiBase {
  public FlavorApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/flavors";
  }

  /**
   * Creates the specified flavor.
   *
   * @param flavorCreateSpec - flavor definition
   * @return Tracking {@link Task}
   * @throws java.io.IOException
   */
  public Task create(FlavorCreateSpec flavorCreateSpec) throws IOException {
    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        getBasePath(),
        serializeObjectAsJson(flavorCreateSpec)
    );

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Creates the specified flavor.
   *
   * @param flavorCreateSpec
   * @param responseCallback
   * @throws IOException
   */
  public void createAsync(final FlavorCreateSpec flavorCreateSpec, final FutureCallback<Task> responseCallback)
      throws IOException {
    createObjectAsync(getBasePath(), serializeObjectAsJson(flavorCreateSpec), responseCallback);
  }

  /**
   * List all flavors.
   *
   * @return List of flavors
   * @throws IOException
   */
  public ResourceList<Flavor> listAll() throws IOException {
    ResourceList<Flavor> flavorResourceList = new ResourceList<>();
    ResourceList<Flavor> resourceList = getFlavorResourceList(getBasePath());
    flavorResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getFlavorResourceList(resourceList.getNextPageLink());
      flavorResourceList.getItems().addAll(resourceList.getItems());
    }

    return flavorResourceList;
  }

  /**
   * List all flavors filtered by query params.
   *
   * @param queryParams
   * @return List of flavors
   * @throws IOException
   */
  public ResourceList<Flavor> listAll(Map<String, String> queryParams) throws IOException {
    ResourceList<Flavor> flavorResourceList = new ResourceList<>();
    ResourceList<Flavor> resourceList = getFlavorResourceList(getBasePath() + generateQueryString(queryParams));
    flavorResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getFlavorResourceList(resourceList.getNextPageLink());
      flavorResourceList.getItems().addAll(resourceList.getItems());
    }
    return flavorResourceList;
  }

  /**
   * Get all flavors at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Flavor> getFlavorResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    ResourceList<Flavor> resourceList = this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Flavor>>() {
        }
    );
    return resourceList;
  }

  /**
   * Lists all flavors.
   *
   * @param responseCallback
   * @throws IOException
   */
  public void listAllAsync(final FutureCallback<ResourceList<Flavor>> responseCallback) throws IOException {
    ResourceList<Flavor> flavorResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Flavor>> callback = new FutureCallback<ResourceList<Flavor>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Flavor> result) {
        if (flavorResourceList.getItems() == null) {
          flavorResourceList.setItems(result.getItems());
        } else {
          flavorResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Flavor>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(flavorResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(getBasePath(), callback, new TypeReference<ResourceList<Flavor>>() {});
  }

  /**
   * Get specified flavor.
   *
   * @param flavorId - id of flavor
   * @return Flavor matching the
   * @throws IOException
   */
  public Flavor getFlavor(String flavorId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), flavorId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Flavor>() {
        }
    );
  }

  /**
   * Gets the specified flavor.
   *
   * @param flavorId
   * @param responseCallback
   * @throws IOException
   */
  public void getFlavorAsync(final String flavorId, final FutureCallback<Flavor> responseCallback) throws IOException {
    final String path = String.format("%s/%s", getBasePath(), flavorId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Flavor>() {
    });
  }

  /**
   * Delete the specified flavor.
   *
   * @param id - id of the flavor
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task delete(String id) throws IOException {
    String path = String.format("%s/%s", getBasePath(), id);

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Deletes the specified flavor.
   *
   * @param flavorId
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String flavorId, final FutureCallback<Task> responseCallback) throws IOException {
    deleteObjectAsync(flavorId, responseCallback);
  }

}
