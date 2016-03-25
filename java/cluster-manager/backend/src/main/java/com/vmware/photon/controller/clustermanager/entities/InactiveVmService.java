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

package com.vmware.photon.controller.clustermanager.entities;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing an inactive Vm in Cluster.
 */
public class InactiveVmService extends StatefulService {

  public InactiveVmService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);
    ValidationUtils.validateState(startState);
    operation.complete();
  }

  /**
   * This class defines the document state associated with a single
   * {@link InactiveVmService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {
    /**
     * Represents the cluster that this vm belongs to.
     */
    @NotNull
    @Immutable
    public String clusterId;
  }
}
