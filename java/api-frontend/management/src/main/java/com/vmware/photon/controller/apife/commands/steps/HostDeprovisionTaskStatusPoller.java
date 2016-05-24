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
import com.vmware.photon.controller.api.Host;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.HostDcpBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.HostDeprovisionFailedException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.dcp.workflow.DeprovisionHostWorkflowService;
import com.vmware.xenon.common.TaskState;

import java.util.List;

/**
 * Polls host task status.
 */
public class HostDeprovisionTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private final TaskCommand taskCommand;
  private final HostDcpBackend hostBackend;
  private final TaskBackend taskBackend;

  public HostDeprovisionTaskStatusPoller(TaskCommand taskCommand, HostDcpBackend hostBackend,
                                         TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    DeprovisionHostWorkflowService.State serviceDocument = hostBackend.getDeployerClient()
        .getHostDeprovisionStatus(remoteTaskLink);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityKind(Host.KIND);
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskState.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(serviceDocument);
    }
    return serviceDocument.taskState;
  }

  private void handleTaskFailure(DeprovisionHostWorkflowService.State state) throws ApiFeException {
    throw new HostDeprovisionFailedException(state.uniqueId, state.taskState.failure.message);
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
      hostEntityList = step.getTransientResourceEntities(Deployment.KIND);
      if (!hostEntityList.isEmpty()) {
        break;
      }
    }
    hostEntity = hostEntityList.get(0);

    hostBackend.updateState(hostEntity, HostState.NOT_PROVISIONED);
  }
}
