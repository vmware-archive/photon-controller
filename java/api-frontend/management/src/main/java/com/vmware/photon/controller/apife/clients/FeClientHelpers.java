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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.backends.TaskBackend;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Helper methods to support the FeClient classes.
 */
public class FeClientHelpers {

  private static final Logger logger = LoggerFactory.getLogger(FeClientHelpers.class);

  /**
   * Run REPLICATE_IMAGE step asynchronously.
   *
   * @param commandFactory
   * @param executor
   * @param taskBackend
   * @param taskEntity
   * @return
   * @throws ExternalException
   */
  public static Task runImageReplicateAsyncSteps(
      TaskCommandFactory commandFactory, ExecutorService executor,
      TaskBackend taskBackend, TaskEntity taskEntity)
      throws ExternalException {
    taskEntity.findStep(Operation.REPLICATE_IMAGE).setDisabled(false);
    taskEntity.findStep(Operation.QUERY_REPLICATE_IMAGE_TASK_RESULT).setDisabled(false);

    TaskCommand command = commandFactory.create(taskEntity);
    logger.info("Run asynchronous task steps for task: {} {}", taskEntity.getId(), taskEntity.getOperation());
    executor.submit(command);

    Task task = taskBackend.getApiRepresentation(taskEntity.getId());
    return task;
  }
}
