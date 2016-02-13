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
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.BuildRuntimeConfigurationTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.BuildRuntimeConfigurationTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
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
import java.util.Iterator;
import java.util.Set;

/**
 * This class implements a DCP micro-service which performs the task of
 * building configuration for all the containers.
 */
public class BuildContainersConfigurationWorkflowService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link BuildContainersConfigurationWorkflowService} instance.
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
     * This value represents the link to the {@link DeploymentService.State} entity.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the host entity link to create vms on.
     */
    @Immutable
    public String hostServiceLink;

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean isNewDeployment;
  }

  public BuildContainersConfigurationWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
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

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
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
        retrieveContainers(currentState);
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

  private void retrieveContainers(final State currentState) {
    if (currentState.hostServiceLink == null) {
      retrieveAllContainers(currentState);
    } else {
      QueryTask queryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(VmService.State.class)
              .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
              .build())
          .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
          .build();

      Operation queryPostOperation = Operation
          .createPost(UriUtils.buildBroadcastRequestUri(
              UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
              ServiceUriPaths.DEFAULT_NODE_SELECTOR))
          .setBody(queryTask)
          .setCompletion((completedOp, failure) -> {
            if (failure != null) {
              failTask(failure);
              return;
            }

            NodeGroupBroadcastResponse rsp = completedOp.getBody(NodeGroupBroadcastResponse.class);
            Set<String> vmServiceLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(rsp);
            checkState(vmServiceLinks.size() == 1);

            QueryTask.Query vmClause = QueryTask.Query.Builder.create()
                .addKindFieldClause(ContainerService.State.class)
                .addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, vmServiceLinks.iterator().next())
                .build();
            QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
            querySpecification.query = vmClause;

            runContainersQuery(currentState, querySpecification);
          });

      sendRequest(queryPostOperation);
    }
  }

  private void retrieveAllContainers(final State currentState) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;

    runContainersQuery(currentState, querySpecification);
  }

  private void runContainersQuery(final State currentState, QueryTask.QuerySpecification querySpecification) {
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
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(BuildContainersConfigurationWorkflowService.this, documentLinks);
              checkState(documentLinks.size() >= 1);
              buildConfiguration(currentState, documentLinks.iterator());
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }
  private void buildConfiguration(State currentState, Iterator<String> documentLinkIterator) {

    if (!documentLinkIterator.hasNext()) {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
      return;
    }

    BuildRuntimeConfigurationTaskService.State startState = new BuildRuntimeConfigurationTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.containerServiceLink = documentLinkIterator.next();
    startState.isNewDeployment = currentState.isNewDeployment;

    TaskUtils.startTaskAsync(this,
        BuildRuntimeConfigurationTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BuildRuntimeConfigurationTaskService.State.class,
        currentState.taskPollDelay,
        new FutureCallback<BuildRuntimeConfigurationTaskService.State>() {
          @Override
          public void onSuccess(@Nullable BuildRuntimeConfigurationTaskService.State result) {
            switch (checkNotNull(result).taskState.stage) {
              case FINISHED:
                try {
                  buildConfiguration(currentState, documentLinkIterator);
                } catch (Throwable t) {
                  failTask(t);
                }
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(BuildContainersConfigurationWorkflowService.this, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(BuildContainersConfigurationWorkflowService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
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
