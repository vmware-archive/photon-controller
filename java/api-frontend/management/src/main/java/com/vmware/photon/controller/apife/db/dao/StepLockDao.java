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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * Step Lock DAO.
 */
@Singleton
public class StepLockDao extends ExtendedAbstractDao<StepLockEntity> {

  @Inject
  public StepLockDao(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public Optional<StepLockEntity> findByEntity(BaseEntity entity) {
    return findByEntity(entity.getId());
  }

  public Optional<StepLockEntity> findByEntity(String entityId) {
    StepLockEntity result = uniqueResult(namedQuery("StepLock.findByEntity")
        .setString("entityId", entityId));
    return Optional.fromNullable(result);
  }

  public List<StepLockEntity> findBySteps(List<String> stepIds) {
    return list(namedQuery("StepLock.findBySteps").setParameterList("stepIds", stepIds));
  }

}
