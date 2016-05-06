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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.Iso;
import com.vmware.photon.controller.api.PersistentDisk;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.lang3.StringUtils;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

/**
 * Class EntityLockService is used for data persistence of entity lock.
 * The EntityLockService provides locks for other entities based on their unique ID.
 * The lock has a state that indicates if it's been acquired or is available.
 * A lock should only be deleted if the entity has been deleted.
 * Client can use a POST to acquire a lock while PUT is used to acquire or release a lock.
 * This service uses idempotent POSTs, which means that a POST will be converted to a PUT if the lock already exists.
 * The recommended usage is:
 * POST to acquire a lock (works if lock exists or not)
 * PUT to release a lock
 */
public class EntityLockService extends StatefulService {

  public static final String LOCK_TAKEN_MESSAGE = "Lock already taken";

  private static Map<String, String> map = new HashMap<>();
  static {
    map.put(Vm.KIND, VmServiceFactory.SELF_LINK);
    map.put(Deployment.KIND, DeploymentServiceFactory.SELF_LINK);
    map.put(PersistentDisk.KIND, DiskServiceFactory.SELF_LINK);
    map.put(EphemeralDisk.KIND, DiskServiceFactory.SELF_LINK);
    map.put(Host.KIND, HostServiceFactory.SELF_LINK);
    map.put(Image.KIND, ImageServiceFactory.SELF_LINK);
    map.put(Iso.KIND, VmServiceFactory.SELF_LINK);
  }

  public EntityLockService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
  }

  @Override
  public void handleCreate(Operation op) {
    ServiceUtils.logInfo(this, "Handling CREATE for EntityLockService %s", getSelfLink());
    State payload = op.getBody(State.class);
    validatePayload(payload);

    InitializationUtils.initialize(payload);
    if (payload.lockOperation != State.LockOperation.ACQUIRE) {
      throw new IllegalArgumentException("Creating a lock with lockOperation!=ACQUIRE is not allowed");
    }
    payload.lockOperation = null; // no need to persist the operation type

    validateState(payload);
    setState(op, payload);
    op.complete();
  }

  @Override
  public void handleStart(Operation op) {
    ServiceUtils.logInfo(this, "Handling START for EntityLockService %s", getSelfLink());
    State payload = op.getBody(State.class);
    validateStartPayload(payload);

    payload.entitySelfLink = generateSelfLink(payload.entityId, payload.entityKind);
    validateState(payload);
    setState(op, payload);
    op.complete();
  }

  @Override
  public void handlePut(Operation op) {
    ServiceUtils.logInfo(this, "Handling PUT for EntityLockService %s", getSelfLink());

    if (!op.hasBody()) {
      op.fail(new IllegalArgumentException("body is required"));
      return;
    }

    State currentState = getState(op);
    State payload = op.getBody(State.class);

    validatePayload(payload);

    checkArgument(currentState.entityId.equalsIgnoreCase(payload.entityId),
        "entityId for a lock cannot be changed");

    checkArgument(currentState.entityKind.equalsIgnoreCase(payload.entityKind),
        "entityKind for a lock cannot be changed");

    State.LockOperation lockOperation = payload.lockOperation;
    payload.lockOperation = null; // no need to persist the operation type

    //Populate entitySelfLink from enityId and entityKind for verifying document remains the same
    if (payload.entitySelfLink == null) {
      payload.entitySelfLink = generateSelfLink(payload.entityId, payload.entityKind);
    }

    switch (lockOperation) {
      case ACQUIRE:
        handleAcquireLockRequest(op, currentState, payload);
        break;
      case RELEASE:
        handleReleaseLockRequest(op, currentState, payload);
        break;
      default:
        op.fail(Operation.STATUS_CODE_BAD_REQUEST);
        return;
    }

    validateState(payload);
    setState(op, payload);
    op.setBody(payload);
    op.complete();
  }

  @Override
  public void handlePatch(Operation op) {
    ServiceUtils.logWarning(this, "PATCH operation is not supported for EntityLockService %s", getSelfLink());
    op.fail(Operation.STATUS_CODE_BAD_METHOD);
  }

  private void handleAcquireLockRequest(Operation op, State currentState, State payload) {
    // if the new payload is identical to the existing state, complete operation with STATUS_CODE_NOT_MODIFIED
    ServiceDocumentDescription documentDescription = this.getDocumentTemplate().documentDescription;
    if (ServiceDocument.equals(documentDescription, currentState, payload)) {
      op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      return;
    }

    // if the lock already has an owner
    if (StringUtils.isNotBlank(currentState.ownerTaskId)) {
      // and the requested owner is new then return lock already taken error
      checkArgument(currentState.ownerTaskId.equalsIgnoreCase(payload.ownerTaskId),
          LOCK_TAKEN_MESSAGE + ". Current ownerTaskId: %s, Request ownerTaskId: %s, EntityId: %s",
          currentState.ownerTaskId, payload.ownerTaskId, currentState.entityId);
    }
  }

  private void handleReleaseLockRequest(Operation op, State currentState, State payload) {
    if (StringUtils.isBlank(currentState.ownerTaskId)) {
      // the lock is already available, make this a no-op
      op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      return;
    }

    // if the release requester is not the owner of the lock then throw BadRequestException
    checkArgument(currentState.ownerTaskId.equalsIgnoreCase(payload.ownerTaskId),
        "Only the current owner can release a lock. Current ownerTaskId: %s, Request ownerTaskId: %s, EntityId: %s",
        currentState.ownerTaskId, payload.ownerTaskId, currentState.entityId);

    //release ownership of lock
    payload.ownerTaskId = null;
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

  private void validateStartPayload(State state) {
    checkArgument(state != null, "state cannot be null");
    checkArgument(state.lockOperation == null, "lockOperation should be null");
    checkArgument(StringUtils.isNotBlank(state.entityId), "entityId cannot be blank");
    checkArgument(StringUtils.isNotBlank(state.ownerTaskId), "ownerTaskId cannot be blank");
  }

  /**
   * Generate document self link from entity id and entity kind.
   *
   * @param entityId
   * @param entityKind
   * @return
   */
  private String generateSelfLink(String entityId, String entityKind) {
    String factoryLink = map.get(entityKind);
    if (factoryLink == null) {
      throw new IllegalArgumentException("Cannot generate selflink for entityKind: " + entityKind);
    }

    StringBuilder builder = new StringBuilder();
    builder.append(factoryLink)
        .append("/")
        .append(entityId);
    return builder.toString();
  }

  /**
   * Durable service state data. Class encapsulating the data for EntityLock.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    @NotBlank
    public String entityId;

    @NotBlank
    public String entityKind;

    public String entitySelfLink;

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
