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

import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageState;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.ApiFeException;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.apife.lib.image.ImageLoader;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * StepCommand that creates image by cloning from vm.
 */
public class VmCreateImageStepCmd extends StepCommand {

  private static final Logger logger = LoggerFactory.getLogger(VmCreateImageStepCmd.class);

  private final ImageBackend imageBackend;

  private final ImageStore imageStore;

  public VmCreateImageStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                              ImageBackend imageBackend, ImageStore imageStore) {
    super(taskCommand, stepBackend, step);
    this.imageBackend = imageBackend;
    this.imageStore = imageStore;
  }

  @Override
  protected void execute() throws ApiFeException, InterruptedException, RpcException {
    List<VmEntity> vmList = step.getTransientResourceEntities(Vm.KIND);
    Preconditions.checkArgument(vmList.size() == 1,
        "There should be only 1 VM referenced by step %s", step.getId());
    VmEntity vm = vmList.get(0);

    List<ImageEntity> imageList = step.getTransientResourceEntities(Image.KIND);
    Preconditions.checkArgument(imageList.size() == 2,
        "There should be 2 images referenced by step %s", step.getId());
    ImageEntity imageEntity = imageList.get(0);
    ImageEntity vmImage = imageList.get(1);
    Preconditions.checkState(
        Objects.equals(vmImage.getId(), vm.getImageId()),
        "image id is inconsistent: %s v.s. %s", vmImage.getId(), vm.getImageId());

    try {
      getImageLoader().createImageFromVm(imageEntity, vm.getId(), vm.getHost());
      imageBackend.updateImageDatastore(imageEntity.getId(), imageStore.getDatastore());
    } catch (ExternalException e) {
      imageBackend.updateState(imageEntity, ImageState.ERROR);
      throw e;
    } catch (Exception e) {
      imageBackend.updateState(imageEntity, ImageState.ERROR);
      throw new InternalException(e);
    }
  }

  @Override
  protected void cleanup() {
  }

  @VisibleForTesting
  protected ImageLoader getImageLoader() throws InternalException {
    return new ImageLoader(imageStore);
  }
}
