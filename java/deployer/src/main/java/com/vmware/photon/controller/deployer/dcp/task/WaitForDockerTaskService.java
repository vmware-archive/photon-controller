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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP microservice which performs the task of waiting for the Docker endpoint on a VM to
 * become ready.
 */
public class WaitForDockerTaskService extends StatefulService {

  /**
   * This class represent the document state associated with a single {@link WaitForDockerTaskService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link VmService} instance representing the VM instance on which
     * the current task should await the readiness of the Docker endpoint.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the length of the initial delay, in milliseconds, before beginning to poll.
     */
    @DefaultInteger(value = 10000)
    @Immutable
    public Integer delayInterval;

    /**
     * This value represents the interval, in milliseconds, to wait between polling iterations.
     */
    @DefaultInteger(value = 3000)
    @Immutable
    public Integer pollInterval;

    /**
     * This value represents the number of polling iterations to attempt before declaring failure.
     */
    @DefaultInteger(value = 600)
    @Immutable
    public Integer maxPollIterations;

    /**
     * This value represents the number of successful polling iterations which are required before success is declared.
     */
    @DefaultInteger(value = 60)
    @Immutable
    public Integer requiredSuccessfulIterations;

    /**
     * This value represents the IP address of the VM on which to poll the endpoint.
     */
    @WriteOnce
    public String ipAddress;

    /**
     * This value represents the number of polling iterations which have been performed.
     */
    @DefaultInteger(value = 0)
    public Integer iterations;

    /**
     * This value represents the number of consecutive successful polling iterations which have been observed.
     */
    @DefaultInteger(value = 0)
    public Integer successfulIterations;
  }

  public WaitForDockerTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.DELAY;
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch operation for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        switch (currentState.taskState.subStage) {
          case DELAY:
            scheduleSelfPatch(currentState);
            break;
          case POLL:
            processWaitForDocker(currentState);
            break;
        }
      }

    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    validateTaskStage(state.taskState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    validateTaskStage(patchState.taskState);
    validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  private void validateTaskStage(TaskState taskState) {
    ValidationUtils.validateTaskStage(taskState);
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        switch (taskState.subStage) {
          case DELAY:
          case POLL:
            break;
          default:
            throw new IllegalStateException("Unrecognized task sub-stage: " + taskState.subStage);
        }
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
  }

  private void validateTaskStageProgression(TaskState startState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(startState, patchState);
    if (null != startState.subStage && null != patchState.subStage) {
      checkState(patchState.subStage.ordinal() >= startState.subStage.ordinal());
    }
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    if (null != patchState.ipAddress) {
      ServiceUtils.logInfo(this, "Setting ip address to %s", patchState.ipAddress);
      startState.ipAddress = patchState.ipAddress;
    }

    if (null != patchState.iterations) {
      ServiceUtils.logInfo(this, "Setting iteration count to %s", patchState.iterations);
      startState.iterations = patchState.iterations;
    }

    if (null != patchState.successfulIterations) {
      ServiceUtils.logInfo(this, "Setting successful iteration count to %s", patchState.successfulIterations);
      startState.successfulIterations = patchState.successfulIterations;
    }

    return startState;
  }

  private void scheduleSelfPatch(State currentState) {
    ServiceUtils.logInfo(this, "Scheduling self-patch to stage STARTED:POLL");
    Runnable runnable = () -> sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.POLL);
    getHost().schedule(runnable, currentState.delayInterval, TimeUnit.MILLISECONDS);
  }

  private void processWaitForDocker(State currentState) {
    if (null == currentState.ipAddress) {
      readIpAddress(currentState);
    } else {
      pollDockerEndpoint(currentState);
    }
  }

  private void readIpAddress(final State currentState) {
    Operation.CompletionHandler completionHandler = (operation, throwable) -> {
      if (null != throwable) {
        failTask(throwable);
        return;
      }

      try {
        State patchState = buildPatch(currentState.taskState.stage, currentState.taskState.subStage, null);
        patchState.ipAddress = operation.getBody(VmService.State.class).ipAddress;
        TaskUtils.sendSelfPatch(this, patchState);
      } catch (Throwable t) {
        failTask(t);
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  private void pollDockerEndpoint(final State currentState) {
    ServiceUtils.logInfo(this, "Performing poll of VM endpoint (iterations: %s)", currentState.iterations);

    Runnable runnable = () -> {
      try {
        String dockerInfo = HostUtils.getDockerProvisionerFactory(this).create(currentState.ipAddress).getInfo();
        ServiceUtils.logInfo(this, "Received docker info response %s", dockerInfo);
        processSuccessfulPollingIteration(currentState);
      } catch (Throwable t) {
        processFailedPollingIteration(currentState, t);
      }
    };

    HostUtils.getListeningExecutorService(this).submit(runnable);
  }

  private void processSuccessfulPollingIteration(State currentState) {
    if (++currentState.successfulIterations >= currentState.requiredSuccessfulIterations) {
      State patchState = buildPatch(TaskState.TaskStage.FINISHED, null, null);
      patchState.successfulIterations = currentState.successfulIterations;
      patchState.iterations = currentState.iterations + 1;
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      getHost().schedule(() -> {
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.POLL, null);
        patchState.successfulIterations = currentState.successfulIterations;
        patchState.iterations = currentState.iterations + 1;
        TaskUtils.sendSelfPatch(this, patchState);
      }, currentState.pollInterval, TimeUnit.MILLISECONDS);
    }
  }

  private void processFailedPollingIteration(final State currentState, Throwable t) {
    if (++currentState.iterations >= currentState.maxPollIterations) {
      State patchState = buildPatch(TaskState.TaskStage.FAILED, null, t);
      patchState.iterations = currentState.iterations;
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      getHost().schedule(() -> {
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.POLL, null);
        patchState.successfulIterations = 0;
        patchState.iterations = currentState.iterations;
        TaskUtils.sendSelfPatch(this, patchState);
      }, currentState.pollInterval, TimeUnit.MILLISECONDS);
    }
  }

  private void sendStageProgressPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }

  /**
   * This class represents the state of a task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum SubStage {
      DELAY,
      POLL,
    }
  }
}
