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
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * This class implements a Xenon micro-service which provides a plain data object
 * representing a management plane VM.
 */
public class VmService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link VmService} instance.
   *
   * We do not migrate the control plane layout, since this would mix old plane layout with new
   * plane layout.
   */
  @NoMigrationDuringUpgrade
  @MigrateDuringDeployment(
      factoryServicePath = VmFactoryService.SELF_LINK,
      serviceName = Constants.DEPLOYER_SERVICE_NAME)
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
     * This value represents the document self-link of the {@link HostService} document in cloud
     * store representing the host on which the VM was or should be created.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the static IP address assigned to the VM.
     */
    @NotNull
    @Immutable
    public String ipAddress;

    /**
     * This value represents the ID of the API-level image object from which this VM was or should
     * be created.
     */
    @WriteOnce
    public String imageId;

    /**
     * This value represents the ID of the API-level project object under which this VM was or
     * should be created.
     */
    @WriteOnce
    public String projectId;

    /**
     * This value represents the ID of the API-level flavor object from which the VM was or should
     * be created.
     */
    @WriteOnce
    public String vmFlavorId;

    /**
     * This value represents the ID of the API-level flavor object from which the boot disk for the
     * VM was or should be created.
     */
    @WriteOnce
    public String diskFlavorId;

    /**
     * This value represents the ID of the API-level VM object created by the current task.
     */
    @WriteOnce
    public String vmId;

    /**
     * This value represents the port number of the deployer's Xenon service of the VM. Note that this should be used
     * only for testing purpose because in a test environment we can setup multiple deployer services on the same
     * machine and hence they will have different port numbers. In real deployment, deployer should all listen on
     * the same port number.
     */
    @Immutable
    @DefaultInteger(Constants.PHOTON_CONTROLLER_PORT)
    public Integer deployerXenonPort;
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
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    patchOperation.complete();
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
  }
}
