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

import com.vmware.photon.controller.api.frontend.backends.ImageBackend;
import com.vmware.photon.controller.api.frontend.backends.StepBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.entities.ImageEntity;
import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.exceptions.ApiFeException;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.api.frontend.lib.ImageStore;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.housekeeper.xenon.ImageSeederService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

/**
 * Step to replicate an image.
 */
public class ImageReplicateStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(ImageReplicateStepCmd.class);

  private final ImageBackend imageBackend;

  private final ImageStore imageStore;

  private ImageEntity imageEntity;

  public ImageReplicateStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                               ImageBackend imageBackend, ImageStore imageStore) {
    super(taskCommand, stepBackend, step);
    this.imageBackend = imageBackend;
    this.imageStore = imageStore;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException {
    List<ImageEntity> entityList = step.getTransientResourceEntities(ImageEntity.KIND);
    checkArgument(entityList.size() == 1,
        "There should be only 1 image referenced by step %s", step.getId());

    this.imageEntity = entityList.get(0);

    if (imageStore.isReplicationNeeded()) {
      List<String> dataStoreIdList = imageBackend.getSeededImageDatastores(imageEntity.getId());
      try {
        checkState(dataStoreIdList.size() >= 1, "The image should be present on at least one image datastore.");
      } catch (IllegalStateException ex) {
        throw new ApiFeException(ex);
      }
      logger.info("Start replicating image {} in datastore {}", imageEntity.getId(), dataStoreIdList.get(0));

      ImageSeederService.State serviceDocument = taskCommand.getHousekeeperXenonClient()
          .replicateImage(dataStoreIdList.get(0), imageEntity);
    } else {
      logger.info("Skip replicating image");
    }
    this.imageBackend.updateState(this.imageEntity, ImageState.READY);
  }

  @Override
  protected void cleanup() {
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    super.markAsFailed(t);

    if (this.imageEntity != null) {
      logger.info("Image replicate failed, mark {} state as ERROR", this.imageEntity.getId());

      if (imageEntity.getState() == ImageState.ERROR) {
        return;
      }
      try {
        this.imageBackend.updateState(this.imageEntity, ImageState.ERROR);
      } catch (ExternalException e) {
        logger.warn("Marking image state to ERROR is failed. ImageEntity: {} Error: {}", this.imageEntity, e);
      }
    }
  }
}
