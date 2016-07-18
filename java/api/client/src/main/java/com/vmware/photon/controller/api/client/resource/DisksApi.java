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
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Disks Api.
 */
public class DisksApi extends ApiBase {
  public DisksApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/disks";
  }

  /**
   * Get details about the specified disk.
   *
   * @param diskId
   * @return Disk details
   * @throws java.io.IOException
   */
  public PersistentDisk getDisk(String diskId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), diskId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<PersistentDisk>() {
        }
    );
  }

  /**
   * This method get a disk object.
   *
   * @param diskId
   * @param responseCallback
   * @throws IOException
   */
  public void getDiskAsync(final String diskId, final FutureCallback<PersistentDisk> responseCallback) throws
      IOException {
    String path = String.format("%s/%s", getBasePath(), diskId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<PersistentDisk>() {
    });
  }

  /**
   * Get tasks associated with the specified disk.
   *
   * @param diskId
   * @return {@link ResourceList} of {@link Task}
   * @throws IOException
   */
  public ResourceList<Task> getTasksForDisk(String diskId) throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), diskId);

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
   * Get all Tasks at specified path.
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
   * This method gets all tasks associated with a disk.
   *
   * @param diskId
   * @param responseCallback
   * @throws IOException
   */
  public void getTasksForDiskAsync(final String diskId, final FutureCallback<ResourceList<Task>> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), diskId);

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

  /**
   * @param diskId
   * @return
   * @throws IOException
   */
  public Task delete(String diskId) throws IOException {
    String path = getBasePath() + "/" + diskId;

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * This method deletes a disk.
   *
   * @param diskId
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String diskId, final FutureCallback<Task> responseCallback) throws
      IOException {

    deleteObjectAsync(diskId, responseCallback);
  }
}
