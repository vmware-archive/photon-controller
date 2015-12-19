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
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.DeploymentBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.DeploymentEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentFailedException;
import com.vmware.photon.controller.apife.exceptions.external.DeploymentNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.StepNotCompletedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.deployer.gen.DeployStageStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatusCode;
import com.vmware.photon.controller.deployer.gen.DeployStatusResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand that monitors the status of a deployment.
 */
public class DeploymentStatusStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentCreateStepCmd.class);

  private static final long DEFAULT_DEPLOYMENT_TIMEOUT = TimeUnit.HOURS.toMillis(4);
  private static final long DEPLOYMENT_STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;

  private static final Map<Operation, ImmutableList<String>> OPERATION_TO_STAGES_MAP = new HashMap<>();
  static {
    OPERATION_TO_STAGES_MAP.put(
      Operation.PROVISION_CONTROL_PLANE_HOSTS, ImmutableList.<String>of("PROVISION_MANAGEMENT_HOSTS"));
    OPERATION_TO_STAGES_MAP.put(
      Operation.PROVISION_CONTROL_PLANE_VMS, ImmutableList.<String>of("CREATE_MANAGEMENT_PLANE"));
    OPERATION_TO_STAGES_MAP.put(
      Operation.PROVISION_CLOUD_HOSTS, ImmutableList.<String>of("PROVISION_CLOUD_HOSTS"));
    OPERATION_TO_STAGES_MAP.put(
        Operation.PROVISION_CLUSTER_MANAGER, ImmutableList.<String>of("ALLOCATE_CM_RESOURCES"));
    OPERATION_TO_STAGES_MAP.put(
      Operation.MIGRATE_DEPLOYMENT_DATA, ImmutableList.<String>of("MIGRATE_DEPLOYMENT_DATA"));
  }

  private final DeploymentBackend deploymentBackend;

  private DeploymentEntity entity;
  private long deploymentTimeout;
  private long statusPollInterval;
  private long maxServiceUnavailableCount;

  public DeploymentStatusStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                 DeploymentBackend deploymentBackend) {
    super(taskCommand, stepBackend, step);
    this.deploymentBackend = deploymentBackend;
    this.deploymentTimeout = DEFAULT_DEPLOYMENT_TIMEOUT;
    this.statusPollInterval = DEPLOYMENT_STATUS_POLL_INTERVAL;
    this.maxServiceUnavailableCount = DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT;
  }

  @Override
  protected void execute() throws ApiFeException, RpcException, InterruptedException {
    // get the entity
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);
    this.entity = deploymentEntityList.get(0);

    // wait for deployment to complete
    DeployStatus status = waitForDeploymentStepToComplete(this.entity.getOperationId());
    if (this.isDeployDone(status)) {
      // mark entity as ready
      deploymentBackend.updateState(this.entity, DeploymentState.READY);
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

  @VisibleForTesting
  protected void setDeploymentTimeout(long timeout) {
    this.deploymentTimeout = timeout;
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
   * Polls for status of the deployment until there is a status indicating success or failure
   * or a timeout marker is reached.
   *
   * @param operationId
   * @throws InterruptedException
   * @throws RpcException
   */
  private DeployStatus waitForDeploymentStepToComplete(String operationId)
      throws ApiFeException, InterruptedException, RpcException {
    long startTime = System.currentTimeMillis();
    int serviceUnavailableOccurrence = 0;

    // Check if deployment is done.
    while (true) {
      DeployStatusResponse response = null;
      try {
        response = this.taskCommand.getDeployerClient().deployStatus(operationId);
        if (this.isDeployStepDone(response.getStatus())) {
          return response.getStatus();
        }

        serviceUnavailableOccurrence = 0;
      } catch (ServiceUnavailableException e) {
        serviceUnavailableOccurrence++;
        if (serviceUnavailableOccurrence >= this.maxServiceUnavailableCount) {
          logger.error("checking deployment status failed {}", response);
          throw e;
        }
      }

      this.checkReplicationTimeout(startTime);
      Thread.sleep(this.statusPollInterval);
    }
  }

  /**
   * Check if the deployment has been taking too long.
   *
   * @param startTimeMs
   * @return
   */
  private void checkReplicationTimeout(long startTimeMs) {
    if (System.currentTimeMillis() - startTimeMs >= this.deploymentTimeout) {
      throw new RuntimeException("Timeout waiting for deployment to complete.");
    }
  }

  /**
   * Determines if the status passed as parameter indicates the deployment step is done.
   *
   * @param status
   * @return
   * @throws ApiFeException
   */
  private boolean isDeployStepDone(DeployStatus status) throws ApiFeException {
    List<String> stages = OPERATION_TO_STAGES_MAP.get(this.step.getOperation());
    if (null == stages) {
      throw new RuntimeException(String.format("Unexpected operation %s", this.step.getOperation()));
    }

    boolean isStepDone = (stages.size() > 0);
    for (String stage : stages) {
      isStepDone = isStepDone && isDone(status, stage);
    }

    if (isStepDone) {
      return isStepDone;
    }

    if (isDeployDone(status)) {
      this.step.addWarning(new StepNotCompletedException());
      return true;
    }

    return false;
  }

  /**
   * Determines if the status passed as parameter indicates the deployment is done.
   *
   * @param status
   * @return
   */
  private boolean isDeployDone(DeployStatus status) throws ApiFeException {
    return isDone(status, null);
  }

  /**
   * Evaluates the status code of a passed in stage or the overall status if the stage is not found.
   *
   * @param status
   * @param stage
   * @return
   * @throws ApiFeException
   */
  private boolean isDone(DeployStatus status, String stage) throws ApiFeException {
    DeployStatusCode code = status.getCode();

    DeployStageStatus stageStatus = findStageStatus(status, stage);
    if (stageStatus != null) {
      code = stageStatus.getCode();
    }

    if (code == null) {
      return false;
    }

    switch (code) {
      case IN_PROGRESS:
        return false;

      case FINISHED:
        // deployment completed
        return true;

      case FAILED:
      case CANCELLED:
        logger.error("deployment failed {}", status);
        throw new DeploymentFailedException(this.entity.getOperationId(), status.getError());

      default:
        logger.error("unexpected deployment status {}", status);
        throw new RuntimeException(status.getError());
    }
  }

  /**
   * Finds the status for the stage of given name.
   *
   * @param status
   * @param stage
   * @return
   */
  private DeployStageStatus findStageStatus(DeployStatus status, String stage) {
    if (!status.isSetStages() || stage == null) {
      return null;
    }

    for (DeployStageStatus stageStatus : status.getStages()) {
      if (stageStatus.getName().equals(stage)) {
        return stageStatus;
      }
    }

    return null;
  }
}
