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

package com.vmware.photon.controller.common.xenon.helpers.services;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.scheduler.RateLimitedWorkQueueService;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.testng.internal.Nullable;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class implements a test service associated with a {@link RateLimitedWorkQueueService}.
 */
public class TestServiceWithWorkQueue extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.SERVICES_ROOT + "/test-services-with-work-queue";

  public static FactoryService createFactory() {
    return FactoryService.create(TestServiceWithWorkQueue.class, TestServiceWithWorkQueue.State.class);
  }

  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the document self-link of the work queue service with which the
     * current task service is associated.
     */
    @NotNull
    @Immutable
    public String workQueueServiceLink;
  }

  public TestServiceWithWorkQueue() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      startOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    try {
      validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    startOp.setBody(startState).complete();

    try {
      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        patchWorkQueue(startState);
      } else {
        failTask(new IllegalStateException("Task is not restartable"));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);

    try {
      validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    patchOp.complete();

    try {
      switch (currentState.taskState.stage) {
        case STARTED:
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
          break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
          patchWorkQueue(currentState);
          break;
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
    checkState(patchState.taskState.stage.ordinal() > currentState.taskState.stage.ordinal());
  }

  private void patchWorkQueue(State currentState) {

    RateLimitedWorkQueueService.PatchState patchState = new RateLimitedWorkQueueService.PatchState();

    switch (currentState.taskState.stage) {
      case CREATED:
        patchState.pendingTaskServiceDelta = 1;
        patchState.taskServiceLink = getSelfLink();
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        patchState.runningTaskServiceDelta = -1;
        break;
      default:
        throw new IllegalStateException("Unexpected task state " + currentState.taskState.stage);
    }

    sendRequest(Operation
        .createPatch(this, currentState.workQueueServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failure));
  }

  @VisibleForTesting
  public static State buildPatch(TaskState.TaskStage taskStage) {
    return buildPatch(taskStage, null);
  }

  @VisibleForTesting
  public static State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
