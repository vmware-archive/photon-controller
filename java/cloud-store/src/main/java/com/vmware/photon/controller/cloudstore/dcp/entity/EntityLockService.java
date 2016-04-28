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

import org.apache.commons.lang3.StringUtils;
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
  public void handleCreate(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling START for EntityLockService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      validatePayload(startState);

      InitializationUtils.initialize(startState);
      if (startState.lockOperation != State.LockOperation.ACQUIRE) {
        throw new IllegalArgumentException("Creating a lock with lockOperation!=ACQUIRE is not allowed");
      }
      startState.lockOperation = null; // no need to persist the operation type

      validateState(startState);
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
    ServiceUtils.logInfo(this, "Handling PUT for EntityLockService %s", getSelfLink());

    if (!op.hasBody()) {
      op.fail(new IllegalArgumentException("body is required"));
      return;
    }

    State currentState = getState(op);
    State newState = op.getBody(State.class);

    checkArgument(newState.lockOperation == State.LockOperation.ACQUIRE,
        "Creating a lock with lockOperation!=ACQUIRE is not allowed");

    validatePayload(newState);

    newState.lockOperation = null; // no need to persist the operation type

    // if the new payload is identical to the existing state, complete operation with STATUS_CODE_NOT_MODIFIED
    ServiceDocumentDescription documentDescription = this.getDocumentTemplate().documentDescription;
    if (ServiceDocument.equals(documentDescription, currentState, newState)) {
      op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      op.complete();
      return;
    }

    checkArgument(currentState.entityId.equalsIgnoreCase(newState.entityId),
        "entityId for a lock cannot be changed");

    // if the lock already has an owner
    if (StringUtils.isNotBlank(currentState.ownerTaskId)) {
      // and the requested owner is new then return loack already taken error
      checkArgument(currentState.ownerTaskId.equalsIgnoreCase(newState.ownerTaskId),
          LOCK_TAKEN_MESSAGE + ". Current ownerTaskId: %s, Request ownerTaskId: %s, EntityId: %s",
          currentState.ownerTaskId, newState.ownerTaskId, currentState.entityId);
    }

    try {
      validateState(newState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, op, e);
      return;
    }

    setState(op, newState);
    op.setBody(newState);
    op.complete();
  }

  @Override
  public void handlePatch(Operation op) {
    ServiceUtils.logInfo(this, "Handling PATCH for EntityLockService %s", getSelfLink());

    State currentState = getState(op);
    State newState = op.getBody(State.class);
    validatePayload(newState);

    checkArgument(newState.lockOperation == State.LockOperation.RELEASE,
        "Patching a lock with lockOperation!=RELEASE is not allowed");

    if (StringUtils.isBlank(currentState.ownerTaskId)) {
      // the lock is already available, make this a no-op
      op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      op.complete();
      return;
    }

    // if the release requester is not the owner of the lock then throw BadRequestException
    checkArgument(currentState.ownerTaskId.equalsIgnoreCase(newState.ownerTaskId),
        "Only the current owner can release a lock. Current ownerTaskId: %s, Request ownerTaskId: %s, EntityId: %s",
        currentState.ownerTaskId, newState.ownerTaskId, currentState.entityId);

    //release ownership of lock
    currentState.ownerTaskId = null;

    try {
      validateState(currentState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, op, e);
      return;
    }

    setState(op, currentState);
    op.setBody(currentState);
    op.complete();
  }

  private void validateState(State state) {
    checkState(state.lockOperation == null, "lockOperation should always be null");
    ValidationUtils.validateState(state);
  }

  private void validatePayload(State state) {
    checkArgument(state != null, "state cannot be null");
    checkArgument(state.lockOperation != null, "lockOperation cannot be null");
    checkArgument(StringUtils.isNotBlank(state.entityId), "entityId cannot be blank");
    checkArgument(StringUtils.isNotBlank(state.ownerTaskId), "ownerTaskId cannot be blank");
  }

  /**
   * Durable service state data. Class encapsulating the data for EntityLock.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    @NotBlank
    public String entityId;

    @RenamedField(originalName = "taskId")
    public String ownerTaskId;

    public LockOperation lockOperation;

    /**
     * Definition of substages.
     */
    public enum LockOperation {
      ACQUIRE,
      RELEASE
    }
  }
}
