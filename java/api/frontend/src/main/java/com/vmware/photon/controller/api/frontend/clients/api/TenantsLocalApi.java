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
package com.vmware.photon.controller.api.frontend.clients.api;

import com.vmware.photon.controller.api.client.resource.TenantsApi;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ProjectCreateSpec;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.ResourceTicket;
import com.vmware.photon.controller.api.model.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;

import com.google.common.util.concurrent.FutureCallback;

import java.io.IOException;

/**
 * This class implements Tenants API for communicating with APIFE locally.
 */
public class TenantsLocalApi implements TenantsApi {
  @Override
  public String getBasePath() {
    return null;
  }

  @Override
  public Task create(String name) throws IOException {
    return null;
  }

  @Override
  public void createAsync(String name, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Tenant> listAll() throws IOException {
    return null;
  }

  @Override
  public void listAllAsync(FutureCallback<ResourceList<Tenant>> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Tenant> listByName(String name) throws IOException {
    return null;
  }

  @Override
  public Tenant getTenant(String tenantId) throws IOException {
    return null;
  }

  @Override
  public void listByNameAsync(String name, FutureCallback<ResourceList<Tenant>> responseCallback) throws IOException {

  }

  @Override
  public Task delete(String id) throws IOException {
    return null;
  }

  @Override
  public void deleteAsync(String id, FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public Task createResourceTicket(String tenantId, ResourceTicketCreateSpec resourceTicketCreateSpec)
      throws IOException {
    return null;
  }

  @Override
  public void createResourceTicketAsync(String tenantId, ResourceTicketCreateSpec resourceTicketCreateSpec,
                                        FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<ResourceTicket> getResourceTickets(String tenantId) throws IOException {
    return null;
  }

  @Override
  public void getResourceTicketsAsync(String tenantId, FutureCallback<ResourceList<ResourceTicket>> responseCallback)
      throws IOException {

  }

  @Override
  public Task createProject(String tenantId, ProjectCreateSpec projectCreateSpec) throws IOException {
    return null;
  }

  @Override
  public void createProjectAsync(String tenantId, ProjectCreateSpec projectCreateSpec,
                                 FutureCallback<Task> responseCallback) throws IOException {

  }

  @Override
  public ResourceList<Project> getProjects(String tenantId) throws IOException {
    return null;
  }

  @Override
  public void getProjectsAsync(String tenantId, FutureCallback<ResourceList<Project>> responseCallback)
      throws IOException {

  }
}
