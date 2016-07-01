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
import com.vmware.photon.controller.api.ClusterCreateSpec;
import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Project;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.base.FlavoredCompact;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Project Api.
 */
public class ProjectApi extends ApiBase {
  public ProjectApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/projects";
  }

  /**
   * Get details about the specified project.
   *
   * @param projectId
   * @return Project details
   * @throws java.io.IOException
   */
  public Project getProject(String projectId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), projectId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<Project>() {
        }
    );
  }

  /**
   * Get details about the specified project.
   *
   * @param projectId
   * @param responseCallback
   * @throws IOException
   */
  public void getProjectAsync(final String projectId, final FutureCallback<Project> responseCallback)
      throws IOException {
    final String path = String.format("%s/%s", getBasePath(), projectId);

    getObjectByPathAsync(path, responseCallback, new TypeReference<Project>() {
    });
  }

  /**
   * Get tasks associated with the specified project.
   *
   * @param projectId
   * @return {@link ResourceList} of {@link Task}
   * @throws IOException
   */
  public ResourceList<Task> getTasksForProject(String projectId) throws IOException {
    String path = String.format("%s/%s/tasks", getBasePath(), projectId);

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
   * Get tasks associated with the specified project.
   *
   * @param projectId
   * @param responseCallback
   * @throws IOException
   */
  public void getTasksForProjectAsync(final String projectId, final FutureCallback<ResourceList<Task>>
      responseCallback)
      throws
      IOException {
    final String path = String.format("%s/%s/tasks", getBasePath(), projectId);

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
   * Delete the specified project.
   *
   * @param id - id of the project to delete
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
   * Delete the specified project.
   *
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String id, final FutureCallback<Task> responseCallback) throws IOException {
    deleteObjectAsync(id, responseCallback);
  }

  /**
   * Create a disk in the specified project.
   *
   * @param projectId      - id of the project in which the disk should be created
   * @param diskCreateSpec - disk specification. See {@link DiskCreateSpec}
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task createDisk(String projectId, DiskCreateSpec diskCreateSpec) throws IOException {
    String path = String.format("%s/%s/disks", getBasePath(), projectId);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(diskCreateSpec));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Create a disk in the specified project.
   *
   * @param projectId
   * @param diskCreateSpec
   * @param responseCallback
   * @throws IOException
   */
  public void createDiskAsync(final String projectId, final DiskCreateSpec diskCreateSpec, final FutureCallback<Task>
      responseCallback)
      throws IOException {
    final String path = String.format("%s/%s/disks", getBasePath(), projectId);

    createObjectAsync(path, serializeObjectAsJson(diskCreateSpec), responseCallback);
  }

  /**
   * Get a list of persistent disks in the specified project.
   *
   * @param projectId - id of project
   * @return {@link ResourceList} of {@link PersistentDisk}
   * @throws IOException
   */
  public ResourceList<PersistentDisk> getDisksInProject(String projectId) throws IOException {
    String path = String.format("%s/%s/disks", getBasePath(), projectId);

    ResourceList<PersistentDisk> persistentDiskResourceList = new ResourceList<>();
    ResourceList<PersistentDisk> resourceList = getPersistentDiskResourceList(path);
    persistentDiskResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getPersistentDiskResourceList(resourceList.getNextPageLink());
      persistentDiskResourceList.getItems().addAll(resourceList.getItems());
    }

    return persistentDiskResourceList;
  }

  /**
   * Get all persistentDisks at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<PersistentDisk> getPersistentDiskResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<PersistentDisk>>() {
        }
    );
  }

  /**
   * Get the list of persistent disks in the specified project.
   *
   * @param projectId
   * @param responseCallback
   * @throws IOException
   */
  public void getDisksInProjectAsync(final String projectId,
                                     final FutureCallback<ResourceList<PersistentDisk>> responseCallback)
      throws IOException {
    final String path = String.format("%s/%s/disks", getBasePath(), projectId);

    ResourceList<PersistentDisk> persistentDiskResourceList = new ResourceList<>();
    FutureCallback<ResourceList<PersistentDisk>> callback = new FutureCallback<ResourceList<PersistentDisk>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<PersistentDisk> result) {
        if (persistentDiskResourceList.getItems() == null) {
          persistentDiskResourceList.setItems(result.getItems());
        } else {
          persistentDiskResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<PersistentDisk>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(persistentDiskResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<PersistentDisk>>() {});
  }

  /**
   * Create a vm in the specified project.
   *
   * @param projectId    - id of the project in which the vm should be created
   * @param vmCreateSpec - vm specification. See {@link VmCreateSpec}
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task createVm(String projectId, VmCreateSpec vmCreateSpec) throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), projectId);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(vmCreateSpec));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Create a vm in the specified project.
   *
   * @param projectId
   * @param vmCreateSpec
   * @param responseCallback
   * @throws IOException
   */
  public void createVmAsync(final String projectId, final VmCreateSpec vmCreateSpec, final FutureCallback<Task>
      responseCallback)
      throws IOException {
    final String path = String.format("%s/%s/vms", getBasePath(), projectId);

    createObjectAsync(path, serializeObjectAsJson(vmCreateSpec), responseCallback);
  }

  /**
   * Get a list of vms in the specified project.
   *
   * @param projectId - id of project
   * @return {@link ResourceList} of {@link FlavoredCompact}
   * @throws IOException
   */
  public ResourceList<FlavoredCompact> getVmsInProject(String projectId) throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), projectId);

    ResourceList<FlavoredCompact> flavoredCompactResourceList = new ResourceList<>();
    ResourceList<FlavoredCompact> resourceList = getFlavoredCompactResourceList(path);
    flavoredCompactResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getFlavoredCompactResourceList(resourceList.getNextPageLink());
      flavoredCompactResourceList.getItems().addAll(resourceList.getItems());
    }

    return flavoredCompactResourceList;
  }

  /**
   * Get all flavoredCompacts at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<FlavoredCompact> getFlavoredCompactResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<FlavoredCompact>>() {
        }
    );
  }

  /**
   * Get a list of vm details in the specified project.
   *
   * @param projectId - id of project
   * @return {@link ResourceList} of {@link Vm}
   * @throws IOException
   */
  public ResourceList<Vm> getVmDetailsInProject(String projectId) throws IOException {
    String path = String.format("%s/%s/vms", getBasePath(), projectId);

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
   * Get a list of vms in the specified project.
   *
   * @param projectId
   * @param responseCallback
   * @throws IOException
   */
  public void getVmsInProjectAsync(final String projectId, final FutureCallback<ResourceList<FlavoredCompact>>
      responseCallback)
      throws
      IOException {
    final String path = String.format("%s/%s/vms", getBasePath(), projectId);

    ResourceList<FlavoredCompact> flavoredCompactResourceList = new ResourceList<>();
    FutureCallback<ResourceList<FlavoredCompact>> callback = new FutureCallback<ResourceList<FlavoredCompact>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<FlavoredCompact> result) {
        if (flavoredCompactResourceList.getItems() == null) {
          flavoredCompactResourceList.setItems(result.getItems());
        } else {
          flavoredCompactResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<FlavoredCompact>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(flavoredCompactResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<FlavoredCompact>>() {});
  }

  /**
   * Create a cluster in the specified project.
   *
   * @param projectId
   * @param clusterCreateSpec
   * @return
   * @throws IOException
   */
  public Task createCluster(String projectId, ClusterCreateSpec clusterCreateSpec) throws IOException {
    String path = String.format("%s/%s/clusters", getBasePath(), projectId);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(clusterCreateSpec));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Create a cluster in the specified project.
   *
   * @param projectId
   * @param clusterCreateSpec
   * @return
   * @throws IOException
   */
  public void createClusterAsync(final String projectId, final ClusterCreateSpec clusterCreateSpec,
    final FutureCallback<Task> responseCallback) throws IOException {
    String path = String.format("%s/%s/clusters", getBasePath(), projectId);

    createObjectAsync(path, serializeObjectAsJson(clusterCreateSpec), responseCallback);
  }

  /**
   * Get a list of clusters in the specified project.
   *
   * @param projectId
   * @return
   * @throws IOException
   */
  public ResourceList<Cluster> getClustersInProject(String projectId) throws IOException {
    String path = String.format("%s/%s/clusters", getBasePath(), projectId);

    ResourceList<Cluster> clusterResourceList = new ResourceList<>();
    ResourceList<Cluster> resourceList = getClusterResourceList(path);
    clusterResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getClusterResourceList(resourceList.getNextPageLink());
      clusterResourceList.getItems().addAll(resourceList.getItems());
    }

    return clusterResourceList;
  }

  /**
   * Get all flavors at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Cluster> getClusterResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Cluster>>() {
        }
    );
  }

  /**
   * Get a list of clusters in the specified project.
   *
   * @param projectId
   * @param responseCallback
   * @throws IOException
   */
  public void getClustersInProjectAsync(final String projectId, final FutureCallback<ResourceList<Cluster>>
      responseCallback) throws IOException {
    String path = String.format("%s/%s/clusters", getBasePath(), projectId);

    ResourceList<Cluster> clusterResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Cluster>> callback = new FutureCallback<ResourceList<Cluster>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Cluster> result) {
        if (clusterResourceList.getItems() == null) {
          clusterResourceList.setItems(result.getItems());
        } else {
          clusterResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Cluster>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(clusterResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<Cluster>>() {});
  }
}
