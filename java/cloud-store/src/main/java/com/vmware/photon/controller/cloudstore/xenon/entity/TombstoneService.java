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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * A tombstone is created whenever we delete an object (e.g. a VM) with associated tasks.
 *
 * Our goal is for tasks to live for the lifetime of the object they affect (e.g. the power-on task for a VM), so people
 * can see what happened to the object. Therefore we can't just delete tasks when they complete, nor can we use Xenon's
 * document expiration to remove them at a fixed time after they complete.
 *
 * What we actually do is delete the tasks some time after the object is deleted. To do this, we need to know that the
 * object was deleted. That's what the tombstone is for: it marks that an object was deleted, and indicates that its
 * associated tasks should be removed.
 *
 * The TombstoneCleanerService does the cleanup.
 */
public class TombstoneService extends StatefulService {

  public TombstoneService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting TombstoneService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
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
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching TombstoneService %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);
      validatePatch(currentState, patchState);

      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    ValidationUtils.validateState(current);
  }

  /**
   * Validate patch data.
   *
   * @param start
   * @param patch
   */
  protected void validatePatch(State start, State patch) {
    ValidationUtils.validatePatch(start, patch);
  }

  /**
   * Durable service state data. Class encapsulating the data for tombstone.
   */
  @MigrateDuringUpgrade(transformationServicePath = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = TombstoneServiceFactory.SELF_LINK,
      destinationFactoryServicePath = TombstoneServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = TombstoneServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_TOMBSTONE_TIME = "tombstoneTime";

    /**
     * The id of the entity that was deleted.
     */
    @Immutable
    @NotNull
    public String entityId;

    /**
     * The kind of the entity that was deleted.
     */
    @Immutable
    @NotNull
    public String entityKind;

    /**
     * Milliseconds since epoch of when this tombstone was created.
     */
    @Immutable
    @NotNull
    @Positive
    public Long tombstoneTime;
  }
}
