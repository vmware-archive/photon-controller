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

import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing a management plane VM.
 */
public class VmService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link VmService} instance.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_HOST_SERVICE_LINK = "hostServiceLink";
    public static final String FIELD_NAME_VM_ID = "vmId";

    /**
     * This value represents the name of the VM.
     */
    @NotNull
    @Immutable
    public String name;

    /**
     * This value represents the link of the {@link HostService} associated
     * with this VM.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the link of the image service document
     * associated with this VM.
     */
    public String imageServiceLink;

    /**
     * This value represents the link of the project service document
     * associated with this VM.
     */
    public String projectServiceLink;

    /**
     * This value represents the link of the VM flavor service document
     * associated with this VM.
     */
    public String vmFlavorServiceLink;

    /**
     * This value represents the link of the disk flavor service document
     * associated with this VM.
     */
    public String diskFlavorServiceLink;

    /**
     * This value represents the ID of the VM object in APIFE.
     */
    public String vmId;

    /**
     * This value represents the IP of the VM.
     */
    public String ipAddress;

    /**
     * This value represents the port number of the deployer's DCP service of the VM. Note that this should be used
     * only for testing purpose because in a test environment we can setup multiple deployer services on the same
     * machine and hence they will have different port numbers. In real deployment, deployer should all listen on
     * the same port number.
     */
    public Integer deployerDcpPort;
  }

  public VmService() {
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
    if (null != patchState.vmId) {
      currentState.vmId = patchState.vmId;
    }

    if (null != patchState.vmFlavorServiceLink) {
      currentState.vmFlavorServiceLink = patchState.vmFlavorServiceLink;
    }

    if (null != patchState.diskFlavorServiceLink) {
      currentState.diskFlavorServiceLink = patchState.diskFlavorServiceLink;
    }

    if (null != patchState.imageServiceLink) {
      currentState.imageServiceLink = patchState.imageServiceLink;
    }

    if (null != patchState.projectServiceLink) {
      currentState.projectServiceLink = patchState.projectServiceLink;
    }
  }
}
