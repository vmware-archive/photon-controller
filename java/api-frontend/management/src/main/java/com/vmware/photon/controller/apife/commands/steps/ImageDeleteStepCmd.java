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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * Step to delete an image.
 */
public class ImageDeleteStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(ImageDeleteStepCmd.class);

  private final ImageBackend imageBackend;

  private final ImageStore imageStore;

  public ImageDeleteStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
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
    ImageEntity image = entityList.get(0);

    try {
      imageStore.deleteImage(image.getId());
    } catch (Exception e) {
      logger.error("Delete image {} failed", image.getId(), e);
      imageBackend.updateState(image, ImageState.ERROR);
      throw e;
    }

    imageBackend.tombstone(image);
  }

  @Override
  protected void cleanup() {
  }
}
