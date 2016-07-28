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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.BackendTaskExecutor;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.TenantBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.TenantCreateSpec;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Frontend client for task used by {@link TenantResource}.
 */
@Singleton
public class TenantFeClient {

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;
  private final TenantBackend tenantBackend;
  private final TaskBackend taskBackend;

  @Inject
  public TenantFeClient(TaskCommandFactory commandFactory, @BackendTaskExecutor ExecutorService executor,
                        TenantBackend tenantBackend, TaskBackend taskBackend) {

    this.commandFactory = commandFactory;
    this.executor = executor;
    this.tenantBackend = tenantBackend;
    this.taskBackend = taskBackend;
  }

  public Tenant get(String id) throws TenantNotFoundException {
    return tenantBackend.getApiRepresentation(id);
  }

  public ResourceList<Tenant> find(Optional<String> name, Optional<Integer> pageSize) {
    return tenantBackend.filter(name, pageSize);
  }

  public ResourceList<Tenant> getPage(String pageLink) throws PageExpiredException {
    return tenantBackend.getPage(pageLink);
  }
  public Task create(TenantCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = tenantBackend.createTenant(spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = tenantBackend.deleteTenant(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Task setSecurityGroups(String id, List<String> securityGroups) throws ExternalException {
    TaskEntity taskEntity = tenantBackend.prepareSetSecurityGroups(id, securityGroups);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    executor.submit(commandFactory.create(taskEntity));

    return task;
  }
}
