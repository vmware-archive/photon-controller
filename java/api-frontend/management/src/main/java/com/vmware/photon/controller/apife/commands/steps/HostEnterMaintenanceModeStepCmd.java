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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.backends.VmBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostHasVmsException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand for host enter maintenance mode.
 */
public class HostEnterMaintenanceModeStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostEnterMaintenanceModeStepCmd.class);

  private final HostBackend hostBackend;
  private final VmBackend vmBackend;
  private HostEntity hostEntity;

  public HostEnterMaintenanceModeStepCmd(TaskCommand taskCommand,
                                         StepBackend stepBackend,
                                         StepEntity step,
                                         HostBackend hostBackend,
                                         VmBackend vmBackend) {
    super(taskCommand, stepBackend, step);
    this.hostBackend = hostBackend;
    this.vmBackend = vmBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {

    List<BaseEntity> entityList = step.getTransientResourceEntities();

    hostEntity = (HostEntity) Iterables.getOnlyElement(entityList);

    int vmCount = vmBackend.countVmsOnHost(hostEntity);
    if (vmCount > 0) {
      throw new HostHasVmsException(hostEntity.getId(), vmCount);
    }

    logger.info("Calling deployer to enter host to maintenance mode {}", hostEntity);
    ChangeHostModeTaskService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .enterMaintenanceMode(hostEntity.getId());
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
