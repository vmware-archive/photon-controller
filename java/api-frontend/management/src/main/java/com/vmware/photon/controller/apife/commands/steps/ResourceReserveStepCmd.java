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
import com.vmware.photon.controller.common.clients.exceptions.InvalidSchedulerException;
import com.vmware.photon.controller.common.clients.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughCpuResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughDatastoreCapacityException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughMemoryResourceException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.StaleGenerationException;
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
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
      if (entity.getKind() != Vm.KIND && entity.getKind() != PersistentDisk.KIND) {
        continue;
      }

      infrastructureEntity = (InfrastructureEntity) entity;
    }
    Preconditions.checkArgument(infrastructureEntity != null,
        "There should be at least one InfrastructureEntity refrenced by step %s", step.getId());

    Resource resource = createResource(infrastructureEntity);
    taskCommand.setResource(resource);

    String reservation;
    if (infrastructureEntity.getKind() == Vm.KIND) {
      String targetHostIp = ((VmEntity) infrastructureEntity).getHostAffinity();
      reservation = loadReservation(resource, targetHostIp);
    } else {
      reservation = loadReservation(resource);
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
      List<ResourceConstraint> datastoreTagConstraints = createDatastoreConstraint(disk.getFlavor_info());
      for (ResourceConstraint resourceConstraint : datastoreTagConstraints) {
        disk.addToResource_constraints(resourceConstraint);
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
    resourceConstraint.setType(ResourceConstraintType.DATASTORE);

    switch (localityEntity.getKind()) {
      case DISK_KIND:
        resourceConstraint.setValues(
            ImmutableList.of(diskBackend.find(PersistentDisk.KIND, localityEntity.getResourceId()).getDatastore()));
        break;

      case DATASTORE_KIND:
        resourceConstraint.setValues(ImmutableList.of(localityEntity.getResourceId()));
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
        String errorMessage = String.format("Blank resource constraint value for " +
            resourceConstraint.getType().toString());
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
        resourceConstraint.setValues(ImmutableList.of(vmBackend.findDatastoreByVmId(vmId)));
        resourceConstraints.add(resourceConstraint);
        logger.info("Create Disk Using resource constraints with datastore id {}, and datastore type {}",
            resourceConstraint.getValues(), resourceConstraint.getType());
      }

      //For persistent disks we are adding datastore tag resource
      resourceConstraints.addAll(createDatastoreConstraint(disk.getFlavor_info()));
    }

    if (!resourceConstraints.isEmpty()) {
      disk.setResource_constraints(resourceConstraints);
    }

    disks.add(disk);
    Resource resource = new Resource();

    resource.setDisks(disks);
    return resource;
  }

  private List<ResourceConstraint> createDatastoreConstraint(Flavor flavor) throws ResourceConstraintException {
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
            resourceConstraint.setValues(ImmutableList.of(key.substring(STORAGE_PREFIX.length())));
            resourceConstraints.add(resourceConstraint);
            logger.info("Adding Datastore_Tag resource constraints with value {}", resourceConstraint.getValues());
          } else {
            //This will go away once we support user defined datastore tagging
            String errorMessage = String.format("Unknown resource constraint value " + key);
            logger.error(errorMessage);

            throw new ResourceConstraintException(errorMessage);
          }
        }
      }
    }
    return resourceConstraints;
  }

  private String loadReservation(Resource resource)
      throws InterruptedException, ApiFeException, RpcException {

    // In regular cases, root scheduler is to be used to determine the target host/agent id.
    return loadReservation(resource, null);
  }

  private String loadReservation(Resource resource, String targetHostIp)
      throws InterruptedException, ApiFeException, RpcException {
    int retries = 0;

    while (true) {
      try {
        PlaceResponse placeResponse;
        ReserveResponse reserveResponse;
        if (targetHostIp == null) {
          placeResponse = taskCommand.getRootSchedulerClient().place(resource);
          ServerAddress serverAddress = placeResponse.getAddress();
          String hostIp = serverAddress.getHost();
          int port = serverAddress.getPort();
          logger.info("placed resource, agent host ip: {}, port: {}", hostIp, port);
          taskCommand.getHostClient().setIpAndPort(hostIp, port);
        } else {
          taskCommand.getHostClient().setHostIp(targetHostIp);
          placeResponse = taskCommand.getHostClient().place(resource);
          logger.info("placed resource, host: {}", targetHostIp);
        }

        int generation = placeResponse.getGeneration();
        resource.setPlacement_list(placeResponse.getPlacementList());
        reserveResponse = taskCommand.getHostClient().reserve(resource, generation);
        String reservation = checkNotNull(reserveResponse.getReservation());
        logger.info("reserved resource, generation: {}, reservation: {}", generation, reservation);
        return reservation;
      } catch (NoSuchResourceException e) {
        logger.error("reserve resource failed: {}, {}", ErrorCode.NO_SUCH_RESOURCE, e.getMessage());
        throw new com.vmware.photon.controller.apife.exceptions.external.NoSuchResourceException();
      } catch (NotEnoughCpuResourceException e) {
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_CPU_RESOURCE, e.getMessage());
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughCpuResourceException();
      } catch (NotEnoughMemoryResourceException e) {
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_MEMORY_RESOURCE, e.getMessage());
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughMemoryResourceException();
      } catch (NotEnoughDatastoreCapacityException e) {
        logger.error("reserve resource failed: {}, {}", ErrorCode.NOT_ENOUGH_DATASTORE_CAPACITY, e.getMessage());
        throw new com.vmware.photon.controller.apife.exceptions.external.NotEnoughDatastoreCapacityException();
      } catch (ResourceConstraintException e) {
        logger.error("reserve resource failed: {}", e);
        throw new UnfulfillableAffinitiesException();
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
