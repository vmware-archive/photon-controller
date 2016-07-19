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

package com.vmware.photon.controller.api.model.builders;

import com.vmware.photon.controller.api.model.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.model.EphemeralDisk;

/**
 * This class implements a builder for {@link AttachedDiskCreateSpec} object.
 */
public class AttachedDiskCreateSpecBuilder {

  private String flavor;

  private String name;

  private String kind;

  private Integer capacityGb;

  private boolean bootDisk;

  public AttachedDiskCreateSpecBuilder() {
    this.kind = EphemeralDisk.KIND;
  }

  public AttachedDiskCreateSpecBuilder flavor(String flavor) {
    this.flavor = flavor;
    return this;
  }

  public AttachedDiskCreateSpecBuilder name(String name) {
    this.name = name;
    return this;
  }

  public AttachedDiskCreateSpecBuilder kind(String kind) {
    this.kind = kind;
    return this;
  }

  public AttachedDiskCreateSpecBuilder capacityGb(Integer capacityGb) {
    this.capacityGb = capacityGb;
    return this;
  }

  public AttachedDiskCreateSpecBuilder bootDisk(boolean bootDisk) {
    this.bootDisk = bootDisk;
    return this;
  }

  public AttachedDiskCreateSpec build() {
    AttachedDiskCreateSpec spec = new AttachedDiskCreateSpec();
    spec.setName(this.name);
    spec.setKind(this.kind);
    spec.setFlavor(this.flavor);
    spec.setCapacityGb(this.capacityGb);
    spec.setBootDisk(this.bootDisk);
    return spec;
  }
}
