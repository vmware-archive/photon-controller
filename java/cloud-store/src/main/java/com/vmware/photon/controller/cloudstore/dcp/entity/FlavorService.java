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

import com.vmware.photon.controller.api.FlavorState;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.cloudstore.CloudStoreModule;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;
import java.util.Set;

/**
 * Class FlavorService is used for data persistence of flavor information.
 */
public class FlavorService extends StatefulService {

  public FlavorService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
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
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());

    try {
      State startState = getState(patchOperation);

      State patchState = patchOperation.getBody(State.class);
      validatePatchState(startState, patchState);

      PatchUtils.patchState(startState, patchState);
      validateState(startState);

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
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    checkNotNull(patchState, "patch can not be null");
    checkState(patchState.state == FlavorState.PENDING_DELETE, "patch state can only be PENDING_DELETE");
    checkNotNull(patchState.deleteRequestTime, "deleteRequestTime can not be null");
  }

  /**
   * Durable service state data. Class encapsulating the data for flavor.
   */
  @MigrateDuringUpgrade(transformationServicePath = UpgradeUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = FlavorServiceFactory.SELF_LINK,
      destinationFactoryServicePath = FlavorServiceFactory.SELF_LINK,
      serviceName = CloudStoreModule.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    @NotNull
    @Immutable
    public String name;

    public Set<String> tags;
    @NotNull
    @Immutable
    public String kind;
    @NotNull
    @Immutable
    public List<QuotaLineItem> cost;

    @NotNull
    public FlavorState state;

    /**
     * The timestamp indicating when flavor is marked as PENDING_DELETE.
     */
    public Long deleteRequestTime;

    /**
     * Data object for Quota Line Item.
     */
    public static class QuotaLineItem {
      public String key;
      public double value;
      public QuotaUnit unit;
    }
  }
}
