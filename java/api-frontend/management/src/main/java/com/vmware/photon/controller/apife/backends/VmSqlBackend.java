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

import com.vmware.photon.controller.api.AttachedDisk;
import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.ImageCreateSpec;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Iso;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmDiskOperation;
import com.vmware.photon.controller.api.VmOperation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.db.dao.TagDao;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.InvalidEntityException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.db.dao.IsoDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmDisksSpecException;
import com.vmware.photon.controller.apife.exceptions.external.IsoAlreadyAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.MoreThanOneIsoAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.PersistentDiskAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * VmSqlBackend is performing VM operations such as create, delete, add tag etc.
 */
@Singleton
public class VmSqlBackend implements VmBackend {

  private static final Logger logger = LoggerFactory.getLogger(VmSqlBackend.class);
  private static final int GB_TO_BYTE_CONVERSION_RATIO = 1024 * 1024 * 1024;

  private final VmDao vmDao;
  private final TagDao tagDao;
  private final IsoDao isoDao;
  private final HostBackend hostBackend;

  private final ResourceTicketBackend resourceTicketBackend;
  private final TagBackend tagBackend;
  private final ProjectBackend projectBackend;
  private final AttachedDiskBackend attachedDiskBackend;
  private final DiskBackend diskBackend;
  private final TaskBackend taskBackend;
  private final EntityLockBackend entityLockBackend;
  private final TombstoneBackend tombstoneBackend;
  private final LocalityBackend localityBackend;
  private final FlavorBackend flavorBackend;
  private final ImageBackend imageBackend;

  @Inject
  public VmSqlBackend(ResourceTicketBackend resourceTicketBackend,
                      TagBackend tagBackend,
                      VmDao vmDao,
                      TagDao tagDao,
                      IsoDao isoDao,
                      HostBackend hostBackend,
                      ProjectBackend projectBackend,
                      AttachedDiskBackend attachedDiskBackend,
                      ImageBackend imageBackend,
                      DiskBackend diskBackend,
                      TaskBackend taskBackend,
                      EntityLockBackend entityLockBackend,
                      TombstoneBackend tombstoneBackend,
                      LocalityBackend localityBackend,
                      FlavorBackend flavorBackend) {

    this.resourceTicketBackend = resourceTicketBackend;
    this.vmDao = vmDao;
    this.tagDao = tagDao;
    this.isoDao = isoDao;
    this.hostBackend = hostBackend;
    this.tagBackend = tagBackend;
    this.projectBackend = projectBackend;
    this.attachedDiskBackend = attachedDiskBackend;
    this.imageBackend = imageBackend;
    this.diskBackend = diskBackend;
    this.taskBackend = taskBackend;
    this.entityLockBackend = entityLockBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.localityBackend = localityBackend;
    this.flavorBackend = flavorBackend;
  }

  @Override
  @Transactional
  public List<Vm> filter(String projectId, Optional<String> name) throws ExternalException {
    ProjectEntity project = projectBackend.findById(projectId);
    List<VmEntity> vms;

    if (name.isPresent()) {
      vms = vmDao.listByName(name.get(), project);
    } else {
      vms = vmDao.findAll(project);
    }

    List<Vm> result = new ArrayList<>(vms.size());

    for (VmEntity vm : vms) {
      result.add(toApiRepresentation(vm));
    }

    return result;
  }

  @Override
  @Transactional
  public List<Vm> filterByProject(String projectId) throws ExternalException {
    return filter(projectId, Optional.<String>absent());
  }

  @Override
  @Transactional
  public List<Vm> filterByTag(String projectId, Tag tag) throws ExternalException {
    ProjectEntity project = projectBackend.findById(projectId);

    List<VmEntity> vms = vmDao.findByTag(tag.getValue(), project);

    List<Vm> result = new ArrayList<>(vms.size());
    for (VmEntity vm : vms) {
      result.add(toApiRepresentation(vm));
    }
    return result;
  }

  @Override
  public List<Vm> filterByFlavor(String flavorId) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  public List<Vm> filterByImage(String imageId) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  public List<Vm> filterByNetwork(String networkId) throws ExternalException {
    throw new NotImplementedException();
  }

  @Override
  @Transactional
  public String findDatastoreByVmId(String id) throws VmNotFoundException {
    Optional<VmEntity> vm = vmDao.findById(id);

    if (vm.isPresent()) {
      return vm.get().getDatastore();
    }
    throw new VmNotFoundException(id);
  }

  @Override
  @Transactional
  public Vm toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Override
  @Transactional
  public List<Task> getTasks(String id, Optional<String> state) throws ExternalException {
    VmEntity vm = findById(id);
    return taskBackend.filter(vm.getId(), vm.getKind(), state);
  }

  @Override
  @Transactional
  public void tombstone(VmEntity vm) throws ExternalException {
    Stopwatch tombstoneWatch = Stopwatch.createStarted();
    vmDao.update(vm);
    String resourceTickedId = projectBackend.findById(vm.getProjectId()).getResourceTicketId();
    ImageEntity image = null;
    if (StringUtils.isNotBlank(vm.getImageId())) {
      image = imageBackend.findById(vm.getImageId());
    }

    FlavorEntity flavor = null;
    if (StringUtils.isNotBlank(vm.getFlavorId())) {
      flavor = flavorBackend.getEntityById(vm.getFlavorId());
    }

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.returnQuota(resourceTickedId, new QuotaCost(vm.getCost()));
    resourceTicketWatch.stop();
    logger.info("VmSqlBackend.tombstone for Vm Id: {}, resourceTicket {}, returnQuota in {} milliseconds",
        vm.getId(),
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    tombstoneBackend.create(Vm.KIND, vm.getId());
    vmDao.delete(vm);
    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      attachedDiskBackend.deleteAttachedDiskById(attachedDisk.getId());
    }

    if (image != null &&
        ImageState.PENDING_DELETE.equals(image.getState()) &&
        vmDao.listByImage(image.getId()).isEmpty()) {
      imageBackend.tombstone(image);
    }

    if (flavor != null &&
        FlavorState.PENDING_DELETE.equals(flavor.getState()) &&
        vmDao.listByFlavor(flavor.getId()).isEmpty()) {
      flavorBackend.tombstone(flavor);
    }

    tombstoneWatch.stop();
    logger.info("VmSqlBackend.tombstone for Vm Id: {} took {} milliseconds",
        vm.getId(),
        tombstoneWatch.elapsed(TimeUnit.MILLISECONDS));
  }

  @Override
  @Transactional
  public void updateState(VmEntity vm, VmState state) throws DiskNotFoundException {
    vm.setState(state);
    vmDao.update(vm);

    if (VmState.ERROR.equals(state)) {
      for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
        BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
        if (EphemeralDisk.KIND.equals(disk.getKind())) {
          diskBackend.updateState(disk, DiskState.ERROR);
        }
      }
    }
  }

  @Override
  @Transactional
  public void updateState(VmEntity vm, VmState state, String agent, String agentIp, String datastoreId,
                          String datastoreName) throws
      ExternalException {
    vm.setAgent(agent);
    vm.setHost(agentIp);
    vm.setDatastore(datastoreId);
    vm.setDatastoreName(datastoreName);
    updateState(vm, state);
  }

  @Override
  @Transactional
  public TaskEntity addTag(String vmId, Tag tag) throws ExternalException {
    VmEntity vm = findById(vmId);
    return tagBackend.addTag(vm, tag);
  }

  @Override
  @Transactional
  public TaskEntity prepareVmCreate(String projectId, VmCreateSpec spec) throws ExternalException {
    VmEntity vm = create(projectId, spec);
    logger.info("created VM: {}", vm);
    TaskEntity task = createTask(vm);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmDelete(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);
    logger.info("deleting VM: {}", vm);
    TaskEntity task = deleteTask(vm);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmOperation(String vmId, Operation operation) throws ExternalException {
    VmEntity vm = findById(vmId);
    if (!VmOperation.VALID_OPERATIONS.contains(operation)) {
      throw new NotImplementedException();
    }

    logger.info("Operation {} on VM {}", operation, vm);
    TaskEntity task = operationTask(vm, operation);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmDiskOperation(String vmId, List<String> diskIds, Operation operation) throws
      ExternalException {
    VmEntity vm = findById(vmId);
    if (!VmDiskOperation.VALID_OPERATIONS.contains(operation)) {
      throw new NotImplementedException();
    }

    logger.info("Operation {} on VM {}", operation, vm);
    TaskEntity task = taskBackend.createQueuedTask(vm, operation);
    diskBackend.createVmDiskOperationStep(task, vm, diskIds, operation);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmAttachIso(String vmId, InputStream inputStream,
                                       String isoFileName) throws ExternalException {
    VmEntity vm = findById(vmId);
    if (!vm.getIsos().isEmpty()) {
      throw new IsoAlreadyAttachedException(vm.getIsos().get(0).getId());
    }
    String name = Paths.get(isoFileName).getFileName().toString();
    IsoEntity iso = createIsoEntity(name);

    logger.info("Operation {} on VM {}", Operation.ATTACH_ISO, vm);
    TaskEntity task = attachIsoTask(inputStream, vm, iso);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmDetachIso(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);

    logger.info("Operation {} on VM {}", Operation.DETACH_ISO, vm);
    TaskEntity task = detachIsoTask(vm);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmGetNetworks(String vmId) throws
      ExternalException {
    VmEntity vm = findById(vmId);
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.GET_NETWORKS);
    taskBackend.getStepBackend().createQueuedStep(task, vm, Operation.GET_NETWORKS);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmGetMksTicket(String vmId) throws
      ExternalException {
    VmEntity vm = findById(vmId);
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.GET_MKS_TICKET);
    taskBackend.getStepBackend().createQueuedStep(task, vm, Operation.GET_MKS_TICKET);
    logger.info("created Task: {}", task);
    return task;
  }

  @Override
  @Transactional
  public List<IsoEntity> isosAttached(VmEntity vmEntity) {
    vmDao.update(vmEntity);
    List<IsoEntity> isoEntityList = new ArrayList<>();
    if (vmEntity.getIsos().isEmpty()) {
      return isoEntityList;
    }
    for (IsoEntity iso : vmEntity.getIsos()) {
      isoEntityList.add(iso);
    }

    return isoEntityList;
  }

  @Override
  @Transactional
  public void detachIso(VmEntity vmEntity) throws ExternalException {
    List<IsoEntity> isoEntityList = isosAttached(vmEntity);
    if (isoEntityList.isEmpty()) {
      logger.warn("Vm {} attached Iso record is not in the database.", vmEntity.getId());
      return;
    }
    if (isoEntityList.size() > 1) {
      throw new MoreThanOneIsoAttachedException(vmEntity.getId());
    }
    IsoEntity isoEntity = isoEntityList.get(0);
    vmEntity.removeIso(isoEntity);
    tombstoneIsoEntity(isoEntity);
  }

  @Override
  @Transactional
  public List<Vm> getAllVmsOnHost(String hostId)
      throws ExternalException {
    List<Vm> vms = new ArrayList<>();
    HostEntity hostEntity = hostBackend.findById(hostId);
    List<VmEntity> vmEntities = vmDao.listAllByHostIp(hostEntity.getAddress());
    for (VmEntity vmEntity : vmEntities) {
      vms.add(toApiRepresentation(vmEntity));
    }
    return vms;
  }

  @Override
  @Transactional
  public int countVmsOnHost(HostEntity hostEntity) {
    return vmDao.countVmsByHostIp(hostEntity.getAddress());
  }

  @Override
  @Transactional
  public TaskEntity prepareSetMetadata(String id, Map<String, String> metadata) throws ExternalException {
    VmEntity vmEntity = findById(id);
    vmEntity.setMetadata(metadata);

    TaskEntity taskEntity = taskBackend.createCompletedTask(vmEntity, Operation.SET_METADATA);
    return taskEntity;
  }

  @Override
  @Transactional
  public TaskEntity prepareVmCreateImage(String vmId, ImageCreateSpec imageCreateSpec)
      throws ExternalException {
    VmEntity vm = findById(vmId);
    ImageEntity vmImage = imageBackend.findById(vm.getImageId());
    ImageEntity image = imageBackend.deriveImage(imageCreateSpec, vmImage);
    logger.info("created image: {}", image);
    TaskEntity task = createImageTask(vm, image, vmImage);
    logger.info("Task created: {}", task);
    return task;
  }

  // findById method should be nested inside other @Transactional method
  @Override
  public VmEntity findById(String id) throws VmNotFoundException {
    Optional<VmEntity> vm = vmDao.findById(id);

    if (vm.isPresent()) {
      return vm.get();
    }

    throw new VmNotFoundException(id);
  }

  @Override
  @Transactional
  public void updateIsoEntitySize(IsoEntity isoEntity, long size) {
    isoEntity.setSize(size);
    isoDao.update(isoEntity);
  }

  @Override
  @Transactional
  public void tombstoneIsoEntity(IsoEntity isoEntity) {
    tombstoneBackend.create(isoEntity.KIND, isoEntity.getId());
    isoDao.delete(isoEntity);
  }

  @Override
  @Transactional
  public void addIso(IsoEntity isoEntity, VmEntity vmEntity) throws VmNotFoundException {
    isoEntity.setVm(vmEntity);
    isoDao.update(isoEntity);
  }

  /**
   * Create VM entity in the database.
   */
  @VisibleForTesting
  @Transactional
  protected VmEntity create(String projectId, VmCreateSpec spec) throws ExternalException {
    Stopwatch createWatch = Stopwatch.createStarted();
    ProjectEntity project = projectBackend.findById(projectId);
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getFlavor(), Vm.KIND);
    if (!FlavorState.READY.equals(flavorEntity.getState())) {
      throw new InvalidFlavorStateException(
          String.format("Create vm using flavor with name: %s is in invalid state %s.",
              flavorEntity.getName(), flavorEntity.getState()));
    }

    VmEntity vm = new VmEntity();
    vm.setName(spec.getName());
    vm.setFlavorId(flavorEntity.getId());
    //The Hibernate requires it to be a separate list, because one list cannot be used by two entities.
    vm.setCost(new ArrayList<>(flavorEntity.getCost()));
    vm.setEnvironment(spec.getEnvironment());

    Set<TagEntity> tags = new HashSet<>();
    for (String tag : spec.getTags()) {
      tags.add(tagDao.findOrCreate(tag));
    }
    vm.setTags(tags);

    vm.setNetworks(spec.getNetworks());

    ImageEntity image = imageBackend.findById(spec.getSourceImageId());
    logger.debug("Image {} found for image name {}", image.getId(), image.getName());

    if (!ImageState.READY.equals(image.getState())) {
      throw new InvalidImageStateException(
          String.format("Image %s is in %s state", image.getId(), image.getState()));
    }
    vm.setImageId(image.getId());
    updateBootDiskCapacity(spec.getAttachedDisks(), image, vm);

    vm.setProjectId(project.getId());
    vm.setState(VmState.CREATING);

    String resourceTickedId = project.getResourceTicketId();

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.consumeQuota(resourceTickedId, new QuotaCost(vm.getCost()));
    resourceTicketWatch.stop();
    logger.info("VmSqlBackend.create for Vm Name: {}, resourceTicket {}, consumeQuota in {} milliseconds",
        vm.getName(),
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    vmDao.create(vm);

    vm.setAttachedDisks(attachedDiskBackend.createAttachedDisks(vm, spec.getAttachedDisks()));
    vm.setAffinities(localityBackend.create(vm, spec.getAffinities()));

    createWatch.stop();
    logger.info("VmSqlBackend.create for Vm Id: {} and Name: {} took {} milliseconds",
        vm.getId(),
        vm.getName(),
        createWatch.elapsed(TimeUnit.MILLISECONDS));

    return vm;
  }

  /**
   * Create VM creation task.
   */
  @VisibleForTesting
  @Transactional
  protected TaskEntity createTask(VmEntity vm) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.CREATE_VM);

    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, vm, Operation.RESERVE_RESOURCE);
    for (Throwable warning : vm.getWarnings()) {
      step.addWarning(warning);
    }

    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);
    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      if (!EphemeralDisk.KIND.equals(disk.getKind())) {
        throw new InvalidVmDisksSpecException("Persistent disk is not allowed to attach to VM during VM creation!");
      }
      entityList.add(disk);
    }

    step = taskBackend.getStepBackend().createQueuedStep(task, entityList, Operation.CREATE_VM);
    for (BaseEntity entity : entityList) {
      entityLockBackend.setStepLock(entity, step);
    }

    return task;
  }

  /**
   * Create VM deletion task.
   */
  @VisibleForTesting
  @Transactional
  protected TaskEntity deleteTask(VmEntity vm) throws ExternalException {
    EntityStateValidator.validateOperationState(vm, vm.getState(), Operation.DELETE_VM, VmState.OPERATION_PREREQ_STATE);

    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.DELETE_VM);

    List<BaseEntity> deleteVmEntityList = new ArrayList<>();
    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      if (!EphemeralDisk.KIND.equals(disk.getKind())) {
        throw new PersistentDiskAttachedException(disk, vm);
      }
      deleteVmEntityList.add(disk);
    }

    deleteVmEntityList.add(vm);
    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, deleteVmEntityList, Operation.DELETE_VM);
    for (BaseEntity entity : deleteVmEntityList) {
      entityLockBackend.setStepLock(entity, step);
    }

    return task;
  }

  /**
   * Create VM operation task.
   */
  @VisibleForTesting
  @Transactional
  protected TaskEntity operationTask(VmEntity vm, Operation op) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, op);
    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, vm, op);
    entityLockBackend.setStepLock(vm, step);
    return task;
  }

  @Transactional
  protected TaskEntity detachIsoTask(VmEntity vm) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vm, Operation.DETACH_ISO);
    logger.info("created Task: {}", task);

    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, vm, Operation.DETACH_ISO);
    entityLockBackend.setStepLock(vm, step);

    return task;
  }

  private Vm toApiRepresentation(VmEntity vmEntity) throws ExternalException {
    Vm vm = new Vm();

    vm.setId(vmEntity.getId());
    vm.setName(vmEntity.getName());
    FlavorEntity flavorEntity = flavorBackend.getEntityById(vmEntity.getFlavorId());
    vm.setFlavor(flavorEntity.getName());

    if (StringUtils.isNotBlank(vmEntity.getImageId())) {
      vm.setSourceImageId(vmEntity.getImageId());
    }

    vm.setState(vmEntity.getState());
    vm.setHost(vmEntity.getHost());
    vm.setDatastore(vmEntity.getDatastore());
    vm.setProjectId(vmEntity.getProjectId());

    List<AttachedDisk> disks = new ArrayList<>();
    Set<String> tags = new HashSet<>();

    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vmEntity.getId())) {
      AttachedDisk disk = new AttachedDisk();

      disk.setId(attachedDisk.getUnderlyingDiskId());
      disk.setKind(attachedDisk.getKind());
      BaseDiskEntity underlyingDisk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      disk.setName(underlyingDisk.getName());
      disk.setFlavor(underlyingDisk.getFlavorId());
      disk.setCapacityGb(underlyingDisk.getCapacityGb());
      disk.setBootDisk(attachedDisk.isBootDisk());
      disk.setState(underlyingDisk.getState().toString());

      disks.add(disk);
    }

    for (TagEntity tag : vmEntity.getTags()) {
      tags.add(tag.getValue());
    }

    vm.setAttachedDisks(disks);
    vm.setTags(tags);

    vm.setMetadata(vmEntity.getMetadata());

    for (IsoEntity isoEntity : vmEntity.getIsos()) {
      vm.addAttachedIso(isoToApiRepresentation(isoEntity));
    }

    return vm;
  }

  private TaskEntity attachIsoTask(InputStream inputStream, VmEntity vmEntity,
                                   IsoEntity isoEntity) throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(vmEntity, Operation.ATTACH_ISO);
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vmEntity);
    entityList.add(isoEntity);

    StepEntity step = taskBackend.getStepBackend().createQueuedStep(task, entityList, Operation.UPLOAD_ISO);
    step.createOrUpdateTransientResource(IsoUploadStepCmd.INPUT_STREAM, inputStream);
    entityLockBackend.setStepLock(isoEntity, step);
    entityLockBackend.setStepLock(vmEntity, step);

    taskBackend.getStepBackend().createQueuedStep(task, entityList, Operation.ATTACH_ISO);

    return task;
  }

  /**
   * Find boot disk and update capacityGb to be image size.
   */
  private void updateBootDiskCapacity(List<AttachedDiskCreateSpec> disks, ImageEntity image, VmEntity vm)
      throws InvalidVmDisksSpecException, InvalidEntityException {
    for (AttachedDiskCreateSpec disk : disks) {
      if (disk.isBootDisk()) {
        if (image.getSize() == null) {
          throw new InvalidEntityException(
              "Image " + image.getId() + " has null size",
              ImmutableList.of("Image " + image.toString() + " has null size"));
        }

        if (disk.getCapacityGb() != null) {
          vm.addWarning(new InvalidVmDisksSpecException("Specified boot disk capacityGb is not used"));
        }

        disk.setCapacityGb((int) (image.getSize() / GB_TO_BYTE_CONVERSION_RATIO));
        return;
      }
    }

    throw new InvalidVmDisksSpecException("No boot disk is specified in VM create Spec!");
  }

  /**
   * Create image of vm creation task.
   */
  private TaskEntity createImageTask(VmEntity vm, ImageEntity image, ImageEntity vmImage)
      throws ExternalException {
    TaskEntity task = taskBackend.createQueuedTask(image, Operation.CREATE_VM_IMAGE);

    List<BaseEntity> stepEntities = ImmutableList.of((BaseEntity) vm, image, vmImage);

    StepEntity step = taskBackend.getStepBackend()
        .createQueuedStep(task,
            stepEntities,
            Operation.CREATE_VM_IMAGE);
    entityLockBackend.setStepLock(vm, step);

    BackendHelpers.createReplicateImageStep(taskBackend, image, task);
    return task;
  }

  private Iso isoToApiRepresentation(IsoEntity isoEntity) {
    Iso iso = new Iso();
    iso.setId(isoEntity.getId());
    iso.setName(isoEntity.getName());
    iso.setSize(isoEntity.getSize());

    return iso;
  }

  private IsoEntity createIsoEntity(String name) {
    IsoEntity isoEntity = new IsoEntity();
    isoEntity.setName(name);

    return isoDao.create(isoEntity);
  }
}
