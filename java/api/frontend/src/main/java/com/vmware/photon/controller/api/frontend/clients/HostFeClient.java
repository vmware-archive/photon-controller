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
import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostCreateSpec;
import com.vmware.photon.controller.api.model.HostSetAvailabilityZoneOperation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Frontend client for Host used by {@link HostsResource} and
 * {@link HostResource}.
 */
@Singleton
public class HostFeClient {
  private static final Logger logger = LoggerFactory.getLogger(HostFeClient.class);

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;
  private final HostBackend hostBackend;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;

  @Inject
  public HostFeClient(HostBackend hostBackend, TaskBackend taskBackend, VmBackend vmBackend,
                      @BackendTaskExecutor ExecutorService executor,
                      TaskCommandFactory commandFactory) {
    this.commandFactory = commandFactory;
    this.executor = executor;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
  }

  public Task createHost(HostCreateSpec hostCreateSpec, String deploymentId) throws ExternalException {
    TaskEntity taskEntity = hostBackend.prepareHostCreate(hostCreateSpec, deploymentId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public Task deleteHost(String id) throws ExternalException {
    TaskEntity taskEntity = hostBackend.prepareHostDelete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public Task enterMaintenanceMode(String id) throws ExternalException {
    TaskEntity taskEntity = hostBackend.enterMaintenance(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public Task exitMaintenanceMode(String id) throws ExternalException {
    TaskEntity taskEntity = hostBackend.exitMaintenance(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public Task resumeHost(String id) throws ExternalException {
    TaskEntity taskEntity = hostBackend.resume(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public Task enterSuspendedMode(String id) throws ExternalException {
    TaskEntity taskEntity = hostBackend.suspend(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task setAvailabilityZone(String hostId, HostSetAvailabilityZoneOperation hostSetAvailabilityZoneOperation)
      throws ExternalException {
    TaskEntity taskEntity = hostBackend.setAvailabilityZone(hostId, hostSetAvailabilityZoneOperation);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);

    return task;
  }

  public ResourceList<Vm> listAllVms(String id, Optional<Integer> pageSize) throws ExternalException {
    return vmBackend.getAllVmsOnHost(id, pageSize);
  }

  public Host getHost(String id) throws ExternalException {
    return hostBackend.toApiRepresentation(id);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return vmBackend.getVmsPage(pageLink);
  }
}
