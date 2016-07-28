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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.DiskState;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Base disk entity is a base class that both persistent and ephemeral disks extend.
 */
public abstract class BaseDiskEntity extends InfrastructureEntity {

  private DiskState state;

  /**
   * Note, the capacityGB comes from the creator's
   * AttachedDisk payload. It's used in quota computation
   * to determine the xxx.capacity component of a disks
   * cost.
   * <p/>
   * Note: It's not clear this *needs* to be persisted as we
   * have it in less useful form embedded as a cost, quota line item.
   */
  private int capacityGb;

  /**
   * When this property is set, the disk is created and attached
   * to the infrastructure. The various backend components (VmBackend, etc,)
   * own this property and use this to manage the lazy creation of disks,
   * as they are attached to VMs.
   * <p/>
   * For example, during VM creation, the VmBackend
   * processes the AttachedDisks array in the Vm. For each record with a diskId, the
   * disk is attached to the Vm entity. For those without a disk entity is created and
   * attached. During this phase, only the model objects are processed and connected.
   * <p/>
   * In the next phase of creation, the backend code is responsible for lazily creating
   * infrastructure and recording the underlying id in the datastore property. If a disk
   * already has a datastore (i.e., its been attached in the past), then no new infrastructure is
   * created. Once this is done, all disks in the AttachedDisks array are ready from attaching to the vm.
   */
  private String datastore;

  // getters and setters
  public int getCapacityGb() {
    return capacityGb;
  }

  public void setCapacityGb(int capacityGb) {
    this.capacityGb = capacityGb;
  }

  public String getDatastore() {
    return datastore;
  }

  public void setDatastore(String datastore) {
    this.datastore = datastore;
  }

  /**
   * this method overrides the base setCost function as it needs
   * to fold in a capacity line item.
   *
   * @param cost - supplies the cost of the entity before entity specific costs (like capacity) are included
   */
  @Override
  public void setCost(List<QuotaLineItemEntity> cost) {
    List<QuotaLineItemEntity> enhancedCost = new ArrayList<>(cost);
    super.setCost(enhancedCost);
  }

  public DiskState getState() {
    return state;
  }

  public void setState(DiskState state) {
    if (this.state == null || state == null) {
      this.state = state;
    } else {
      DiskStateChecks.checkValidStateChange(this, state);
      this.state = state;
    }
  }

  public abstract String getAgent();

  public abstract void setAgent(String agent);
}
