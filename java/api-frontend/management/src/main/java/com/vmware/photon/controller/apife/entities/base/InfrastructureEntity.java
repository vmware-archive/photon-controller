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

package com.vmware.photon.controller.apife.entities.base;

import com.vmware.photon.controller.api.common.entities.TaggableEntity;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.entities.base.TagEntity;
import com.vmware.photon.controller.api.model.base.Named;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for infrastructure entities (VMs, disks, etc).
 * Common properties include name, flavor, tags.
 * All infrastructure entities belong to {@link com.vmware.photon.controller.apife.entities.ProjectEntity}.
 */
public abstract class InfrastructureEntity extends BaseEntity
    implements Named, TaggableEntity {

  // todo(markl): discuss the need/desire to persist cost. we easily compute cost
  // todo(markl): from the findById/flavor, but since the cost can change, the cost charged
  // todo(markl): on creation is persisted: https://www.pivotaltracker.com/story/show/48188449
  protected List<QuotaLineItemEntity> cost = new ArrayList<>();
  private String name;

  private String flavorId;

  private String projectId;

  private Set<TagEntity> tags;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFlavorId() {
    return flavorId;
  }

  public void setFlavorId(String flavorId) {
    this.flavorId = flavorId;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public List<QuotaLineItemEntity> getCost() {
    return cost;
  }

  public void setCost(List<QuotaLineItemEntity> cost) {
    this.cost = cost;
  }

  public Set<TagEntity> getTags() {
    if (tags == null) {
      tags = new HashSet<>();
    }
    return tags;
  }

  public void setTags(Set<TagEntity> tags) {
    this.tags = tags;
  }

  @Override
  protected Objects.ToStringHelper toStringHelper() {
    Objects.ToStringHelper result = super.toStringHelper()
        .add("name", name);

    if (flavorId != null) {
      result.add("flavorId", flavorId);
    }

    return result;
  }
}
