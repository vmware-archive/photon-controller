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

import com.vmware.photon.controller.api.EphemeralDisk;

import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Transient;

/**
 * Ephemeral disk entity.
 */
@Entity(name = "EphemeralDisk")
@NamedQueries({
    @NamedQuery(
        name = "EphemeralDisk.findAll",
        query = "SELECT disk FROM EphemeralDisk disk WHERE disk.projectId = :projectId"
    ),
    @NamedQuery(
        name = "EphemeralDisk.findByName",
        query = "SELECT disk FROM EphemeralDisk disk WHERE disk.name = :name AND disk.projectId = :projectId"
    ),
    @NamedQuery(
        name = "EphemeralDisk.findByTag",
        query = "SELECT disk FROM EphemeralDisk disk INNER JOIN disk.tags tag " +
            "WHERE tag.value = :value AND disk.projectId = :projectId"
    ),
    @NamedQuery(
        name = "EphemeralDisk.findByFlavor",
        query = "SELECT disk FROM EphemeralDisk disk WHERE disk.flavorId = :flavorId "
    )
})
public class EphemeralDiskEntity extends BaseDiskEntity {

  @Transient
  private String agent;

  @Override
  public String getKind() {
    return EphemeralDisk.KIND;
  }

  @Override
  public String getAgent() {
    return agent;
  }

  @Override
  public void setAgent(String agent) {
    this.agent = agent;
  }
}
