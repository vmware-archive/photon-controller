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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateContainerTaskService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForServiceTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForServiceTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.lang3.EnumUtils;

import javax.annotation.Nullable;

import java.io.IOException;

/**
 * Implements a DCP workflow service to create a container and wait for the service
 * within the container to start.
 */
public class CreateAndValidateContainerWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link DeploymentWorkflowService} task.
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
      CREATE_CONTAINER,
      WAIT_FOR_SERVICE_STARTUP
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link CreateAndValidateContainerWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the URL of the ContainerService object which
     * represents the container to be created.
     */
    @NotNull
    @Immutable
    public String containerServiceLink;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * Maximum retries for checking if service is ready.
     * - Primarily used for testing purposes ONLY
     */
    @Immutable
    public Integer maxRetries;
  }

  public CreateAndValidateContainerWorkflowService() {
    super(State.class);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (null == startState.maxRetries) {
      startState.maxRetries = HostUtils.getDeployerContext(this).getWaitForServiceMaxRetryCount();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_CONTAINER;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed for the current
   * service instance.
   *
   * @param patch Supplies the start operation object.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies current state object.
   */
  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving from %s:%s to stage %s:%s",
          startState.taskState.stage, startState.taskState.subStage,
          patchState.taskState.stage, patchState.taskState.subStage);
    }

    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method performs document owner operations in response to a patch operation.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedStage(State currentState) throws IOException {
    switch (currentState.taskState.subStage) {
      case CREATE_CONTAINER:
        processCreateContainer(currentState);
        break;
      case WAIT_FOR_SERVICE_STARTUP:
        processGetContainerService(currentState);
        break;
    }
  }

  /**
   * This method creates containers based on the container service entities.
   */
  private void processCreateContainer(final State currentState) {
    final Service service = this;

    FutureCallback<CreateContainerTaskService.State> callback = new FutureCallback<CreateContainerTaskService.State>() {
      @Override
      public void onSuccess(@Nullable CreateContainerTaskService.State result) {
        if (result.taskState.stage == TaskState.TaskStage.FAILED) {
          State state = buildPatch(TaskState.TaskStage.FAILED, null, null);
          state.taskState.failure = result.taskState.failure;
          TaskUtils.sendSelfPatch(service, state);
          return;
        }

        if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
          return;
        }

        TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.STARTED,
            TaskState.SubStage.WAIT_FOR_SERVICE_STARTUP, null));
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    CreateContainerTaskService.State startState = new CreateContainerTaskService.State();
    startState.containerServiceLink = currentState.containerServiceLink;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;

    TaskUtils.startTaskAsync(
        this,
        CreateContainerTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateContainerTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void processGetContainerService(final State currentState) throws IOException {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerService.State containerState = operation.getBody(ContainerService.State.class);
          processGetContainerTemplate(currentState, containerState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.containerServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  private void processGetContainerTemplate(final State currentState, final ContainerService.State containerState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerTemplateService.State containerTemplateState = operation.getBody(
              ContainerTemplateService.State.class);
          waitForService(currentState, containerState, containerTemplateState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerState.containerTemplateServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method takes a container template and calls the corresponding
   * child task.
   *
   * @param currentState           Supplies the current state object.
   * @param containerState         Supplies the container service state object.
   * @param containerTemplateState Supplies the container template service state object.
   */
  private void waitForService(final State currentState,
                              final ContainerService.State containerState,
                              final ContainerTemplateService.State containerTemplateState) {
    if (EnumUtils.isValidEnum(ContainersConfig.ContainerType.class, containerTemplateState.name)) {
      ServiceUtils.logInfo(this, "Waiting for: %s start", containerTemplateState.name);
      final Service service = this;

      FutureCallback<WaitForServiceTaskService.State> callback = new FutureCallback<WaitForServiceTaskService
          .State>() {
        @Override
        public void onSuccess(@Nullable WaitForServiceTaskService.State result) {
          if (result.taskState.stage == TaskState.TaskStage.FAILED) {
            State state = buildPatch(TaskState.TaskStage.FAILED, null, null);
            state.taskState.failure = result.taskState.failure;
            TaskUtils.sendSelfPatch(service, state);
            return;
          }

          if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
            return;
          }

          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED, null, null));
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      };

      WaitForServiceTaskService.State startState = new WaitForServiceTaskService.State();
      startState.vmServiceLink = containerState.vmServiceLink;
      startState.containerType = containerTemplateState.name;
      startState.taskPollDelay = currentState.taskPollDelay;
      startState.maxRetries = currentState.maxRetries;

      TaskUtils.startTaskAsync(
          this,
          WaitForServiceTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          WaitForServiceTaskService.State.class,
          currentState.taskPollDelay,
          callback);
    } else {
      ServiceUtils.logInfo(this, "Non-standard container template: %s, will skip wait!", containerTemplateState.name);
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null, null));
    }
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }
}
