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

import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

/**
 * Step to delete all image replica.
 */
public class ImageReplicasDeleteStepCmd extends StepCommand {

  private static Logger logger = LoggerFactory.getLogger(ImageReplicasDeleteStepCmd.class);

  public ImageReplicasDeleteStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(taskCommand, stepBackend, step);
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException {
    List<ImageEntity> entityList = step.getTransientResourceEntities(ImageEntity.KIND);
    checkArgument(entityList.size() == 1,
        "There should be only 1 image referenced by step %s", step.getId());
    ImageEntity image = entityList.get(0);

    try {
      taskCommand.getHousekeeperClient().removeImage(image.getId());
    } catch (InterruptedException | RpcException e) {
      logger.error("Delete image {} replicas failed", image.getId(), e);
    }
  }

  @Override
  protected void cleanup() {
  }
}
