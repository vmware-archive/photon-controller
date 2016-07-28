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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.BackendTaskExecutor;
import com.vmware.photon.controller.api.frontend.backends.ImageBackend;
import com.vmware.photon.controller.api.frontend.backends.TaskBackend;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommand;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;

/**
 * Frontend client for image used by {@link ImageResource}.
 */
@Singleton
public class ImageFeClient {

  private static final Logger logger = LoggerFactory.getLogger(VmFeClient.class);

  private final TaskCommandFactory commandFactory;
  private final ExecutorService executor;
  private final ImageBackend imageBackend;
  private final TaskBackend taskBackend;

  @Inject
  public ImageFeClient(TaskCommandFactory commandFactory, ImageBackend imageBackend,
                       @BackendTaskExecutor ExecutorService executor, TaskBackend taskBackend) {
    this.commandFactory = commandFactory;
    this.executor = executor;
    this.imageBackend = imageBackend;
    this.taskBackend = taskBackend;
  }

  public Task create(InputStream inputStream, String name, ImageReplicationType replicationType) throws
      InternalException, ExternalException {
    TaskEntity taskEntity = imageBackend.prepareImageUpload(inputStream, name, replicationType);
    boolean hasReplicateImageStep = taskEntity.containsStep(Operation.REPLICATE_IMAGE);

    // Run UPLOAD_IMAGE step synchronously.
    Task task = runImageUploadSyncSteps(taskEntity, hasReplicateImageStep);
    if (!task.getState().equals(TaskEntity.State.STARTED.toString())) {
      logger.error("Run task {} went into state {}", task.getId(), task.getState());
      return task;
    }
    if (!hasReplicateImageStep) {
      return task;
    }

    // Run REPLICATE_IMAGE step asynchronously.
    task = runImageReplicateAsyncSteps(commandFactory, executor, taskBackend, taskEntity);
    return task;
  }

  public Task delete(String id) throws ExternalException {
    TaskEntity taskEntity = imageBackend.prepareImageDelete(id);
    Task task = taskBackend.getApiRepresentation(taskEntity);

    return task;
  }

  public Image get(String id) throws ExternalException {
    return imageBackend.toApiRepresentation(id);
  }

  public ResourceList<Image> list(Optional<String> name, Optional<Integer> pageSize) throws ExternalException {
    return imageBackend.filter(name, pageSize);
  }

  public ResourceList<Image> getImagesPage(String pageLink) throws PageExpiredException {
    return imageBackend.getImagesPage(pageLink);
  }

  private Task runImageUploadSyncSteps(TaskEntity taskEntity, boolean hasReplicateImageStep)
      throws ExternalException {
    if (hasReplicateImageStep) {
      taskEntity.findStep(Operation.REPLICATE_IMAGE).setDisabled(true);
    }
    TaskCommand command = commandFactory.create(taskEntity);
    logger.info("Run synchronous task steps for task: {} {}", taskEntity.getId(), taskEntity.getOperation());
    command.run();

    Task task = taskBackend.getApiRepresentation(taskEntity.getId());
    return task;
  }

  private static Task runImageReplicateAsyncSteps(
      TaskCommandFactory commandFactory, ExecutorService executor,
      TaskBackend taskBackend, TaskEntity taskEntity)
      throws ExternalException {
    taskEntity.findStep(Operation.REPLICATE_IMAGE).setDisabled(false);

    TaskCommand command = commandFactory.create(taskEntity);
    logger.info("Run asynchronous task steps for task: {} {}", taskEntity.getId(), taskEntity.getOperation());
    executor.submit(command);

    Task task = taskBackend.getApiRepresentation(taskEntity.getId());
    return task;
  }

}
