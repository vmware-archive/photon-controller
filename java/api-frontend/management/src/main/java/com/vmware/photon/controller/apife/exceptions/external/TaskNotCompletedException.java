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

package com.vmware.photon.controller.apife.exceptions.external;

import com.vmware.photon.controller.apife.entities.StepEntity;

import org.apache.commons.lang3.StringUtils;

/**
 * Gets thrown when requested disk is not found.
 */
public class TaskNotCompletedException extends ExternalException {
  private final StepEntity stepEntity;

  public TaskNotCompletedException(StepEntity stepEntity) {
    super(ErrorCode.TASK_NOT_COMPLETED);
    this.stepEntity = stepEntity;
    addData("id", stepEntity.getId());
    addData("state", stepEntity.getState().toString());
    addData("error", StringUtils.join(stepEntity.getErrors(), ';'));
  }

  @Override
  public String getMessage() {
    return String.format("Step \"%s\" did not complete.", stepEntity);
  }
}
