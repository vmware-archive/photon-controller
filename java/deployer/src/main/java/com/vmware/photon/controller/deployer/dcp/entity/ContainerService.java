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

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Range;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.Map;

/**
 * This class implements a DCP micro-service which provides a plain data object representing a container.
 */
public class ContainerService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link ContainerService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK = "containerTemplateServiceLink";
    public static final String FIELD_NAME_VM_SERVICE_LINK = "vmServiceLink";

    /**
     * Docker has a cpu shares constraint to restrict the maximum cpu allocation of a container in times of contention.
     * The recommended min share value is 2 and max is 1024. If 0 is set as cpu share value, it is counted as 1024.
     * https://docs.docker.com/engine/reference/run/#cpu-share-constraint
     */
    public static final int DOCKER_CPU_SHARES_MIN = 2;
    public static final int DOCKER_CPU_SHARES_MAX = 1024;

    /**
     * This value represents the relative path to the REST endpoint of the {@link ContainerTemplateService} object
     * representing the container template which was used to create the container.
     */
    @NotNull
    @Immutable
    public String containerTemplateServiceLink;

    /**
     * This value represents the relative path to the REST endpoint of the {@link VmService} object representing the VM
     * on which the container was created.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the amount of memory which will be set as the max limit to the container.
     */
    @WriteOnce
    @Range(min = 4, max = Long.MAX_VALUE)
    public Long memoryMb;

    /**
     * This value represents the cpu share constraint needed by docker for creating the container.
     */
    @WriteOnce
    @Range(min = DOCKER_CPU_SHARES_MIN, max = DOCKER_CPU_SHARES_MAX)
    public Integer cpuShares;

    /**
     * This value represents the dynamic parameters that has to be set inside the container.
     */
    public Map<String, String> dynamicParameters;

    /**
     * This value represents the ID of the running container. This value is unique to the VM hosting the container, but
     * is not guaranteed to be unique globally.
     */
    @WriteOnce
    public String containerId;
  }

  public ContainerService() {
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
    ValidationUtils.validateState(startState);
    startOperation.complete();
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    ValidationUtils.validatePatch(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    ValidationUtils.validateState(currentState);
    patchOperation.complete();
  }
}
