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

package com.vmware.photon.controller.api.frontend.commands.steps;

import com.vmware.photon.controller.api.frontend.backends.HostBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for host enter suspended mode.
 * <p>
 * Host can only go into SUSPENDED mode from READY or MAINTENANCE modes:
 * READY <=> SUSPENDED <=> MAINTENANCE MODE => DEPROVISIONED
 * <p>
 * When the host is in SUSPENDED mode no new VMs can be created on this host.
 * All existing VMs will be killed or moved out before the host can be set to MAINTENANCE mode.
 */
public class HostEnterSuspendedModeStepCmd extends StepCommand {
  private static final Logger logger = LoggerFactory.getLogger(HostEnterSuspendedModeStepCmd.class);

  private final HostBackend hostBackend;
  private HostEntity hostEntity;

  public HostEnterSuspendedModeStepCmd(TaskCommand taskCommand,
                                       StepBackend stepBackend,
                                       StepEntity stepEntity,
                                       HostBackend hostBackend) {
    super(taskCommand, stepBackend, stepEntity);
    this.hostBackend = hostBackend;
  }

  @Override
  protected void execute() throws ExternalException {

    // Precondition check: only one host can be referenced.
    List<BaseEntity> entityList = step.getTransientResourceEntities();
    hostEntity = (HostEntity) Iterables.getOnlyElement(entityList);

    // Call deployer for action and error handling.
    logger.info("Calling deployer to suspend host {}", hostEntity);
    ChangeHostModeTaskService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .enterSuspendedMode(hostEntity.getId());
    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }
  }

  @Override
  protected void cleanup() {

  }
}
