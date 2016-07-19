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

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand that kicks of finalize migration of a deployment on destination deployer service.
 */
public class DeploymentFinalizeMigrationStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentFinalizeMigrationStepCmd.class);
  public static final String SOURCE_ADDRESS_RESOURCE_KEY = "source-address";

  private final DeploymentBackend deploymentBackend;
  private DeploymentEntity destinationDeploymentEntity;
  private String sourceLoadbalancerAddress;

  public DeploymentFinalizeMigrationStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                            DeploymentBackend deploymentBackend) {
    super(taskCommand, stepBackend, step);
    this.deploymentBackend = deploymentBackend;
    this.sourceLoadbalancerAddress = (String) step.getTransientResource(SOURCE_ADDRESS_RESOURCE_KEY);
  }

  @Override
  protected void execute() throws RpcException, InterruptedException, ExternalException {
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);

    destinationDeploymentEntity = deploymentEntityList.get(0);
    // call deployer
    logger.info("Calling finalize deployment migration {}", deploymentEntityList);
    FinalizeDeploymentMigrationWorkflowService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .finalizeMigrateDeployment(sourceLoadbalancerAddress, destinationDeploymentEntity.getId());
    // pass remoteTaskId to XenonTaskStatusStepCmd
    for (StepEntity nextStep : taskCommand.getTask().getSteps()) {
      nextStep.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
          serviceDocument.documentSelfLink);
    }
    this.destinationDeploymentEntity.setOperationId(serviceDocument.documentSelfLink);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.destinationDeploymentEntity != null) {
      logger.error("Finalize deployment migration failed for deploymentEntity {}", this
          .destinationDeploymentEntity
          .getId
              ());
    }
  }

  @VisibleForTesting
  protected void setDeploymentEntity(DeploymentEntity deploymentEntity) {
    this.destinationDeploymentEntity = deploymentEntity;
  }

  @VisibleForTesting
  protected void setSourceLoadbalancerAddress(String sourceLoadbalancerAddress) {
    this.sourceLoadbalancerAddress = sourceLoadbalancerAddress;
  }
}
