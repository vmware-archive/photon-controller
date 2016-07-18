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
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Resource Ticket Api.
 */
public class ResourceTicketApi extends ApiBase {
  public ResourceTicketApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/resource-tickets";
  }

  /**
   * Get details about the specified resource ticket.
   *
   * @param resourceTicketId
   * @return Resource ticket details
   * @throws IOException
   */
  public ResourceTicket getResourceTicket(String resourceTicketId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), resourceTicketId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceTicket>() {
        }
    );
  }

  /**
   * Get details about the specified resource ticket.
   *
   * @param resourceTicketId
   * @param responseCallback
   * @throws IOException
   */
  public void getResourceTicketAsync(final String resourceTicketId, final FutureCallback<ResourceTicket>
      responseCallback) throws
      IOException {
    final String path = String.format("%s/%s", getBasePath(), resourceTicketId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<ResourceTicket>() {
    });
  }

  /**
   * Get tasks associated with the specified resource ticket.
   *
   * @param resourceTicketId
   * @return {@link ResourceList} of {@link Task}
   * @throws IOException
   */
  public ResourceList<Task> getTasksForResourceTicket(String resourceTicketId) throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), resourceTicketId);
    ResourceList<Task> taskResourceList = new ResourceList<>();
    ResourceList<Task> resourceList = getTaskResourceList(path);
    taskResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getTaskResourceList(resourceList.getNextPageLink());
      taskResourceList.getItems().addAll(resourceList.getItems());
    }

    return taskResourceList;
  }

  /**
   * Get all tasks at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Task> getTaskResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Task>>() {
        }
    );
  }

  /**
   * Get tasks associated with the specified resource ticket.
   *
   * @param resourceTicketId
   * @param responseCallback
   * @throws IOException
   */
  public void getTasksForResourceTicketAsync(final String resourceTicketId, final FutureCallback<ResourceList<Task>>
      responseCallback) throws
      IOException {
    final String path = String.format("%s/%s/tasks", getBasePath(), resourceTicketId);

    ResourceList<Task> taskResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Task>> callback = new FutureCallback<ResourceList<Task>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Task> result) {
        if (taskResourceList.getItems() == null) {
          taskResourceList.setItems(result.getItems());
        } else {
          taskResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Task>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(taskResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<Task>>() {});

  }
}
