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

import com.vmware.photon.controller.api.common.exceptions.external.ConcurrentTaskException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockService;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity Lock operations using DCP cloud store.
 */
@Singleton
public class EntityLockDcpBackend implements EntityLockBackend {

  private static final Logger logger = LoggerFactory.getLogger(EntityLockDcpBackend.class);

  private final ApiFeXenonRestClient dcpClient;

  @Inject
  public EntityLockDcpBackend(ApiFeXenonRestClient dcpClient) {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public void setTaskLock(String entityId, TaskEntity task) throws ConcurrentTaskException {
    checkNotNull(entityId, "Entity cannot be null.");
    checkNotNull(task, "TaskEntity cannot be null.");

    EntityLockService.State state = new EntityLockService.State();
    state.taskId = task.getId();
    state.entityId = entityId;
    state.documentSelfLink = entityId;

    try {
      task.getLockedEntityIds().add(entityId);
      dcpClient.post(true, EntityLockServiceFactory.SELF_LINK, state);
      logger.info("Entity Lock with entityId : {} and taskId: {} has been set", state.entityId, state.taskId);
    } catch (XenonRuntimeException e) {
      //re-throw any exception other than a conflict which indicated the lock already exists
      if (e.getCompletedOperation().getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
        throw e;
      }

      //creation failed since a lock for this entity already exists

      //check if the lock is being re-acquired by the same task, if yes then nothing needs to be done
      EntityLockService.State lock = null;
      try {
        lock = getByEntityId(entityId);
      } catch (DocumentNotFoundException ex) {
        String errorMessage = String.format(
            "Failed to create lock for entityid {%s} and taskid {%s} because an existing lock was detected but it " +
                "disappeared thereafter, throwing ConcurrentTaskException anyways so that client can re-try",
            entityId,
            task.getId());
        logger.warn(errorMessage);
        throw new ConcurrentTaskException();
      }

      if (!lock.taskId.equals(task.getId())) {
        logger.warn("Entity Lock with entityId: {} already acquired by taskId {}", entityId, lock.taskId);
        throw new ConcurrentTaskException();
      }

      logger.info("Ignoring lock conflict for entityId : {} because task id : {} already owns it",
          entityId, task.getId());
    }
  }

  @Override
  public void clearTaskLocks(TaskEntity task) {
    checkNotNull(task, "TaskEntity cannot be null.");
    List<String> failedToDeleteLockedEntityIds = new ArrayList<>();
    for (String lockedEntityId : task.getLockedEntityIds()) {
      String lockUrl = EntityLockServiceFactory.SELF_LINK + "/" + lockedEntityId;
      EntityLockService.State lock = null;
      try {
        lock = getByEntityId(lockedEntityId);
      } catch (DocumentNotFoundException ex) {
        logger.warn("Lock did not exist for entity {}", lockedEntityId, ex);
      }

      if (lock.taskId.equals(task.getId())) {
        try {
          dcpClient.delete(lockUrl, new EntityLockService.State());
          logger.info("Entity Lock with taskId : {} and url : {} has been cleared", task.getId(), lockUrl);
        } catch (Throwable swallowedException) {
          if (swallowedException.getCause() instanceof DocumentNotFoundException) {
            logger.warn("Lock did not exist for entity {}", lockedEntityId, swallowedException);
          } else {
            failedToDeleteLockedEntityIds.add(lockedEntityId);
            logger.error("Failed to delete entity lock with url: " + lockUrl, swallowedException);
          }
        }
      }
    }
    task.setLockedEntityIds(failedToDeleteLockedEntityIds);
  }

  private EntityLockService.State getByEntityId(String entityId) throws DocumentNotFoundException {
    Operation operation = dcpClient.get(EntityLockServiceFactory.SELF_LINK + "/" + entityId);
    return operation.getBody(EntityLockService.State.class);
  }

  @Override
  public Boolean lockExistsForEntityId(String entityId) {
    try {
      getByEntityId(entityId);
      return true;
    } catch (DocumentNotFoundException ex) {
      return false;
    }
  }
}
