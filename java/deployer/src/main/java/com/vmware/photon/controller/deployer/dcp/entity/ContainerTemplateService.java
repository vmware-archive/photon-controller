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

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.Map;

/**
 * This class implements a DCP micro-service which provides a plain data object representing a container template.
 */
public class ContainerTemplateService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link ContainerTemplateService} instance.
   */
  @NoMigrationDuringUpgrade
  @MigrateDuringDeployment(
      factoryServicePath = ContainerTemplateFactoryService.SELF_LINK,
      serviceName = Constants.DEPLOYER_SERVICE_NAME)
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_NAME = "name";

    /**
     * This value represents the name of the container template.
     */
    @NotNull
    @Immutable
    public String name;

    /**
     * Specifies if this container should be replicated (on all docker vms) or should be a singleton instance.
     */
    public Boolean isReplicated;

    /**
     * This value lists the number of CPUs which should be allocated for the container.
     */
    @NotNull
    @Immutable
    @Positive
    public Integer cpuCount;

    /**
     * This value lists the amount of RAM which should be allocated for the container, in GB.
     */
    @NotNull
    @Immutable
    @Positive
    public Long memoryMb;

    /**
     * This value lists the amount of disk which should be allocated for the container, in GB.
     */
    @NotNull
    @Immutable
    @Positive
    public Integer diskGb;

    /**
     * This value represents the image name of the container.
     */
    @NotNull
    @Immutable
    public String containerImage;

    /**
     * This value represents the container name of the container whose volumes that had to be mounted on this container.
     */
    @Immutable
    public String volumesFrom;

    /**
     * This value represents whether the container has to be started in privileged mode.
     */
    @Immutable
    @DefaultBoolean(value = false)
    public Boolean isPrivileged;

    /**
     * This value represents whether the container uses host network.
     */
    @Immutable
    @DefaultBoolean(value = true)
    public Boolean useHostNetwork;

    /**
     * This value represents the host volumes that can be mounted on the container.
     * <Host Volume, Container Volume>
     */
    @Immutable
    public Map<String, String> volumeBindings;

    /**
     * This value represents the container ports that has to be forwarded to the host.
     * <Container Port, Host Port>
     */
    @Immutable
    public Map<Integer, Integer> portBindings;

    /**
     * This value represents the environment variables that has to be set inside the container.
     */
    @Immutable
    public Map<String, String> environmentVariables;
  }

  public ContainerTemplateService() {
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

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }
}
