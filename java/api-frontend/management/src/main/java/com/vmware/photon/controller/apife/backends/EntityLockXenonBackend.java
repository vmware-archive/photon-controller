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

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.EntityLockService;
import com.vmware.photon.controller.cloudstore.xenon.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity Lock operations using Xenon cloud store.
 */
@Singleton
public class EntityLockXenonBackend implements EntityLockBackend {

  private static final Logger logger = LoggerFactory.getLogger(EntityLockXenonBackend.class);

  private final ApiFeXenonRestClient xenonClient;

  @Inject
  public EntityLockXenonBackend(ApiFeXenonRestClient xenonClient) {
    this.xenonClient = xenonClient;
    this.xenonClient.start();
  }

  @Override
  public void setTaskLock(BaseEntity entity, TaskEntity task) throws ConcurrentTaskException {
    checkNotNull(entity, "Entity cannot be null.");
    checkNotNull(task, "TaskEntity cannot be null.");

    EntityLockService.State state = new EntityLockService.State();
    state.ownerTaskId = task.getId();
    state.entityId = entity.getId();
    state.entityKind = entity.getKind();
    state.documentSelfLink = entity.getId();
    state.lockOperation = EntityLockService.State.LockOperation.ACQUIRE;

    try {
      task.getLockedEntityIds().add(entity);
      // POST to the entity lock service will be converted to a PUT if the lock already exists
      xenonClient.post(EntityLockServiceFactory.SELF_LINK, state);
      logger.info("Entity Lock with entityId : {} and taskId: {} has been set", state.entityId, state.ownerTaskId);
    } catch (XenonRuntimeException e) {
      if (e.getCompletedOperation().getStatusCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
        String errorMessage = e.getCompletedOperation().getBody(ServiceErrorResponse.class).message;
        if (StringUtils.isNotBlank(errorMessage) && errorMessage.contains(EntityLockService.LOCK_TAKEN_MESSAGE)) {
          task.getLockedEntityIds().remove(entity);
          throw new ConcurrentTaskException();
        }
      }
      throw e;
    }
  }

  @Override
  public void clearTaskLocks(TaskEntity task) {
    checkNotNull(task, "TaskEntity cannot be null.");
    List<BaseEntity> failedToDeleteLockedEntityIds = new ArrayList<>();
    for (BaseEntity lockedEntity : task.getLockedEntityIds()) {
      try {
        String lockedEntityId = lockedEntity.getId();
        EntityLockService.State state = new EntityLockService.State();
        state.ownerTaskId = task.getId();
        state.entityId = lockedEntityId;
        state.entityKind = lockedEntity.getKind();
        state.documentSelfLink = lockedEntityId;
        state.lockOperation = EntityLockService.State.LockOperation.RELEASE;
        xenonClient.put(EntityLockServiceFactory.SELF_LINK + "/" + lockedEntityId, state);
        logger.info("Entity Lock with taskId : {} and entityId : {} has been cleared", task.getId(), lockedEntityId);
      } catch (Throwable swallowedException) {
        failedToDeleteLockedEntityIds.add(lockedEntity);
        logger.error("Failed to delete entity lock with entityId: " + lockedEntity.getId(), swallowedException);
      }
    }
    task.setLockedEntityIds(failedToDeleteLockedEntityIds);
  }

  private EntityLockService.State getByEntityId(String entityId) throws DocumentNotFoundException {
    Operation operation = xenonClient.get(EntityLockServiceFactory.SELF_LINK + "/" + entityId);
    return operation.getBody(EntityLockService.State.class);
  }

  @Override
  public Boolean lockExistsForEntityId(String entityId) {
    try {
      EntityLockService.State lockState = getByEntityId(entityId);
      return StringUtils.isNotBlank(lockState.ownerTaskId);
    } catch (DocumentNotFoundException ex) {
      return false;
    }
  }
}
