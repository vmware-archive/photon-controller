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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.DiskCreateSpec;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.db.dao.AttachedDiskDao;
import com.vmware.photon.controller.apife.db.dao.BaseDiskDao;
import com.vmware.photon.controller.apife.db.dao.EphemeralDiskDao;
import com.vmware.photon.controller.apife.db.dao.PersistentDiskDao;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.DiskStateChecks;
import com.vmware.photon.controller.apife.entities.EphemeralDiskEntity;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * The Disk Sql Backend.
 */
@Singleton
public class DiskSqlBackend implements DiskBackend {

  private static final Logger logger = LoggerFactory.getLogger(DiskSqlBackend.class);

  private final EphemeralDiskDao ephemeralDiskDao;
  private final PersistentDiskDao persistentDiskDao;
  private final AttachedDiskDao attachedDiskDao;
  private final ProjectBackend projectBackend;

  private final TaskBackend taskBackend;
  private final EntityLockBackend entityLockBackend;
  private final TombstoneBackend tombstoneBackend;
  private final LocalityBackend localityBackend;
  private final FlavorBackend flavorBackend;
  private final ResourceTicketBackend resourceTicketBackend;

  @Inject
  public DiskSqlBackend(PersistentDiskDao persistentDiskDao,
                        EphemeralDiskDao ephemeralDiskDao,
                        ResourceTicketBackend resourceTicketBackend,
                        AttachedDiskDao attachedDiskDao,
                        ProjectBackend projectBackend,
                        TaskBackend taskBackend,
                        EntityLockBackend entityLockBackend,
                        TombstoneBackend tombstoneBackend,
                        LocalityBackend localityBackend,
                        FlavorBackend flavorBackend) {
    this.persistentDiskDao = persistentDiskDao;
    this.ephemeralDiskDao = ephemeralDiskDao;
    this.resourceTicketBackend = resourceTicketBackend;
    this.attachedDiskDao = attachedDiskDao;
    this.projectBackend = projectBackend;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.localityBackend = localityBackend;
    this.flavorBackend = flavorBackend;
  }

  @Transactional
  public PersistentDisk toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findPersistentDisk(id));
  }

  @Transactional
  public List<PersistentDisk> filter(String projectId, Optional<String> name) throws ExternalException {
    ProjectEntity project = projectBackend.findById(projectId);
    List<PersistentDiskEntity> disks;

    if (name.isPresent()) {
      disks = persistentDiskDao.listByName(name.get(), project);
    } else {
      disks = persistentDiskDao.findAll(project);
    }

    List<PersistentDisk> result = new ArrayList<>(disks.size());

    for (PersistentDiskEntity disk : disks) {
      result.add(toApiRepresentation(disk));
    }

    return result;
  }

  @Transactional
  public TaskEntity prepareDiskCreate(String projectId, DiskCreateSpec spec) throws ExternalException {
    BaseDiskEntity diskEntity = create(projectId, spec);
    logger.info("created Disk: {}", diskEntity);
    TaskEntity task = createTask(diskEntity.getKind(), diskEntity.getId());
    logger.info("created Task: {}", task);
    return task;
  }

  @Transactional
  public TaskEntity prepareDiskDelete(String diskId) throws ExternalException {
    PersistentDiskEntity diskEntity = findPersistentDisk(diskId);
    logger.info("deleting Disk: {}", diskEntity);
    TaskEntity task = deleteTask(diskEntity, true);
    logger.info("created Task: {}", task);
    return task;
  }

  @Transactional
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

  @Transactional
  public void tombstone(String kind, String diskId) throws ExternalException {
    Stopwatch tombstoneWatch = Stopwatch.createStarted();

    BaseDiskEntity disk = getDiskDao(kind).findById(diskId).orNull();
    checkNotNull(disk);

    String resourceTickedId = projectBackend.findById(disk.getProjectId()).getResourceTicketId();

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.returnQuota(resourceTickedId, new QuotaCost(disk.getCost()));
    resourceTicketWatch.stop();
    logger.info("DiskSqlBackend.tombstone for Disk Id: {}, Kind:{}, resourceTicket {}, returnQuota in {} milliseconds",
        diskId,
        kind,
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    tombstoneBackend.create(kind, disk.getId());
    getDiskDao(kind).delete(disk);
    tombstoneWatch.stop();
    logger.info("DiskSqlBackend.tombstone for Disk Id: {}, Kind:{} took {} milliseconds",
        diskId,
        kind,
        tombstoneWatch.elapsed(TimeUnit.MILLISECONDS));
  }

  @Transactional
  public void updateState(BaseDiskEntity disk, DiskState state) {
    if (PersistentDisk.KIND.equals(disk.getKind())) {
      updateState((PersistentDiskEntity) disk, persistentDiskDao, state);
    } else if (EphemeralDisk.KIND.equals(disk.getKind())) {
      updateState((EphemeralDiskEntity) disk, ephemeralDiskDao, state);
    } else {
      throw new IllegalArgumentException(
          String.format("Invalid disk kind %s", disk.getKind())
      );
    }
  }

  @Transactional
  public void updateState(BaseDiskEntity disk, DiskState state, String agent, String datastore) throws
      DiskNotFoundException {
    disk.setAgent(agent);
    disk.setDatastore(datastore);
    updateState(disk, state);
  }

  @Transactional
  public void createVmDiskOperationStep(TaskEntity task, VmEntity vm,
                                        List<String> diskIds, Operation operation)
      throws ExternalException {

    List<BaseEntity> entityList = new ArrayList<>();
    // Add vm entity
    entityList.add(vm);
    // Add disk entities
    for (String diskId : diskIds) {
      BaseDiskEntity disk = find(PersistentDisk.KIND, diskId);
      // Check if disk is a valid state for the operation
      DiskStateChecks.checkOperationState(disk, operation);
      entityList.add(disk);
    }

    /*
     * If we make it to this point all disks have been found
     * and they are all detached (otherwise find() and checkOperationState()
     * would have thrown exceptions)
     */

    StepEntity stepEntity = taskBackend.getStepBackend().createQueuedStep(task, entityList, operation);

    // Create step locks
    for (BaseEntity entity : entityList) {
      entityLockBackend.setStepLock(entity, stepEntity);
    }
  }

  @Transactional
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    BaseDiskEntity disk = persistentDiskDao.findById(id).orNull();
    if (disk == null) {
      disk = ephemeralDiskDao.findById(id).orNull();
    }

    if (disk == null) {
      throw new DiskNotFoundException(
          String.format("%s or %s", PersistentDisk.KIND, EphemeralDisk.KIND), id);
    }

    return taskBackend.filter(disk.getId(), disk.getKind(), state);
  }

  @Transactional
  public BaseDiskEntity find(String kind, String id) throws DiskNotFoundException {
    BaseDiskEntity disk;

    switch (kind) {
      case PersistentDisk.KIND:
        disk = findPersistentDisk(id);
        break;
      case EphemeralDisk.KIND:
        disk = findEphemeralDisk(id);
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + kind);
    }

    return disk;
  }

  public boolean existsUsingFlavor(String flavorId) throws ExternalException {
    throw new NotImplementedException();
  }

  private PersistentDisk toApiRepresentation(PersistentDiskEntity persistentDiskEntity) throws ExternalException {
    PersistentDisk persistentDisk = new PersistentDisk();
    persistentDisk.setId(persistentDiskEntity.getId());
    persistentDisk.setName(persistentDiskEntity.getName());
    FlavorEntity flavorEntity = flavorBackend.getEntityById(persistentDiskEntity.getFlavorId());
    persistentDisk.setFlavor(flavorEntity.getName());
    persistentDisk.setState(persistentDiskEntity.getState());
    persistentDisk.setDatastore(persistentDiskEntity.getDatastore());
    persistentDisk.setCapacityGb(persistentDiskEntity.getCapacityGb());
    persistentDisk.setProjectId(persistentDiskEntity.getProjectId());

    AttachedDiskEntity attachedDiskEntity = attachedDiskDao.findByDisk(persistentDiskEntity).orNull();
    if (attachedDiskEntity != null) {
      persistentDisk.setVms(ImmutableList.of(attachedDiskEntity.getVmId()));
    }

    return persistentDisk;
  }

  private BaseDiskDao<BaseDiskEntity> getDiskDao(String kind) {
    switch (kind) {
      case PersistentDisk.KIND:
        return (BaseDiskDao) persistentDiskDao;
      case EphemeralDisk.KIND:
        return (BaseDiskDao) ephemeralDiskDao;
      default:
        throw new IllegalArgumentException(kind);
    }
  }

  /**
   * Internal API (e.g., VM rundown, deletes the model for the disk chain, returning a
   * list of tasks that need to be executed, in order, in order to trigger the backend flows.
   *
   * @param disk     the disk that will be deleted
   * @param validate validate the state transition
   * @return a list of tasks, some of which may contains pending flows.
   * @throws ExternalException
   */
  private TaskEntity deleteTask(BaseDiskEntity disk, boolean validate) throws ExternalException {
    if (validate) {
      DiskStateChecks.checkOperationState(disk, Operation.DELETE_DISK);
    }
    TaskEntity task = taskBackend.createQueuedTask(disk, Operation.DELETE_DISK);
    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, disk, Operation.DELETE_DISK);
    entityLockBackend.setStepLock(disk, step);
    return task;
  }

  private <T extends BaseDiskEntity> T findDisk(String id,
                                                BaseDiskDao<T> diskDao,
                                                String kind) throws DiskNotFoundException {
    T disk = diskDao.findById(id).orNull();
    if (disk == null) {
      throw new DiskNotFoundException(kind, id);
    }
    return disk;
  }

  private <T extends BaseDiskEntity> void updateState(T disk,
                                                      BaseDiskDao<T> diskDao,
                                                      DiskState state) {
    disk.setState(state);
    diskDao.update(disk);
  }

  private PersistentDiskEntity findPersistentDisk(String id) throws DiskNotFoundException {
    return findDisk(id, persistentDiskDao, PersistentDisk.KIND);
  }

  private EphemeralDiskEntity findEphemeralDisk(String id) throws DiskNotFoundException {
    return findDisk(id, ephemeralDiskDao, EphemeralDisk.KIND);
  }

  private TaskEntity deleteTask(String kind, String id) throws ExternalException {
    return deleteTask(find(kind, id), true);
  }

  /**
   * Create disk in DB using disk spec.
   */
  private BaseDiskEntity create(String projectId, DiskCreateSpec spec) throws ExternalException {
    Stopwatch createWatch = Stopwatch.createStarted();

    ProjectEntity project = projectBackend.findById(projectId);

    String kind = spec.getKind();

    // flavor need to be queried before locality affinity entity is saved, otherwise,
    // hibernate will throw exception for disk entity is not saved yet
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getFlavor(), kind);
    BaseDiskEntity disk;

    switch (kind) {
      case PersistentDisk.KIND:
        PersistentDiskEntity persistentDisk = new PersistentDiskEntity();
        persistentDisk.setAffinities(localityBackend.create(persistentDisk, spec.getAffinities()));
        disk = persistentDisk;
        break;
      case EphemeralDisk.KIND:
        disk = new EphemeralDiskEntity();
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + kind);
    }

    disk.setState(DiskState.CREATING);
    disk.setName(spec.getName());
    disk.setCapacityGb(spec.getCapacityGb());
    disk.setFlavorId(flavorEntity.getId());

    List<QuotaLineItemEntity> enhancedCost = new ArrayList<>(flavorEntity.getCost());
    String capacityKey = kind + ".capacity";
    QuotaLineItemEntity capacity = new QuotaLineItemEntity(capacityKey, spec.getCapacityGb(), QuotaUnit.GB);
    for (QuotaLineItemEntity qli : enhancedCost) {

      // assert/crash if capacity key is present in a disk entity's static cost
      // this is computed in this code at runtime.
      if (qli.getKey().equals(capacityKey)) {
        checkState(!qli.getKey().equals(capacityKey));
      }
    }
    enhancedCost.add(capacity);
    disk.setCost(enhancedCost);
    disk.setProjectId(project.getId());

    String resourceTickedId = project.getResourceTicketId();

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.consumeQuota(resourceTickedId, new QuotaCost(disk.getCost()));
    resourceTicketWatch.stop();
    logger.info("DiskSqlBackend.create for Disk Id: {}, Kind:{}, resourceTicket {}, consumeQuota in {} milliseconds",
        disk.getId(),
        kind,
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    BaseDiskDao<BaseDiskEntity> diskDao = getDiskDao(spec.getKind());

    createWatch.stop();
    logger.info("DiskSqlBackend.create for Disk Id: {}, Kind:{} took {} milliseconds",
        disk.getId(),
        kind,
        createWatch.elapsed(TimeUnit.MILLISECONDS));

    return diskDao.create(disk);
  }

  /**
   * Create Disk creation task.
   */
  private TaskEntity createTask(String kind, String diskId) throws ExternalException {
    BaseDiskEntity diskEntity = getDiskDao(kind).findById(diskId).orNull();
    checkNotNull(diskEntity);

    TaskEntity task = taskBackend.createQueuedTask(diskEntity, Operation.CREATE_DISK);
    taskBackend.getStepBackend().createQueuedStep(task, diskEntity, Operation.RESERVE_RESOURCE);

    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, diskEntity, Operation.CREATE_DISK);
    entityLockBackend.setStepLock(diskEntity, step);

    return task;
  }

}
