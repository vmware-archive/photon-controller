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

import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.housekeeper.dcp.ImageSeederService;
import com.vmware.xenon.common.TaskState;

import java.util.List;

/**
 * Polls image replicate task status.
 */
public class ImageReplicateTaskStatusPoller implements XenonTaskStatusStepCmd.XenonTaskStatusPoller {
  private final TaskCommand taskCommand;
  private final ImageBackend imageBackend;
  private final TaskBackend taskBackend;
  private final ImageEntity entity;

  public ImageReplicateTaskStatusPoller(TaskCommand taskCommand, ImageBackend imageBackend,
                                        TaskBackend taskBackend) {
    this.taskCommand = taskCommand;
    this.imageBackend = imageBackend;
    this.taskBackend = taskBackend;
    List<ImageEntity> entities = null;
    for (StepEntity step : taskCommand.getTask().getSteps()) {
      entities = step.getTransientResourceEntities(Image.KIND);
      if (!entities.isEmpty()) {
        break;
      }
    }
    this.entity = entities.get(0);
  }

  @Override
  public TaskState poll(String remoteTaskLink) throws DocumentNotFoundException, ApiFeException {
    ImageSeederService.State serviceDocument = taskCommand.getHousekeeperXenonClient()
        .getReplicateImageStatus(remoteTaskLink);
    if (serviceDocument.taskInfo.stage == TaskState.TaskStage.FINISHED) {
      TaskEntity taskEntity = taskCommand.getTask();
      taskEntity.setEntityKind(Image.KIND);
      taskBackend.update(taskEntity);
    } else if (serviceDocument.taskInfo.stage == TaskState.TaskStage.FAILED) {
      handleTaskFailure(serviceDocument);
    }
    return serviceDocument.taskInfo;
  }

  private void handleTaskFailure(ImageSeederService.State state) throws ApiFeException {
    if (this.entity != null) {
      this.imageBackend.updateState(this.entity, ImageState.ERROR);
    }
    throw new ExternalException(state.taskInfo.failure.message);
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
    if (this.entity != null) {
      imageBackend.updateState(this.entity, ImageState.READY);
    }
  }
}
