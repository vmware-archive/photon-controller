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
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockService;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
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
 * Entity Lock operations using DCP cloud store.
 */
@Singleton
public class EntityLockDcpBackend implements EntityLockBackend {

  private static final Logger logger = LoggerFactory.getLogger(EntityLockDcpBackend.class);

  private final ApiFeDcpRestClient dcpClient;

  @Inject
  public EntityLockDcpBackend(ApiFeDcpRestClient dcpClient) {
    this.dcpClient = dcpClient;
    this.dcpClient.start();
  }

  @Override
  public void setTaskLock(String entityId, TaskEntity task) throws ConcurrentTaskException {
    checkNotNull(entityId, "Entity cannot be null.");
    checkNotNull(task, "TaskEntity cannot be null.");

    EntityLockService.State state = new EntityLockService.State();
    state.ownerId = task.getId();
    state.entityId = entityId;
    state.documentSelfLink = entityId;
    state.isAvailable = false;

    try {
      dcpClient.post(EntityLockServiceFactory.SELF_LINK, state);
      task.getLockedEntityIds().add(entityId);
      logger.info("Entity Lock with entityId : {} and taskId: {} has been set", state.entityId, state.ownerId);
    } catch (XenonRuntimeException e) {
      if (e.getCompletedOperation().getStatusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
        String errorMessage = e.getCompletedOperation().getBody(ServiceErrorResponse.class).message;
        if (StringUtils.isNotBlank(errorMessage) && errorMessage.contains(EntityLockService.LOCK_TAKEN_MESSAGE)) {
          throw new ConcurrentTaskException();
        }
      }
      throw e;
    }
  }

  @Override
  public void clearTaskLocks(TaskEntity task) {
    checkNotNull(task, "TaskEntity cannot be null.");
    List<String> failedToDeleteLockedEntityIds = new ArrayList<>();
    for (String lockedEntityId : task.getLockedEntityIds()) {
      try {
        EntityLockService.State state = new EntityLockService.State();
        state.ownerId = task.getId();
        state.entityId = lockedEntityId;
        state.documentSelfLink = lockedEntityId;
        state.isAvailable = true;
        dcpClient.post(EntityLockServiceFactory.SELF_LINK, state);
        logger.info("Entity Lock with taskId : {} and entityId : {} has been cleared", task.getId(), lockedEntityId);
      } catch (Throwable swallowedException) {
        failedToDeleteLockedEntityIds.add(lockedEntityId);
        logger.error("Failed to delete entity lock with entityId: " + lockedEntityId, swallowedException);
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
      EntityLockService.State lockState = getByEntityId(entityId);
      return !lockState.isAvailable;
    } catch (DocumentNotFoundException ex) {
      return false;
    }
  }
}
