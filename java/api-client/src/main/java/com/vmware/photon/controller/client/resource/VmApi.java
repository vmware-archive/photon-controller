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

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.api.model.VmMetadata;
import com.vmware.photon.controller.api.model.VmNetworks;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Vm api.
 */
public class VmApi extends ApiBase {
  public VmApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/vms";
  }

  /**
   * Get details about the specified vm.
   * @param vmId
   * @return Vm details
   * @throws java.io.IOException
   */
  public Vm getVm(String vmId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Vm>() {
        }
    );
  }

  /**
   * Get details about the specified vm.
   *
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void getVmAsync(final String vmId, final FutureCallback<Vm>
      responseCallback) throws IOException {
    String path = String.format("%s/%s", getBasePath(), vmId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Vm>() {
    });
  }

  /**
   * Get tasks associated with the specified vm.
   * @param vmId
   * @return {@link ResourceList} of {@link Task}
   * @throws IOException
   */
  public ResourceList<Task> getTasksForVm(String vmId) throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), vmId);

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
   * Get tasks associated with the specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void getTasksForVmAsync(final String vmId,
                                     final FutureCallback<ResourceList<Task>> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), vmId);

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
   * Delete the specified vm.
   *
   * @param id - id of the vm to delete
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
   * Delete the specified vm.
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String id, final FutureCallback<Task> responseCallback) throws IOException {
    deleteObjectAsync(id, responseCallback);
  }

  /**
   * Add the specified tag to the specified VM.
   * @param vmId - id of the vm
   * @param tag - {@link Tag}
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task addTagToVm(String vmId, Tag tag) throws IOException {
    String path = String.format("%s/%s/tags", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(tag));
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Add the specified tag to the specified VM.
   * @param vmId
   * @param tag
   * @param responseCallback
   * @throws IOException
   */
  public void addTagToVmAsync(final String vmId, Tag tag, final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/tags", getBasePath(), vmId);

    createObjectAsync(path, serializeObjectAsJson(tag), responseCallback);
  }

  /**
   * Perform a VM Start Operation on specified vm.
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task performStartOperation(String vmId) throws IOException {
    String path = String.format("%s/%s/start", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Perform a VM Start Operation on specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void performStartOperationAsync(final String vmId,
                                         final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/start", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Perform a VM Stop Operation on specified vm.
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task performStopOperation(String vmId) throws IOException {
    String path = String.format("%s/%s/stop", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Perform a VM Stop Operation on specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void performStopOperationAsync(final String vmId,
                                         final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/stop", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Perform a VM Restart Operation on specified vm.
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task performRestartOperation(String vmId) throws IOException {
    String path = String.format("%s/%s/restart", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Perform a VM Restart Operation on specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void performRestartOperationAsync(final String vmId,
                                         final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/restart", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Perform a VM Resume Operation on specified vm.
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task performResumeOperation(String vmId) throws IOException {
    String path = String.format("%s/%s/resume", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Perform a VM Resume Operation on specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void performResumeOperationAsync(final String vmId,
                                         final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resume", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Perform a VM Suspend Operation on specified vm.
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task performSuspendOperation(String vmId) throws IOException {
    String path = String.format("%s/%s/suspend", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Perform a VM Suspend Operation on specified vm.
   * @param vmId
   * @param responseCallback
   * @throws IOException
   */
  public void performSuspendOperationAsync(final String vmId,
                                         final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/suspend", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }


  /**
   * Attaches a persistent disk to specified VM.
   * @param vmId - id of the vm
   * @param vmDiskOperation {@link VmDiskOperation} specifiying the disk to attach
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task attachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException {
    String path = String.format("%s/%s/attach_disk", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(vmDiskOperation));
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Attaches a persistent disk to specified VM.
   * @param vmId
   * @param vmDiskOperation
   * @param responseCallback
   * @throws IOException
   */
  public void attachDiskAsync(final String vmId, VmDiskOperation vmDiskOperation,
                                    final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/attach_disk", getBasePath(), vmId);

    createObjectAsync(path, serializeObjectAsJson(vmDiskOperation), responseCallback);
  }

  /**
   * Detaches a persistent disk to specified VM.
   * @param vmId - id of the vm
   * @param vmDiskOperation {@link VmDiskOperation} specifiying the disk to detach
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task detachDisk(String vmId, VmDiskOperation vmDiskOperation) throws IOException {
    String path = String.format("%s/%s/detach_disk", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(vmDiskOperation));
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Detached a persistent disk to specified VM.
   * @param vmId
   * @param vmDiskOperation
   * @param responseCallback
   * @throws IOException
   */
  public void detachDiskAsync(final String vmId, VmDiskOperation vmDiskOperation,
                              final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/detach_disk", getBasePath(), vmId);

    createObjectAsync(path, serializeObjectAsJson(vmDiskOperation), responseCallback);
  }

  /**
   * Uploads the iso pointed to by the inputFileName and attaches it to specified vm.
   *
   * @param vmId - id of the vm
   * @param inputFileName - path of the iso to upload and attach
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task uploadAndAttachIso(String vmId, String inputFileName) throws IOException {
    String path = String.format("%s/%s/attach_iso", getBasePath(), vmId);

    HttpResponse response = this.restClient.upload(path, inputFileName, ImmutableMap.<String, String>of());

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Detaches the iso from the specified VM.
   *
   * @param vmId - id of the vm
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task detachIso(String vmId) throws IOException {
    String path = String.format("%s/%s/detach_iso", getBasePath(), vmId);
    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST, path, null);

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Detaches the iso from the specified VM.
   *
   * @param vmId - id of the vm
   * @param responseCallback
   * @throws IOException
   */
  public void detachIsoAsync(final String vmId, final FutureCallback<Task> responseCallback) throws IOException {
    String path = String.format("%s/%s/detach_iso", getBasePath(), vmId);

    createObjectAsync(path, null, responseCallback);
  }

  /**
   * Gets network information for the specified VM.
   *
   * @param vmId - Supplies the ID of the target VM.
   * @return Tracking {@link Task}.
   * @throws IOException
   */
  public Task getNetworks(String vmId) throws IOException {
    String path = String.format("%s/%s/networks", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Gets network information for the specified VM.
   *
   * @param vmId - Supplies the ID of the target VM.
   * @param responseCallback
   * @throws IOException
   */
  public void getNetworksAsync(String vmId, FutureCallback<Task> responseCallback) throws IOException {
    String path = String.format("%s/%s/networks", getBasePath(), vmId);

    getObjectByPathAsync(path, HttpStatus.SC_CREATED, responseCallback, new TypeReference<Task>() { });
  }

  public static VmNetworks parseVmNetworksFromTask(Task task) throws IOException {
    // task.getResourceProperties() is a HashSet, we need to serialize it to Json
    // then deserialize with correct type.
    String json = objectMapper.writeValueAsString(task.getResourceProperties());
    return deserializeObjectFromJson(json, new TypeReference<VmNetworks>(){});
  }

  /**
   * Add metadata to the specified VM.
   * @param vmId
   * @param metadata
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task setMetadata(final String vmId, final VmMetadata metadata)
      throws IOException {
    String path = String.format("%s/%s/set_metadata", getBasePath(), vmId);

    HttpResponse httpResponse = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(metadata));
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_CREATED);

    return parseTaskFromHttpResponse(httpResponse);
  }

  /**
   * Add metadata to the specified VM.
   * @param vmId
   * @param metadata
   * @param responseCallback
   * @throws IOException
   */
  public void setMetadataAsync(
      final String vmId,
      final VmMetadata metadata,
      final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/set_metadata", getBasePath(), vmId);

    createObjectAsync(path, serializeObjectAsJson(metadata), responseCallback);
  }
}
