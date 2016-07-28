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

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.entities.AttachedDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.BaseDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.DiskStateChecks;
import com.vmware.photon.controller.api.frontend.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.FlavorEntity;
import com.vmware.photon.controller.api.frontend.entities.LocalityEntity;
import com.vmware.photon.controller.api.frontend.entities.PersistentDiskEntity;
import com.vmware.photon.controller.api.frontend.entities.ProjectEntity;
import com.vmware.photon.controller.api.frontend.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.entities.base.TagEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.lib.QuotaCost;
import com.vmware.photon.controller.api.frontend.utils.PaginationUtils;
import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskCreateSpec;
import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.api.model.DiskType;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.FlavorState;
import com.vmware.photon.controller.api.model.LocalitySpec;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Disk Xenon backend.
 */
public class DiskXenonBackend implements DiskBackend {

  private static final Logger logger = LoggerFactory.getLogger(DiskXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final ProjectBackend projectBackend;
  private final ResourceTicketBackend resourceTicketBackend;
  private final TaskBackend taskBackend;
  private final FlavorBackend flavorBackend;
  private final EntityLockBackend entityLockBackend;
  private final AttachedDiskBackend attachedDiskBackend;
  private final TombstoneBackend tombstoneBackend;

  @Inject
  public DiskXenonBackend(ApiFeXenonRestClient xenonClient, ProjectBackend projectBackend, FlavorBackend flavorBackend,
                        ResourceTicketBackend resourceTicketBackend, TaskBackend taskBackend,
                        EntityLockBackend entityLockBackend, AttachedDiskBackend attachedDiskBackend,
                        TombstoneBackend tombstoneBackend) {
    this.xenonClient = xenonClient;
    this.projectBackend = projectBackend;
    this.flavorBackend = flavorBackend;
    this.resourceTicketBackend = resourceTicketBackend;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.attachedDiskBackend = attachedDiskBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.xenonClient.start();
  }

  @Override
  public PersistentDisk toApiRepresentation(String id) throws ExternalException {
    DiskService.State state = findById(id);
    return toPersistentDisk(state);
  }

  @Override
  public ResourceList<PersistentDisk> filter(String projectId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    projectBackend.findById(projectId);
    ResourceList<DiskService.State> diskDocuments = findByProjectIdAndName(projectId, name, pageSize);

    return toPersistentDiskList(diskDocuments);
  }

  @Override
  public boolean existsUsingFlavor(String flavorId) throws ExternalException {
    List<DiskService.State> diskDocuments = findByFlavor(flavorId);
    return !diskDocuments.isEmpty();
  }

  @Override
  public TaskEntity prepareDiskCreate(String projectId, DiskCreateSpec spec) throws ExternalException {
    BaseDiskEntity diskEntity = create(projectId, spec);
    logger.info("created Disk: {}", diskEntity);
    TaskEntity task = createTask(diskEntity.getKind(), diskEntity.getId());
    return task;
  }

  @Override
  public TaskEntity prepareDiskDelete(String diskId) throws ExternalException {
    PersistentDiskEntity diskEntity = (PersistentDiskEntity) find(PersistentDisk.KIND, diskId);
    logger.info("deleting Disk: {}", diskEntity);
    TaskEntity task = deleteTask(diskEntity, true);
    return task;
  }

  @Override
  public BaseDiskEntity create(String projectId, AttachedDiskCreateSpec spec) throws ExternalException {
    DiskCreateSpec diskSpec = new DiskCreateSpec();
    diskSpec.setName(spec.getName());
    checkState(spec.getKind().startsWith("ephemeral"),
        String.format("Attached disk can only be ephemeral disk, but got %s", spec.getKind()));
    diskSpec.setKind(EphemeralDisk.KIND);
    diskSpec.setFlavor(spec.getFlavor());
    diskSpec.setCapacityGb(spec.getCapacityGb());

    return create(projectId, diskSpec);
  }

  @Override
  public void tombstone(String kind, String diskId) throws ExternalException {
    BaseDiskEntity diskEntity = find(kind, diskId);

    String resourceTickedId = projectBackend.findById(diskEntity.getProjectId()).getResourceTicketId();
    resourceTicketBackend.returnQuota(resourceTickedId, new QuotaCost(diskEntity.getCost()));
    logger.info("tombstone for Disk Id: {}, Kind:{}, resourceTicket {}",
        diskId,
        kind,
        resourceTickedId);

    FlavorEntity flavor = null;
    if (StringUtils.isNotBlank(diskEntity.getFlavorId())) {
      flavor = flavorBackend.getEntityById(diskEntity.getFlavorId());
    }

    xenonClient.delete(DiskServiceFactory.SELF_LINK + "/" + diskEntity.getId(),
        new DiskService.State());
    tombstoneBackend.create(kind, diskEntity.getId());
    if (flavor != null &&
        FlavorState.PENDING_DELETE.equals(flavor.getState())) {
      flavorBackend.tombstone(flavor);
    }
    logger.info("Disk {} has been cleared", diskEntity.getId());
  }

  @Override
  public void updateState(BaseDiskEntity disk, DiskState state) throws DiskNotFoundException {
    DiskService.State diskServiceState = new DiskService.State();
    diskServiceState.state = state;
    disk.setState(state);
    updateState(disk, diskServiceState);
  }

  @Override
  public void updateState(BaseDiskEntity disk, DiskState state, String agent,
                          String datastore) throws DiskNotFoundException {
    DiskService.State diskServiceState = new DiskService.State();
    diskServiceState.state = state;
    diskServiceState.agent = agent;
    diskServiceState.datastore = datastore;
    disk.setState(state);
    disk.setAgent(agent);
    disk.setDatastore(datastore);
    updateState(disk, diskServiceState);
  }

  @Override
  public BaseDiskEntity find(String kind, String id) throws DiskNotFoundException {
    DiskService.State state = findById(id);
    return toDiskEntity(state, kind);
  }

  @Override
  public ResourceList<PersistentDisk> getDisksPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    ResourceList<DiskService.State> diskStates = PaginationUtils.xenonQueryResultToResourceList(
        DiskService.State.class,
        queryResult);

    List<PersistentDisk> disks = new ArrayList<>();
    for (DiskService.State diskState : diskStates.getItems()) {
      disks.add(toPersistentDisk(diskState));
    }

    ResourceList<PersistentDisk> result = new ResourceList<>();
    result.setItems(disks);
    result.setNextPageLink(diskStates.getNextPageLink());
    result.setPreviousPageLink(diskStates.getPreviousPageLink());

    return result;
  }

  private TaskEntity createTask(String kind, String diskId) throws ExternalException {
    BaseDiskEntity diskEntity = find(kind, diskId);

    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(diskEntity);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.RESERVE_RESOURCE);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.CREATE_DISK);

    TaskEntity task = taskBackend.createTaskWithSteps(diskEntity, Operation.CREATE_DISK, false, stepEntities);

    task.getToBeLockedEntities().add(diskEntity);
    return task;
  }

  private BaseDiskEntity toDiskEntity(DiskService.State state, String kind) {
    BaseDiskEntity diskEntity;

    switch (kind) {
      case PersistentDisk.KIND:
        diskEntity = toPersistentDiskEntity(state);
        break;
      case EphemeralDisk.KIND:
        diskEntity = toEphemeralDiskEntity(state);
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + kind);
    }

    return diskEntity;
  }

  private PersistentDiskEntity toPersistentDiskEntity(DiskService.State diskState) {
    PersistentDiskEntity diskEntity = new PersistentDiskEntity();
    toBaseDiskEntity(diskEntity, diskState);
    if (diskState.affinities == null) {
      return diskEntity;
    }
    for (LocalitySpec localitySpec : diskState.affinities) {
      LocalityEntity localityEntity = new LocalityEntity();
      localityEntity.setResourceId(localitySpec.getId());
      localityEntity.setKind(localitySpec.getKind());
      diskEntity.getAffinities().add(localityEntity);
    }
    return diskEntity;
  }

  private EphemeralDiskEntity toEphemeralDiskEntity(DiskService.State diskState) {
    EphemeralDiskEntity diskEntity = new EphemeralDiskEntity();
    toBaseDiskEntity(diskEntity, diskState);
    return diskEntity;
  }

  private void toBaseDiskEntity(BaseDiskEntity diskEntity, DiskService.State diskState) {
    String id = ServiceUtils.getIDFromDocumentSelfLink(diskState.documentSelfLink);
    diskEntity.setId(id);
    diskEntity.setProjectId(diskState.projectId);
    diskEntity.setName(diskState.name);
    diskEntity.setState(diskState.state);
    diskEntity.setCapacityGb(diskState.capacityGb);
    diskEntity.setFlavorId(diskState.flavorId);
    diskEntity.setDatastore(diskState.datastore);
    diskEntity.setAgent(diskState.agent);
    if (diskState.tags != null) {
      for (String tagText : diskState.tags) {
        TagEntity tagEntity = new TagEntity();
        tagEntity.setValue(tagText);
        diskEntity.getTags().add(tagEntity);
      }
    }

    if (diskState.cost != null) {
      List<QuotaLineItemEntity> cost = new ArrayList<>();
      for (QuotaLineItem item : diskState.cost) {
        cost.add(new QuotaLineItemEntity(item.getKey(), item.getValue(), item.getUnit()));
      }
      diskEntity.setCost(cost);
    }
  }

  private DiskService.State findById(String id) throws DiskNotFoundException {
    com.vmware.xenon.common.Operation result;
    try {
      result = xenonClient.get(DiskServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DiskNotFoundException(id);
    }
    return result.getBody(DiskService.State.class);
  }

  private PersistentDisk toPersistentDisk(DiskService.State diskState) throws ExternalException {
    PersistentDisk persistentDisk = new PersistentDisk();
    String id = ServiceUtils.getIDFromDocumentSelfLink(diskState.documentSelfLink);
    persistentDisk.setId(id);
    persistentDisk.setName(diskState.name);
    persistentDisk.setState(diskState.state);
    persistentDisk.setCapacityGb(diskState.capacityGb);
    persistentDisk.setProjectId(diskState.projectId);

    FlavorEntity flavorEntity = flavorBackend.getEntityById(diskState.flavorId);
    persistentDisk.setFlavor(flavorEntity.getName());
    persistentDisk.setDatastore(diskState.datastore);
    persistentDisk.setCost(diskState.cost);

    if (diskState.tags != null) {
      persistentDisk.setTags(new HashSet<>(diskState.tags));
    }

    PersistentDiskEntity persistentDiskEntity = toPersistentDiskEntity(diskState);
    AttachedDiskEntity attachedDiskEntity = attachedDiskBackend.findAttachedDisk(persistentDiskEntity);
    if (attachedDiskEntity != null) {
      persistentDisk.setVms(ImmutableList.of(attachedDiskEntity.getVmId()));
    }
    return persistentDisk;
  }

  private void updateState(BaseDiskEntity diskEntity, DiskService.State state) throws DiskNotFoundException {
    String diskId = diskEntity.getId();

    try {
      xenonClient.patch(DiskServiceFactory.SELF_LINK + "/" + diskId, state);
    } catch (DocumentNotFoundException e) {
      throw new DiskNotFoundException(diskEntity.getKind(), diskId);
    }
  }

  private ResourceList<DiskService.State> findByProjectIdAndName(String projectId, Optional<String> name,
                                                                 Optional<Integer> pageSize) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    termsBuilder.put("projectId", projectId);
    termsBuilder.put("diskType", DiskType.PERSISTENT.name());
    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(DiskService.State.class, termsBuilder.build(),
        pageSize, true);
    return PaginationUtils.xenonQueryResultToResourceList(DiskService.State.class, queryResult);
  }

  private ResourceList<PersistentDisk> toPersistentDiskList(ResourceList<DiskService.State> diskDocuments)
      throws ExternalException {
    ResourceList<PersistentDisk> result = new ResourceList<>();

    List<PersistentDisk> persistentDiskList = new ArrayList<>();
    for (DiskService.State state : diskDocuments.getItems()) {
      persistentDiskList.add(toPersistentDisk(state));
    }

    result.setItems(persistentDiskList);
    result.setNextPageLink(diskDocuments.getNextPageLink());
    result.setPreviousPageLink(diskDocuments.getPreviousPageLink());
    return result;
  }

  /**
   * Internal API (e.g., VM rundown, deletes the model for the disk chain, returning a
   * task that need to be executed, in order, in order to trigger the backend flows.
   *
   * @param disk     the disk that will be deleted
   * @param validate validate the state transition
   * @return TaskEntity, some of which may contains pending flows.
   * @throws ExternalException
   */
  private TaskEntity deleteTask(BaseDiskEntity disk, boolean validate) throws ExternalException {
    if (validate) {
      DiskStateChecks.checkOperationState(disk, Operation.DELETE_DISK);
    }

    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(disk);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DELETE_DISK);

    TaskEntity task = taskBackend.createTaskWithSteps(disk, Operation.DELETE_DISK, false, stepEntities);
    task.getToBeLockedEntities().add(disk);
    return task;
  }

  private List<QuotaLineItem> setCostCapacity(Flavor flavor, String kind, int capacityGb) {
    List<QuotaLineItem> enhancedCost = new ArrayList<>(flavor.getCost());
    String capacityKey = kind + ".capacity";
    QuotaLineItem capacity = new QuotaLineItem(capacityKey, capacityGb, QuotaUnit.GB);
    for (QuotaLineItem qli : enhancedCost) {

      // assert/crash if capacity key is present in a disk entity's static cost
      // this is computed in this code at runtime.
      if (qli.getKey().equals(capacityKey)) {
        checkState(!qli.getKey().equals(capacityKey));
      }
    }
    enhancedCost.add(capacity);
    return enhancedCost;
  }


  private BaseDiskEntity create(String projectId, DiskCreateSpec spec) throws ExternalException {
    ProjectEntity projectEntity = projectBackend.findById(projectId);
    DiskService.State diskState = new DiskService.State();
    BaseDiskEntity disk;

    String kind = spec.getKind();
    switch (kind) {
      case PersistentDisk.KIND:
        diskState.diskType = DiskType.PERSISTENT;
        diskState.affinities = spec.getAffinities();
        disk = new PersistentDiskEntity();
        break;
      case EphemeralDisk.KIND:
        diskState.diskType = DiskType.EPHEMERAL;
        disk = new EphemeralDiskEntity();
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + kind);
    }
    diskState.projectId = projectId;
    diskState.name = spec.getName();
    diskState.state = DiskState.CREATING;
    diskState.capacityGb = spec.getCapacityGb();

    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getFlavor(), kind);
    if (!FlavorState.READY.equals(flavorEntity.getState())) {
      throw new InvalidFlavorStateException(
          String.format("Create disk using flavor with name: %s is in invalid state %s.",
              flavorEntity.getName(), flavorEntity.getState()));
    }
    diskState.flavorId = flavorEntity.getId();
    Flavor flavor = flavorBackend.getApiRepresentation(flavorEntity.getId());
    diskState.cost = setCostCapacity(flavor, kind, spec.getCapacityGb());

    List<QuotaLineItemEntity> cost =
        diskState.cost.stream()
            .map(item -> new QuotaLineItemEntity(item.getKey(), item.getValue(), item.getUnit()))
            .collect(Collectors.toList());
    resourceTicketBackend.consumeQuota(projectEntity.getResourceTicketId(), new QuotaCost(cost));

    for (String tagText : spec.getTags()) {
      if (diskState.tags == null) {
        diskState.tags = new HashSet<>();
      }
      diskState.tags.add(tagText);
    }

    com.vmware.xenon.common.Operation result = xenonClient.post(DiskServiceFactory.SELF_LINK, diskState);
    DiskService.State createdState = result.getBody(DiskService.State.class);

    toBaseDiskEntity(disk, createdState);
    logger.info("Disk {} has been created", disk.getId());

    return disk;
  }

  private List<DiskService.State> findByFlavor(String flavorId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    termsBuilder.put("flavorId", flavorId);
    return xenonClient.queryDocuments(DiskService.State.class, termsBuilder.build());
  }
}
