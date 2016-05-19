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

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * Class TombstoneService is used for data persistence of resource tombstones.
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
  @MigrateDuringUpgrade(transformationServicePath = UpgradeUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = TombstoneServiceFactory.SELF_LINK,
      destinationFactoryServicePath = TombstoneServiceFactory.SELF_LINK,
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
