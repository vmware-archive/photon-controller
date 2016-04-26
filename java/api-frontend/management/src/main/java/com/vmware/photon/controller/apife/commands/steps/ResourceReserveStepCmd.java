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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.DiskBackend;
import com.vmware.photon.controller.apife.backends.FlavorBackend;
import com.vmware.photon.controller.apife.backends.NetworkBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.backends.clients.SchedulerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLocalitySpecException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.UnfulfillableAffinitiesException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidSchedulerException;
import com.vmware.photon.controller.common.clients.exceptions.NotLeaderException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.StaleGenerationException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.flavors.gen.Flavor;
import com.vmware.photon.controller.flavors.gen.QuotaLineItem;
import com.vmware.photon.controller.flavors.gen.QuotaUnit;
import com.vmware.photon.controller.host.gen.ReserveResponse;
import com.vmware.photon.controller.resource.gen.CloneType;
import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.DiskImage;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.resourceConstants;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTask;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTaskService;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand for resource reservation.
 */
public class ResourceReserveStepCmd extends StepCommand {

  private static final int MAX_PLACEMENT_RETRIES = 5;
  private static final long PLACEMENT_RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(1);

  private static final String DISK_KIND = "disk";
  private static final String VM_KIND = "vm";
  private static final String HOST_KIND = "host";
  private static final String DATASTORE_KIND = "datastore";
  private static final String PORT_GROUP_KIND = "portGroup";
  private static final String AVAILABILITY_ZONE_KIND = "availabilityZone";
  private static final String STORAGE_PREFIX = "storage.";
  private static final Logger logger = LoggerFactory.getLogger(ResourceReserveStepCmd.class);
  private final DiskBackend diskBackend;
  private final VmBackend vmBackend;
  private final NetworkBackend networkBackend;
  private final FlavorBackend flavorBackend;
  private InfrastructureEntity infrastructureEntity;

  public ResourceReserveStepCmd(TaskCommand taskCommand,
                                StepBackend stepBackend,
                                StepEntity step,
                                DiskBackend diskBackend,
                                VmBackend vmBackend,
                                NetworkBackend networkBackend,
                                FlavorBackend flavorBackend) {
    super(taskCommand, stepBackend, step);

    this.diskBackend = diskBackend;
    this.vmBackend = vmBackend;
    this.networkBackend = networkBackend;
    this.flavorBackend = flavorBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> entityList = step.getTransientResourceEntities();
    for (BaseEntity entity : entityList) {
      if (!entity.getKind().equals(Vm.KIND) && !entity.getKind().equals(PersistentDisk.KIND)) {
        continue;
      }

      infrastructureEntity = (InfrastructureEntity) entity;
    }
    Preconditions.checkArgument(infrastructureEntity != null,
        "There should be at least one InfrastructureEntity referenced by step %s", step.getId());

    Resource resource = createResource(infrastructureEntity);
    taskCommand.setResource(resource);

    String reservation;
    if (infrastructureEntity.getKind().equals(Vm.KIND)) {
      String targetHostIp = ((VmEntity) infrastructureEntity).getHostAffinity();
      reservation = loadReservation(resource, targetHostIp, infrastructureEntity.getKind());
    } else {
      reservation = loadReservation(resource, infrastructureEntity.getKind());
    }
    taskCommand.setReservation(reservation);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (infrastructureEntity != null) {
      String entityId = infrastructureEntity.getId();
      logger.info("Resource reservation failed, mark entity {} state as ERROR", entityId);

      try {
        String kind = infrastructureEntity.getKind();
        switch (kind) {
          case Vm.KIND:
            vmBackend.updateState((VmEntity) infrastructureEntity, VmState.ERROR);
            break;
          case PersistentDisk.KIND:
            diskBackend.updateState((PersistentDiskEntity) infrastructureEntity, DiskState.ERROR);
            break;
          default:
            throw new IllegalStateException(
                String.format("%s is invalid!", kind));
        }
      } catch (Exception ex) {
        logger.error("Fail to update entity {} state", entityId, ex);
      }
    }
  }

  @VisibleForTesting
  protected void setInfrastructureEntity(InfrastructureEntity infrastructureEntity) {
    this.infrastructureEntity = infrastructureEntity;
  }

  private Resource createResource(InfrastructureEntity entity)
      throws InternalException, ExternalException, ResourceConstraintException {
    switch (entity.getKind()) {
      case Vm.KIND:
        return createResource((VmEntity) entity);
      case EphemeralDisk.KIND:
      case PersistentDisk.KIND:
        return createResource((BaseDiskEntity) entity);
      default:
        throw new InternalException(String.format("Invalid entity kind %s", entity.getKind()));
    }
  }

  private Resource createResource(VmEntity entity) throws ExternalException, InternalException,
      ResourceConstraintException {
    List<Disk> attachedDisks = new ArrayList<>();

    List<ResourceConstraint> dataStoreResourceConstraintList = getDatastoreAffinityConstraintsFromVm(entity);

    for (AttachedDiskEntity attachedDisk : entity.getAttachedDisks()) {
      BaseDiskEntity underlyingDisk = attachedDisk.getUnderlyingTransientDisk();
      boolean newDisk = underlyingDisk.getState() == DiskState.CREATING;
      boolean persistent = attachedDisk.getKind().equals(PersistentDisk.KIND);
      Disk disk = new Disk(underlyingDisk.getId(), underlyingDisk.getFlavorId(), persistent, newDisk,
          underlyingDisk.getCapacityGb());
      disk.setFlavor_info(getFlavor(underlyingDisk));

      if (attachedDisk.isBootDisk()) {
        // Use image specified in flavor.
        checkNotNull(entity.getImageId());
        logger.info("Use image {} as boot disk", entity.getImageId());
        disk.setImage(new DiskImage(entity.getImageId(), CloneType.COPY_ON_WRITE));
      }

      List<ResourceConstraint> datastoreTagConstraints = createDatastoreTagConstraint(disk.getFlavor_info());
      if (datastoreTagConstraints != null && !datastoreTagConstraints.isEmpty()) {
        for (ResourceConstraint resourceConstraint : datastoreTagConstraints) {
          disk.addToResource_constraints(resourceConstraint);
        }
      }

      if (dataStoreResourceConstraintList != null && !dataStoreResourceConstraintList.isEmpty()) {
        for (ResourceConstraint resourceConstraint : dataStoreResourceConstraintList) {
          disk.addToResource_constraints(resourceConstraint);
        }
      }

      attachedDisks.add(disk);
    }

    com.vmware.photon.controller.resource.gen.Vm vm = new com.vmware.photon.controller.resource.gen.Vm();
    FlavorEntity flavorEntity = flavorBackend.getEntityById(entity.getFlavorId());
    vm.setFlavor(flavorEntity.getName());

    vm.setId(entity.getId());
    vm.setDisks(attachedDisks);
    vm.setState(com.vmware.photon.controller.resource.gen.State.STOPPED);
    vm.setFlavor_info(getFlavor(entity));
    vm.setProject_id(entity.getProjectId());
    vm.setTenant_id(this.getTenantId(entity));

    setEnvironments(entity, vm);
    createAffinityConstraints(entity, vm);
    createNetworkConstraints(entity, vm);

    Resource resource = new Resource();
    resource.setVm(vm);
    return resource;
  }

  private ResourceConstraint createVmResourceConstraint(LocalityEntity localityEntity)
      throws DiskNotFoundException, InvalidLocalitySpecException {
    ResourceConstraint resourceConstraint = new ResourceConstraint();
    ArrayList<String> constraintValues = new ArrayList<String>();

    switch (localityEntity.getKind()) {
      case DISK_KIND:
        resourceConstraint.setType(ResourceConstraintType.DATASTORE);
        constraintValues.add(diskBackend.find(PersistentDisk.KIND, localityEntity.getResourceId()).getDatastore());
        resourceConstraint.setValues(constraintValues);
        break;

      case DATASTORE_KIND:
        resourceConstraint.setType(ResourceConstraintType.DATASTORE);
        constraintValues.add(localityEntity.getResourceId());
        resourceConstraint.setValues(constraintValues);
        break;

      case AVAILABILITY_ZONE_KIND:
        resourceConstraint.setType(ResourceConstraintType.AVAILABILITY_ZONE);
        constraintValues.add(localityEntity.getResourceId());
        resourceConstraint.setValues(constraintValues);
        break;

      case HOST_KIND:
      case PORT_GROUP_KIND:
        logger.info("{} locality is not honored by root scheduler and is to be ignored when building the resource " +
            "constraints", localityEntity.getKind());
        return null;

      default:
        String errorMessage = String.format("%s locality is an unexpected constraint for creating a VM.",
            localityEntity.getKind());
        logger.error(errorMessage);

        throw new InvalidLocalitySpecException(errorMessage);
    }

    for (String value : resourceConstraint.getValues()) {
      if (StringUtils.isBlank(value)) {
        String errorMessage = "Blank resource constraint value for " + resourceConstraint.getType().toString();
        logger.error(errorMessage);

        throw new InvalidLocalitySpecException(errorMessage);
      }
    }

    return resourceConstraint;
  }

  private Resource createResource(BaseDiskEntity diskEntity) throws ExternalException, ResourceConstraintException {
    List<Disk> disks = new ArrayList<>();
    List<ResourceConstraint> resourceConstraints = new ArrayList<>();
    boolean newDisk = diskEntity.getState() == DiskState.CREATING;

    FlavorEntity flavorEntity = flavorBackend.getEntityById(diskEntity.getFlavorId());
    Disk disk = new Disk(diskEntity.getId(), flavorEntity.getName(), true, newDisk,
        diskEntity.getCapacityGb());
    disk.setFlavor_info(getFlavor(diskEntity));

    if (diskEntity instanceof PersistentDiskEntity) {
      PersistentDiskEntity persistDisk = (PersistentDiskEntity) diskEntity;
      // multiple vm affinities are allowed
      for (String vmId : persistDisk.getAffinities(VM_KIND)) {
        ResourceConstraint resourceConstraint = new ResourceConstraint();
        resourceConstraint.setType(ResourceConstraintType.DATASTORE);
        ArrayList<String> constraintValues = new ArrayList<String>();
        constraintValues.add(vmBackend.findDatastoreByVmId(vmId));
        resourceConstraint.setValues(constraintValues);
        resourceConstraints.add(resourceConstraint);
        logger.info("Create Disk Using resource constraints with datastore id {}, and datastore type {}",
            resourceConstraint.getValues(), resourceConstraint.getType());
      }

      //For persistent disks we are adding datastore tag resource
      resourceConstraints.addAll(createDatastoreTagConstraint(disk.getFlavor_info()));
    }

    if (!resourceConstraints.isEmpty()) {
      disk.setResource_constraints(resourceConstraints);
    }

    disks.add(disk);
    Resource resource = new Resource();

    resource.setDisks(disks);
    return resource;
  }

  private List<ResourceConstraint> createDatastoreTagConstraint(Flavor flavor) throws ResourceConstraintException {
    List<ResourceConstraint> resourceConstraints = new ArrayList<>();

    List<QuotaLineItem> quotaLineItems = flavor.getCost();
    if (quotaLineItems != null) {
      for (QuotaLineItem quotaLineItem : quotaLineItems) {
        String key = quotaLineItem.getKey();
        // Only send the resourceConstraints with the tag that is known to the agent
        // When we see an unknown tag, we fail exception for Beta1
        if (key.startsWith(STORAGE_PREFIX)) {
          if (key.equalsIgnoreCase(STORAGE_PREFIX + resourceConstants.LOCAL_VMFS_TAG) ||
              key.equalsIgnoreCase(STORAGE_PREFIX + resourceConstants.SHARED_VMFS_TAG) ||
              key.equalsIgnoreCase(STORAGE_PREFIX + resourceConstants.NFS_TAG)) {
            ResourceConstraint resourceConstraint = new ResourceConstraint();
            resourceConstraint.setType(ResourceConstraintType.DATASTORE_TAG);
            ArrayList<String> constraintValues = new ArrayList<String>();
            constraintValues.add(key.substring(STORAGE_PREFIX.length()));
            resourceConstraint.setValues(constraintValues);
            resourceConstraints.add(resourceConstraint);
            logger.info("Adding Datastore_Tag resource constraints with value {}", resourceConstraint.getValues());
          } else {
            //This will go away once we support user defined datastore tagging
            String errorMessage = "Unknown resource constraint value " + key;
            logger.error(errorMessage);

            throw new ResourceConstraintException(errorMessage);
          }
        }
      }
    }
    return resourceConstraints;
  }

  private String loadReservation(Resource resource, String entityKind)
      throws InterruptedException, ApiFeException, RpcException {

    // In regular cases, root scheduler is to be used to determine the target host/agent id.
    return loadReservation(resource, null, entityKind);
  }

  private String loadReservation(Resource resource, String targetHostIp, String entityKind)
      throws InterruptedException, ApiFeException, RpcException {
    int retries = 0;

    while (true) {
      try {
        ReserveResponse reserveResponse;
        int generation;

        // If the host ip is unknown a Xenon PlacementTask is created to send to the scheduler
        // to find a suitable host with the given resource requested.
        // Otherwise a thrift place request is sent directly.
        if (targetHostIp == null) {
          PlacementTask placementResponse = sendPlaceRequest(resource);
          ServerAddress serverAddress = placementResponse.serverAddress;
          String hostIp = serverAddress.getHost();
          int port = serverAddress.getPort();
          generation = placementResponse.generation;
          resource.setPlacement_list(placementResponse.resource.getPlacement_list());
          logger.info("placed resource, agent host ip: {}, port: {}", hostIp, port);
          taskCommand.getHostClient().setIpAndPort(hostIp, port);
        } else {
          taskCommand.getHostClient().setHostIp(targetHostIp);
          PlaceResponse placeResponse = taskCommand.getHostClient().place(resource);
          generation = placeResponse.getGeneration();
          resource.setPlacement_list(placeResponse.getPlacementList());
          logger.info("placed resource, host: {}", targetHostIp);
        }

        reserveResponse = taskCommand.getHostClient().reserve(resource, generation);
        String reservation = checkNotNull(reserveResponse.getReservation());
        logger.info("reserved resource, generation: {}, reservation: {}", generation, reservation);
        return reservation;
      } catch (StaleGenerationException e) {
        if (++retries >= MAX_PLACEMENT_RETRIES) {
          throw e;
        }

        logger.info("retrying: {}", e.getClass().toString());
      } catch (InvalidSchedulerException e) {
        if (++retries >= MAX_PLACEMENT_RETRIES) {
          throw e;
        }

        // we should sleep here a bit between retries to give the scheduling tree some time to stabilize.
        Thread.sleep(PLACEMENT_RETRY_INTERVAL);
        logger.info("retrying: {}", e.getClass().toString());
      }
    }
  }

  /**
   * Searches for a host that have the specified resources.
   *
   * @param resource the resources requested
   * @return the result of finding a host: either an OK response with the host address or an
   *             error of the failure.
   * @throws ApiFeException
   * @throws RpcException
   */
  private PlacementTask sendPlaceRequest(Resource resource) throws ApiFeException, RpcException {
    SchedulerXenonRestClient schedulerXenonRestClient = taskCommand.getSchedulerXenonRestClient();
    logger.info("place request resource: {}", resource);
    PlacementTask placementTask = new PlacementTask();
    placementTask.resource = resource;
    placementTask.taskState = new TaskState();
    placementTask.taskState.isDirect = true;

    // Wait for the response of the PlacementTask
    Operation placementResponse = schedulerXenonRestClient.post(PlacementTaskService.FACTORY_LINK, placementTask);
    PlacementTask taskResponse = placementResponse.getBody(PlacementTask.class);

    switch (taskResponse.resultCode) {
      case OK:
        break;
      case NO_SUCH_RESOURCE:
        logger.error("reserve resource failed: {}, {}", ErrorCode.NO_SUCH_RESOURCE, taskResponse.error);
        throw new com.vmware.photon.controller.apife.exceptions.external.NoSuchResourceException();
      case NOT_ENOUGH_CPU_RESOURCE:
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_CPU_RESOURCE, taskResponse.error);
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughCpuResourceException();
      case NOT_ENOUGH_MEMORY_RESOURCE:
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_MEMORY_RESOURCE, taskResponse.error);
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughMemoryResourceException();
      case NOT_ENOUGH_DATASTORE_CAPACITY:
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_DATASTORE_CAPACITY, taskResponse.error);
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughDatastoreCapacityException();
      case RESOURCE_CONSTRAINT:
        logger.error("reserve resource failed: {}, {}", ErrorCode.UNFULLFILLABLE_AFFINITIES, taskResponse.error);
        throw new UnfulfillableAffinitiesException();
      case INVALID_SCHEDULER:
        throw new InvalidSchedulerException(taskResponse.error);
      case SYSTEM_ERROR:
        logger.error("reserve resource failed: System error, {}", taskResponse.error);
        throw new SystemErrorException(taskResponse.error);
      case NOT_LEADER:
        throw new NotLeaderException();
      case INVALID_STATE:
        logger.error("reserve resource failed: Invalid agent state, {}", taskResponse.error);
        throw new InvalidAgentStateException(taskResponse.error);
      default:
        throw new RpcException(String.format("Unknown result: %s : %s", taskResponse.resultCode,
            taskResponse.error));
    }

    return taskResponse;
  }

  private Flavor getFlavor(InfrastructureEntity infrastructureEntity) throws ExternalException {
    Flavor flavorInfo = new Flavor();
    FlavorEntity flavorEntity = flavorBackend.getEntityById(infrastructureEntity.getFlavorId());
    flavorInfo.setName(flavorEntity.getName());

    List<QuotaLineItemEntity> quotaLineItemEntities = infrastructureEntity.getCost();
    List<QuotaLineItem> quotaLineItemList = new ArrayList<>();

    for (QuotaLineItemEntity quotaLineItemEntity : quotaLineItemEntities) {
      QuotaLineItem quotaLine = new QuotaLineItem();
      quotaLine.setKey(quotaLineItemEntity.getKey());
      quotaLine.setValue(String.valueOf(quotaLineItemEntity.getValue()));
      quotaLine.setUnit((QuotaUnit.values())[quotaLineItemEntity.getUnit().ordinal()]);

      quotaLineItemList.add(quotaLine);
    }

    flavorInfo.setCost(quotaLineItemList);
    return flavorInfo;
  }

  private String getTenantId(InfrastructureEntity entity) throws InternalException {
    List<ProjectEntity> projectEntityList = this.step.getTransientResourceEntities(ProjectEntity.KIND);
    if (projectEntityList == null || projectEntityList.size() != 1) {
      logger.error("Could not find Project entity in transient resource list.");
      throw new InternalException("Project entity not found in the step.");
    }

    ProjectEntity projectEntity = projectEntityList.get(0);
    if (!Objects.equals(projectEntity.getId(), entity.getProjectId())) {
      logger.error(
          "Project entity in transient resource list did not match VMs project. (VM proj. = {}), (List proj. = {})",
          entity.getProjectId(), projectEntity.getId());
      throw new InternalException("Project entity in transient resource list did not match VMs project.");
    }

    return projectEntity.getTenantId();
  }

  private void setEnvironments(VmEntity entity, com.vmware.photon.controller.resource.gen.Vm vm) {
    if (entity.getEnvironment() != null && !entity.getEnvironment().isEmpty()) {
      vm.setEnvironment(entity.getEnvironment());
    }
  }

  private void createAffinityConstraints(
      VmEntity entity,
      com.vmware.photon.controller.resource.gen.Vm vm)
      throws DiskNotFoundException, InvalidLocalitySpecException {
    if (entity.getAffinities() != null && !entity.getAffinities().isEmpty()) {
      for (LocalityEntity localityEntity : entity.getAffinities()) {
        ResourceConstraint resourceConstraint = createVmResourceConstraint(localityEntity);
        if (resourceConstraint != null) {
          logger.info("Adding resource constraint for vm {}, with id {}, and type {}",
              vm.getId(), resourceConstraint.getValues(), resourceConstraint.getType());
          vm.addToResource_constraints(resourceConstraint);
        }
      }
    }
  }

  private List<ResourceConstraint> getDatastoreAffinityConstraintsFromVm(
      VmEntity vmEntity)
      throws DiskNotFoundException, InvalidLocalitySpecException {
    List<ResourceConstraint> resourceConstraints = new ArrayList<>();
    if (vmEntity.getAffinities() != null && !vmEntity.getAffinities().isEmpty()) {
      for (LocalityEntity localityEntity : vmEntity.getAffinities()) {
        if (localityEntity.getKind().equals(DATASTORE_KIND)) {
          ResourceConstraint resourceConstraint = new ResourceConstraint();
          resourceConstraint.setType(ResourceConstraintType.DATASTORE);
          ArrayList<String> constraintValues = new ArrayList<String>();
          constraintValues.add(localityEntity.getResourceId());
          resourceConstraint.setValues(constraintValues);
          logger.info("Found datastore resource constraint for vm {}, with id {}, and type {}",
              vmEntity.getId(), resourceConstraint.getValues(), resourceConstraint.getType());
          resourceConstraints.add(resourceConstraint);
        }
      }
    }
    return resourceConstraints;
  }

  private void createNetworkConstraints(
      VmEntity entity,
      com.vmware.photon.controller.resource.gen.Vm vm)
      throws NetworkNotFoundException {
    if (entity.getNetworks() != null && !entity.getNetworks().isEmpty()) {
      for (String network : entity.getNetworks()) {
        ResourceConstraint resourceConstraint = new ResourceConstraint();
        resourceConstraint.setType(ResourceConstraintType.NETWORK);
        for (String portGroup : networkBackend.toApiRepresentation(network).getPortGroups()) {
          resourceConstraint.addToValues(portGroup);
        }

        vm.addToResource_constraints(resourceConstraint);
      }
    }
  }
}
