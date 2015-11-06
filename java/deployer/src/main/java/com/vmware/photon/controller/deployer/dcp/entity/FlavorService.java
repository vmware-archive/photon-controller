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
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing a flavor.
 */
public class FlavorService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link FlavorService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the name of the VM flavor.
     */
    @NotNull
    @Immutable
    public String vmFlavorName;

    /**
     * This value represents the name of the disk flavor.
     */
    @NotNull
    @Immutable
    public String diskFlavorName;

    /**
     * This value represents the number of the CPUs which should be allocated for the VM.
     */
    @NotNull
    @Immutable
    public Integer cpuCount;

    /**
     * This value represents the amount of RAM which should be allocated for the VM, in GB.
     */
    @NotNull
    @Immutable
    public Integer memoryGb;

    /**
     * This value represents the amount of disk which should be allocated for the VM, in GB.
     */
    @NotNull
    @Immutable
    public Integer diskGb;
  }

  public FlavorService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    validateState(startState);
    startOperation.complete();
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }
}
