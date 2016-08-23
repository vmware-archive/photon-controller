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
import com.vmware.photon.controller.api.frontend.backends.VmBackend;
import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.api.frontend.backends.utils.TaskUtils;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.VmEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.api.model.ImageCreateSpec;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.api.model.VmCreateSpec;
import com.vmware.photon.controller.api.model.VmFloatingIpSpec;
import com.vmware.photon.controller.apibackend.servicedocuments.AssignFloatingIpToVmWorkflowDocument;
import com.vmware.photon.controller.apibackend.workflows.AssignFloatingIpToVmWorkflowService;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Frontend client for VM used by {@link VmResource}.
 */
@Singleton
public class VmFeClient {
  private static final Logger logger = LoggerFactory.getLogger(VmFeClient.class);

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;
  private final VmBackend vmBackend;
  private final TaskBackend taskBackend;
  private final PhotonControllerXenonRestClient backendClient;
  private final ApiFeXenonRestClient cloudStoreClient;

  @Inject
  public VmFeClient(TaskCommandFactory commandFactory,
                    VmBackend vmBackend,
                    @BackendTaskExecutor ExecutorService executor,
                    TaskBackend taskBackend,
                    PhotonControllerXenonRestClient photonControllerXenonRestClient,
                    ApiFeXenonRestClient cloudStoreClient) {
    this.commandFactory = commandFactory;
    this.executor = executor;
    this.vmBackend = vmBackend;
    this.taskBackend = taskBackend;

    this.backendClient = photonControllerXenonRestClient;
    this.backendClient.start();

    this.cloudStoreClient = cloudStoreClient;
    this.cloudStoreClient.start();
  }

  public Vm get(String id) throws ExternalException {
    return vmBackend.toApiRepresentation(id);
  }

  public ResourceList<Vm> find(String projectId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    return vmBackend.filter(projectId, name, pageSize);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return vmBackend.getVmsPage(pageLink);
  }

  public Task create(String projectId, VmCreateSpec spec) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmCreate(projectId, spec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task delete(String vmId) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmDelete(vmId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    // Start the thread below after toApiRepresentation of taskEntity to avoid
    // associating taskEntity with two open sessions
    executor.submit(commandFactory.create(taskEntity));

    return task;
  }

  public Task operate(String vmId, Operation operation) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmOperation(vmId, operation);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task operateDisks(String vmId, List<String> diskIds, Operation operation) throws ExternalException {

    TaskEntity taskEntity = vmBackend.prepareVmDiskOperation(vmId, diskIds, operation);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task attachIso(String vmId, InputStream inputStream, String name) throws
      ExternalException, IOException {
    TaskEntity taskEntity = vmBackend.prepareVmAttachIso(vmId, inputStream, name);
    taskEntity.findStep(Operation.ATTACH_ISO).setDisabled(true);

    TaskCommand command = commandFactory.create(taskEntity);
    logger.info("Run synchronous task steps for task: {} {}", taskEntity.getId(), taskEntity.getOperation());
    command.run();

    Task task = taskBackend.getApiRepresentation(taskEntity.getId());
    if (!task.getState().equals(TaskEntity.State.STARTED.toString())) {
      logger.error("Run task {} went into state {}", task.getId(), task.getState());
      return task;
    }

    taskEntity.findStep(Operation.ATTACH_ISO).setDisabled(false);
    command = commandFactory.create(taskEntity);
    logger.info("Run asynchronous task steps for task: {} {}", task.getId(), task.getOperation());
    executor.submit(command);
    return task;
  }

  public Task detachIso(String vmId) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmDetachIso(vmId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task addTag(String vmId, Tag tag) throws ExternalException {
    TaskEntity taskEntity = vmBackend.addTag(vmId, tag);
    Task task = taskBackend.getApiRepresentation(taskEntity);
    return task;
  }

  public Task getNetworks(String vmId) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmGetNetworks(vmId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task getMksTicket(String vmId) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmGetMksTicket(vmId);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task setMetadata(String vmId, Map<String, String> metadata) throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareSetMetadata(vmId, metadata);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task createImage(String vmId, ImageCreateSpec imageCreateSpec)
      throws ExternalException {
    TaskEntity taskEntity = vmBackend.prepareVmCreateImage(vmId, imageCreateSpec);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    TaskCommand command = commandFactory.create(taskEntity);
    executor.submit(command);
    return task;
  }

  public Task assignFloatingIp(String vmId, VmFloatingIpSpec spec) throws ExternalException {
    VmEntity vmEntity = vmBackend.findById(vmId);

    if (!vmEntity.getNetworks().contains(spec.getNetworkId())) {
      throw new NetworkNotFoundException(spec.getNetworkId());
    }

    AssignFloatingIpToVmWorkflowDocument startState = new AssignFloatingIpToVmWorkflowDocument();
    startState.vmId = vmId;
    startState.networkId = spec.getNetworkId();

    AssignFloatingIpToVmWorkflowDocument finalState = backendClient.post(
        AssignFloatingIpToVmWorkflowService.FACTORY_LINK,
        startState).getBody(AssignFloatingIpToVmWorkflowDocument.class);

    return TaskUtils.convertBackEndToFrontEnd(finalState.taskServiceState);
  }
}
