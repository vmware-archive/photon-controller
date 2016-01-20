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
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * This class implements a DCP task service which allocates the API-level resources for the management tenant.
 */
public class AllocateTenantResourcesTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link AllocateTenantResourcesTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-states for a {@link AllocateTenantResourcesTaskService} task.
     */
    public enum SubStage {
      CREATE_TENANT,
      CREATE_RESOURCE_TICKET,
      CREATE_PROJECT,
    }

    /**
     * This value defines the state of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link AllocateTenantResourcesTaskService} task.
   */
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
    public Integer controlFlags;

    /**
     * This value represents the delay, in milliseconds, to use when polling a task object returned
     * by an API call. If no value is specified, the default task poll delay will be used.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the set of quota line items which should be applied to the resource
     * ticket and project created by this task.
     */
    @Immutable
    public List<QuotaLineItem> quotaLineItems;

    /**
     * This value represents the ID of the tenant allocated by the current task.
     */
    @WriteOnce
    public String tenantId;

    /**
     * This value represents the ID of the resource ticket allocated by the current task.
     */
    @WriteOnce
    public String resourceTicketId;

    /**
     * This value represents the ID of the project allocated by the current task.
     */
    @WriteOnce
    public String projectId;
  }

  public AllocateTenantResourcesTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_TENANT;
    }

    operation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation operation) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(operation);
    State patchState = operation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    operation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
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
          case CREATE_RESOURCE_TICKET:
          case CREATE_PROJECT:
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
      case CREATE_RESOURCE_TICKET:
        processCreateResourceTicketSubStage(currentState);
        break;
      case CREATE_PROJECT:
        processCreateProjectSubStage(currentState);
        break;
    }
  }

  //
  // CREATE_TENANT sub-stage routines
  //

  private void processCreateTenantSubStage(State currentState) throws Throwable {

    ServiceUtils.logInfo(this, "Creating tenant with name " + Constants.TENANT_NAME);

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createAsync(Constants.TENANT_NAME, new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processCreateTenantTask(currentState, task);
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

  private void processCreateTenantTask(State currentState, Task task) {

    ApiUtils.pollTaskAsync(task,
        HostUtils.getApiClient(this),
        this,
        currentState.taskPollDelay,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processTenantCreation(currentState, task);
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

  private void processTenantCreation(State currentState, Task task) {
    ServiceUtils.logInfo(this, "Created tenant with ID " + task.getEntity().getId());
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_RESOURCE_TICKET, null);
    patchState.tenantId = task.getEntity().getId();
    sendStageProgressPatch(currentState, patchState);
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

    ServiceUtils.logInfo(this, "Creating resource ticket with tenant ID %s, create spec %s", currentState.tenantId,
        Utils.toJson(createSpec));

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createResourceTicketAsync(currentState.tenantId, createSpec, new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processCreateResourceTicketTask(currentState, task);
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

  private void processCreateResourceTicketTask(State currentState, Task task) {

    ApiUtils.pollTaskAsync(task,
        HostUtils.getApiClient(this),
        this,
        currentState.taskPollDelay,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processResourceTicketCreation(currentState, task);
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

  private void processResourceTicketCreation(State currentState, Task task) {
    ServiceUtils.logInfo(this, "Created resource ticket with ID " + task.getEntity().getId());
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_PROJECT, null);
    patchState.resourceTicketId = task.getEntity().getId();
    sendStageProgressPatch(currentState, patchState);
  }

  //
  // CREATE_PROJECT sub-stage routines
  //

  private void processCreateProjectSubStage(State currentState) throws Throwable {

    ResourceTicketReservation reservation = new ResourceTicketReservation();
    reservation.setName(Constants.RESOURCE_TICKET_NAME);
    reservation.setLimits(Arrays.asList(new QuotaLineItem("subdivide.percent", 100, QuotaUnit.COUNT)));

    ProjectCreateSpec createSpec = new ProjectCreateSpec();
    createSpec.setName(Constants.PROJECT_NAME);
    createSpec.setResourceTicket(reservation);

    ServiceUtils.logInfo(this, "Creating project with tenant ID %s, create spec %s", currentState.tenantId,
        Utils.toJson(createSpec));

    HostUtils.getApiClient(this)
        .getTenantsApi()
        .createProjectAsync(currentState.tenantId, createSpec, new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processCreateProjectTask(currentState, task);
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

  private void processCreateProjectTask(State currentState, Task task) {

    ApiUtils.pollTaskAsync(task,
        HostUtils.getApiClient(this),
        this,
        currentState.taskPollDelay,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@NotNull Task task) {
            try {
              processProjectCreation(currentState, task);
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

  private void processProjectCreation(State currentState, Task task) {
    ServiceUtils.logInfo(this, "Created project with ID " + task.getEntity().getId());
    State patchState = buildPatch(TaskState.TaskStage.FINISHED, null, null);
    patchState.projectId = task.getEntity().getId();
    sendStageProgressPatch(currentState, patchState);
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(State currentState, State patchState) {

    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", patchState.taskState.stage,
        patchState.taskState.subStage);

    if (ControlFlags.disableOperationProcessingOnStageTransition(currentState.controlFlags)) {
      patchState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    }

    TaskUtils.sendSelfPatch(this, patchState);
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;
    patchState.taskState.subStage = subStage;

    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
