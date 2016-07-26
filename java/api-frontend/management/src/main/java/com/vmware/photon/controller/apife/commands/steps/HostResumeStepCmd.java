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

import com.vmware.photon.controller.apife.backends.HostBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Step command for host resuming to normal mode from suspended mode.
 */
public class HostResumeStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostExitMaintenanceModeStepCmd.class);
  private final HostBackend hostBackend;
  private final TaskCommand taskCommand;
  private HostEntity hostEntity;

  public HostResumeStepCmd(TaskCommand taskCommand,
                           StepBackend stepBackend,
                           StepEntity step,
                           HostBackend hostBackend) {
    super(taskCommand, stepBackend, step);

    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<BaseEntity> entityList = step.getTransientResourceEntities();
    Preconditions.checkArgument(entityList.size() == 1,
        "There should be only 1 host referenced by step %s", step.getId());

    hostEntity = (HostEntity) entityList.get(0);
    logger.info("Calling deployer to resume host {}", hostEntity);
    ChangeHostModeTaskService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .enterNormalMode(hostEntity.getId());
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
