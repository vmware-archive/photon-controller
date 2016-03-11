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

package com.vmware.photon.controller.provisioner.xenon.entity;


import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;


/**
 * ComputeService that is referenced from slingshot. Not used today except its id field.
 */
public class ComputeService extends StatefulService {

  public ComputeService() {
    super(ComputeService.ComputeState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      throw new IllegalArgumentException("body is required");
    } else {
      this.completeStart(start);
    }
  }

  private void completeStart(Operation start) {
    try {
      ComputeService.ComputeState e = (ComputeService.ComputeState) start.getBody(ComputeService.ComputeState.class);
      this.validateState(e);
      start.complete();
    } catch (Throwable var4) {
      start.fail(var4);
    }

  }

  public void validateState(ComputeService.ComputeState state) {
    if (state.id == null) {
      throw new IllegalArgumentException("id is required");
    }
  }

  public void handlePatch(Operation patch) {
    ComputeService.ComputeState currentState = (ComputeService.ComputeState) this.getState(patch);
    ComputeService.ComputeState patchBody = (ComputeService.ComputeState) patch.getBody(ComputeService.ComputeState
        .class);
    boolean isChanged = false;
    if (patchBody.id != null && !patchBody.id.equals(currentState.id)) {
      currentState.id = patchBody.id;
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(304);
    }

    patch.complete();
  }

  /**
   * Only id field is used for now.
   */
  public static class ComputeState extends ServiceDocument {
    public static final String FIELD_NAME_ID = "id";
    public String id;
  }

}
