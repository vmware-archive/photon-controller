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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.backends.clients.ServicesManagerClient;
import com.vmware.photon.controller.api.frontend.commands.steps.HarborServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.KubernetesServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.MesosServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.ServiceDeleteStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.ServiceResizeStepCmd;
import com.vmware.photon.controller.api.frontend.commands.steps.SwarmServiceCreateStepCmd;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ServiceNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Service;
import com.vmware.photon.controller.api.model.ServiceCreateSpec;
import com.vmware.photon.controller.api.model.ServiceResizeOperation;
import com.vmware.photon.controller.api.model.Tag;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.servicesmanager.util.ServicesUtil;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URISyntaxException;

/**
 * Call service manager Xenon services to fulfill service operations.
 */
@Singleton
public class ServiceBackend {
  private static final Logger logger = LoggerFactory.getLogger(ServiceBackend.class);

  private final ServicesManagerClient servicesManagerClient;
  private final TaskBackend taskBackend;
  private final VmBackend vmBackend;

  @Inject
  public ServiceBackend(ServicesManagerClient servicesManagerClient,
                        TaskBackend taskBackend, VmBackend vmBackend)
      throws URISyntaxException {
    this.servicesManagerClient = servicesManagerClient;
    this.taskBackend = taskBackend;
    this.vmBackend = vmBackend;
  }

  public ServicesManagerClient getServicesManagerClient() {
    return servicesManagerClient;
  }

  // Kick off the creation of a service.
  public TaskEntity create(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException{
    switch (spec.getType()) {
      case KUBERNETES:
        return createKubernetesService(projectId, spec);
      case MESOS:
        return createMesosService(projectId, spec);
      case SWARM:
        return createSwarmService(projectId, spec);
      case HARBOR:
        return createHarborService(projectId, spec);
      default:
        throw new SpecInvalidException("Unsupported service type: " + spec.getType());
    }
  }

  // Kick off service resize operation.
  public TaskEntity resize(String serviceId, ServiceResizeOperation resizeOperation)
      throws TaskNotFoundException, ServiceNotFoundException {
    // Check if a service exists for the passed serviceId
    checkServiceId(serviceId);

    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(serviceId, Service.KIND, Operation.RESIZE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.RESIZE_SERVICE_INITIATE);

    // Pass serviceId and resizeOperation to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(ServiceResizeStepCmd.SERVICE_ID_RESOURCE_KEY, serviceId);
    initiateStep.createOrUpdateTransientResource(ServiceResizeStepCmd.RESIZE_OPERATION_RESOURCE_KEY, resizeOperation);

    // Dummy steps that are mapped to KubernetesServiceResizeTask's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESIZE_SERVICE_INITIALIZE_SERVICE);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.RESIZE_SERVICE_RESIZE);

    // Send acknowledgement to client
    return taskEntity;
  }

  public Service get(String serviceId) throws ServiceNotFoundException {
    return servicesManagerClient.getService(serviceId);
  }

  public ResourceList<Service> find(String projectId, Optional<Integer> pageSize) throws ExternalException {
    return servicesManagerClient.getServices(projectId, pageSize);
  }

  public TaskEntity delete(String serviceId) throws TaskNotFoundException, ServiceNotFoundException {
    // Check if a service exists for the passed serviceId
    checkServiceId(serviceId);

    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(serviceId, Service.KIND, Operation.RESIZE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.DELETE_SERVICE_INITIATE);

    // Pass serviceId to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(ServiceDeleteStepCmd.SERVICE_ID_RESOURCE_KEY, serviceId);

    // Dummy steps that are mapped to ServiceDeleteTaskState's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_SERVICE_UPDATE_SERVICE_DOCUMENT);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_SERVICE_DELETE_VMS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.DELETE_SERVICE_DOCUMENT);

    // Send acknowledgement to client
    return taskEntity;
  }

  public TaskEntity triggerMaintenance(String serviceId) throws ServiceNotFoundException {
    checkServiceId(serviceId);
    // Trigger the maintenance operation and return that is has been completed.
    servicesManagerClient.triggerMaintenance(serviceId);
    return taskBackend.createCompletedTask(serviceId, Service.KIND, null, Operation.TRIGGER_SERVICE_MAINTENANCE);
  }

  public ResourceList<Vm> findVms(String serviceId, Optional<Integer> pageSize) throws ExternalException {
    // Get projectId for service
    Service service = servicesManagerClient.getService(serviceId);

    // Find VMs by tag
    Tag serviceIdTag = new Tag(ServicesUtil.createServiceTag(serviceId));
    return vmBackend.filterByTag(service.getProjectId(), serviceIdTag, pageSize);
  }

  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    return vmBackend.getVmsPage(pageLink);
  }

  public ResourceList<Service> getServicesPage(String pageLink) throws ExternalException {
    return servicesManagerClient.getServicesPages(pageLink);
  }

  public int getNumberServices() {
    return servicesManagerClient.getNumber(Optional.<String>absent());
  }

  public int getNumberServicesByProject(String projectId) {
    return servicesManagerClient.getNumber(Optional.of(projectId));
  }

  private void checkServiceId(String serviceId) throws ServiceNotFoundException {
    Service service = servicesManagerClient.getService(serviceId);
    checkNotNull(service);
  }

  private TaskEntity createKubernetesService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.CREATE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_KUBERNETES_SERVICE_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(KubernetesServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(KubernetesServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to KubernetesServiceCreateTaskState's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_SERVICE_SETUP_ETCD);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_SERVICE_SETUP_MASTER);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation
        .CREATE_KUBERNETES_SERVICE_UPDATE_EXTENDED_PROPERTIES);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_KUBERNETES_SERVICE_SETUP_WORKERS);

    return taskEntity;
  }

  private TaskEntity createMesosService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.CREATE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_MESOS_SERVICE_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(MesosServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(MesosServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to MesosServiceCreateTaskState's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_SERVICE_SETUP_ZOOKEEPERS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_SERVICE_SETUP_MASTERS);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_SERVICE_SETUP_MARATHON);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_MESOS_SERVICE_SETUP_WORKERS);

    return taskEntity;
  }

  private TaskEntity createSwarmService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(
        null, Operation.CREATE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_SWARM_SERVICE_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(SwarmServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to SwarmServiceCreateTaskState's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_SERVICE_SETUP_ETCD);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_SERVICE_SETUP_MASTER);
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_SWARM_SERVICE_SETUP_WORKERS);

    return taskEntity;
  }

  private TaskEntity createHarborService(String projectId, ServiceCreateSpec spec)
      throws SpecInvalidException, TaskNotFoundException {
    // Create the steps
    TaskEntity taskEntity = taskBackend.createQueuedTask(null, Operation.CREATE_SERVICE);
    StepEntity initiateStep = taskBackend.getStepBackend().createQueuedStep(
        taskEntity, Operation.CREATE_HARBOR_SERVICE_INITIATE);

    // Pass projectId and createSpec to initiateStep as transient resources
    initiateStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.PROJECT_ID_RESOURCE_KEY, projectId);
    initiateStep.createOrUpdateTransientResource(HarborServiceCreateStepCmd.CREATE_SPEC_RESOURCE_KEY, spec);

    // Dummy steps that are mapped to HarborServiceCreateTaskState's subStages
    taskBackend.getStepBackend().createQueuedStep(taskEntity, Operation.CREATE_HARBOR_SERVICE_SETUP_HARBOR);
    taskBackend.getStepBackend().createQueuedStep(taskEntity,
        Operation.CREATE_HARBOR_SERVICE_UPDATE_EXTENDED_PROPERTIES);

    return taskEntity;
  }
}
