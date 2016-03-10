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
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * Class AttachedDiskService is used for data persistence of attached disk.
 */
public class AttachedDiskService extends StatefulService {

  public AttachedDiskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting AttachedDiskService %s", getSelfLink());
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
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.logInfo(this, "Deleting AttachedDiskService %s", getSelfLink());
    State currentState = getState(deleteOperation);
    if (currentState.documentExpirationTimeMicros <= 0) {
      currentState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
          ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS);
    }

    if (deleteOperation.hasBody()) {
      State deleteState = deleteOperation.getBody(State.class);
      if (deleteState.documentExpirationTimeMicros > 0) {
        currentState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
            deleteState.documentExpirationTimeMicros);
      }
    }

    if (currentState.documentExpirationTimeMicros > 0) {
      ServiceUtils.logInfo(this,
          "Expiring AttachedDiskService %s in %d micros",
          getSelfLink(),
          currentState.documentExpirationTimeMicros);
    }

    setState(deleteOperation, currentState);
    deleteOperation.complete();
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
   * Durable service state data. Class encapsulating the data for Project.
   */
  public static class State extends ServiceDocument {

    public boolean bootDisk;

    public String kind;

    @NotBlank
    @Immutable
    public String vmId;

    public String persistentDiskId;

    public String ephemeralDiskId;

  }
}
