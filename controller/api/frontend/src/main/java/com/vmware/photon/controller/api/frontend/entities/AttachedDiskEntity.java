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

package com.vmware.photon.controller.api.frontend.entities;

import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Attached disk entity.
 * This entity is a model entity representing a disk that's attached to a vm. The vm and disk objects
 * are real infrastructure entities that have a real, project level quota cost associated with them.
 * The attached disk entity tracks which vm in a project a given disk is attached do.
 */
public class AttachedDiskEntity extends BaseEntity {

  private String kind;

  private boolean bootDisk = false;

  private String vmId;

  private String persistentDiskId;

  private PersistentDiskEntity transientPersistentDisk;

  private String ephemeralDiskId;

  private EphemeralDiskEntity transientEphemeralDisk;

  @Override
  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getPersistentDiskId() {
    return persistentDiskId;
  }

  public void setPersistentDiskId(String persistentDiskId) {
    this.persistentDiskId = persistentDiskId;
  }

  public String getEphemeralDiskId() {
    return ephemeralDiskId;
  }

  public void setEphemeralDiskId(String ephemeralDiskId) {
    this.ephemeralDiskId = ephemeralDiskId;
  }

  public PersistentDiskEntity getTransientPersistentDisk() {
    return transientPersistentDisk;
  }

  public void setTransientPersistentDiskId(PersistentDiskEntity persistentDisk) {
    this.transientPersistentDisk = persistentDisk;
  }

  public EphemeralDiskEntity getTransientEphemeralDiskId() {
    return transientEphemeralDisk;
  }

  public void setETransientphemeralDisk(EphemeralDiskEntity ephemeralDisk) {
    this.transientEphemeralDisk = ephemeralDisk;
  }

  public String getVmId() {
    return vmId;
  }

  public void setVmId(String vmId) {
    this.vmId = vmId;
  }

  public boolean isBootDisk() {
    return bootDisk;
  }

  public void setBootDisk(boolean bootDisk) {
    this.bootDisk = bootDisk;
  }

  public String getUnderlyingDiskId() {
    switch (kind) {
      case PersistentDisk.KIND:
        return persistentDiskId;
      case EphemeralDisk.KIND:
        return ephemeralDiskId;
    }
    throw new IllegalStateException("Unknown disk kind: " + kind);
  }

  public BaseDiskEntity getUnderlyingTransientDisk() {
    switch (kind) {
      case PersistentDisk.KIND:
        return transientPersistentDisk;
      case EphemeralDisk.KIND:
        return transientEphemeralDisk;
    }
    throw new IllegalStateException("Unknown disk kind: " + kind);
  }

  public void setUnderlyingDiskIdAndKind(BaseDiskEntity diskEntity) {
    switch (diskEntity.getKind()) {
      case PersistentDisk.KIND:
        persistentDiskId = diskEntity.getId();
        kind = PersistentDisk.KIND;
        transientPersistentDisk = (PersistentDiskEntity) diskEntity;
        return;
      case EphemeralDisk.KIND:
        ephemeralDiskId = diskEntity.getId();
        kind = EphemeralDisk.KIND;
        transientEphemeralDisk = (EphemeralDiskEntity) diskEntity;
        return;
    }
    throw new IllegalArgumentException("Unknown disk kind: " + diskEntity.getKind());
  }

  public String getDiskId() {
    String id = getUnderlyingDiskId();
    return checkNotNull(id);
  }
}
