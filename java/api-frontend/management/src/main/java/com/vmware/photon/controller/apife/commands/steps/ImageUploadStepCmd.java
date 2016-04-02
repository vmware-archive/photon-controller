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
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.ImageBackend;
import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmdkFormatException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.apife.lib.image.ImageLoader;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.util.List;

/**
 * Step to upload an image.
 */
public class ImageUploadStepCmd extends StepCommand {

  public static final String INPUT_STREAM = "input-stream";

  private static final Logger logger = LoggerFactory.getLogger(ImageUploadStepCmd.class);

  private final ImageBackend imageBackend;

  private final ImageStore imageStore;

  private final ImageConfig config;

  public ImageUploadStepCmd(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step,
                            ImageBackend imageBackend, ImageStore imageStore, ImageConfig imageConfig) {
    super(taskCommand, stepBackend, step);
    this.imageBackend = imageBackend;
    this.imageStore = imageStore;
    this.config = imageConfig;
  }

  @Override
  protected void execute() throws ApiFeException {
    InputStream inputStream = (InputStream) step.getTransientResource(INPUT_STREAM);
    checkNotNull(inputStream, "InputStream is not defined in TransientResource");

    List<ImageEntity> entityList = step.getTransientResourceEntities(ImageEntity.KIND);
    checkArgument(entityList.size() == 1,
        "There should be only 1 image referenced by step %s", step.getId());

    ImageEntity imageEntity = entityList.get(0);
    try {
      ImageLoader.Result result = getImageLoader().uploadImage(imageEntity, inputStream);
      imageBackend.updateSettings(imageEntity, result.imageSettings);
      imageBackend.updateSize(imageEntity, result.imageSize);
      imageBackend.updateImageDatastore(imageEntity.getId(), imageStore.getDatastore());
    } catch (VmdkFormatException e) {
      imageBackend.updateState(imageEntity, ImageState.ERROR);
      throw new InvalidVmdkFormatException(e.getMessage());
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
