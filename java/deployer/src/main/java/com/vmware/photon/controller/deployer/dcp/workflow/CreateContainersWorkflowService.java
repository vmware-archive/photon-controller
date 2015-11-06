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
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.task.RegisterAuthClientTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.RegisterAuthClientTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;

import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * This class implements a DCP service representing the create containers workflow.
 */
public class CreateContainersWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link CreateContainersWorkflowService} task.
   */
  public static class TaskState extends com.vmware.dcp.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this work-flow.
     */
    public enum SubStage {
      CREATE_ZOOKEEPER_AND_DB_CONTAINERS,
      CREATE_LIGHTWAVE_CONTAINER,
      REGISTER_AUTH_CLIENT,
      CREATE_SERVICE_CONTAINERS,
      CREATE_LOAD_BALANCER_CONTAINER
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link CreateContainersWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * Saves the auth enabled status to avoid reading deployment state multiple times.
     */
    @NotNull
    @Immutable
    public Boolean isAuthEnabled;
  }

  public CreateContainersWorkflowService() {
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
      startState.taskState.subStage = TaskState.SubStage.CREATE_ZOOKEEPER_AND_DB_CONTAINERS;
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
   * This method is called when a patch operation is performed for the current
   * service instance.
   *
   * @param patch Supplies the start operation object.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    validateState(startState);
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
        case CREATE_ZOOKEEPER_AND_DB_CONTAINERS:
        case CREATE_LIGHTWAVE_CONTAINER:
        case REGISTER_AUTH_CLIENT:
        case CREATE_SERVICE_CONTAINERS:
        case CREATE_LOAD_BALANCER_CONTAINER:
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
      case CREATE_ZOOKEEPER_AND_DB_CONTAINERS:
        createContainers(currentState, Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper,
                ContainersConfig.ContainerType.CloudStore),
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER);
        break;
      case CREATE_LIGHTWAVE_CONTAINER:
        createLightwaveContainer(currentState,
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.REGISTER_AUTH_CLIENT);
        break;
      case REGISTER_AUTH_CLIENT:
        processRegisterAuthClient(currentState,
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.CREATE_SERVICE_CONTAINERS);
        break;
      case CREATE_SERVICE_CONTAINERS:
        createContainers(currentState, Arrays.asList(
                ContainersConfig.ContainerType.Chairman,
                ContainersConfig.ContainerType.Deployer,
                ContainersConfig.ContainerType.Housekeeper,
                ContainersConfig.ContainerType.ManagementApi,
                ContainersConfig.ContainerType.RootScheduler),
            TaskState.TaskStage.STARTED,
            TaskState.SubStage.CREATE_LOAD_BALANCER_CONTAINER);
        break;
      case CREATE_LOAD_BALANCER_CONTAINER:
        createContainers(currentState, Arrays.asList(
                ContainersConfig.ContainerType.LoadBalancer),
            TaskState.TaskStage.FINISHED, null);
        break;
    }
  }

  /**
   * This method creates containers based on the list of container types.
   *
   * @param currentState   Supplies the current state object.
   * @param containerTypes Supplies the list of container types for which
   *                       containers have to be created.
   */
  private void createContainers(final State currentState, List<ContainersConfig.ContainerType> containerTypes,
                                final TaskState.TaskStage nextStage, final TaskState.SubStage nextSubStage) {
    final AtomicInteger pendingContainerTypes = new AtomicInteger(containerTypes.size());
    final Service service = this;

    FutureCallback<String> futureCallback =
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String result) {
            if (0 == pendingContainerTypes.decrementAndGet()) {
              ServiceUtils.logInfo(service, "Stage %s completed", result);
              TaskUtils.sendSelfPatch(service, buildPatch(nextStage, nextSubStage, null));
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (ContainersConfig.ContainerType containerType : containerTypes) {
      queryContainerTemplate(currentState, containerType, futureCallback);
    }
  }

  /**
   * This method checks if authentication is enabled and if yes, starts deployment of lightwave container.
   *
   * @param currentState Current state object
   * @param nextStage Next stage to set
   * @param nextSubStage Next substage to set
   */
  private void createLightwaveContainer(final State currentState,
      final TaskState.TaskStage nextStage, final TaskState.SubStage nextSubStage) {
    final Service service = this;

    if (currentState.isAuthEnabled) {
      ServiceUtils.logInfo(service, "Stage %s starting, authentication is enabled",
        TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER);
      createContainers(currentState, Arrays.asList(ContainersConfig.ContainerType.Lightwave),
        nextStage,
        nextSubStage);
    } else {
      ServiceUtils.logInfo(service, "Stage %s completed, authentication is not enabled",
        TaskState.SubStage.CREATE_LIGHTWAVE_CONTAINER);
      TaskUtils.sendSelfPatch(service, buildPatch(nextStage, nextSubStage, null));
    }
  }

  /**
   * This method registers client in Lightwave to obtain the logon and logout urls for API-FE.
   *
   * @param currentState Current state object
   * @throws Throwable
   */
  private void processRegisterAuthClient(final State currentState,
      TaskState.TaskStage nextStage, TaskState.SubStage nextSubStage) throws Throwable {
    log(Level.INFO, "Registering authentication client with Lightwave");

    final Service service = this;
    if (!currentState.isAuthEnabled) {
      log(Level.INFO, "Stage %s finished, no need to register as auth is disabled",
        TaskState.SubStage.REGISTER_AUTH_CLIENT);
      TaskUtils.sendSelfPatch(service, buildPatch(nextStage, nextSubStage, null));
      return;
    }

    FutureCallback<RegisterAuthClientTaskService.State> callback
        = new FutureCallback<RegisterAuthClientTaskService.State>() {
      @Override
      public void onSuccess(@Nullable RegisterAuthClientTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            log(Level.INFO, "Stage %s finished successfully", TaskState.SubStage.REGISTER_AUTH_CLIENT);
            TaskUtils.sendSelfPatch(service, buildPatch(nextStage, nextSubStage, null));
            break;
          case FAILED:
            State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
            patchState.taskState.failure = result.taskState.failure;
            TaskUtils.sendSelfPatch(service, patchState);
            break;
          case CANCELLED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
            break;
          default:
            State patchFailState = buildPatch(TaskState.TaskStage.FAILED, null, null);
            TaskUtils.sendSelfPatch(service, patchFailState);
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    RegisterAuthClientTaskService.State startState = new RegisterAuthClientTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;

    TaskUtils.startTaskAsync(
        this,
        RegisterAuthClientTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        RegisterAuthClientTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method queries for specific container template based on container type.
   *
   * @param currentState   Supplies the current state object.
   * @param containerType  Supplies the type of container whose template has to
   *                       be found.
   * @param futureCallback Supplies the callback to be called when the container
   *                       creation succeeds.
   */
  private void queryContainerTemplate(final State currentState,
                                      ContainersConfig.ContainerType containerType,
                                      final FutureCallback<String> futureCallback) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(containerType.name());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
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
              QueryTaskUtils.logQueryResults(CreateContainersWorkflowService.this, documentLinks);
              checkState(1 == documentLinks.size());
              queryContainersForTemplate(currentState, documentLinks.iterator().next(), futureCallback);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryContainersForTemplate(final State currentState,
                                          String containerTemplateServiceLink,
                                          final FutureCallback<String> futureCallback) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
        .setTermMatchValue(containerTemplateServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);
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
              QueryTaskUtils.logQueryResults(CreateContainersWorkflowService.this, documentLinks);
              checkState(documentLinks.size() > 0);
              createContainerTasks(currentState, documentLinks, futureCallback);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void createContainerTasks(State currentState,
                                    Collection<String> documentLinks,
                                    FutureCallback<String> futureCallback) {

    List<CreateAndValidateContainerWorkflowService.State> createContainerTasks = new ArrayList<>();
    for (String documentLink : documentLinks) {
      CreateAndValidateContainerWorkflowService.State startState =
          new CreateAndValidateContainerWorkflowService.State();
      startState.containerServiceLink = documentLink;
      startState.deploymentServiceLink = currentState.deploymentServiceLink;
      createContainerTasks.add(startState);
    }

    processCreateContainers(currentState, createContainerTasks, futureCallback);
  }

  /**
   * This method creates containers by calling all the create container tasks.
   *
   * @param currentState         Supplies the current state object.
   * @param createContainerTasks Supplies a list of create container tasks.
   * @param futureCallback       Supplies the callback to be called when the container
   *                             creation succeeds.
   */
  private void processCreateContainers(
      final State currentState,
      final List<CreateAndValidateContainerWorkflowService.State> createContainerTasks,
      final FutureCallback<String> futureCallback) {

    final AtomicInteger pendingCreates = new AtomicInteger(createContainerTasks.size());
    final Service service = this;

    FutureCallback<CreateAndValidateContainerWorkflowService.State> callback =
        new FutureCallback<CreateAndValidateContainerWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateAndValidateContainerWorkflowService.State result) {
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
              futureCallback.onSuccess(currentState.taskState.subStage.name());
            }
          }

          @Override
          public void onFailure(Throwable t) {
            futureCallback.onFailure(t);
          }
        };

    for (CreateAndValidateContainerWorkflowService.State startState : createContainerTasks) {

      TaskUtils.startTaskAsync(
          this,
          CreateAndValidateContainerWorkflowFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CreateAndValidateContainerWorkflowService.State.class,
          currentState.taskPollDelay,
          callback);
    }
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
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage, null));
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
   * @param patchStage
   * @param patchSubStage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected static State buildPatch(
      TaskState.TaskStage patchStage,
      @Nullable TaskState.SubStage patchSubStage,
      @Nullable Throwable t) {

    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
