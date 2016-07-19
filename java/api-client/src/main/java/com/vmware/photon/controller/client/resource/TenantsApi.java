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

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tenants Api implementation.
 */
public class TenantsApi extends ApiBase {

  public TenantsApi(RestClient restClient) {
    super(restClient);
  }

  @Override
  public String getBasePath() {
    return "/tenants";
  }

  /**
   * Creates the specified tenant.
   *
   * @param name - name of the tenant
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task create(String name) throws IOException {

    Map<String, String> map = new HashMap<>();
    map.put("name", name);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        getBasePath(),
        serializeObjectAsJson(map)
    );

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Creates the specified tenant.
   * @param name
   * @param responseCallback
   * @throws IOException
   */
  public void createAsync(final String name, final FutureCallback<Task> responseCallback)
      throws IOException {
    Map<String, String> map = new HashMap<>();
    map.put("name", name);

    createObjectAsync(getBasePath(), serializeObjectAsJson(map), responseCallback);
  }

  /**
   * List all tenants.
   *
   * @return List of tenants
   * @throws IOException
   */
  public ResourceList<Tenant> listAll() throws IOException {
    return listByName(null);
  }

  /**
   * List all tenants.
   *
   * @param responseCallback
   * @throws IOException
   */
  public void listAllAsync(final FutureCallback<ResourceList<Tenant>> responseCallback) throws IOException {
    listByNameAsync(null, responseCallback);
  }

    /**
     * List tenant specified by the given name.
     *
     * @param name - name of the tenant
     * @return List of tenants matching the specified tenants.
     * @throws IOException
     */
  public ResourceList<Tenant> listByName(String name) throws IOException {
    String path = getBasePath();
    if (name != null) {
      path += "?name=" + name;
    }

    ResourceList<Tenant> tenantResourceList = new ResourceList<>();
    ResourceList<Tenant> resourceList = getTenantResourceList(path);
    tenantResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getTenantResourceList(resourceList.getNextPageLink());
      tenantResourceList.getItems().addAll(resourceList.getItems());
    }

    return tenantResourceList;
  }

  /**
   * Get tenant by id.
   * @param tenantId
   * @return
   * @throws IOException
   */
  public Tenant getTenant(String tenantId) throws IOException {
    String path = String.format("%s/%s", getBasePath(), tenantId);

    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);

    return this.restClient.parseHttpResponse(
            httpResponse,
            new TypeReference<Tenant>() {
            }
    );
  }

  /**
   * Get all tenants at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Tenant> getTenantResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Tenant>>() {
        }
    );
  }

  /**
   * List tenant by the given name.
   *
   * @param name
   * @param responseCallback
   * @throws IOException
   */
  public void listByNameAsync(final String name, final FutureCallback<ResourceList<Tenant>> responseCallback) throws
      IOException {
    String path = getBasePath();
    if (name != null) {
      path += "?name=" + name;
    }

    ResourceList<Tenant> tenantResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Tenant>> callback = new FutureCallback<ResourceList<Tenant>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Tenant> result) {
        if (tenantResourceList.getItems() == null) {
          tenantResourceList.setItems(result.getItems());
        } else {
          tenantResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Tenant>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(tenantResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<Tenant>>() {});
  }

  /**
   * Delete the specified tenant.
   *
   * @param id - id of the tenant
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task delete(String id) throws IOException {
    String path = getBasePath() + "/" + id;

    HttpResponse response = this.restClient.perform(RestClient.Method.DELETE, path, null);

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Delete the specified tenant.
   *
   * @param id
   * @param responseCallback
   * @throws IOException
   */
  public void deleteAsync(final String id, final FutureCallback<Task> responseCallback) throws IOException {
    deleteObjectAsync(id, responseCallback);
  }

  /**
   * Creates a resource ticket defined by the spec under tenant specified by tenant id.
   * @param tenantId - id of tenant
   * @param resourceTicketCreateSpec - resource ticket create spec definition
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task createResourceTicket(String tenantId, ResourceTicketCreateSpec resourceTicketCreateSpec)
      throws IOException {
    String path = String.format("%s/%s/resource-tickets", getBasePath(), tenantId);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(resourceTicketCreateSpec));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Creates a resource ticket defined by the spec under tenant specified by tenant id.
   *
   * @param tenantId
   * @param resourceTicketCreateSpec
   * @param responseCallback
   * @return
   * @throws IOException
   */
  public void createResourceTicketAsync(String tenantId, ResourceTicketCreateSpec resourceTicketCreateSpec,
                                        final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resource-tickets", getBasePath(), tenantId);

    createObjectAsync(path, serializeObjectAsJson(resourceTicketCreateSpec), responseCallback);
  }

    /**
     * Returns a list of all resource tickets defined for the specified tenant.
     * @param tenantId - id of tenant
     * @return {@link ResourceList} of {@link ResourceTicket}
     * @throws IOException
     */
  public ResourceList<ResourceTicket> getResourceTickets(String tenantId) throws IOException {
    String path = String.format("%s/%s/resource-tickets", getBasePath(), tenantId);

    ResourceList<ResourceTicket> resourceTicketResourceList = new ResourceList<>();
    ResourceList<ResourceTicket> resourceList = getResourceTicketResourceList(path);
    resourceTicketResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getResourceTicketResourceList(resourceList.getNextPageLink());
      resourceTicketResourceList.getItems().addAll(resourceList.getItems());
    }

    return resourceTicketResourceList;
  }

  /**
   * Get all resourceTickets at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<ResourceTicket> getResourceTicketResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<ResourceTicket>>() {
        }
    );
  }

  /**
   * Returns a list of all resource tickets for the specified tenant.
   *
   * @param tenantId
   * @param responseCallback
   * @throws IOException
   */
  public void getResourceTicketsAsync(final String tenantId, final FutureCallback<ResourceList<ResourceTicket>>
      responseCallback)
      throws IOException {
    String path = String.format("%s/%s/resource-tickets", getBasePath(), tenantId);

    ResourceList<ResourceTicket> resourceTicketResourceList = new ResourceList<>();
    FutureCallback<ResourceList<ResourceTicket>> callback = new FutureCallback<ResourceList<ResourceTicket>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<ResourceTicket> result) {
        if (resourceTicketResourceList.getItems() == null) {
          resourceTicketResourceList.setItems(result.getItems());
        } else {
          resourceTicketResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<ResourceTicket>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(resourceTicketResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<ResourceTicket>>() {});
  }

  /**
   * Creates project within the specified tenant.
   * @param tenantId - id of tenant
   * @param projectCreateSpec - Project specification. See {@link ProjectCreateSpec}
   * @return Tracking {@link Task}
   * @throws IOException
   */
  public Task createProject(String tenantId, ProjectCreateSpec projectCreateSpec) throws IOException {
    String path = String.format("%s/%s/projects", getBasePath(), tenantId);

    HttpResponse response = this.restClient.perform(
        RestClient.Method.POST,
        path,
        serializeObjectAsJson(projectCreateSpec));

    this.restClient.checkResponse(response, HttpStatus.SC_CREATED);
    return parseTaskFromHttpResponse(response);
  }

  /**
   * Create project within the specified tenant.
   * @param tenantId
   * @param projectCreateSpec
   * @param responseCallback
   * @throws IOException
   */
  public void createProjectAsync(String tenantId, ProjectCreateSpec projectCreateSpec,
                                        final FutureCallback<Task> responseCallback)
      throws IOException {
    String path = String.format("%s/%s/projects", getBasePath(), tenantId);

    createObjectAsync(path, serializeObjectAsJson(projectCreateSpec), responseCallback);
  }

  /**
   * Returns a list of all projects defined for the specified tenant.
   * @param tenantId - id of tenant
   * @return {@link ResourceList} of {@link Project}
   * @throws IOException
   */
  public ResourceList<Project> getProjects(String tenantId) throws IOException {
    String path = String.format("%s/%s/projects", getBasePath(), tenantId);

    ResourceList<Project> projectResourceList = new ResourceList<>();
    ResourceList<Project> resourceList = getProjectResourceList(path);
    projectResourceList.setItems(resourceList.getItems());
    while (resourceList.getNextPageLink() != null && !resourceList.getNextPageLink().isEmpty()) {
      resourceList = getProjectResourceList(resourceList.getNextPageLink());
      projectResourceList.getItems().addAll(resourceList.getItems());
    }

    return projectResourceList;
  }

  /**
   * Get all projects at specified path.
   *
   * @param path
   * @return
   * @throws IOException
   */
  private ResourceList<Project> getProjectResourceList(String path) throws IOException {
    HttpResponse httpResponse = this.restClient.perform(RestClient.Method.GET, path, null);
    this.restClient.checkResponse(httpResponse, HttpStatus.SC_OK);
    return this.restClient.parseHttpResponse(
        httpResponse,
        new TypeReference<ResourceList<Project>>() {
        }
    );
  }

  /**
   * Returns a list of all projects defined for the specified tenant.
   * @param tenantId
   * @param responseCallback
   * @throws IOException
   */
  public void getProjectsAsync(final String tenantId, final FutureCallback<ResourceList<Project>>
      responseCallback)
      throws IOException {
    String path = String.format("%s/%s/projects", getBasePath(), tenantId);

    ResourceList<Project> projectResourceList = new ResourceList<>();
    FutureCallback<ResourceList<Project>> callback = new FutureCallback<ResourceList<Project>>() {
      @Override
      public void onSuccess(@Nullable ResourceList<Project> result) {
        if (projectResourceList.getItems() == null) {
          projectResourceList.setItems(result.getItems());
        } else {
          projectResourceList.getItems().addAll(result.getItems());
        }
        if (result.getNextPageLink() != null && !result.getNextPageLink().isEmpty()) {
          try {
            getObjectByPathAsync(result.getNextPageLink(), this, new TypeReference<ResourceList<Project>>() {});
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
          responseCallback.onSuccess(projectResourceList);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        responseCallback.onFailure(t);
      }
    };

    getObjectByPathAsync(path, callback, new TypeReference<ResourceList<Project>>() {});
  }
}
