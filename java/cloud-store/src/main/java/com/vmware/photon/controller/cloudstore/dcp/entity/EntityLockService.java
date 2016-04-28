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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.RenamedField;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Class EntityLockService is used for data persistence of entity lock.
 */
public class EntityLockService extends StatefulService {

  public static final String LOCK_TAKEN_MESSAGE = "Lock already taken";

  public EntityLockService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting EntityLockService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      ValidationUtils.validateState(startState);
      if (startState.isAvailable) {
        throw new IllegalArgumentException("Creating a lock with isAvailable=true is not allowed");
      }
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePut(Operation op) {
    ServiceUtils.logInfo(this, "Handling PUT for service %s", getSelfLink());

    if (!op.hasBody()) {
      op.fail(new IllegalArgumentException("body is required"));
      return;
    }

    State currentState = getState(op);
    State newState = op.getBody(State.class);

    // if the new payload is identical to the existing state, complete operation with STATUS_CODE_NOT_MODIFIED
    ServiceDocumentDescription documentDescription = this.getDocumentTemplate().documentDescription;
    if (ServiceDocument.equals(documentDescription, currentState, newState)) {
      op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      op.complete();
      return;
    }

    // validate the payload.
    // TODO(pankaj): We need to replace the logic in the validation utils to return IllegalArgumentException instead of
    // IllegalStateException since Xenon would automatically map IllegalArgumentException to http bad request
    // https://www.pivotaltracker.com/story/show/114581369
    //
    try {
      ValidationUtils.validateState(newState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, op, e);
      return;
    }

    checkArgument(currentState.entityId.equalsIgnoreCase(newState.entityId), "entityId for a lock cannot be changed");

    // if the requester is new and the request is to release the lock then
    checkState(!(!currentState.ownerId.equalsIgnoreCase(newState.ownerId) && newState.isAvailable),
        "Only the current owner can release a lock. Current ownerId: %s, Request ownerId: %s, EntityId: %s",
        currentState.ownerId, newState.ownerId, currentState.entityId);

    // if the requester is new and the request is to acquire the lock and the lock is not available
    checkState(!(!currentState.ownerId.equalsIgnoreCase(newState.ownerId) &&
            !newState.isAvailable &&
            !currentState.isAvailable),
        LOCK_TAKEN_MESSAGE + ". Current ownerId: %s, Request ownerId: %s, EntityId: %s",
        currentState.ownerId, newState.ownerId, currentState.entityId);

    setState(op, newState);
    op.complete();
  }

  /**
   * Durable service state data. Class encapsulating the data for EntityLock.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    @NotBlank
    public String entityId;

    @RenamedField(originalName = "taskId")
    @NotBlank
    public String ownerId;

    @NotBlank
    public Boolean isAvailable;
  }
}
