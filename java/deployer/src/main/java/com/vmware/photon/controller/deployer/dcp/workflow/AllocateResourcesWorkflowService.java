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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.CreateFlavorTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateFlavorTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateProjectTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateProjectTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateResourceTicketTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateResourceTicketTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateTenantTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateTenantTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP service representing the workflow of allocating resources.
 */
public class AllocateResourcesWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link AllocateResourcesWorkflowService} task.
   */
  public static class TaskState extends com.vmware.dcp.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum SubStage {
      CREATE_FLAVORS,
      CREATE_TENANT,
      CREATE_RESOURCE_TICKET,
      CREATE_PROJECT,
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
     * This value represents the service links of the VmService entities which have not
     * been assigned to a project.
     */
    public List<String> vmServiceLinks;

    /**
     * This value represents the service link of the TenantService entity.
     */
    public String tenantServiceLink;

    /**
     * This value represents the service link of the ResourceTicketService entity.
     */
    public String resourceTicketServiceLink;

    /**
     * This value represents the service link of the ProjectService entity.
     */
    public String projectServiceLink;
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

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_FLAVORS;
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
        case CREATE_FLAVORS:
        case CREATE_TENANT:
        case CREATE_RESOURCE_TICKET:
        case CREATE_PROJECT:
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
      case CREATE_FLAVORS:
        queryVms(currentState);
        break;
      case CREATE_TENANT:
        createTenant(currentState);
        break;
      case CREATE_RESOURCE_TICKET:
        createResourceTicket(currentState);
        break;
      case CREATE_PROJECT:
        createProject(currentState);
        break;
      case UPDATE_VMS:
        updateVms(currentState);
    }
  }

  private void queryVms(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(AllocateResourcesWorkflowService.this, documentLinks);
              if (documentLinks.isEmpty()) {
                throw new RuntimeException("Found 0 vms");
              } else {
                createFlavors(currentState, new ArrayList<>(documentLinks));
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void createFlavors(final State currentState, final List<String> vmServiceLinks) {
    ServiceUtils.logInfo(this, "Creating flavor entities");
    final AtomicInteger pendingCreates = new AtomicInteger(vmServiceLinks.size());
    final Service service = this;

    FutureCallback<CreateFlavorTaskService.State> callback =
        new FutureCallback<CreateFlavorTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateFlavorTaskService.State result) {
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

            if (0 == pendingCreates.decrementAndGet()) {
              State state = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TENANT, null);
              state.vmServiceLinks = vmServiceLinks;
              TaskUtils.sendSelfPatch(service, state);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (String vmServiceLink : vmServiceLinks) {

      CreateFlavorTaskService.State createFlavorState =
          generateCreateFlavorTaskServiceState(currentState, vmServiceLink);

      TaskUtils.startTaskAsync(
          this,
          CreateFlavorTaskFactoryService.SELF_LINK,
          createFlavorState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CreateFlavorTaskService.State.class,
          currentState.taskPollDelay,
          callback);
    }
  }

  private CreateFlavorTaskService.State generateCreateFlavorTaskServiceState(State currentState, String vmServiceLink) {
    CreateFlavorTaskService.State state = new CreateFlavorTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.vmServiceLink = vmServiceLink;
    state.queryTaskInterval = currentState.taskPollDelay;
    return state;
  }

  private void createTenant(final State currentState) {
    ServiceUtils.logInfo(this, "Creating tenant");
    final Service service = this;

    FutureCallback<CreateTenantTaskService.State> callback =
        new FutureCallback<CreateTenantTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateTenantTaskService.State result) {
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

            State patchState = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.CREATE_RESOURCE_TICKET, null);
            patchState.tenantServiceLink = result.tenantServiceLink;
            TaskUtils.sendSelfPatch(service, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateTenantTaskService.State createTenantState = generateCreateTenantTaskServiceState(currentState);

    TaskUtils.startTaskAsync(
        this,
        CreateTenantTaskFactoryService.SELF_LINK,
        createTenantState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateTenantTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private CreateTenantTaskService.State generateCreateTenantTaskServiceState(final State currentState) {
    CreateTenantTaskService.State state = new CreateTenantTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = com.vmware.dcp.common.TaskState.TaskStage.CREATED;
    state.taskPollDelay = currentState.taskPollDelay;
    return state;
  }

  private void createResourceTicket(final State currentState) {
    ServiceUtils.logInfo(this, "Creating resource ticket");
    final Service service = this;

    FutureCallback<CreateResourceTicketTaskService.State> callback =
        new FutureCallback<CreateResourceTicketTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateResourceTicketTaskService.State result) {
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

            State patchState = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.CREATE_PROJECT, null);
            patchState.resourceTicketServiceLink = result.resourceTicketServiceLink;
            TaskUtils.sendSelfPatch(service, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateResourceTicketTaskService.State createResourceTicketState =
        generateCreateResourceTicketTaskServiceState(currentState);

    TaskUtils.startTaskAsync(
        this,
        CreateResourceTicketTaskFactoryService.SELF_LINK,
        createResourceTicketState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateResourceTicketTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private CreateResourceTicketTaskService.State generateCreateResourceTicketTaskServiceState(final State currentState) {
    CreateResourceTicketTaskService.State state = new CreateResourceTicketTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = com.vmware.dcp.common.TaskState.TaskStage.CREATED;
    state.taskPollDelay = currentState.taskPollDelay;
    state.tenantServiceLink = currentState.tenantServiceLink;
    state.quotaLineItems = new ArrayList<>();
    state.quotaLineItems.add(new QuotaLineItem("vm.count", Integer.MAX_VALUE, QuotaUnit.COUNT));
    return state;
  }

  private void createProject(final State currentState) {
    ServiceUtils.logInfo(this, "Creating project");
    final Service service = this;

    FutureCallback<CreateProjectTaskService.State> callback =
        new FutureCallback<CreateProjectTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateProjectTaskService.State result) {
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

            State patchState = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.UPDATE_VMS, null);
            patchState.projectServiceLink = result.projectServiceLink;
            TaskUtils.sendSelfPatch(service, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateProjectTaskService.State createProjectState = generateCreateProjectTaskServiceState(currentState);

    TaskUtils.startTaskAsync(
        this,
        CreateProjectTaskFactoryService.SELF_LINK,
        createProjectState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateProjectTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private CreateProjectTaskService.State generateCreateProjectTaskServiceState(final State currentState) {
    CreateProjectTaskService.State state = new CreateProjectTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = com.vmware.dcp.common.TaskState.TaskStage.CREATED;
    state.taskPollDelay = currentState.taskPollDelay;
    state.resourceTicketServiceLink = currentState.resourceTicketServiceLink;
    return state;
  }

  private void updateVms(final State currentState) {

    VmService.State patchState = new VmService.State();
    patchState.projectServiceLink = currentState.projectServiceLink;

    OperationJoin
        .create(currentState.vmServiceLinks.stream()
            .map(vmServiceLink -> Operation.createPatch(this, vmServiceLink).setBody(patchState)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
          } else {
            sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
          }
        })
        .sendWith(this);
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    if (null != patchState.vmServiceLinks) {
      startState.vmServiceLinks = patchState.vmServiceLinks;
    }

    if (null != patchState.tenantServiceLink) {
      startState.tenantServiceLink = patchState.tenantServiceLink;
    }

    if (null != patchState.resourceTicketServiceLink) {
      startState.resourceTicketServiceLink = patchState.resourceTicketServiceLink;
    }

    if (null != patchState.projectServiceLink) {
      startState.projectServiceLink = patchState.projectServiceLink;
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

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, exs.values().iterator().next()));
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
