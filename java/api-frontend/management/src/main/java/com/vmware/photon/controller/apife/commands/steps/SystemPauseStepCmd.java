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

import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepCommand to pause system.
 */
public class SystemPauseStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(SystemPauseStepCmd.class);

  private final ServiceConfig serviceConfig;

  public SystemPauseStepCmd(TaskCommand taskCommand,
                            StepBackend stepBackend,
                            StepEntity step,
                            ServiceConfig serviceConfig) {
    super(taskCommand, stepBackend, step);
    this.serviceConfig = serviceConfig;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    try {
      logger.info("Pausing APIFE service...");
      this.serviceConfig.pause();
    } catch (Exception ex) {
      throw new InternalException(ex);
    }
  }

  @Override
  protected void cleanup() {
  }
}
