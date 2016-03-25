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

import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.Map;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing a cluster.
 */
public class ClusterService extends StatefulService {

  public ClusterService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      ValidationUtils.validateState(startState);
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
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);
      PatchUtils.patchState(currentState, patchState);
      ValidationUtils.validateState(currentState);
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
   * This class defines the document state associated with a single
   * {@link ClusterService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    /**
     * The state of the cluster.
     */
    @NotNull
    public ClusterState clusterState;

    /**
     * The name of the cluster.
     */
    @NotBlank
    @Immutable
    public String clusterName;

    /**
     * The type of the cluster.
     */
    @NotNull
    @Immutable
    public ClusterType clusterType;

    /**
     * Image Identifier used to create the Kubernetes Cluster.
     */
    @NotNull
    @Immutable
    public String imageId;

    /**
     * Project Identifier used to create this cluster.
     */
    @NotBlank
    @Immutable
    public String projectId;

    /**
     * Network Identifier used for creating the vms in this cluster.
     */
    public String vmNetworkId;

    /**
     * Name of the Disk Flavor used for the vms created in the cluster.
     */
    @NotBlank
    @Immutable
    public String diskFlavorName;

    /**
     * Name of the Vm Flavor used for master vms in this cluster.
     */
    @NotBlank
    @Immutable
    public String masterVmFlavorName;

    /**
     * Name of the Vm Flavor used for other vms in this cluster.
     */
    @NotBlank
    @Immutable
    public String otherVmFlavorName;

    /**
     * Number of slave Nodes in this cluster.
     */
    @NotNull
    public Integer slaveCount;

    /**
     * This property specifies extended properties of the cluster.
     */
    @NotBlank
    @Immutable
    public Map<String, String> extendedProperties;
  }
}
