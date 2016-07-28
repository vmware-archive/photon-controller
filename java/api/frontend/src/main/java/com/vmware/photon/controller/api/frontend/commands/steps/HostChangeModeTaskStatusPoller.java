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

import com.vmware.photon.controller.api.frontend.backends.HostXenonBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.HostEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.HostStateChangeException;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.task.ChangeHostModeTaskService;
import com.vmware.xenon.common.TaskState;

import java.util.List;

/**
 * Polls host task status.
 */
public class HostChangeModeTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private final TaskCommand taskCommand;
  private final HostXenonBackend hostBackend;
  private final TaskBackend taskBackend;
  private HostState targetHostState;

  public HostChangeModeTaskStatusPoller(TaskCommand taskCommand, HostXenonBackend hostBackend,
                                        TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    ChangeHostModeTaskService.State serviceDocument = taskCommand.getDeployerXenonClient()
        .getHostChangeModeStatus(remoteTaskLink);
    setHostState(serviceDocument);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityKind(Host.KIND);
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskState.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(serviceDocument);
    }
    return serviceDocument.taskState;
  }

  private void setHostState(ChangeHostModeTaskService.State state) {
    switch (state.hostMode) {
      case NORMAL:
        this.targetHostState = HostState.READY;
        break;
      case ENTERING_MAINTENANCE:
        this.targetHostState = HostState.SUSPENDED;
        break;
      case MAINTENANCE:
        this.targetHostState = HostState.MAINTENANCE;
        break;
      case DEPROVISIONED:
        this.targetHostState = HostState.NOT_PROVISIONED;
        break;
      default:
        this.targetHostState = HostState.ERROR;
        break;
    }
  }

  private void handleTaskFailure(ChangeHostModeTaskService.State state) throws ApiFeException {
    throw new HostStateChangeException(state.hostServiceLink,
        this.targetHostState, new Exception(state.taskState.failure.message));
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
    HostEntity hostEntity;
    List<HostEntity> hostEntityList = null;
    for (StepEntity step : taskCommand.getTask().getSteps()) {
      hostEntityList = step.getTransientResourceEntities(Host.KIND);
      if (!hostEntityList.isEmpty()) {
        break;
      }
    }
    hostEntity = hostEntityList.get(0);

    hostBackend.updateState(hostEntity, this.targetHostState);
  }
}
