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

import com.vmware.photon.controller.api.model.Subnet;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.api.model.base.Named;
import com.vmware.photon.controller.apife.entities.base.BaseEntity;

/**
 * Network entity.
 */
public class NetworkEntity extends BaseEntity implements Named {

  private String name;

  private String description;

  private SubnetState state;

  private String portGroups;

  private Boolean isDefault;

  @Override
  public String getKind() {
    return Subnet.KIND;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SubnetState getState() {
    return state;
  }

  public void setState(SubnetState state) {
    if (this.getState() != null && state != null) {
      EntityStateValidator.validateStateChange(this.getState(), state, SubnetState.PRECONDITION_STATES);
    }

    this.state = state;
  }

  public String getPortGroups() {
    return portGroups;
  }

  public void setPortGroups(String portGroups) {
    this.portGroups = portGroups;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }
}
