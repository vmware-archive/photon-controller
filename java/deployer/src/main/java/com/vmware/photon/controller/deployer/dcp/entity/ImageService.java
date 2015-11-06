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

package com.vmware.photon.controller.deployer.dcp.entity;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing an image.
 */
public class ImageService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link ImageService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the file path of the image on the deployer.
     */
    @NotNull
    @Immutable
    public String imageFile;

    /**
     * This value represents the friendly name of the VM image.
     */
    @NotNull
    @Immutable
    public String imageName;

    /**
     * This value represents the ID of the image in APIFE.
     */
    @WriteOnce
    public String imageId;
  }

  public ImageService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);
    startOperation.complete();
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    validateState(currentState);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    applyPatch(currentState, patchState);
    validateState(currentState);
    patchOperation.complete();
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
  }

  private void applyPatch(State currentState, State patchState) {
    if (null != patchState.imageId) {
      currentState.imageId = patchState.imageId;
    }
  }
}
