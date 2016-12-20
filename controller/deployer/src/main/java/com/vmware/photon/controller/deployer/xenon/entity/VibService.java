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

package com.vmware.photon.controller.deployer.xenon.entity;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * This class implements a Xenon service representing a VIB to be uploaded and installed.
 */
public class VibService extends StatefulService {

  /**
   * This class defines the document state associated with a {@link VibService} instance.
   */
  @NoMigrationDuringUpgrade
  @MigrateDuringDeployment(
      factoryServicePath = VibFactoryService.SELF_LINK,
      serviceName = Constants.DEPLOYER_SERVICE_NAME)
  public static class State extends ServiceDocument {

    /**
     * This field name string is used when constructing queries.
     */
    public static final String FIELD_NAME_HOST_SERVICE_LINK = "hostServiceLink";

    /**
     * This value represents the name of the VIB file.
     */
    @NotNull
    @Immutable
    public String vibName;

    /**
     * This value represents the document self-link of the host service entity representing the
     * host to which the VIB should be uploaded and installed.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the path on the remote host to which the VIB file has been uploaded.
     */
    public String uploadPath;
  }

  public VibService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      startOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State startState = startOp.getBody(State.class);

    try {
      ValidationUtils.validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    startOp.setBody(startState).complete();
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);

    try {
      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      ValidationUtils.validateState(currentState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    patchOp.complete();
  }
}
