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
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.exceptions.external.DuplicateHostException;
import com.vmware.photon.controller.apife.exceptions.external.InvalidLoginException;
import com.vmware.photon.controller.apife.exceptions.external.IpAddressInUseException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.xenon.task.ValidateHostTaskService;
import com.vmware.xenon.common.TaskState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Polls host task status.
 */
public class HostCreateTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private static final Logger logger = LoggerFactory.getLogger(HostCreateTaskStatusPoller.class);

  private final TaskCommand taskCommand;
  private final HostDcpBackend hostBackend;
  private final TaskBackend taskBackend;
  private final HostEntity entity;

  public HostCreateTaskStatusPoller(TaskCommand taskCommand, HostDcpBackend hostBackend,
                                    TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.hostBackend = hostBackend;
    this.taskBackend = taskBackend;
    List<HostEntity> entityList = null;
    for (StepEntity step : taskCommand.getTask().getSteps()) {
      entityList = step.getTransientResourceEntities(Host.KIND);
      if (!entityList.isEmpty()) {
        break;
      }
    }
    this.entity = entityList.get(0);
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    ValidateHostTaskService.State serviceDocument = hostBackend.getDeployerClient()
        .getHostCreationStatus(remoteTaskLink);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityKind(Host.KIND);
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskState.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(serviceDocument);
    }
    return serviceDocument.taskState;
  }

  private void handleTaskFailure(ValidateHostTaskService.State state) throws ApiFeException {
    logger.info("Host create failed, mark entity {} state as ERROR", this.entity);
    if (this.entity != null) {
      this.hostBackend.updateState(this.entity, HostState.ERROR);
    }

    switch (state.taskState.resultCode) {
      case ExistHostWithSameAddress:
        throw new DuplicateHostException(state.taskState.failure.message);
      case InvalidLogin:
        throw new InvalidLoginException();
      case ManagementVmAddressAlreadyInUse:
        throw new IpAddressInUseException(state.hostAddress);
      default:
        break;
    }
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
    logger.info("handleDone for HostCreateTaskPoller Host: {}", entity);
    if (this.entity != null) {
      hostBackend.updateState(entity, HostState.NOT_PROVISIONED);
    }
  }
}
