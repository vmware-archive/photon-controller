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

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.AvailabilityZoneState;
import com.vmware.photon.controller.api.model.base.Named;

/**
 * AvailabilityZone entity.
 */
public class AvailabilityZoneEntity extends BaseEntity implements Named {

  private String name;

  private AvailabilityZoneState state;

  public AvailabilityZoneEntity() {
  }

  public AvailabilityZoneEntity(String name) {
    this.name = name;
  }

  @Override
  public String getKind() {
    return AvailabilityZone.KIND;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public AvailabilityZoneState getState() {
    return state;
  }

  public void setState(AvailabilityZoneState state) {
    if (this.getState() != null && state != null) {
      EntityStateValidator.validateStateChange(this.getState(), state, AvailabilityZoneState.PRECONDITION_STATES);
    }

    this.state = state;
  }

  public AvailabilityZone toApiRepresentation() {
    AvailabilityZone availabilityZone = new AvailabilityZone();
    availabilityZone.setId(getId());
    availabilityZone.setName(getName());
    availabilityZone.setKind(getKind());
    availabilityZone.setState(getState());
    return availabilityZone;
  }
}
