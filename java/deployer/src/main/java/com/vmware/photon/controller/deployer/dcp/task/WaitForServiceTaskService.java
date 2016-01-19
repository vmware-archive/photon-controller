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

import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactory;
import com.vmware.photon.controller.deployer.healthcheck.HealthCheckHelperFactoryProvider;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.EnumUtils;

import javax.annotation.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Waits for the specified Zookeeper to be up and running and joined in a cluster.
 */
public class WaitForServiceTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link WaitForServiceTaskService}
   * instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * The vm service link for the zookeeper instance.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * The type of container service we are checking.
     */
    @NotNull
    @Immutable
    public String containerType;

    /**
     * Maximum retries to check zookeeper liveness.
     */
    @Immutable
    public Integer maxRetries;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value allows processing of post and patch operations to be
     * disabled, effectively making all service instances listeners. It is set
     * only in test scenarios.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    @DefaultInteger(value = 10)
    @Immutable
    public Integer consecutiveReadyCount;
  }

  public WaitForServiceTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed for the current service instance.
   *
   * @param start
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (null == startState.maxRetries) {
      startState.maxRetries = HostUtils.getDeployerContext(this).getWaitForServiceMaxRetryCount();
    }

    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current service.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateStartState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState
   * @param patchState
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
    }

    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method performs document owner operations in response to a patch
   * operation.
   *
   * @param currentState
   */
  private void processStartedStage(final State currentState) {
    final Service service = this;
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);

          if (EnumUtils.isValidEnum(ContainersConfig.ContainerType.class, currentState.containerType)) {
            ContainersConfig.ContainerType containerType =
                ContainersConfig.ContainerType.valueOf(currentState.containerType);

            final HealthChecker healthChecker =
                getHealthCheckHelperFactory().create(service, containerType, vmState.ipAddress).getHealthChecker();
            final AtomicInteger retryCounter = new AtomicInteger(currentState.maxRetries);
            final AtomicInteger consecutiveReady = new AtomicInteger(0);

            scheduleHealthCheckQuery(currentState, healthChecker, retryCounter, consecutiveReady);
          } else {
            // Assume success
            sendStageProgressPatch(TaskState.TaskStage.FINISHED);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation get = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(handler);

    sendRequest(get);
  }

  private void scheduleHealthCheckQuery(
      final State currentState,
      final HealthChecker healthChecker,
      final AtomicInteger retryCounter,
      final AtomicInteger consecutiveReady) {

    final Service service = this;

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (retryCounter.decrementAndGet() < 0) {
          String errMessage = String.format("%s not ready after %s retries",
              currentState.containerType,
              currentState.maxRetries);
          ServiceUtils.logSevere(service, errMessage);
          failTask(new RuntimeException(errMessage));
          return;
        }

        ServiceUtils.logInfo(service, "HealthCheck (%s) retry: %s of maxRetries(%s)",
            currentState.containerType,
            currentState.maxRetries - retryCounter.get(),
            currentState.maxRetries);


        if (healthChecker.isReady()) {
          if (consecutiveReady.incrementAndGet() > currentState.consecutiveReadyCount
              || retryCounter.get() == 0) {
            sendStageProgressPatch(TaskState.TaskStage.FINISHED);
            return;
          }
        } else {
          consecutiveReady.set(0);
        }

        scheduleHealthCheckQuery(currentState, healthChecker, retryCounter, consecutiveReady);
      }
    };

    getHost().schedule(runnable, currentState.taskPollDelay, TimeUnit.MILLISECONDS);
  }

  @VisibleForTesting
  protected HealthCheckHelperFactory getHealthCheckHelperFactory() {
    return ((HealthCheckHelperFactoryProvider) getHost()).getHealthCheckHelperFactory();
  }

  /**
   * This method sends a patch operation to the current service instance
   * to move to a new state.
   *
   * @param stage
   */
  private void sendStageProgressPatch(TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "sendStageProgressPatch %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage
   * @param e
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }
}
