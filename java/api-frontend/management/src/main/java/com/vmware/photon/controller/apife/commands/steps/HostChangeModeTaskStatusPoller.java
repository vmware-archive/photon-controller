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

import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostDcpBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostStateChangeException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.xenon.common.TaskState;

/**
 * Polls host task status.
 */
public class HostChangeModeTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private final TaskCommand taskCommand;
  private final HostDcpBackend hostBackend;
  private final TaskBackend taskBackend;

  public HostChangeModeTaskStatusPoller(TaskCommand taskCommand, HostDcpBackend hostBackend,
                                        TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    ChangeHostModeTaskService.State serviceDocument = hostBackend.getDeployerClient()
        .getHostChangeModeStatus(remoteTaskLink);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityKind(Host.KIND);
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskState.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(serviceDocument);
    }
    return serviceDocument.taskState;
  }

  private void handleTaskFailure(ChangeHostModeTaskService.State state) throws ApiFeException {
    HostState hostState;
    switch (state.hostMode) {
      case NORMAL:
        hostState = HostState.READY;
        break;
      case ENTERING_MAINTENANCE:
        hostState = HostState.SUSPENDED;
        break;
      case MAINTENANCE:
        hostState = HostState.MAINTENANCE;
        break;
      case DEPROVISIONED:
        hostState = HostState.NOT_PROVISIONED;
        break;
      default:
        hostState = HostState.ERROR;
        break;
    }

    throw new HostStateChangeException(state.hostServiceLink,
        hostState, new Exception(state.taskState.failure.message));
  }


  @Override
  public int getTargetSubStage(Operation op) {
    return 0;
  }

  @Override
  public int getSubStage(TaskState taskState) {
    return 0;
  }
}
