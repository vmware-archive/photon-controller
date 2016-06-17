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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateFactoryService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerSpecLayoutTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CreateVmSpecLayoutTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateVmSpecLayoutTaskService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.HashMap;

/**
 * Implements a Xenon workflow service representing the manifest generation workflow.
 */
public class CreateManagementPlaneLayoutWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link CreateManagementPlaneLayoutWorkflowService} task.
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
      CREATE_CONTAINER_TEMPLATES,
      ALLOCATE_DOCKER_VMS,
      SCHEDULE_CONTAINER_ALLOCATION,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link CreateManagementPlaneLayoutWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * Task State.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents if we deploy a loadbalancer.
     */
    @DefaultBoolean(value = true)
    @Immutable
    public Boolean isLoadbalancerEnabled;

    /**
     * This value represents if we deploy with authentication enabled.
     */
    @DefaultBoolean(value = false)
    @Immutable
    public Boolean isAuthEnabled;

    /**
     * This value represents the query specification which can be used to identify the hosts to create vms on.
     */
    @Immutable
    public QueryTask.QuerySpecification hostQuerySpecification;

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean isNewDeployment;
  }

  public CreateManagementPlaneLayoutWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  /**
   * This method is called when a start operation is performed for the current service instance.
   *
   * @param start
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service");
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_CONTAINER_TEMPLATES;
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
        sendStageProgressPatch(startState.taskState);
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

  /**
   * This method validates a state object for internal consistency.
   *
   * @param startState Supplies current state object.
   */
  protected void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    // The task sub-state must be at least equal to the current task sub-state
    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }

    if (TaskState.TaskStage.STARTED == patchState.taskState.stage) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState
   * @param patchState
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage
          || patchState.taskState.subStage != startState.taskState.subStage) {
        ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
        startState.taskState = patchState.taskState;
      }
    }

    return startState;
  }

  /**
   * This method performs document owner operations in response to a patch
   * operation.
   *
   * @param currentState
   */
  private void processStartedStage(final State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case CREATE_CONTAINER_TEMPLATES:
        createContainerTemplates(currentState);
        break;
      case ALLOCATE_DOCKER_VMS:
        performDockerVmAllocation(currentState);
        break;
      case SCHEDULE_CONTAINER_ALLOCATION:
        scheduleContainerAllocation(currentState);
        break;
    }
  }

  private void createContainerTemplates(State currentState) {
    if (currentState.isNewDeployment) {
      OperationJoin
          .create(HostUtils.getContainersConfig(this).getContainerSpecs().values().stream()
              .filter(spec -> currentState.isLoadbalancerEnabled
                  || !spec.getType().equals(ContainersConfig.ContainerType.LoadBalancer.name()))
              .filter(spec -> currentState.isAuthEnabled
                  || !spec.getType().equals(ContainersConfig.ContainerType.Lightwave.name()))
              .map(spec -> buildTemplateStartState(spec))
              .map(templateStartState -> Operation.createPost(this, ContainerTemplateFactoryService.SELF_LINK)
                  .setBody(templateStartState)))
          .setCompletion((ops, exs) -> {
            if (null != exs && !exs.isEmpty()) {
              failTask(exs.values().iterator().next());
            } else {
              TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED,
                  TaskState.SubStage.ALLOCATE_DOCKER_VMS, null));
            }
          })
          .sendWith(this);
    } else {
      ServiceUtils.logInfo(this, "Not a new deployment, moving to next stage");
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED,
          TaskState.SubStage.ALLOCATE_DOCKER_VMS, null));
    }
  }

  private ContainerTemplateService.State buildTemplateStartState(ContainersConfig.Spec spec) {
    ContainerTemplateService.State startState = new ContainerTemplateService.State();
    startState.name = spec.getType();
    startState.isReplicated = spec.getIsReplicated();
    startState.cpuCount = spec.getCpuCount();
    startState.memoryMb = spec.getMemoryMb();
    startState.diskGb = spec.getDiskGb();
    startState.isPrivileged = spec.getIsPrivileged();
    startState.useHostNetwork = spec.getUseHostNetwork();
    startState.volumesFrom = spec.getVolumesFrom();
    startState.containerImage = spec.getContainerImage();

    startState.portBindings = new HashMap<>();
    if (null != spec.getPortBindings()) {
      startState.portBindings.putAll(spec.getPortBindings());
    }

    startState.volumeBindings = new HashMap<>();
    if (null != spec.getVolumeBindings()) {
      startState.volumeBindings.putAll(spec.getVolumeBindings());
    }

    startState.environmentVariables = new HashMap<>();
    if (null != spec.getDynamicParameters()) {
      startState.environmentVariables.putAll(spec.getDynamicParameters());
    }

    return startState;
  }

  private void performDockerVmAllocation(final State currentState) {
    ServiceUtils.logInfo(this, "Allocating base docker VMs");
    final Service service = this;

    FutureCallback<CreateVmSpecLayoutTaskService.State> callback =
        new FutureCallback<CreateVmSpecLayoutTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateVmSpecLayoutTaskService.State result) {
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

            ServiceUtils.logInfo(service, "VM allocations complete");

            State state = buildPatch(
                TaskState.TaskStage.STARTED,
                TaskState.SubStage.SCHEDULE_CONTAINER_ALLOCATION,
                null);

            TaskUtils.sendSelfPatch(service, state);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    CreateVmSpecLayoutTaskService.State requestState = new CreateVmSpecLayoutTaskService.State();
    requestState.hostQuerySpecification = currentState.hostQuerySpecification;
    requestState.taskPollDelay = currentState.taskPollDelay;

    TaskUtils.startTaskAsync(
        this,
        CreateVmSpecLayoutTaskFactoryService.SELF_LINK,
        requestState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateVmSpecLayoutTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void scheduleContainerAllocation(final State currentState) {
    ServiceUtils.logInfo(this, "Allocating Containers");
    final Service service = this;

    FutureCallback<CreateContainerSpecLayoutTaskService.State> callback =
        new FutureCallback<CreateContainerSpecLayoutTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainerSpecLayoutTaskService.State result) {
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

            ServiceUtils.logInfo(service, "Container allocations complete");
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED, null, null));
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    CreateContainerSpecLayoutTaskService.State requestState = new
        CreateContainerSpecLayoutTaskService.State();
    requestState.hostQuerySpecification = currentState.hostQuerySpecification;
    requestState.taskPollDelay = currentState.taskPollDelay;

    TaskUtils.startTaskAsync(
        this,
        CreateContainerSpecLayoutTaskFactoryService.SELF_LINK,
        requestState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateContainerSpecLayoutTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method sends a patch operation to the current service instance
   * to move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage, null));
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage
   * @param subStage
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
