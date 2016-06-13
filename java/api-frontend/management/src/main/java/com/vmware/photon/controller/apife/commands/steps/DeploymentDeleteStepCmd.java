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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.xenon.workflow.RemoveDeploymentWorkflowService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand that kicks of deleting deployment on deployer service.
 */
public class DeploymentDeleteStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentDeleteStepCmd.class);

  private final DeploymentBackend deploymentBackend;
  private DeploymentEntity deploymentEntity;

  public DeploymentDeleteStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                 DeploymentBackend deploymentBackend) {
    super(taskCommand, stepBackend, step);
    this.deploymentBackend = deploymentBackend;
  }

  @Override
  protected void execute() throws RpcException, InterruptedException {
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);

    deploymentEntity = deploymentEntityList.get(0);
    // call deployer
    logger.info("Calling delete deployment  {}", deploymentEntity);
    RemoveDeploymentWorkflowService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .removeDeployment(deploymentEntity.getId());
    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }
    this.deploymentEntity.setOperationId(serviceDocument.documentSelfLink);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.deploymentEntity != null) {
      logger.error("Deployment delete failed, mark deploymentEntity {} state as ERROR", this.deploymentEntity.getId());
      try {
        this.deploymentBackend.updateState(this.deploymentEntity, DeploymentState.ERROR);
      } catch (DeploymentNotFoundException e) {
        logger.warn("Could not find deployment to mark as error, DeploymentId=" + e.getId(), e);
      }
    }
  }

  @VisibleForTesting
  protected void setDeploymentEntity(DeploymentEntity deploymentEntity) {
    this.deploymentEntity = deploymentEntity;
  }
}
