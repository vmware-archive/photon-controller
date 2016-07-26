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

import com.vmware.photon.controller.apife.entities.base.BaseEntity;

/**
 * ISO entity.
 * This entity is a model entity representing a ISO attached to a vm. The ISO can't exist without
 * attaching to a vm. ISO image is uploaded through API, and can only be attached to one vm.
 */
public class IsoEntity extends BaseEntity {

  public static final String KIND = "iso";

  private String name;

  private Long size;

  private VmEntity vm;

  @Override
  public String getKind() {
    return KIND;
  }

  public VmEntity getVm() {
    return vm;
  }

  public void setVm(VmEntity vm) {
    this.vm = vm;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }
}
