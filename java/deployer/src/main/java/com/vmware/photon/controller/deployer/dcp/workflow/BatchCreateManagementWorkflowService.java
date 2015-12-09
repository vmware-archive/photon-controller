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

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.UploadImageTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadImageTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP service representing the workflow of creating all management vms.
 */
public class BatchCreateManagementWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link CreateManagementVmWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this work-flow.
     */
    public enum SubStage {
      UPLOAD_IMAGE,
      ALLOCATE_RESOURCES,
      CREATE_VMS,
      CREATE_CONTAINERS,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link BatchCreateManagementWorkflowService} instance.
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
     * The image filename used to create the vm.
     */
    @NotNull
    @Immutable
    public String imageFile;

    /**
     * This value represents the polling interval override value to use for child tasks.
     */
    @Immutable
    public Integer childPollInterval;

    /**
     * This value represents if authentication is enabled or not.
     */
    @NotNull
    @Immutable
    public Boolean isAuthEnabled;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the NTP server configured at VM.
     */
    @Immutable
    public String ntpEndpoint;
  }

  public BatchCreateManagementWorkflowService() {
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
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPLOAD_IMAGE;
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
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
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
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(null != currentState.taskState.subStage, "Sub-stage cannot be null in STARTED stage.");
      switch (currentState.taskState.subStage) {
        case UPLOAD_IMAGE:
        case ALLOCATE_RESOURCES:
        case CREATE_VMS:
        case CREATE_CONTAINERS:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage.toString());
      }
    } else {
      checkState(null == currentState.taskState.subStage, "Sub-stage must be null in stages other than STARTED.");
    }
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
    checkNotNull(startState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    // The task sub-state must be at least equal to the current task sub-state
    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
    // A document can never be patched to the CREATED state.
    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patchState.taskState.stage.ordinal() >= startState.taskState.stage.ordinal());

    // Patches cannot be applied to documents in terminal states.
    checkState(startState.taskState.subStage == null
        || startState.taskState.stage.ordinal() <= TaskState.TaskStage.STARTED.ordinal());
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage
          || patchState.taskState.subStage != startState.taskState.subStage) {
        ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      }
    }
    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) {
    switch (currentState.taskState.subStage) {
      case UPLOAD_IMAGE:
        uploadImage(currentState);
        break;
      case ALLOCATE_RESOURCES:
        allocateResources(currentState);
        break;
      case CREATE_VMS:
        createVms(currentState);
        break;
      case CREATE_CONTAINERS:
        createContainers(currentState);
        break;
    }
  }

  /**
   * This method starts the upload image task.
   *
   * @param currentState Supplies the current state object.
   */
  private void uploadImage(final State currentState) {
    final Service service = this;

    FutureCallback<UploadImageTaskService.State> callback = new FutureCallback<UploadImageTaskService.State>() {
      @Override
      public void onSuccess(@Nullable UploadImageTaskService.State result) {
        if (result.taskState.stage == TaskState.TaskStage.FAILED) {
          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FAILED, null, result.taskState.failure));
        } else if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
        } else {
          updateVmServices(currentState, ImageServiceFactory.SELF_LINK + "/" + result.imageId);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };
    UploadImageTaskService.State startState = createUploadImageState(currentState);

    TaskUtils.startTaskAsync(
        this,
        UploadImageTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        UploadImageTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method creates a {@link UploadImageTaskService.State} object.
   *
   * @param currentState Supplies the current state object.
   * @return
   */
  private UploadImageTaskService.State createUploadImageState(State currentState) {
    UploadImageTaskService.State state = new UploadImageTaskService.State();
    state.imageName = "management-vm-image";
    state.imageFile = currentState.imageFile;
    state.imageReplicationType = ImageReplicationType.ON_DEMAND;
    state.taskState = new com.vmware.xenon.common.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.queryUploadImageTaskInterval = currentState.childPollInterval;
    return state;
  }

  /**
   * @param currentState
   */
  private void updateVmServices(final State currentState, final String imageServiceLink) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
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
              NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
              Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryResults(queryResponse);
              QueryTaskUtils.logQueryResults(BatchCreateManagementWorkflowService.this, documentLinks);
              updateVmStates(currentState, documentLinks, imageServiceLink);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void updateVmStates(State currentState, Set<String> documentLinks, String imageServiceLink) {

    if (documentLinks.isEmpty()) {
      throw new DcpRuntimeException("Document links set is empty");
    }

    VmService.State patchState = new VmService.State();
    patchState.imageServiceLink = imageServiceLink;

    OperationJoin
        .create(documentLinks.stream()
            .map(documentLink -> Operation.createPatch(this, documentLink).setBody(patchState)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs.values());
          } else {
            updateDeploymentState(currentState, imageServiceLink);
          }
        })
        .sendWith(this);
  }

  private void updateDeploymentState(State currentState, String imageServiceLink) {
    DeploymentService.State deploymentService = new DeploymentService.State();
    deploymentService.imageId = ServiceUtils.getIDFromDocumentSelfLink(imageServiceLink);
    MiscUtils.updateDeploymentState(this, deploymentService, (operation, throwable) -> {
      if (throwable != null) {
        failTask(throwable);
        return;
      }

      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED,
          TaskState.SubStage.ALLOCATE_RESOURCES));
    });
  }

  private void allocateResources(final State currentState) {
    final Service service = this;

    FutureCallback<AllocateResourcesWorkflowService.State> callback =
        new FutureCallback<AllocateResourcesWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable AllocateResourcesWorkflowService.State result) {

            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.STARTED,
                    TaskState.SubStage.CREATE_VMS));
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(service, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    AllocateResourcesWorkflowService.State startState = new AllocateResourcesWorkflowService.State();
    startState.taskPollDelay = currentState.taskPollDelay;

    TaskUtils.startTaskAsync(
        this,
        AllocateResourcesWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        AllocateResourcesWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void createVms(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
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
              QueryTaskUtils.logQueryResults(BatchCreateManagementWorkflowService.this, documentLinks);
              createVms(currentState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void createVms(State currentState, Collection<String> documentLinks) {

    final AtomicInteger latch = new AtomicInteger(documentLinks.size());
    final Service service = this;
    final Map<String, Throwable> exceptions = new ConcurrentHashMap<>();
    for (final String documentLink : documentLinks) {

      FutureCallback<CreateManagementVmWorkflowService.State> futureCallback =
          new FutureCallback<CreateManagementVmWorkflowService.State>() {
            @Override
            public void onSuccess(@Nullable CreateManagementVmWorkflowService.State state) {
              if (state.taskState.stage != TaskState.TaskStage.FINISHED) {
                exceptions.put(documentLink, new RuntimeException("CreateManagementWorkFlow did not finish."));
              }

              if (0 == latch.decrementAndGet()) {
                if (0 == exceptions.size()) {
                  TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.STARTED,
                      TaskState.SubStage.CREATE_CONTAINERS));
                } else {
                  failTask(exceptions.values());
                }
              }
            }

            @Override
            public void onFailure(Throwable throwable) {
              exceptions.put(documentLink, throwable);
              if (0 == latch.decrementAndGet()) {
                failTask(exceptions.values());
              }
            }
          };

      CreateManagementVmWorkflowService.State startState = createVmWorkflowState(currentState, documentLink);

      TaskUtils.startTaskAsync(
          this,
          CreateManagementVmWorkflowFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CreateManagementVmWorkflowService.State.class,
          currentState.taskPollDelay,
          futureCallback);
    }
  }

  private CreateManagementVmWorkflowService.State createVmWorkflowState(State currentState, String vmServiceLink) {
    CreateManagementVmWorkflowService.State state = new CreateManagementVmWorkflowService.State();
    state.vmServiceLink = vmServiceLink;
    state.childPollInterval = currentState.childPollInterval;
    state.ntpEndpoint = currentState.ntpEndpoint;
    return state;
  }

  /**
   * This method starts the create containers workflow.
   *
   * @param currentState Supplies the current state object.
   */
  private void createContainers(final State currentState) {
    final Service service = this;

    FutureCallback<CreateContainersWorkflowService.State> callback = new
        FutureCallback<CreateContainersWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainersWorkflowService.State result) {
            if (result.taskState.stage == TaskState.TaskStage.FAILED) {
              TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FAILED, null, result.taskState.failure));
            } else if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
              TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
            } else {
              sendProgressPatch(result.taskState, TaskState.TaskStage.FINISHED, null);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateContainersWorkflowService.State startState = new CreateContainersWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.isAuthEnabled = currentState.isAuthEnabled;
    startState.taskPollDelay = currentState.taskPollDelay;
    TaskUtils.startTaskAsync(
        this,
        CreateContainersWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateContainersWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method sends a progress patch depending of the taskState of the provided state.
   *
   * @param state    Supplies the state.
   * @param stage    Supplies the stage to progress to.
   * @param subStage Supplies the substate to progress to.
   */
  private void sendProgressPatch(
      com.vmware.xenon.common.TaskState state,
      TaskState.TaskStage stage,
      TaskState.SubStage subStage) {
    switch (state.stage) {
      case FINISHED:
        TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
        break;
      case CANCELLED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.CANCELLED, null));
        break;
      case FAILED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, state.failure));
        break;
    }
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage));
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

  private void failTask(Collection<Throwable> failures) {
    failures.forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param errorResponse
   * @return
   */
  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }
}
