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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

/**
 * Tombstone entity.
 */
@Entity(name = "Tombstone")
@NamedQueries({
    @NamedQuery(
        name = "Tombstone.findByEntityKind",
        query = "SELECT tombstone FROM Tombstone tombstone WHERE tombstone.entityKind = :entityKind"
    ),
    @NamedQuery(
        name = "Tombstone.findByEntityId",
        query = "SELECT tombstone FROM Tombstone tombstone WHERE tombstone.entityId = :entityId"
    ),
    @NamedQuery(
        name = "Tombstone.listAll",
        query = "SELECT tombstone FROM Tombstone tombstone"
    ),
    @NamedQuery(
        name = "Tombstone.listByTimeOlderThan",
        query = "SELECT tombstone FROM Tombstone tombstone WHERE tombstone.tombstoneTime < :date"
    )
})
public class TombstoneEntity extends BaseEntity {
  public static final String KIND = "tombstone";

  @Column(unique = true)
  private String entityId;

  private String entityKind;

  private long tombstoneTime;

  @Override
  public String getKind() {
    return KIND;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getEntityKind() {
    return entityKind;
  }

  public void setEntityKind(String entityKind) {
    this.entityKind = entityKind;
  }

  public void setEntity(BaseEntity entity) {
    if (entity != null) {
      this.entityKind = entity.getKind();
      this.entityId = entity.getId();
    }
  }

  public long getTombstoneTime() {
    return tombstoneTime;
  }

  public void setTombstoneTime(long tombstoneTime) {
    this.tombstoneTime = tombstoneTime;
  }
}
