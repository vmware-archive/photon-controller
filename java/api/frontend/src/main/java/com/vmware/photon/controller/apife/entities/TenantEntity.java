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

import com.vmware.photon.controller.apife.entities.base.VisibleModelEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tenant entity.
 */
public class TenantEntity extends VisibleModelEntity {

  public static final String KIND = "tenant";

  //Transient
  private Set<String> groups;

  private List<SecurityGroupEntity> securityGroups = new ArrayList<>();

  @Override
  public String getKind() {
    return KIND;
  }

  public Set<String> getGroups() {
    return this.groups;
  }

  public void setGroups(Set<String> groups) {
    this.groups = groups;
  }

  public List<SecurityGroupEntity> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<SecurityGroupEntity> securityGroups) {
    this.securityGroups = securityGroups;
  }
}
