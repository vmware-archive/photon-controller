/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.housekeeper.xenon.HostsConfigSyncService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepCommand for hosts config sync.
 */
public class HostsConfigSyncStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(HostsConfigSyncStepCmd.class);

  public HostsConfigSyncStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws RpcException, InterruptedException {
    HostsConfigSyncService.State serviceDocument = taskCommand.getHousekeeperXenonClient().syncHostsConfig();

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
