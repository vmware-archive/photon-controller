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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.AttachedDiskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.AttachedDiskServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * AttachedDiskXenonBackend is performing attach disk and detach disk operations.
 */
public class AttachedDiskXenonBackend implements AttachedDiskBackend {
  private static final Logger logger = LoggerFactory.getLogger(AttachedDiskXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;
  private final DiskBackend diskBackend;

  @Inject
  public AttachedDiskXenonBackend(ApiFeXenonRestClient xenonClient, DiskBackend diskBackend) {
    this.xenonClient = xenonClient;
    this.diskBackend = diskBackend;
    this.xenonClient.start();
  }

  @Override
  public void attachDisks(VmEntity vm, List<PersistentDiskEntity> disks) {
    checkNotNull(vm);
    checkNotNull(vm.getId());

    for (PersistentDiskEntity disk : disks) {
      vm.getAttachedDisks().add(createAttachedDisk(vm, disk));
    }
  }

  @Override
  public void deleteAttachedDisks(VmEntity vm, List<PersistentDiskEntity> disks) throws ExternalException {
    List<AttachedDiskEntity> attachedDiskEntities = new ArrayList<>();
    for (PersistentDiskEntity disk : disks) {
      // Lookup attached disk entity
      AttachedDiskEntity attachedDiskEntity = toAttachedDiskEntity(findByDiskId(PersistentDisk.KIND, disk.getId()),
          disk);
      if (attachedDiskEntity != null) {
        attachedDiskEntities.add(attachedDiskEntity);
      }
    }

    vm.removeAttachedDisks(attachedDiskEntities);

    for (AttachedDiskEntity attachedDisk : attachedDiskEntities) {
      deleteAttachedDisk(PersistentDisk.KIND, attachedDisk.getPersistentDiskId());
    }
  }

  @Override
  public List<AttachedDiskEntity> createAttachedDisks(VmEntity vm, List<AttachedDiskCreateSpec> specs) throws
      ExternalException {
    List<AttachedDiskEntity> attachments = new ArrayList<>();
    for (AttachedDiskCreateSpec spec : specs) {
      attachments.add(createAttachedDisk(vm, spec));
    }
    return attachments;
  }

  @Override
  public void deleteAttachedDisk(String kind, String diskId) throws ExternalException {
    diskBackend.find(kind, diskId);

    AttachedDiskService.State attachedDiskState = findByDiskId(kind, diskId);
    if (attachedDiskState == null) {
      logger.info("Disk {} not attached", diskId);
      return;
    }

    String id = ServiceUtils.getIDFromDocumentSelfLink(attachedDiskState.documentSelfLink);
    xenonClient.delete(AttachedDiskServiceFactory.SELF_LINK + "/" + id, new AttachedDiskService.State());
    logger.info("AttachedDisk with diskId {} has been cleared", id);
  }

  @Override
  public AttachedDiskEntity findAttachedDisk(BaseDiskEntity diskEntity) {
    AttachedDiskService.State state = findByDiskId(diskEntity.getKind(), diskEntity.getId());
    return toAttachedDiskEntity(state, diskEntity);
  }

  @Override
  public List<AttachedDiskEntity> findByVmId(String vmId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("vmId", vmId);

    List<AttachedDiskService.State> attachedDisks = xenonClient.queryDocuments(
        AttachedDiskService.State.class, termsBuilder.build());
    List<AttachedDiskEntity> attachedDiskEntities = new ArrayList<>();
    for (AttachedDiskService.State state : attachedDisks) {
      attachedDiskEntities.add(toAttachedDiskEntity(state, null));
    }
    return attachedDiskEntities;
  }

  @Override
  public void deleteAttachedDiskById(String attachedDiskId) {
    xenonClient.delete(AttachedDiskServiceFactory.SELF_LINK + "/" + attachedDiskId,
        new AttachedDiskService.State());
    logger.info("AttachedDisk with id {} has been cleared", attachedDiskId);
  }

  private AttachedDiskEntity createAttachedDisk(VmEntity vm, AttachedDiskCreateSpec spec)
      throws ExternalException {
    String projectId = vm.getProjectId();

    AttachedDiskService.State state = new AttachedDiskService.State();
    state.bootDisk = spec.isBootDisk();
    state.vmId = vm.getId();
    state.kind = spec.getKind();
    BaseDiskEntity diskEntity = diskBackend.create(projectId, spec);
    switch (diskEntity.getKind()) {
      case EphemeralDisk.KIND:
        state.ephemeralDiskId = diskEntity.getId();
        break;
      default:
        throw new IllegalArgumentException("Create Vm can only attach Ephemeral disk, " +
            "but got: " + diskEntity.getKind());
    }
    com.vmware.xenon.common.Operation result = xenonClient.post(AttachedDiskServiceFactory.SELF_LINK, state);
    AttachedDiskService.State createdState = result.getBody(AttachedDiskService.State.class);

    AttachedDiskEntity attachedDiskEntity = toAttachedDiskEntity(createdState, diskEntity);
    logger.info("AttachedDisk {} has been created", attachedDiskEntity.getId());

    return attachedDiskEntity;
  }

  private AttachedDiskEntity createAttachedDisk(VmEntity vm, PersistentDiskEntity persistentDiskEntity) {
    AttachedDiskService.State state = new AttachedDiskService.State();
    state.bootDisk = false;
    state.vmId = vm.getId();
    state.kind = persistentDiskEntity.getKind();
    state.persistentDiskId = persistentDiskEntity.getId();

    com.vmware.xenon.common.Operation result = xenonClient.post(AttachedDiskServiceFactory.SELF_LINK, state);
    AttachedDiskService.State createdState = result.getBody(AttachedDiskService.State.class);

    String id = ServiceUtils.getIDFromDocumentSelfLink(createdState.documentSelfLink);
    AttachedDiskEntity attachedDiskEntity = new AttachedDiskEntity();
    attachedDiskEntity.setId(id);
    attachedDiskEntity.setVmId(vm.getId());
    attachedDiskEntity.setUnderlyingDiskIdAndKind(persistentDiskEntity);
    attachedDiskEntity.setBootDisk(createdState.bootDisk);

    logger.info("AttachedDisk {} has been created", persistentDiskEntity.getId());

    return attachedDiskEntity;
  }

  private AttachedDiskService.State findByDiskId(String kind, String diskId) {
    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    switch (kind) {
      case PersistentDisk.KIND:
        termsBuilder.put("persistentDiskId", diskId);
        break;
      case EphemeralDisk.KIND:
        termsBuilder.put("ephemeralDiskId", diskId);
        break;
      default:
        throw new IllegalArgumentException("Unknown disk kind: " + kind);
    }

    List<AttachedDiskService.State> attachedDisks = xenonClient.queryDocuments(
        AttachedDiskService.State.class, termsBuilder.build());

    if (attachedDisks.isEmpty()) {
      return null;
    }
    checkArgument(attachedDisks.size() == 1, "Disk id should be unique.");
    return attachedDisks.get(0);
  }

  private AttachedDiskEntity toAttachedDiskEntity(AttachedDiskService.State state, BaseDiskEntity diskEntity) {
    if (state == null) {
      return null;
    }
    AttachedDiskEntity attachedDiskEntity = new AttachedDiskEntity();
    String id = ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink);
    attachedDiskEntity.setId(id);
    attachedDiskEntity.setKind(state.kind);
    attachedDiskEntity.setVmId(state.vmId);
    attachedDiskEntity.setBootDisk(state.bootDisk);
    attachedDiskEntity.setEphemeralDiskId(state.ephemeralDiskId);
    attachedDiskEntity.setPersistentDiskId(state.persistentDiskId);
    if (diskEntity != null) {
      attachedDiskEntity.setUnderlyingDiskIdAndKind(diskEntity);
    }

    return attachedDiskEntity;
  }
}
