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

package com.vmware.photon.controller.api.frontend.exceptions.external;

import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.model.Operation;

import org.apache.commons.lang3.StringUtils;

/**
 * Gets thrown when targeted step is not found.
 */
public class StepNotFoundException extends ExternalException {
  private final TaskEntity taskEntity;
  private final Operation operation;

  public StepNotFoundException(TaskEntity taskEntity, Operation operation) {
    super(ErrorCode.STEP_NOT_FOUND);
    this.operation = operation;
    this.taskEntity = taskEntity;
    addData("id", taskEntity.getId());
    addData("state", taskEntity.getState().toString());
    addData("steps", StringUtils.join(taskEntity.getSteps(), ';'));
  }

  @Override
  public String getMessage() {
    return String.format("Step \"%s\" is not found in task \"%s\".",
        operation, taskEntity.getId());
  }
}
