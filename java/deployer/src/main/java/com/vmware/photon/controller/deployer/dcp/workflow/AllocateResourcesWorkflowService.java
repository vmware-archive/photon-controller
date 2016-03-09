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

import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateTenantResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateTenantResourcesTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class implements a DCP service representing the workflow of allocating resources.
 */
public class AllocateResourcesWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link AllocateResourcesWorkflowService} task.
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
      ALLOCATE_TENANT_RESOURCES,
      UPDATE_VMS
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link AllocateResourcesWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents ID of the allocated tenant entity.
     */
    public String tenantId;

    /**
     * This value represents ID of the allocated resource ticket entity.
     */
    public String resourceTicketId;

    /**
     * This value represents ID of the allocated project entity.
     */
    public String projectId;
  }

  public AllocateResourcesWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
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

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.ALLOCATE_TENANT_RESOURCES;
    }

    start.setBody(startState).complete();

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
        processStartedState(currentState);
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
    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case ALLOCATE_TENANT_RESOURCES:
        case UPDATE_VMS:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage);
      }
    }
  }

  private void validateTaskSubStage(TaskState taskState) {
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
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
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(final State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case ALLOCATE_TENANT_RESOURCES:
        allocateTenantResources(currentState);
        break;
      case UPDATE_VMS:
        updateVms(currentState);
        break;
    }
  }

  private void allocateTenantResources(State currentState) {

    AllocateTenantResourcesTaskService.State startState = new AllocateTenantResourcesTaskService.State();
    startState.taskPollDelay = currentState.taskPollDelay;
    startState.quotaLineItems = new ArrayList<>(
        Collections.singletonList(new QuotaLineItem("vm.count", Integer.MAX_VALUE, QuotaUnit.COUNT)));

    TaskUtils.startTaskAsync(this,
        AllocateTenantResourcesTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        AllocateTenantResourcesTaskService.State.class,
        currentState.taskPollDelay,
        new FutureCallback<AllocateTenantResourcesTaskService.State>() {
          @Override
          public void onSuccess(@NotNull AllocateTenantResourcesTaskService.State state) {
            switch (state.taskState.stage) {
              case FINISHED: {
                State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VMS, null);
                patchState.tenantId = state.tenantId;
                patchState.resourceTicketId = state.resourceTicketId;
                patchState.projectId = state.projectId;
                TaskUtils.sendSelfPatch(AllocateResourcesWorkflowService.this, patchState);
                break;
              }
              case FAILED: {
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = state.taskState.failure;
                TaskUtils.sendSelfPatch(AllocateResourcesWorkflowService.this, patchState);
                break;
              }
              case CANCELLED: {
                State patchState = buildPatch(TaskState.TaskStage.CANCELLED, null, null);
                TaskUtils.sendSelfPatch(AllocateResourcesWorkflowService.this, patchState);
                break;
              }
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void updateVms(State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                updateVms(currentState, o.getBody(QueryTask.class).results.documentLinks);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void updateVms(State currentState, List<String> vmServiceLinks) {

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.projectServiceLink = UriUtils.buildUriPath(ProjectServiceFactory.SELF_LINK, currentState.projectId);

    OperationJoin
        .create(vmServiceLinks.stream()
            .map((vmServiceLink) -> Operation.createPatch(this, vmServiceLink).setBody(vmPatchState)))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                updateDeploymentState(currentState);
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void updateDeploymentState(State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DeploymentService.State.class)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> documentLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(documentLinks.size() == 1);
                updateDeploymentState(currentState, documentLinks.get(0));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void updateDeploymentState(State currentState, String deploymentServiceLink) {

    DeploymentService.State deploymentPatchState = new DeploymentService.State();
    deploymentPatchState.projectId = currentState.projectId;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(deploymentServiceLink)
        .setBody(deploymentPatchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
              }
            }));
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    if (null != patchState.tenantId) {
      startState.tenantId = patchState.tenantId;
    }

    if (null != patchState.resourceTicketId) {
      startState.resourceTicketId = patchState.resourceTicketId;
    }

    if (null != patchState.projectId) {
      startState.projectId = patchState.projectId;
    }

    return startState;
  }

  private void sendStageProgressPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", stage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage, null));
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void failTask(Collection<Throwable> exs) {
    ServiceUtils.logSevere(this, exs);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, exs.iterator().next()));
  }

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
