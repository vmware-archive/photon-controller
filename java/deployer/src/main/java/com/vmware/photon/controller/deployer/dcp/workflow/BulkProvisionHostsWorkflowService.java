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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionHostTaskService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP microservice which performs the task of provisioning a set of ESX hosts.
 */
public class BulkProvisionHostsWorkflowService extends StatefulService {

  /**
   * This class represents the state of a {@link BulkProvisionHostsWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;

    /**
     * This value represents the possible sub-stages for a task.
     */
    public enum SubStage {
      UPLOAD_VIB,
    }
  }

  /**
   * This class represents the document state associated with a {@link BulkProvisionHostsWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the deployment in whose context the task operation is
     * being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the usage tag associated with the hosts to be provisioned.
     */
    @NotNull
    @Immutable
    public String usageTag;

    /**
     * This value represents the query specification which can be used to identify the hosts to be provisioned.
     */
    @Immutable
    public QueryTask.QuerySpecification querySpecification;

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
     * This value represents the interval, in milliseconds, to wait when polling the state of a child task.
     */
    @Immutable
    public Integer taskPollDelay;
  }

  public BulkProvisionHostsWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (null == startState.querySpecification) {
      startState.querySpecification = buildQuerySpecification(startState.usageTag);
    }

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPLOAD_VIB;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

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

  private QueryTask.QuerySpecification buildQuerySpecification(String usageTag) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    String usageTagsKey = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_USAGE_TAGS);

    if (UsageTag.MGMT.name().equals(usageTag)) {
      QueryTask.Query mgmtUsageTagClause = new QueryTask.Query()
          .setTermPropertyName(usageTagsKey)
          .setTermMatchValue(UsageTag.MGMT.name());

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(mgmtUsageTagClause);
      return querySpecification;
    } else if (UsageTag.CLOUD.name().equals(usageTag)) {
      QueryTask.Query cloudUsageTagClause = new QueryTask.Query()
          .setTermPropertyName(usageTagsKey)
          .setTermMatchValue(UsageTag.CLOUD.name());

      QueryTask.Query mgmtUsageTagClause = new QueryTask.Query()
          .setTermPropertyName(usageTagsKey)
          .setTermMatchValue(UsageTag.MGMT.name());

      mgmtUsageTagClause.occurance = QueryTask.Query.Occurance.MUST_NOT_OCCUR;

      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query.addBooleanClause(kindClause);
      querySpecification.query.addBooleanClause(cloudUsageTagClause);
      querySpecification.query.addBooleanClause(mgmtUsageTagClause);
      return querySpecification;
    } else {
      throw new IllegalStateException("Unknown usage tags value: " + usageTag);
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
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
    validateTaskSubStage(state.taskState);

    if (TaskState.TaskStage.STARTED == state.taskState.stage) {
      switch (state.taskState.subStage) {
          case UPLOAD_VIB:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + state.taskState.subStage);
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

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case UPLOAD_VIB:
        processUploadVibSubStage(currentState);
        break;
    }
  }

  private void processUploadVibSubStage(final State currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(currentState.querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    NodeGroupBroadcastResponse queryResponse = completedOp.getBody(NodeGroupBroadcastResponse.class);
                    Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
                    if (UsageTag.CLOUD.name().equals(currentState.usageTag)) {
                      if (documentLinks.isEmpty()) {
                        TaskUtils.sendSelfPatch(BulkProvisionHostsWorkflowService.this,
                            buildPatch(TaskState.TaskStage.FINISHED, null, null));
                        return;
                      }
                    } else {
                      checkState(documentLinks.size() > 0);
                    }

                    final AtomicInteger pendingChildren = new AtomicInteger(documentLinks.size());

                    for (String documentLink : documentLinks) {
                      processUploadVibSubStage(currentState, documentLink,
                          new FutureCallback<ProvisionHostTaskService.State>() {
                            @Override
                            public void onSuccess(@Nullable ProvisionHostTaskService.State state) {
                              switch (state.taskState.stage) {
                                case FINISHED:
                                  if (pendingChildren.decrementAndGet() == 0) {
                                    sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
                                  }
                                  break;
                                case FAILED:
                                  State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                                  patchState.taskState.failure = state.taskState.failure;
                                  TaskUtils.sendSelfPatch(BulkProvisionHostsWorkflowService.this, patchState);
                                  break;
                                case CANCELLED:
                                  sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
                                  break;
                              }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                              failTask(throwable);
                            }
                          });
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void processUploadVibSubStage(State currentState, String hostServiceLink,
                                        FutureCallback<ProvisionHostTaskService.State> provisionHostFutureCallback) {
    final Service service = this;

    FutureCallback<UploadVibTaskService.State> futureCallback = new FutureCallback<UploadVibTaskService.State>() {
      @Override
      public void onSuccess(@Nullable UploadVibTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED: {
            provisionHost(currentState, hostServiceLink, result.vibPaths.values().iterator().next(),
                provisionHostFutureCallback);
            break;
          }
          case FAILED: {
            State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
            patchState.taskState.failure = result.taskState.failure;
            TaskUtils.sendSelfPatch(service, patchState);
            break;
          }
          case CANCELLED:
            sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    UploadVibTaskService.State startState = createUploadVibTaskState(currentState, hostServiceLink);

    TaskUtils.startTaskAsync(
        this,
        UploadVibTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        UploadVibTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private UploadVibTaskService.State createUploadVibTaskState(
      final State currentState,
      String hostServiceLink) {
    UploadVibTaskService.State startState = new UploadVibTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = hostServiceLink;
    return startState;
  }

  private void provisionHost(State currentState,
                             String hostServiceLink,
                             String vibPath,
                             FutureCallback<ProvisionHostTaskService.State> provisionHostFutureCallback) {

    ProvisionHostTaskService.State startState = new ProvisionHostTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = hostServiceLink;
    startState.vibPath = vibPath;

    TaskUtils.startTaskAsync(
        this,
        ProvisionHostTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        ProvisionHostTaskService.State.class,
        currentState.taskPollDelay,
        provisionHostFutureCallback);
  }

  private void sendStageProgressPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", stage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
