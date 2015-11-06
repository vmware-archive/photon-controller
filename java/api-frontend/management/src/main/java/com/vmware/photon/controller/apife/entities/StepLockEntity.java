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

import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

/**
 * Step Lock Entity.
 */
@Entity(name = "StepLock")
@NamedQueries({
    @NamedQuery(
        name = "StepLock.findByEntity",
        query = "SELECT stepLock FROM StepLock stepLock WHERE stepLock.entityId = :entityId"
    ),
    @NamedQuery(
        name = "StepLock.findBySteps",
        query = "SELECT stepLock FROM StepLock stepLock WHERE stepLock.stepId in :stepIds"
    )
})
public class StepLockEntity extends BaseEntity {

  @Column(unique = true, nullable = false)
  private String entityId;

  @NotEmpty
  private String stepId;

  @Override
  public String getKind() {
    return "step-lock";
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getStepId() {
    return stepId;
  }

  public void setStepId(String stepId) {
    this.stepId = stepId;
  }

}
