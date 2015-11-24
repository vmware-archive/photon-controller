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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.DiskType;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotBlank;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Class DiskService is used for data persistence of disk.
 */
public class DiskService extends StatefulService {

  public DiskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting DiskService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.logSevere(this, t);
      ServiceUtils.failOperationAsBadRequest(startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);

    try {
      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  /**
   * Durable service state data. Class encapsulating the data for Disk.
   */
  public static class State extends ServiceDocument {
    @NotBlank
    @Immutable
    public String name;

    @NotNull
    @Immutable
    public DiskType diskType;

    @NotNull
    @Immutable
    public String flavorId;

    @NotBlank
    @Immutable
    public String projectId;

    public Set<String> tags;

    public List<QuotaLineItem> cost;

    @NotNull
    public DiskState state;

    public Integer capacityGb;

    public String datastore;

    public String agent;

    public List<LocalitySpec> affinities;
  }
}
