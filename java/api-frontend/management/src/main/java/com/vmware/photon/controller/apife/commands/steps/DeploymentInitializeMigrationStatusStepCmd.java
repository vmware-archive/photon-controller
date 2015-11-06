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
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentMigrationFailedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand that monitors the status of upgrade warm-up or initialize migration of a deployment.
 */
public class DeploymentInitializeMigrationStatusStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentInitializeMigrationStatusStepCmd.class);

  private static final long DEFAULT_INITIALIZE_MIGRATE_DEPLOYMENT_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long INITIALIZE_MIGRATE_DEPLOYMENT_STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;

  private DeploymentEntity deploymentEntity;
  private long operationTimeout;
  private long statusPollInterval;
  private long maxServiceUnavailableCount;

  public DeploymentInitializeMigrationStatusStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
    this.operationTimeout = DEFAULT_INITIALIZE_MIGRATE_DEPLOYMENT_TIMEOUT;
    this.statusPollInterval = INITIALIZE_MIGRATE_DEPLOYMENT_STATUS_POLL_INTERVAL;
    this.maxServiceUnavailableCount = DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT;
  }

  @Override
  protected void execute() throws ApiFeException, RpcException, InterruptedException {
    // get the deploymentEntity
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);
    deploymentEntity = deploymentEntityList.get(0);

    // check status for initialize deployment
    pollInitializeDeploymentMigration(deploymentEntity.getOperationId());
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);
  }

  @VisibleForTesting
  protected void setDeploymentEntity(DeploymentEntity deploymentEntity) {
    this.deploymentEntity = deploymentEntity;
  }

  @VisibleForTesting
  protected void setOperationTimeout(long timeout) {
    this.operationTimeout = timeout;
  }

  @VisibleForTesting
  protected void setStatusPollInterval(long interval) {
    this.statusPollInterval = interval;
  }

  @VisibleForTesting
  protected void setMaxServiceUnavailableCount(long count) {
    this.maxServiceUnavailableCount = count;
  }

  /**
   * Polls for status of initialize the deployment migration until there is a status indicating success or failure
   * or a timeout marker is reached.
   *
   * @param operationId
   * @throws InterruptedException
   * @throws RpcException
   */
  private void pollInitializeDeploymentMigration(String operationId)
      throws ApiFeException, InterruptedException, RpcException {
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;

    // Check if replication is done.
    while (true) {
      InitializeMigrateDeploymentStatusResponse response = null;
      try {
        response = this.taskCommand.getDeployerClient().initializeMigrateStatus(operationId);
        if (this.isMigrateDeploymentDone(response.getStatus())) {
          return;
        }

        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= this.maxServiceUnavailableCount) {
          logger.error("checking initialize migrate deployment status failed {}", response);
          throw e;
        }
      }

      this.checkReplicationTimeout(startTime);
      Thread.sleep(this.statusPollInterval);
    }
  }

  /**
   * Check if the replication has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  protected void checkReplicationTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= this.operationTimeout) {
      throw new RuntimeException("Timeout waiting for initialize migrate deployment to complete.");
    }
  }

  /**
   * Determines if the status passed as parameters indicates the initialize migrate deployment is done.
   *
   * @param status
   * @return
   */
  private boolean isMigrateDeploymentDone(InitializeMigrateDeploymentStatus status) throws ApiFeException {
    switch (status.getCode()) {
      case IN_PROGRESS:
        return false;

      case FINISHED:
        return true;

      case FAILED:
        logger.error("deployment failed {}", status);
        throw new DeploymentMigrationFailedException(this.deploymentEntity.getOperationId(), status.getError());

      default:
        logger.error("unexpected initialize migrate deployment status {}", status);
        throw new RuntimeException(status.getError());
    }
  }
}
