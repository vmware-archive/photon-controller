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

import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.resource.gen.ImageReplication;

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

    ImageEntity imageEntity = entityList.get(0);
    ImageReplication replicationType;
    switch (imageEntity.getReplicationType()) {
      case ON_DEMAND:
        replicationType = ImageReplication.ON_DEMAND;
        break;
      case EAGER:
        replicationType = ImageReplication.EAGER;
        break;
      default:
        throw new IllegalArgumentException("ImageReplicationType unknown: " + imageEntity.getReplicationType());
    }

    if (imageStore.isReplicationNeeded()) {
      try {
        List<String> dataStoreIdList = imageBackend.getSeededImageDatastores(imageEntity.getId());
        checkState(dataStoreIdList.size() >= 1, "The image should be present on at least one image datastore.");
        logger.info("Start replicating image {} in datastore {}", imageEntity.getId(), dataStoreIdList.get(0));

        taskCommand.getHousekeeperClient().replicateImage(
            dataStoreIdList.get(0), imageEntity.getId(), replicationType);
      } catch (RpcException | InterruptedException | IllegalStateException e) {
        imageBackend.updateState(imageEntity, ImageState.ERROR);
        throw new ApiFeException(e);
      }
    } else {
      logger.info("Skip replicating image");
    }

    imageBackend.updateState(imageEntity, ImageState.READY);
  }

  @Override
  protected void cleanup() {
  }

}
