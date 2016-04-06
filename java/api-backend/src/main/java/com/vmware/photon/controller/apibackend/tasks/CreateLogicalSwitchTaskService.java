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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalSwitchTask;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

/**
 * Implements an Xenon service that represents a task to create a logical switch.
 */
public class CreateLogicalSwitchTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-logical-switch-tasks";

  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(CreateLogicalSwitchTaskService.class);
  }

  public CreateLogicalSwitchTaskService() {
    super(CreateLogicalSwitchTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    CreateLogicalSwitchTask startState = start.getBody(CreateLogicalSwitchTask.class);
    InitializationUtils.initialize(startState);

    try {
      validateState(startState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, start, e);
      return;
    }

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    CreateLogicalSwitchTask currentState = getState(patch);
    CreateLogicalSwitchTask patchState = patch.getBody(CreateLogicalSwitchTask.class);

    try {
      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patch, e);
      return;
    }

    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        createLogicalSwitch();
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void createLogicalSwitch() {
    ServiceUtils.logInfo(this, "Creating logical switch");

    try {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED));
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(CreateLogicalSwitchTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(CreateLogicalSwitchTask currentState, CreateLogicalSwitchTask patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    return buildPatch(stage, t == null ? null : Utils.toServiceErrorResponse(t));
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage, @Nullable ServiceErrorResponse errorResponse) {
    CreateLogicalSwitchTask state = new CreateLogicalSwitchTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;

    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
