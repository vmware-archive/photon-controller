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

import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.apife.backends.HostXenonBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.exceptions.external.HostProvisionFailedException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.util.Pair;
import com.vmware.xenon.common.TaskState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Polls host task status.
 */
public class HostProvisionTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private static final Logger logger = LoggerFactory.getLogger(HostProvisionTaskStatusPoller.class);

  private final TaskCommand taskCommand;
  private final HostXenonBackend hostBackend;
  private final TaskBackend taskBackend;
  private HostEntity hostEntity;

  public HostProvisionTaskStatusPoller(TaskCommand taskCommand, HostXenonBackend hostBackend,
                                       TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    List<HostEntity> entityList = null;
    for (StepEntity step : taskCommand.getTask().getSteps()) {
      entityList = step.getTransientResourceEntities(Host.KIND);
      if (!entityList.isEmpty()) {
        break;
      }
    }
    this.hostEntity = entityList.get(0);

    Pair<TaskState, String> pair = hostBackend.getDeployerClient()
        .getHostProvisionStatus(remoteTaskLink);
    TaskState taskState = pair.getFirst();
    if (taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityId(pair.getSecond());
      taskEntity.setEntityKind(Host.KIND);
      taskBackend.update(taskEntity);
    } else if (taskState.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(taskState);
    }
    return taskState;
  }

  private void handleTaskFailure(TaskState state) throws ApiFeException {
    logger.info("Host create failed, mark entity {} state as ERROR", this.hostEntity);
    if (this.hostEntity != null) {
      hostBackend.updateState(this.hostEntity, HostState.READY);
    }
    throw new HostProvisionFailedException(state.toString(), state.failure.message);
  }

  @Override
  public int getTargetSubStage(Operation op) {
    return 0;
  }

  @Override
  public int getSubStage(TaskState taskState) {
    return 0;
  }

  @Override
  public void handleDone(TaskState taskState) throws ApiFeException {
    logger.info("handleDone, mark entity {} state as READY", this.hostEntity);
    if (this.hostEntity != null) {
      hostBackend.updateState(this.hostEntity, HostState.READY);
    }
  }
}
