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

package com.vmware.photon.controller.apife.commands.steps;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepCommand for checking the progress of image seeding.
 * If image seeding is done, an empty list of candidate datastores
 * (available for reading images from) is put in the task context.
 * If image seeding is not finished, a filled list of candidate
 * datastores is saved in the task context.
 * <p>
 * The following steps can use these saved candidate datastores
 * to create constraints when creating VMs (as an example).
 */
public class ImageSeedingProgressCheckStepCmd extends StepCommand {
  public static final String IMAGE_ID_KEY_NAME = "image_id";
  public static final String CANDIDATE_IMAGE_STORES_KEY_NAME = "candidate_image_stores";

  private static final Logger logger = LoggerFactory.getLogger(ImageSeedingProgressCheckStepCmd.class);

  private ImageBackend imageBackend;
  private StepEntity stepEntity;

  public ImageSeedingProgressCheckStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity stepEntity,
                                          ImageBackend imageBackend) {
    super(taskCommand, stepBackend, stepEntity);

    this.stepEntity = stepEntity;
    this.imageBackend = imageBackend;
  }

  @Override
  protected void execute() throws ExternalException {
    TaskEntity taskEntity = stepEntity.getTask();
    String imageId = (String) taskEntity.getTransientResources(IMAGE_ID_KEY_NAME);

    taskEntity.setTransientResources(CANDIDATE_IMAGE_STORES_KEY_NAME,
        imageBackend.getSeededImageDatastores(imageId));
  }

  @Override
  protected void cleanup() {
  }
}
