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

import com.vmware.photon.controller.api.frontend.backends.DeploymentXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.DeploymentEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.DeploymentMigrationFailedException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.workflow.FinalizeDeploymentMigrationWorkflowService;
import com.vmware.xenon.common.TaskState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * StepCommand that monitors the status of upgrade warm-up or finalize migration of a deployment.
 */
public class DeploymentFinalizeMigrationStatusStepCmd extends XenonTaskStatusStepCmd {

  private static final Logger logger = LoggerFactory.getLogger(DeploymentFinalizeMigrationStatusStepCmd.class);

  private static final long DEFAULT_FINALIZE_MIGRATE_DEPLOYMENT_TIMEOUT = TimeUnit.HOURS.toMillis(2);
  private static final long FINALIZE_MIGRATE_DEPLOYMENT_STATUS_POLL_INTERVAL = TimeUnit.SECONDS.toMillis(10);
  private static final long DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT = 100;

  public DeploymentFinalizeMigrationStatusStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                                                  XenonTaskStatusPoller xenonTaskStatusPoller) {
    super(taskCommand, stepBackend, step, xenonTaskStatusPoller);
    this.setOperationTimeout(DEFAULT_FINALIZE_MIGRATE_DEPLOYMENT_TIMEOUT);
    this.setPollInterval(FINALIZE_MIGRATE_DEPLOYMENT_STATUS_POLL_INTERVAL);
    this.setMaxServiceUnavailableCount(DEFAULT_MAX_SERVICE_UNAVAILABLE_COUNT);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);
  }

  @VisibleForTesting
  protected void setOperationTimeout(long timeout) {
    super.setTimeout(timeout);
  }

  @VisibleForTesting
  protected void setStatusPollInterval(long interval) {
    super.setPollInterval(interval);
  }

  @VisibleForTesting
  protected void setMaxServiceUnavailableCount(long count) {
    super.setDocumentNotFoundMaxCount(count);
  }

  @Override
  protected void execute() throws ApiFeException, RpcException, InterruptedException {
    // get the entity
    List<DeploymentEntity> deploymentEntityList =
        step.getTransientResourceEntities(Deployment.KIND);
    Preconditions.checkArgument(deploymentEntityList.size() == 1);
    DeploymentEntity entity = deploymentEntityList.get(0);
    step.createOrUpdateTransientResource(XenonTaskStatusStepCmd.REMOTE_TASK_LINK_RESOURCE_KEY,
        entity.getOperationId());
    setRemoteTaskLink(entity.getOperationId());
    super.execute();
  }

  /**
   * Polls task status.
   */
  public static class DeploymentFinalizeMigrationStatusStepPoller implements XenonTaskStatusStepCmd
      .XenonTaskStatusPoller {
    private final DeploymentXenonBackend deploymentBackend;

    private DeploymentEntity entity;
    private TaskCommand taskCommand;
    private TaskBackend taskBackend;

    public DeploymentFinalizeMigrationStatusStepPoller(TaskCommand taskCommand,
                                      TaskBackend taskBackend,
                                      DeploymentXenonBackend deploymentBackend) {
      this.taskCommand = taskCommand;
      this.deploymentBackend = deploymentBackend;
      this.taskBackend = taskBackend;
    }


    @Override
    public int getTargetSubStage(Operation op) {
      return FinalizeDeploymentMigrationWorkflowService.TaskState.SubStage.values().length;
    }

    @Override
    public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
      List<DeploymentEntity> deploymentEntityList = null;
      for (StepEntity step : taskCommand.getTask().getSteps()) {
        deploymentEntityList = step.getTransientResourceEntities(Deployment.KIND);
        if (!deploymentEntityList.isEmpty()) {
          break;
        }
      }
      this.entity = deploymentEntityList.get(0);

      FinalizeDeploymentMigrationWorkflowService.State serviceDocument = deploymentBackend.getDeployerClient()
          .getFinalizeMigrateDeploymentStatus(remoteTaskLink);
      if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
        TaskEntity taskEntity = taskCommand.getTask();
        taskEntity.setEntityId(ServiceUtils.getIDFromDocumentSelfLink(serviceDocument.destinationDeploymentId));
        taskEntity.setEntityKind(Deployment.KIND);
        taskBackend.update(taskEntity);
      } else if (serviceDocument.taskState.stage != TaskState.TaskStage.STARTED){
        handleTaskFailure(serviceDocument.taskState);
      }
      return serviceDocument.taskState;
    }

    private void handleTaskFailure(TaskState state) throws ApiFeException {
      if (this.entity != null) {
        logger.info("Deployment finalize migration failed, mark entity");
      }
      throw new DeploymentMigrationFailedException(this.entity == null ? "" : this.entity.getId()
          , state.failure.message);
    }

    @Override
    public void handleDone(TaskState taskState) throws ApiFeException {
    }

    @Override
    public int getSubStage(TaskState taskState) {
      return ((FinalizeDeploymentMigrationWorkflowService.TaskState) taskState).subStage.ordinal();
    }

    @VisibleForTesting
    protected void setEntity(DeploymentEntity entity) {
      this.entity = entity;
    }


  }
}
