/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.housekeeper.xenon.HostsConfigSyncService;
import com.vmware.xenon.common.TaskState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls hosts config task status.
 */
public class HostsConfigSyncTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private static final Logger logger = LoggerFactory.getLogger(HostsConfigSyncTaskStatusPoller.class);

  private final TaskCommand taskCommand;
  private final TaskBackend taskBackend;

  public HostsConfigSyncTaskStatusPoller(TaskCommand taskCommand, TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.taskBackend = taskBackend;
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    HostsConfigSyncService.State serviceDocument = taskCommand.getHousekeeperXenonClient()
        .getSyncConfigSyncStatus(remoteTaskLink);
    if (serviceDocument.taskState.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskState.stage == TaskState.TaskStage.FAILED) {
      throw new ExternalException(serviceDocument.taskState.failure.message);
    }
    return serviceDocument.taskState;
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
    logger.info("handleDone for HostsConfigSyncTaskStatusPoller");
  }
}
