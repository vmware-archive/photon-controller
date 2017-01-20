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
import com.vmware.photon.controller.api.frontend.backends.ServiceBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Frontend client for service tasks.
 */
@Singleton
public class ServiceFeClient {
  private static final Logger logger = LoggerFactory.getLogger(ServiceFeClient.class);

  private final ServiceBackend serviceBackend;
  private final TaskBackend taskBackend;
  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;

  @Inject
  public ServiceFeClient(ServiceBackend serviceBackend,
                         TaskBackend taskBackend,
                         TaskCommandFactory commandFactory,
                         @BackendTaskExecutor ExecutorService executor) {
    this.serviceBackend = serviceBackend;
    this.taskBackend = taskBackend;
    this.commandFactory = commandFactory;
    this.executor = executor;
  }

  public Task create(String projectId, ServiceCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = serviceBackend.create(projectId, spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task resize(String serviceId, ServiceResizeOperation operation) throws ExternalException {
    TaskEntity taskEntity = serviceBackend.resize(serviceId, operation);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Service get(String id) throws ExternalException {
    return serviceBackend.get(id);
  }

  public ResourceList<Service> find(String projectId, Optional<Integer> pageSize) throws ExternalException {
    return serviceBackend.find(projectId, pageSize);
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = serviceBackend.delete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task triggerMaintenance(String serviceId) throws ExternalException {
    TaskEntity taskEntity = serviceBackend.triggerMaintenance(serviceId);
    return taskBackend.getApiRepresentation(taskEntity);
  }

  public ResourceList<Vm> findVms(String serviceId, Optional<Integer> pageSize) throws ExternalException {
    return serviceBackend.findVms(serviceId, pageSize);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return serviceBackend.getVmsPage(pageLink);
  }

  public ResourceList<Service> getServicesPage(String pageLink) throws ExternalException {
    return serviceBackend.getServicesPage(pageLink);
  }
}
