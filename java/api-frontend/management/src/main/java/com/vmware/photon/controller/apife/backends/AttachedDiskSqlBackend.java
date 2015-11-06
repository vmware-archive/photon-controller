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
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.db.dao.AttachedDiskDao;
import com.vmware.photon.controller.apife.db.dao.VmDao;
import com.vmware.photon.controller.apife.entities.AttachedDiskEntity;
import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.PersistentDiskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * AttachedDiskSqlBackend is performing attach disk and detach disk operations.
 */
@Singleton
public class AttachedDiskSqlBackend implements AttachedDiskBackend {

  private final AttachedDiskDao attachedDiskDao;
  private final VmDao vmDao;
  private final DiskBackend diskBackend;

  @Inject
  public AttachedDiskSqlBackend(AttachedDiskDao attachedDiskDao,
                                VmDao vmDao,
                                DiskBackend diskBackend) {
    this.attachedDiskDao = attachedDiskDao;
    this.vmDao = vmDao;
    this.diskBackend = diskBackend;
  }

  /**
   * Create attached disks in DB, VmEntity is also modified.
   *
   * @param vm
   * @param disks
   */
  @Transactional
  public void attachDisks(VmEntity vm, List<PersistentDiskEntity> disks) {
    vmDao.update(vm);
    for (PersistentDiskEntity disk : disks) {
      checkNotNull(vm);
      checkNotNull(vm.getId());
      AttachedDiskEntity attachedDisk = new AttachedDiskEntity();
      attachedDisk.setVmId(vm.getId());
      attachedDisk.setUnderlyingDiskIdAndKind(checkNotNull(disk));
      vm.getAttachedDisks().add(attachedDiskDao.create(attachedDisk));
    }
  }


  /**
   * Delete attached disks in DB, VmEntity is also modified.
   *
   * @param vm
   * @param disks
   */
  @Transactional
  public void deleteAttachedDisks(VmEntity vm, List<PersistentDiskEntity> disks) {
    vmDao.update(vm);
    List<AttachedDiskEntity> attachedDiskEntities = new ArrayList<>();
    for (PersistentDiskEntity disk : disks) {
      // Lookup attached disk entity
      AttachedDiskEntity attachedDiskEntity = findAttachedDisk(disk);
      if (attachedDiskEntity != null) {
        attachedDiskEntities.add(attachedDiskEntity);
      }
    }

    vm.removeAttachedDisks(attachedDiskEntities);

    for (AttachedDiskEntity attachedDisk : attachedDiskEntities) {
      attachedDiskDao.delete(attachedDisk);
    }
  }

  /*
   * Called from VmBackend
   */
  public List<AttachedDiskEntity> createAttachedDisks(VmEntity vm, List<AttachedDiskCreateSpec> specs)
      throws ExternalException {
    List<AttachedDiskEntity> attachments = new ArrayList<>();
    for (AttachedDiskCreateSpec spec : specs) {
      attachments.add(createAttachedDiskEntity(vm, spec));
    }
    return attachments;
  }

  @Transactional
  public void deleteAttachedDisk(String kind, String id) throws ExternalException {
    BaseDiskEntity disk = diskBackend.find(kind, id);

    AttachedDiskEntity attachedDisk = attachedDiskDao.findByDisk(disk).orNull();
    if (attachedDisk != null) {
      attachedDiskDao.delete(attachedDisk);
    }
  }

  public AttachedDiskEntity findAttachedDisk(BaseDiskEntity diskEntity) {
    return attachedDiskDao.findByDisk(diskEntity).orNull();
  }

  @Transactional
  @Override
  public List<AttachedDiskEntity> findByVmId(String vmId) {
    return attachedDiskDao.findByVmId(vmId);
  }

  @Transactional
  @Override
  public void deleteAttachedDiskById(String attachedDiskId) {
    Optional<AttachedDiskEntity> attachedDiskEntity = attachedDiskDao.findById(attachedDiskId);

    if (attachedDiskEntity.isPresent()) {
      attachedDiskDao.delete(attachedDiskEntity.get());
    }
  }

  private AttachedDiskEntity createAttachedDiskEntity(VmEntity vm, AttachedDiskCreateSpec spec)
      throws ExternalException {
    String projectId = vm.getProjectId();

    AttachedDiskEntity attachedDisk = new AttachedDiskEntity();
    vm.addAttachedDisk(attachedDisk);
    attachedDisk.setBootDisk(spec.isBootDisk());

    attachedDisk.setUnderlyingDiskIdAndKind(diskBackend.create(projectId, spec));

    return attachedDiskDao.create(attachedDisk);
  }
}
