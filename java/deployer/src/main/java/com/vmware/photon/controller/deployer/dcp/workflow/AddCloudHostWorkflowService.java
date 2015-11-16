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
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * This class implements a DCP micro-service which performs the task of
 * adding a new cloud host to an existing deployment.
 */
public class AddCloudHostWorkflowService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link AddCloudHostWorkflowService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value allows processing of post and patch operations to be
     * disabled, effectively making all service instances listeners. It is set
     * only in test scenarios.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the link to the {@link HostService} entity.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;
  }

  public AddCloudHostWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    startOperation.setBody(startState).complete();

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

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
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
        getSuccessfulDeployment(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void getSuccessfulDeployment(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(AddCloudHostWorkflowService.this, documentLinks);
                    checkState(documentLinks.size() >= 1);
                    getDeploymentService(currentState, documentLinks.iterator().next());
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void getDeploymentService(final State currentState, final String deploymentServiceLink) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);
                  provisionCloudHost(currentState, deploymentServiceLink, deploymentState.chairmanServerList);
                }
            ));
  }

  private void provisionCloudHost(final State currentState, String deploymentServiceLink, Set<String>
      chairmanServerList) {
    final Service service = this;

    FutureCallback<BulkProvisionHostsWorkflowService.State> callback = new
        FutureCallback<BulkProvisionHostsWorkflowService.State>() {
      @Override
      public void onSuccess(@Nullable BulkProvisionHostsWorkflowService.State result) {
        if (result.taskState.stage == TaskState.TaskStage.FAILED) {
          State state = buildPatch(TaskState.TaskStage.FAILED, null);
          state.taskState.failure = result.taskState.failure;
          TaskUtils.sendSelfPatch(service, state);
          return;
        }

        if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
          return;
        }

        ServiceUtils.logInfo(service, "Provision new cloud host completed");
        TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED, null));
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    BulkProvisionHostsWorkflowService.State startState = new BulkProvisionHostsWorkflowService.State();
    startState.deploymentServiceLink = deploymentServiceLink;
    startState.chairmanServerList = chairmanServerList;
    startState.usageTag = UsageTag.CLOUD.name();
    startState.querySpecification = generateQuerySpecification(currentState.hostServiceLink);

    TaskUtils.startTaskAsync(
        this,
        BulkProvisionHostsWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BulkProvisionHostsWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private QueryTask.QuerySpecification generateQuerySpecification(String hostServiceLink) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(HostService.State.FIELD_NAME_SELF_LINK)
        .setTermMatchValue(hostServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
    return querySpecification;
  }

  private State applyPatch(State currentState, State patchState) {
    if (patchState.taskState.stage != currentState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      currentState.taskState = patchState.taskState;
    }

    return currentState;
  }

  private void sendStageProgressPatch(final TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
