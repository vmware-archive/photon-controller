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
 * Project entity.
 */
public class ProjectEntity extends VisibleModelEntity {

  public static final String KIND = "project";

  private String tenantId;

  private String resourceTicketId;

  //Transient
  private Set<String> groups;

  private List<SecurityGroupEntity> securityGroups = new ArrayList<>();

  public String getResourceTicketId() {
    return resourceTicketId;
  }

  public void setResourceTicketId(String resourceTicketId) {
    this.resourceTicketId = resourceTicketId;
  }

  @Override
  public String getKind() {
    return ProjectEntity.KIND;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Set<String> getGroups() {
    return this.groups;
  }

  public void setGroups(Set<String> groups) {
    this.groups = groups;
  }

  public List<SecurityGroupEntity> getSecurityGroups() {
    return this.securityGroups;
  }

  public void setSecurityGroups(List<SecurityGroupEntity> securityGroups) {
    this.securityGroups = securityGroups;
  }
}
