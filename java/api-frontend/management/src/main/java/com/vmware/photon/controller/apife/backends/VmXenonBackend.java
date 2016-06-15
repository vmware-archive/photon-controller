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
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Tag;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.api.VmDiskOperation;
import com.vmware.photon.controller.api.VmOperation;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.NotImplementedException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.commands.steps.IsoUploadStepCmd;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.DiskStateChecks;
import com.vmware.photon.controller.apife.entities.EntityStateValidator;
import com.vmware.photon.controller.apife.entities.FlavorEntity;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.IsoEntity;
import com.vmware.photon.controller.apife.entities.LocalityEntity;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.ProjectEntity;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.DiskNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAttachDisksException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidFlavorStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidImageStateException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmDisksSpecException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.apife.exceptions.external.IsoAlreadyAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.MoreThanOneIsoAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.PersistentDiskAttachedException;
import com.vmware.photon.controller.apife.exceptions.external.VmNotFoundException;
import com.vmware.photon.controller.apife.lib.QuotaCost;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * VmXenonBackend is performing VM operations such as create, delete, add tag etc.
 */
public class VmXenonBackend implements VmBackend {

  private static final Logger logger = LoggerFactory.getLogger(VmXenonBackend.class);
  private static final int GB_TO_BYTE_CONVERSION_RATIO = 1024 * 1024 * 1024;

  private final ApiFeXenonRestClient xenonClient;

  private final ResourceTicketBackend resourceTicketBackend;
  private final ProjectBackend projectBackend;
  private final FlavorBackend flavorBackend;
  private final ImageBackend imageBackend;
  private final AttachedDiskBackend attachedDiskBackend;
  private final TaskBackend taskBackend;
  private final DiskBackend diskBackend;
  private final HostBackend hostBackend;
  private final NetworkBackend networkBackend;
  private final TombstoneBackend tombstoneBackend;
  private final Boolean useVirtualNetwork;

  @Inject
  public VmXenonBackend(
      ApiFeXenonRestClient xenonClient,
      ResourceTicketBackend resourceTicketBackend,
                      ProjectBackend projectBackend,
                      AttachedDiskBackend attachedDiskBackend,
                      ImageBackend imageBackend,
                      DiskBackend diskBackend,
                      TaskBackend taskBackend,
                      FlavorBackend flavorBackend,
                      HostBackend hostBackend,
                      NetworkBackend networkBackend,
                      TombstoneBackend tombstoneBackend,
                      @Named("useVirtualNetwork") Boolean useVirtualNetwork) {
    this.xenonClient = xenonClient;
    xenonClient.start();

    this.resourceTicketBackend = resourceTicketBackend;
    this.projectBackend = projectBackend;
    this.attachedDiskBackend = attachedDiskBackend;
    this.imageBackend = imageBackend;
    this.diskBackend = diskBackend;
    this.taskBackend = taskBackend;
    this.flavorBackend = flavorBackend;
    this.hostBackend = hostBackend;
    this.networkBackend = networkBackend;
    this.tombstoneBackend = tombstoneBackend;
    this.useVirtualNetwork = useVirtualNetwork;
  }


  @Override
  public ResourceList<Vm> filter(String projectId, Optional<String> name, Optional<Integer> pageSize)
      throws ExternalException {
    projectBackend.findById(projectId);
    ResourceList<VmEntity> vmEntities;

    if (name.isPresent()) {
      vmEntities = filterVmEntities(
          Optional.of(projectId),
          Optional.<String>absent(),
          name,
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          pageSize);
    } else {
      vmEntities = filterVmEntities(
          Optional.of(projectId),
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          Optional.<String>absent(),
          pageSize);
    }

    return toApiRepresentation(vmEntities);
  }

  @Override
  public ResourceList<Vm> filterByProject(String projectId, Optional<Integer> pageSize) throws ExternalException {
    return filter(projectId, Optional.<String>absent(), pageSize);
  }

  @Override
  public ResourceList<Vm> filterByTag(String projectId, Tag tag, Optional<Integer> pageSize) throws ExternalException {
    projectBackend.findById(projectId);
    ResourceList<VmEntity> vmEntities = filterVmEntities(
        Optional.of(projectId),
        Optional.of(tag.getValue()),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        pageSize);


    return toApiRepresentation(vmEntities);
  }

  @Override
  public List<Vm> filterByFlavor(String flavorId) throws ExternalException {
    ResourceList<VmEntity> vms = filterVmEntities(
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.of(flavorId),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<Integer>absent());

    List<Vm> result = new ArrayList<>();

    if (vms != null) {
      for (VmEntity vm : vms.getItems()) {
        result.add(toApiRepresentation(vm));
      }
    }

    return result;
  }

  @Override
  public List<Vm> filterByImage(String imageId) throws ExternalException {
    ResourceList<VmEntity> vms = filterVmEntities(
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.of(imageId),
        Optional.<String>absent(),
        Optional.<Integer>absent());

    List<Vm> result = new ArrayList<>();
    for (VmEntity vm : vms.getItems()) {
      result.add(toApiRepresentation(vm));
    }

    return result;
  }

  @Override
  public List<Vm> filterByNetwork(String networkId) throws ExternalException {
    ResourceList<VmEntity> vms = filterVmEntities(
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.of(networkId),
        Optional.<Integer>absent());

    List<Vm> result = new ArrayList<>();
    for (VmEntity vmEntity : vms.getItems()) {
      result.add(toApiRepresentation(vmEntity));
    }

    return result;
  }

  @Override
  public ResourceList<Vm> getVmsPage(String pageLink) throws ExternalException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = xenonClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    ResourceList<VmService.State> vmStates = PaginationUtils.xenonQueryResultToResourceList(VmService.State.class,
        queryResult);

    List<Vm> vms = new ArrayList<>();
    for (VmService.State vmState : vmStates.getItems()) {
      vms.add(toApiRepresentation(toVmEntity(vmState)));
    }

    ResourceList<Vm> result = new ResourceList<>();
    result.setItems(vms);
    result.setNextPageLink(vmStates.getNextPageLink());
    result.setPreviousPageLink(vmStates.getPreviousPageLink());

    return result;
  }

  @Override
  public String findDatastoreByVmId(String id) throws VmNotFoundException {
    VmService.State vm = getVmById(id);
    return vm.datastore;
  }

  @Override
  public Vm toApiRepresentation(String id) throws ExternalException {
    return toApiRepresentation(findById(id));
  }

  @Override
  public void tombstone(VmEntity vm) throws ExternalException {
    Stopwatch tombstoneWatch = Stopwatch.createStarted();
    String resourceTickedId = projectBackend.findById(vm.getProjectId()).getResourceTicketId();
    ImageEntity image = null;
    if (StringUtils.isNotBlank(vm.getImageId())) {
      image = imageBackend.findById(vm.getImageId());
    }

    FlavorEntity flavor = null;
    if (StringUtils.isNotBlank(vm.getFlavorId())) {
      flavor = flavorBackend.getEntityById(vm.getFlavorId());
    }

    List<NetworkEntity> networkList = new LinkedList<>();
    if (null != vm.getNetworks()) {
      for (String network : vm.getNetworks()) {
        networkList.add(networkBackend.findById(network));
      }
    }

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.returnQuota(resourceTickedId, new QuotaCost(vm.getCost()));
    resourceTicketWatch.stop();
    logger.info("VmXenonBackend.tombstone for Vm Id: {}, resourceTicket {}, returnQuota in {} milliseconds",
        vm.getId(),
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    tombstoneBackend.create(Vm.KIND, vm.getId());

    xenonClient.delete(VmServiceFactory.SELF_LINK + "/" + vm.getId(), new VmService.State());

    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      attachedDiskBackend.deleteAttachedDiskById(attachedDisk.getId());
    }

    if (image != null &&
        ImageState.PENDING_DELETE.equals(image.getState())) {
      imageBackend.tombstone(image);
    }

    if (flavor != null &&
        FlavorState.PENDING_DELETE.equals(flavor.getState())) {
      flavorBackend.tombstone(flavor);
    }

    for (NetworkEntity network : networkList) {
      if (NetworkState.PENDING_DELETE.equals(network.getState())) {
        networkBackend.tombstone(network);
      }
    }

    tombstoneWatch.stop();
    logger.info("VmXenonBackend.tombstone for Vm Id: {} took {} milliseconds",
        vm.getId(),
        tombstoneWatch.elapsed(TimeUnit.MILLISECONDS));
  }

  @Override
  public void updateState(VmEntity vmEntity, VmState state)
      throws VmNotFoundException, DiskNotFoundException {
    VmService.State vm = new VmService.State();
    vm.vmState = state;
    patchVmService(vmEntity.getId(), vm);
    vmEntity.setState(state);

    if (VmState.ERROR.equals(state)) {
      for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vmEntity.getId())) {
        BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
        if (EphemeralDisk.KIND.equals(disk.getKind())) {
          diskBackend.updateState(disk, DiskState.ERROR);
        }
      }
    }
  }

  @Override
  public void updateState(VmEntity vmEntity, VmState state, String agent,
                          String agentIp, String datastoreId, String datastoreName)
      throws ExternalException {
    VmService.State vm = new VmService.State();
    vm.vmState = state;
    vm.agent = agent;
    vm.host = agentIp;
    vm.datastore = datastoreId;
    vm.datastoreName = datastoreName;

    patchVmService(vmEntity.getId(), vm);
    vmEntity.setState(state);
    vmEntity.setAgent(agent);
    vmEntity.setHost(agentIp);
    vmEntity.setDatastore(datastoreId);
    vmEntity.setDatastoreName(datastoreName);
  }

  @Override
  public TaskEntity addTag(String vmId, Tag tag) throws ExternalException {
    VmService.State vm = getVmById(vmId);
    if (vm.tags == null) {
      vm.tags = new HashSet<>();
    }
    vm.tags.add(tag.getValue());
    patchVmService(vmId, vm);
    VmEntity vmEntity = toVmEntity(vm);
    return taskBackend.createCompletedTask(vmEntity, Operation.ADD_TAG);
  }

  @Override
  public TaskEntity prepareVmCreate(String projectId, VmCreateSpec spec) throws ExternalException {
    ProjectEntity project = projectBackend.findById(projectId);
    VmEntity vm = create(project, spec);
    logger.info("created VM: {}", vm);
    TaskEntity task = createTask(project, vm);
    return task;
  }

  @Override
  public TaskEntity prepareVmDelete(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);
    logger.info("deleting VM: {}", vm);
    TaskEntity task = deleteTask(vm);
    return task;
  }

  @Override
  public TaskEntity prepareVmOperation(String vmId, Operation operation) throws ExternalException {
    VmEntity vm = findById(vmId);
    if (!VmOperation.VALID_OPERATIONS.contains(operation)) {
      throw new NotImplementedException();
    }

    logger.info("Operation {} on VM {}", operation, vm);
    TaskEntity task = operationTask(vm, operation);
    return task;
  }

  @Override
  public TaskEntity prepareVmDiskOperation(String vmId, List<String> diskIds, Operation operation)
      throws ExternalException {
    VmEntity vm = findById(vmId);
    String projectId = vm.getProjectId();
    if (!VmDiskOperation.VALID_OPERATIONS.contains(operation)) {
      throw new NotImplementedException();
    }

    logger.info("Operation {} on VM {}", operation, vm);

    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    // Add vm entity
    entityList.add(vm);
    // Add disk entities
    for (String diskId : diskIds) {
      BaseDiskEntity disk = diskBackend.find(PersistentDisk.KIND, diskId);

      // Check if disk is a valid state for the operation
      DiskStateChecks.checkOperationState(disk, operation);

      // Check if disk and vm is in the same project
      if (!projectId.equals(disk.getProjectId())) {
        throw new InvalidAttachDisksException(String.format("Disk %s and Vm %s are not in the same project, " +
            "can not attach.", disk.getId(), vm.getId()));
      }
      entityList.add(disk);
    }

    /*
     * If we make it to this point all disks have been found
     * and they are all detached (otherwise find() and checkOperationState()
     * would have thrown exceptions)
     */

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(operation);

    TaskEntity task = taskBackend.createTaskWithSteps(vm, operation, false, stepEntities);
    for (BaseEntity entity : entityList) {
      task.getToBeLockedEntities().add(entity);
    }

    return task;
  }

  @Override
  public TaskEntity prepareVmAttachIso(String vmId, InputStream inputStream, String isoFileName)
      throws ExternalException {
    VmEntity vm = findById(vmId);
    if (!vm.getIsos().isEmpty()) {
      throw new IsoAlreadyAttachedException(vm.getIsos().get(0).getId());
    }
    String name = Paths.get(isoFileName).getFileName().toString();
    IsoEntity iso = createIsoEntity(name);

    logger.info("Operation {} on VM {}", Operation.ATTACH_ISO, vm);
    TaskEntity task = attachIsoTask(inputStream, vm, iso);
    return task;
  }

  @Override
  public TaskEntity prepareVmDetachIso(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);

    logger.info("Operation {} on VM {}", Operation.DETACH_ISO, vm);
    TaskEntity task = detachIsoTask(vm);
    return task;
  }

  @Override
  public TaskEntity prepareVmGetNetworks(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.GET_NETWORKS);

    TaskEntity task = taskBackend.createTaskWithSteps(vm, Operation.GET_NETWORKS, false, stepEntities);
    return task;
  }

  @Override
  public TaskEntity prepareVmGetMksTicket(String vmId) throws ExternalException {
    VmEntity vm = findById(vmId);
    if (!VmState.STARTED.equals(vm.getState())) {
      throw new InvalidVmStateException("Get Mks Ticket is not allowed on vm that is not powered on.");
    }
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.GET_MKS_TICKET);

    TaskEntity task = taskBackend.createTaskWithSteps(vm, Operation.GET_MKS_TICKET, false, stepEntities);
    return task;
  }

  @Override
  public List<IsoEntity> isosAttached(VmEntity vmEntity) throws VmNotFoundException {
    vmEntity = findById(vmEntity.getId());
    return vmEntity.getIsos();
  }

  @Override
  public void detachIso(VmEntity vmEntity) throws ExternalException {
    List<IsoEntity> isoEntityList = isosAttached(vmEntity);
    if (isoEntityList.isEmpty()) {
      logger.warn("Vm {} attached Iso record is not in the database.", vmEntity.getId());
      return;
    }
    if (isoEntityList.size() > 1) {
      throw new MoreThanOneIsoAttachedException(vmEntity.getId());
    }
    VmService.State vm = new VmService.State();
    vm.isos = new ArrayList<>();
    patchVmService(vmEntity.getId(), vm);
    vmEntity.setIsos(new ArrayList<>());
  }

  @Override
  public ResourceList<Vm> getAllVmsOnHost(String hostId, Optional<Integer> pageSize) throws ExternalException {
    HostEntity hostEntity = hostBackend.findById(hostId);

    ResourceList<VmEntity> vmEntities = filterVmEntities(
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.of(hostEntity.getAddress()),
        Optional.<String>absent(),
        Optional.<String>absent(),
        Optional.<String>absent(),
        pageSize);

    return toApiRepresentation(vmEntities);
  }

  @Override
  public int countVmsOnHost(HostEntity hostEntity) throws ExternalException {
    ResourceList<Vm> vms = getAllVmsOnHost(hostEntity.getId(), Optional.<Integer>absent());
    if (vms.getItems() != null) {
      return vms.getItems().size();
    }
    return 0;
  }

  @Override
  public TaskEntity prepareSetMetadata(String id, Map<String, String> metadata) throws ExternalException {
    VmService.State vmPatch = new VmService.State();
    vmPatch.metadata = metadata;
    patchVmService(id, vmPatch);

    TaskEntity taskEntity = taskBackend.createCompletedTask(findById(id), Operation.SET_METADATA);
    return taskEntity;
  }

  @Override
  public TaskEntity prepareVmCreateImage(String vmId, ImageCreateSpec imageCreateSpec) throws ExternalException {
    VmEntity vm = findById(vmId);
    ImageEntity vmImage = imageBackend.findById(vm.getImageId());
    ImageEntity image = imageBackend.deriveImage(imageCreateSpec, vmImage);
    logger.info("created image: {}", image);
    TaskEntity task = createImageTask(vm, image, vmImage);
    return task;
  }

  @Override
  public VmEntity findById(String id) throws VmNotFoundException {
    return toVmEntity(getVmById(id));
  }

  @Override
  public void updateIsoEntitySize(IsoEntity isoEntity, long size) {
    isoEntity.setSize(size);
  }

  @Override
  public void tombstoneIsoEntity(IsoEntity isoEntity) {
    // Do nothing here for the Xenon implementation
  }

  @Override
  public void addIso(IsoEntity isoEntity, VmEntity vmEntity) throws VmNotFoundException {
    VmService.State vmPatch = new VmService.State();
    vmPatch.isos = new ArrayList<>();
    vmPatch.isos.add(isoToApiRepresentation(isoEntity));

    patchVmService(vmEntity.getId(), vmPatch);
  }

  private VmEntity toVmEntity(VmService.State vm) {
    VmEntity vmEntity = new VmEntity();
    String vmId = ServiceUtils.getIDFromDocumentSelfLink(vm.documentSelfLink);
    vmEntity.setId(vmId);
    vmEntity.setName(vm.name);
    vmEntity.setProjectId(vm.projectId);
    vmEntity.setFlavorId(vm.flavorId);
    vmEntity.setImageId(vm.imageId);
    vmEntity.setState(vm.vmState);

    if (null != vm.affinities) {
      for (LocalitySpec localitySpec : vm.affinities) {
        LocalityEntity localityEntity = new LocalityEntity();
        localityEntity.setResourceId(localitySpec.getId());
        localityEntity.setKind(localitySpec.getKind());
        vmEntity.getAffinities().add(localityEntity);
      }
    }

    if (null != vm.isos) {
      for (Iso iso : vm.isos) {
        IsoEntity isoEntity = new IsoEntity();
        isoEntity.setId(iso.getId());
        isoEntity.setName(iso.getName());
        isoEntity.setSize(iso.getSize());
        vmEntity.addIso(isoEntity);
      }
    }

    if (null != vm.metadata) {
      vmEntity.setMetadata(vm.metadata);
    }

    if (null != vm.networks) {
      vmEntity.setNetworks(getNetworkIds(vm.networks));
    }

    if (null != vm.tags) {
      for (String tagText : vm.tags) {
        TagEntity tagEntity = new TagEntity();
        tagEntity.setValue(tagText);
        vmEntity.getTags().add(tagEntity);
      }
    }

    vmEntity.setAgent(vm.agent);
    vmEntity.setHost(vm.host);
    vmEntity.setDatastore(vm.datastore);
    vmEntity.setDatastoreName(vm.datastoreName);

    if (null != vm.cost) {
      for (QuotaLineItem quotaLineItem : vm.cost) {
        QuotaLineItemEntity quotaLineItemEntity = new QuotaLineItemEntity();
        quotaLineItemEntity.setKey(quotaLineItem.getKey());
        quotaLineItemEntity.setValue(quotaLineItem.getValue());
        quotaLineItemEntity.setUnit(quotaLineItem.getUnit());
        vmEntity.getCost().add(quotaLineItemEntity);
      }
    }

    return vmEntity;
  }

  private VmService.State getVmById(String id) throws VmNotFoundException {
    com.vmware.xenon.common.Operation result;
    try {
      result = xenonClient.get(VmServiceFactory.SELF_LINK + "/" + id);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new VmNotFoundException(id);
    }

    return result.getBody(VmService.State.class);
  }

  private VmEntity create(ProjectEntity project, VmCreateSpec spec) throws ExternalException {
    Stopwatch createWatch = Stopwatch.createStarted();
    FlavorEntity flavorEntity = flavorBackend.getEntityByNameAndKind(spec.getFlavor(), Vm.KIND);
    if (!FlavorState.READY.equals(flavorEntity.getState())) {
      throw new InvalidFlavorStateException(
          String.format("Create vm using flavor with name: %s is in invalid state %s.",
              flavorEntity.getName(), flavorEntity.getState()));
    }

    VmService.State vm = new VmService.State();

    vm.name = spec.getName();
    vm.flavorId = flavorEntity.getId();
    List<QuotaLineItemEntity> cost = new ArrayList<>(flavorEntity.getCost());
    for (QuotaLineItemEntity quotaLineItemEntity : cost) {
      QuotaLineItem quotaLineItem = new QuotaLineItem();
      quotaLineItem.setKey(quotaLineItemEntity.getKey());
      quotaLineItem.setValue(quotaLineItemEntity.getValue());
      quotaLineItem.setUnit(quotaLineItemEntity.getUnit());

      if (vm.cost == null) {
        vm.cost = new ArrayList<>();
      }
      vm.cost.add(quotaLineItem);
    }

    vm.tags = new HashSet<>();
    if (spec.getTags() != null) {
      for (String tagText : spec.getTags()) {
        vm.tags.add(tagText);
      }
    }

    vm.networks = getNetworkInfo(spec.getNetworks());

    ImageEntity image = imageBackend.findById(spec.getSourceImageId());

    if (!ImageState.READY.equals(image.getState())) {
      throw new InvalidImageStateException(
          String.format("Image %s is in %s state", image.getId(), image.getState()));
    }

    vm.imageId = image.getId();
    List<Throwable> warnings = new ArrayList<>();
    updateBootDiskCapacity(spec.getAttachedDisks(), image, warnings);

    vm.projectId = project.getId();
    vm.vmState = VmState.CREATING;

    String resourceTickedId = project.getResourceTicketId();

    Stopwatch resourceTicketWatch = Stopwatch.createStarted();
    resourceTicketBackend.consumeQuota(resourceTickedId, new QuotaCost(cost));
    resourceTicketWatch.stop();
    logger.info("VmXenonBackend.create for Vm Name: {}, resourceTicket {}, consumeQuota in {} milliseconds",
        vm.name,
        resourceTickedId,
        resourceTicketWatch.elapsed(TimeUnit.MILLISECONDS));

    vm.affinities = spec.getAffinities();

    com.vmware.xenon.common.Operation createOperation = xenonClient.post(VmServiceFactory.SELF_LINK, vm);
    VmService.State createdVm = createOperation.getBody(VmService.State.class);

    VmEntity vmEntity = toVmEntity(createdVm);

    //set transient properties of vm entity
    vmEntity.setAttachedDisks(attachedDiskBackend.createAttachedDisks(vmEntity, spec.getAttachedDisks()));
    vmEntity.setWarnings(warnings);
    vmEntity.setEnvironment(spec.getEnvironment());

    createWatch.stop();
    logger.info("VmXenonBackend.create for Vm Id: {} and name: {} took {} milliseconds",
        vmEntity.getId(),
        vm.name,
        createWatch.elapsed(TimeUnit.MILLISECONDS));

    return vmEntity;
  }

  private TaskEntity createTask(ProjectEntity project, VmEntity vm) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.addTransientResourceEntity(project);
    step.setOperation(Operation.RESERVE_RESOURCE);
    for (Throwable warning : vm.getWarnings()) {
      step.addWarning(warning);
    }

    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      if (!EphemeralDisk.KIND.equals(disk.getKind())) {
        throw new InvalidVmDisksSpecException("Persistent disk is not allowed to attach to VM during VM creation!");
      }
      entityList.add(disk);
    }

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.CREATE_VM);

    // Conditional step. If virtual network is to be used, we need to connect the vm
    // with the logical switch.
    if (useVirtualNetwork) {
      step = new StepEntity();
      step.setOperation(Operation.CONNECT_VM_SWITCH);

      stepEntities.add(step);
    }

    TaskEntity task = taskBackend.createTaskWithSteps(vm, Operation.CREATE_VM, false, stepEntities);
    task.getToBeLockedEntities().add(vm);

    return task;
  }

  /**
   * Find boot disk and update capacityGb to be image size.
   */
  @VisibleForTesting
  protected static void updateBootDiskCapacity(List<AttachedDiskCreateSpec> disks, ImageEntity image,
                                        List<Throwable> warnings)
      throws InvalidVmDisksSpecException, InvalidImageStateException {
    for (AttachedDiskCreateSpec disk : disks) {
      if (disk.isBootDisk()) {
        if (image.getSize() == null) {
          throw new InvalidImageStateException(
              "Image " + image.getId() + " has null size");
        }

        if (disk.getCapacityGb() != null) {
          warnings.add(new InvalidVmDisksSpecException("Specified boot disk capacityGb is not used"));
        }

        disk.setCapacityGb((int) (image.getSize() / GB_TO_BYTE_CONVERSION_RATIO));
        return;
      }
    }

    throw new InvalidVmDisksSpecException("No boot disk is specified in VM create Spec!");
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

  private ResourceList<Vm> toApiRepresentation(ResourceList<VmEntity> vmEntities) throws ExternalException {
    ResourceList<Vm> result = new ResourceList<>();

    List<Vm> vms = new ArrayList<>();
    for (VmEntity vmEntity : vmEntities.getItems()) {
      vms.add(toApiRepresentation(vmEntity));
    }

    result.setItems(vms);
    result.setNextPageLink(vmEntities.getNextPageLink());
    result.setPreviousPageLink(vmEntities.getPreviousPageLink());

    return result;
  }

  private ResourceList<VmEntity> filterVmEntities(
      Optional<String> projectId, Optional<String> tag, Optional<String> name, Optional<String> host,
      Optional<String> flavorId, Optional<String> imageId, Optional<String> networkId, Optional<Integer> pageSize) {

    ResourceList<VmEntity> vmEntityList = new ResourceList<>();
    ResourceList<VmService.State> vms = filterVmDocuments(projectId, tag, name, host, flavorId,
        imageId, networkId, pageSize);
    vmEntityList.setItems(vms.getItems().stream()
        .map(vm -> toVmEntity(vm))
        .collect(Collectors.toList())
    );
    vmEntityList.setNextPageLink(vms.getNextPageLink());
    vmEntityList.setPreviousPageLink(vms.getPreviousPageLink());

    return vmEntityList;
  }

  private ResourceList<VmService.State> filterVmDocuments(
      Optional<String> projectId, Optional<String> tag, Optional<String> name, Optional<String> host,
      Optional<String> flavorId, Optional<String> imageId, Optional<String> networkId, Optional<Integer> pageSize) {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (projectId.isPresent()) {
      termsBuilder.put("projectId", projectId.get());
    }

    if (tag.isPresent()) {
      String key = QueryTask.QuerySpecification.buildCollectionItemName(
          VmService.State.FIELD_NAME_TAGS);
      termsBuilder.put(key, tag.get());
    }

    if (name.isPresent()) {
      termsBuilder.put("name", name.get());
    }

    if (host.isPresent()) {
      termsBuilder.put("host", host.get());
    }

    if (flavorId.isPresent()) {
      termsBuilder.put("flavorId", flavorId.get());
    }

    if (imageId.isPresent()) {
      termsBuilder.put("imageId", imageId.get());
    }

    if (networkId.isPresent()) {
      String key = QueryTask.QuerySpecification.buildCollectionItemName(
          VmService.State.FIELD_NAME_NETWORKS);
      termsBuilder.put(key, networkId.get());
    }

    ServiceDocumentQueryResult queryResult = xenonClient.queryDocuments(VmService.State.class, termsBuilder.build(),
        pageSize, true);
    return PaginationUtils.xenonQueryResultToResourceList(VmService.State.class, queryResult);
  }

  private TaskEntity deleteTask(VmEntity vm) throws ExternalException {
    EntityStateValidator.validateOperationState(vm, vm.getState(), Operation.DELETE_VM, VmState.OPERATION_PREREQ_STATE);

    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    for (AttachedDiskEntity attachedDisk : attachedDiskBackend.findByVmId(vm.getId())) {
      BaseDiskEntity disk = diskBackend.find(attachedDisk.getKind(), attachedDisk.getUnderlyingDiskId());
      if (!EphemeralDisk.KIND.equals(disk.getKind())) {
        throw new PersistentDiskAttachedException(disk, vm);
      }
      entityList.add(disk);
    }
    entityList.add(vm);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DELETE_VM);

    TaskEntity task = taskBackend.createTaskWithSteps(vm, Operation.DELETE_VM, false, stepEntities);
    task.getToBeLockedEntities().add(vm);
    return task;
  }

  private TaskEntity operationTask(VmEntity vm, Operation op) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(op);

    TaskEntity task = taskBackend.createTaskWithSteps(vm, op, false, stepEntities);
    task.getToBeLockedEntities().add(vm);
    return task;
  }

  private TaskEntity attachIsoTask(InputStream inputStream, VmEntity vmEntity,
                                   IsoEntity isoEntity) throws ExternalException {

    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vmEntity);
    entityList.add(isoEntity);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.createOrUpdateTransientResource(IsoUploadStepCmd.INPUT_STREAM, inputStream);
    step.addResources(entityList);
    step.setOperation(Operation.UPLOAD_ISO);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.ATTACH_ISO);

    TaskEntity task = taskBackend.createTaskWithSteps(vmEntity, Operation.ATTACH_ISO, false, stepEntities);

    task.getToBeLockedEntities().add(isoEntity);
    task.getToBeLockedEntities().add(vmEntity);

    return task;
  }

  private TaskEntity detachIsoTask(VmEntity vmEntity) throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vmEntity);
    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.DETACH_ISO);

    TaskEntity task = taskBackend.createTaskWithSteps(vmEntity, Operation.DETACH_ISO, false, stepEntities);
    task.getToBeLockedEntities().add(vmEntity);

    return task;
  }

  private TaskEntity createImageTask(VmEntity vm, ImageEntity image, ImageEntity vmImage)
      throws ExternalException {
    List<StepEntity> stepEntities = new ArrayList<>();
    List<BaseEntity> entityList = new ArrayList<>();
    entityList.add(vm);
    entityList.add(image);
    entityList.add(vmImage);

    StepEntity step = new StepEntity();
    stepEntities.add(step);
    step.addResources(entityList);
    step.setOperation(Operation.CREATE_VM_IMAGE);

    step = new StepEntity();
    stepEntities.add(step);
    step.addResource(image);
    step.setOperation(Operation.REPLICATE_IMAGE);

    TaskEntity task = taskBackend.createTaskWithSteps(image, Operation.CREATE_VM_IMAGE, false, stepEntities);
    task.getToBeLockedEntities().add(vm);

    return task;
  }

  private void patchVmService(String vmId, VmService.State vmServiceState)
      throws VmNotFoundException {
    try {
      xenonClient.patch(VmServiceFactory.SELF_LINK + "/" + vmId, vmServiceState);
    } catch (DocumentNotFoundException e) {
      throw new VmNotFoundException(vmId);
    }
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
    isoEntity.setId(UUID.randomUUID().toString());
    isoEntity.setName(name);

    return isoEntity;
  }

  private List<VmService.State.NetworkInfo> getNetworkInfo(List<String> networks) {
    List<VmService.State.NetworkInfo> networkInfoList = new ArrayList<VmService.State.NetworkInfo>();

    for (String id : networks) {
      VmService.State.NetworkInfo networkInfo = new VmService.State.NetworkInfo();
      networkInfo.id = id;
    }

    return networkInfoList;
  }

  private List<String> getNetworkIds(List<VmService.State.NetworkInfo> networkInfoList) {
    List<String> networks = new ArrayList<String>();

    for (VmService.State.NetworkInfo networkInfo : networkInfoList) {
      networks.add(networkInfo.id);
    }

    return networks;
  }
}
