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

import com.vmware.photon.controller.api.ProjectCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceTicketCreateSpec;
import com.vmware.photon.controller.api.ResourceTicketReservation;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon task service which allocates tenant resources for a deployment.
 */
public class AllocateTenantResourcesTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link AllocateTenantResourcesTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages for a {@link AllocateTenantResourcesTaskService}
     * task.
     */
    public enum SubStage {
      CREATE_TENANT,
      WAIT_FOR_TENANT,
      CREATE_RESOURCE_TICKET,
      WAIT_FOR_RESOURCE_TICKET,
      CREATE_PROJECT,
      WAIT_FOR_PROJECT,
      UPDATE_VMS,
    }

    /**
     * This value represents the state of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link AllocateTenantResourcesTaskService}
   * task.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

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
     * This value represents the optional document self-link of the parent task service to be
     * notified on completion.
     */
    @Immutable
    public String parentTaskServiceLink;

    /**
     * This value represents the optional patch body to be sent to the parent task service on
     * successful completion.
     */
    @Immutable
    public String parentPatchBody;

    /**
     * This value represents the delay, in milliseconds, to use when polling child tasks.
     */
    @Positive
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the document self-link of the {@link DeploymentService} to be updated
     * with the IDs of the allocated tenant resources.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the optional quota line items to apply when creating the resource
     * ticket.
     */
    @Immutable
    public List<QuotaLineItem> quotaLineItems;

    /**
     * This value represents the ID of the API-level task to create the tenant.
     */
    @WriteOnce
    public String createTenantTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the tenant to be created. It is informational only.
     */
    @DefaultInteger(value = 0)
    public Integer createTenantPollCount;

    /**
     * This value represents the ID of the API-level tenant object created by the current task.
     */
    @WriteOnce
    public String tenantId;

    /**
     * This value represents the ID of the API-level task to create the resource ticket.
     */
    @WriteOnce
    public String createResourceTicketTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the resource ticket to be created. It is informational
     * only.
     */
    @DefaultInteger(value = 0)
    public Integer createResourceTicketPollCount;

    /**
     * This value represents the ID of the API-level resource ticket object created by the current
     * task.
     */
    @WriteOnce
    public String resourceTicketId;

    /**
     * This value represents the ID of the API-level task to create the project.
     */
    @WriteOnce
    public String createProjectTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the project to be created. It is informational only.
     */
    @DefaultInteger(value = 0)
    public Integer createProjectPollCount;

    /**
     * This value represents the ID of the API-level project object created by the current task.
     */
    @WriteOnce
    public String projectId;
  }

  public AllocateTenantResourcesTaskService() {
    super(State.class);
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

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      validateState(startState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, e);
      return;
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TENANT);
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
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, e);
      return;
    }

    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      } else {
        TaskUtils.notifyParentTask(this, currentState.taskState, currentState.parentTaskServiceLink,
            currentState.parentPatchBody);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    validateTaskState(state.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void validateTaskState(TaskState taskState) {
    ValidationUtils.validateTaskStage(taskState);
    switch (taskState.stage) {
      case CREATED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(taskState.subStage == null);
        break;
      case STARTED:
        checkState(taskState.subStage != null);
        switch (taskState.subStage) {
          case CREATE_TENANT:
          case WAIT_FOR_TENANT:
          case CREATE_RESOURCE_TICKET:
          case WAIT_FOR_RESOURCE_TICKET:
          case CREATE_PROJECT:
          case WAIT_FOR_PROJECT:
          case UPDATE_VMS:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case CREATE_TENANT:
        processCreateTenantSubStage(currentState);
        break;
      case WAIT_FOR_TENANT:
        processWaitForTenantSubStage(currentState);
        break;
      case CREATE_RESOURCE_TICKET:
        processCreateResourceTicketSubStage(currentState);
        break;
      case WAIT_FOR_RESOURCE_TICKET:
        processWaitForResourceTicketSubStage(currentState);
        break;
      case CREATE_PROJECT:
        processCreateProjectSubStage(currentState);
        break;
      case WAIT_FOR_PROJECT:
        processWaitForProjectSubStage(currentState);
        break;
      case UPDATE_VMS:
        processUpdateVmsSubStage(currentState);
        break;
    }
  }

  //
  // CREATE_TENANT sub-stage routines
  //

  private void processCreateTenantSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createAsync(Constants.TENANT_NAME,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processCreateTenantTaskResult(task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processCreateTenantTaskResult(Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_TENANT);
        patchState.createTenantTaskId = task.getId();
        patchState.createTenantPollCount = 1;
        TaskUtils.sendSelfPatch(this, patchState);
        break;
      case "COMPLETED":
        updateTenantId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_TENANT sub-stage routines
  //

  private void processWaitForTenantSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.createTenantTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processTenantCreationTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processTenantCreationTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_TENANT);
              patchState.createTenantPollCount = currentState.createTenantPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        updateTenantId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  private void updateTenantId(String tenantId) {
    ServiceUtils.logInfo(this, "Created tenant with ID " + tenantId);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_RESOURCE_TICKET);
    patchState.tenantId = tenantId;
    sendStageProgressPatch(patchState);
  }

  //
  // CREATE_RESOURCE_TICKET sub-stage routines
  //

  private void processCreateResourceTicketSubStage(State currentState) throws Throwable {

    ResourceTicketCreateSpec createSpec = new ResourceTicketCreateSpec();
    createSpec.setName(Constants.RESOURCE_TICKET_NAME);

    if (currentState.quotaLineItems != null) {
      createSpec.setLimits(currentState.quotaLineItems);
    }

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createResourceTicketAsync(currentState.tenantId, createSpec,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processCreateResourceTicketTaskResult(task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processCreateResourceTicketTaskResult(Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_RESOURCE_TICKET);
        patchState.createResourceTicketTaskId = task.getId();
        patchState.createResourceTicketPollCount = 1;
        sendStageProgressPatch(patchState);
        break;
      case "COMPLETED":
        updateResourceTicketId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_RESOURCE_TICKET sub-stage routines
  //

  private void processWaitForResourceTicketSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.createResourceTicketTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processResourceTicketCreationTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processResourceTicketCreationTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_RESOURCE_TICKET);
              patchState.createResourceTicketPollCount = currentState.createResourceTicketPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        updateResourceTicketId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  private void updateResourceTicketId(String resourceTicketId) {
    ServiceUtils.logInfo(this, "Created resource ticket with ID " + resourceTicketId);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_PROJECT);
    patchState.resourceTicketId = resourceTicketId;
    sendStageProgressPatch(patchState);
  }

  //
  // CREATE_PROJECT sub-stage routines
  //

  private void processCreateProjectSubStage(State currentState) throws Throwable {

    ResourceTicketReservation reservation = new ResourceTicketReservation();
    reservation.setName(Constants.RESOURCE_TICKET_NAME);
    reservation.setLimits(Collections.singletonList(new QuotaLineItem("subdivide.percent", 100.0, QuotaUnit.COUNT)));

    ProjectCreateSpec createSpec = new ProjectCreateSpec();
    createSpec.setName(Constants.PROJECT_NAME);
    createSpec.setResourceTicket(reservation);

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createProjectAsync(currentState.tenantId, createSpec,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processCreateProjectTaskResult(task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processCreateProjectTaskResult(Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_PROJECT);
        patchState.createProjectTaskId = task.getId();
        patchState.createProjectPollCount = 1;
        sendStageProgressPatch(patchState);
        break;
      case "COMPLETED":
        updateProjectId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_PROJECT sub-stage routines
  //

  private void processWaitForProjectSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.createProjectTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processProjectCreationTaskResult(currentState, task);
                } catch (Throwable t) {
                  failTask(t);
                }
              }

              @Override
              public void onFailure(Throwable throwable) {
                failTask(throwable);
              }
            });
  }

  private void processProjectCreationTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_PROJECT);
              patchState.createProjectPollCount = currentState.createProjectPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        updateProjectId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  private void updateProjectId(String projectId) {
    ServiceUtils.logInfo(this, "Created project with ID " + projectId);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VMS);
    patchState.projectId = projectId;
    sendStageProgressPatch(patchState);
  }

  //
  // UPDATE_VMS sub-stage routines
  //

  private void processUpdateVmsSubStage(State currentState) {

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
    vmPatchState.projectId = currentState.projectId;

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
                updateDeployment(currentState);
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void updateDeployment(State currentState) {

    DeploymentService.State deploymentPatchState = new DeploymentService.State();
    deploymentPatchState.projectId = currentState.projectId;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
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

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", stage, subStage);
    sendStageProgressPatch(buildPatch(stage, subStage, null));
  }

  private void sendStageProgressPatch(State patchState) {
    ServiceUtils.logTrace(this, "Sending self-patch: {}", patchState);
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  private void failTask(Collection<Throwable> failures) {
    ServiceUtils.logSevere(this, failures);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;
    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
