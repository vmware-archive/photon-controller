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

import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.XenonClient;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * StepCommand to resume system.
 */
public class SystemResumeStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(SystemResumeStepCmd.class);

  public static final String DEPLOYMENT_ID_RESOURCE_KEY = "deployment-id";
  private String deploymentId;

  public SystemResumeStepCmd(TaskCommand taskCommand,
                             StepBackend stepBackend,
                             StepEntity step
                             ) {
    super(taskCommand, stepBackend, step);
    deploymentId = (String) step.getTransientResource(DEPLOYMENT_ID_RESOURCE_KEY);

  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    try {
      logger.info("Resuming APIFE service...");

      DeploymentService.State state = new DeploymentService.State();
      state.state = DeploymentState.READY;
      state.documentSelfLink = DeploymentServiceFactory.SELF_LINK + "/" + deploymentId;

      com.vmware.xenon.common.Operation operation =
          taskCommand.getApiFeXenonRestClient().patch(state.documentSelfLink, state,
              EnumSet.of(XenonClient.HeaderOption.HEADER_OPTION_FULL_QUORUM));

      logger.info("Resumed APIFE service...");
    } catch (DocumentNotFoundException ex) {
      throw new InternalException(ex);
    }
  }

  @Override
  protected void cleanup() {
  }
}
