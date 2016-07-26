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

import com.vmware.photon.controller.apife.backends.StepBackend;
import com.vmware.photon.controller.apife.commands.BaseCommand;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.exceptions.external.TaskNotFoundException;

import org.apache.commons.lang3.StringUtils;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class extends BaseCommand and represents a single activity. Its corresponding DB/API
 * object is a Step.
 */
public abstract class StepCommand extends BaseCommand {

  protected final TaskCommand taskCommand;
  protected final StepBackend stepBackend;
  protected final StepEntity step;

  protected StepCommand(TaskCommand taskCommand, StepBackend stepBackend, StepEntity step) {
    super(getActivityId(step));
    this.taskCommand = taskCommand;
    this.stepBackend = stepBackend;
    this.step = step;
    checkNotNull(step);
  }

  private static String getActivityId(StepEntity stepEntity) {
    String activityId = "";
    if (stepEntity.getTask() != null &&
        StringUtils.isNotBlank(stepEntity.getTask().getId())) {
      activityId = stepEntity.getTask().getId();
    }

    String operation = "";
    if (stepEntity.getOperation() != null) {
      operation = stepEntity.getOperation().getOperation();
    }

    if (StringUtils.isNotBlank(operation)) {
      activityId = activityId + ":" + operation;
    }

    return activityId;
  }

  @Override
  protected void markAsStarted() throws TaskNotFoundException {
    stepBackend.markStepAsStarted(step);
  }

  @Override
  protected void markAsDone() throws Throwable {
    stepBackend.markStepAsDone(step);
  }

  @Override
  protected void markAsFailed(Throwable t) throws TaskNotFoundException {
    stepBackend.markStepAsFailed(step, t);
  }
}
