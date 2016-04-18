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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;
import com.vmware.photon.controller.apife.exceptions.external.NoManagementHostException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.deployer.gen.DeployResponse;
import com.vmware.photon.controller.deployer.gen.Deployment;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * StepCommand that kicks of a deployment on deployer service.
 */
public class DeploymentCreateStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentCreateStepCmd.class);

  private final DeploymentBackend deploymentBackend;
  private DeploymentEntity entity;

  public DeploymentCreateStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                 DeploymentBackend deploymentBackend) {
    super(taskCommand, stepBackend, step);
    this.deploymentBackend = deploymentBackend;
  }

  @Override
  protected void execute() throws RpcException, InterruptedException, ExternalException {
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(com.vmware.photon.controller.api.Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);

    // build the deployment object
    this.entity = deploymentEntityList.get(0);
    Deployment deployment = buildDeployment(this.entity);

    // call deployer
    try {
      DeployResponse response = taskCommand.getDeployerClient().deploy(deployment);
      this.entity.setOperationId(response.getOperation_id());
    } catch (com.vmware.photon.controller.common.clients.exceptions.NoManagementHostException e) {
      throw new NoManagementHostException(deployment.getId());
    } catch (com.vmware.photon.controller.common.clients.exceptions.InvalidAuthConfigException e) {
      throw new InvalidAuthConfigException(e.getMessage());
    }
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.entity != null) {
      logger.info("Deployment create failed, mark entity {} state as ERROR", this.entity.getId());
      try {
        this.deploymentBackend.updateState(this.entity, DeploymentState.ERROR);
      } catch (DeploymentNotFoundException e) {
        logger.warn("Could not find deployment to mark as error, DeploymentId=" + e.getId(), e);
      }
    }
  }

  @VisibleForTesting
  protected void setEntity(DeploymentEntity entity) {
    this.entity = entity;
  }

  private Deployment buildDeployment(DeploymentEntity entity) {
    Deployment deployment = new Deployment();
    deployment.setId(entity.getId());
    if (entity.getImageDatastores() != null && !entity.getImageDatastores().isEmpty()) {
      deployment.setImageDatastore(entity.getImageDatastores().iterator().next());
    }
    deployment.setUseImageDatastoreForVms(entity.getUseImageDatastoreForVms());
    deployment.setNtpEndpoint(entity.getNtpEndpoint());
    deployment.setSyslogEndpoint(entity.getSyslogEndpoint());
    deployment.setAuthEnabled(entity.getAuthEnabled());
    deployment.setOauthEndpoint(entity.getOauthEndpoint());
    deployment.setOauthTenant(entity.getOauthTenant());
    deployment.setOauthUsername(entity.getOauthUsername());
    deployment.setOauthPassword(entity.getOauthPassword());
    deployment.setLoadBalancerEnabled(entity.getLoadBalancerEnabled());

    return deployment;
  }
}
