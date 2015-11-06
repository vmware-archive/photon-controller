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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.db.dao.StepLockDao;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepLockEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;


/**
 * Step Lock Backend.
 */
@Singleton
public class StepLockSqlBackend implements EntityLockBackend {

  private final StepLockDao stepLockDao;

  @Inject
  public StepLockSqlBackend(StepLockDao stepLockDao) {
    this.stepLockDao = stepLockDao;
  }

  @Override
  @Transactional
  public void setStepLock(BaseEntity entity, StepEntity step) throws ConcurrentTaskException {
    Preconditions.checkNotNull(entity.getId());
    if (lockExistsForEntityId(entity.getId())) {
      throw new ConcurrentTaskException();
    } else {
      StepLockEntity lock = new StepLockEntity();
      lock.setEntityId(entity.getId());
      lock.setStepId(step.getId());
      stepLockDao.create(lock);
    }
  }

  @Override
  @Transactional
  public void clearLocks(StepEntity step) {
    List<String> stepIds = new ArrayList<>();
    stepIds.add(step.getId());
    List<StepLockEntity> locks = stepLockDao.findBySteps(stepIds);
    for (StepLockEntity lock : locks) {
      stepLockDao.delete(lock);
    }
  }

  @Override
  public void setTaskLock(String entityId, TaskEntity task) throws ConcurrentTaskException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearTaskLocks(TaskEntity task) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Transactional
  public Boolean lockExistsForEntityId(String entityId) {
    Optional<StepLockEntity> optionalLock = stepLockDao.findByEntity(entityId);
    return optionalLock.isPresent();
  }
}
