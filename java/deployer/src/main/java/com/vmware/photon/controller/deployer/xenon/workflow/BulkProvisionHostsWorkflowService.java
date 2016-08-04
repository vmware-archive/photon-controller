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

import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorService;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ProvisionHostTaskService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This class implements a Xenon microservice which performs the task of provisioning a set of ESX hosts.
 */
public class BulkProvisionHostsWorkflowService extends StatefulService {

  /**
   * This class represents the document state associated with a {@link BulkProvisionHostsWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
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
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    public Integer taskPollDelay;

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
     * This value represents whether the provisioning of the host is auth enabled or not.
     */
    @Immutable
    public boolean authEnabled;
  }

  public BulkProvisionHostsWorkflowService() {
    super(State.class);

    /**
     * These attributes are required because the {@link UploadVibTaskService} task is scheduled by
     * the task scheduler. If and when this is not the case -- either these attributes are no
     * longer required, or this task is not scheduled by the task scheduler -- then they should be
     * removed, along with the same attributes in higher-level task services which create instances
     * of this task.
     */
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
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

    try {
      validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    if (startState.querySpecification == null) {
      startState.querySpecification = buildQuerySpecification(startState.usageTag);
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        sendStageProgressPatch(TaskState.TaskStage.STARTED);
      } else {
        throw new IllegalStateException("Task is not restartable");
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
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processProvisionHosts(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void processProvisionHosts(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(currentState.querySpecification).setDirect(true))
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processProvisionHosts(currentState, QueryTaskUtils.getBroadcastQueryDocumentLinks(o));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processProvisionHosts(State currentState, Set<String> hostServiceLinks) {

    if (hostServiceLinks.isEmpty() && currentState.usageTag.equals(UsageTag.CLOUD.name())) {
      ServiceUtils.logInfo(this, "No dedicated cloud hosts were found");
      sendStageProgressPatch(TaskState.TaskStage.FINISHED);
      return;
    }

    checkState(hostServiceLinks.size() > 0);

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processProvisionHostsSubStage(
                      currentState,
                      hostServiceLinks,
                      o.getBody(DeploymentService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processProvisionHostsSubStage(State currentState,
                                             Set<String> hostServiceLinks,
                                             DeploymentService.State deploymentState) {

    if (deploymentState.sdnEnabled) {
      // Due to bug https://bugzilla.eng.vmware.com/show_bug.cgi?id=1646837, we cannot provision
      // hosts concurrently if NSX is used. Otherwise NSX will fail to register hosts as
      // fabric nodes.
      processProvisionHostsSubStage(currentState, hostServiceLinks.iterator());
    } else {
      ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
      startState.parentTaskLink = getSelfLink();
      startState.parentPatchBody = Utils.toJson(false, false, buildPatch(TaskState.TaskStage.FINISHED, null));
      startState.pendingCompletionCount = hostServiceLinks.size();
      startState.errorThreshold = 0.0;

      sendRequest(Operation
          .createPost(this, ChildTaskAggregatorFactoryService.SELF_LINK)
          .setBody(startState)
          .setCompletion(
              (o, e) -> {
                try {
                  if (e != null) {
                    failTask(e);
                  } else {
                    processProvisionHostsSubStage(currentState, hostServiceLinks,
                        o.getBody(ServiceDocument.class).documentSelfLink);
                  }
                } catch (Throwable t) {
                  failTask(t);
                }
              }));
    }
  }

  private void processProvisionHostsSubStage(State currentState,
                                             Iterator<String> hostServiceLink) {
    if (hostServiceLink.hasNext()) {
      ProvisionHostTaskService.State startState = new ProvisionHostTaskService.State();
      startState.deploymentServiceLink = currentState.deploymentServiceLink;
      startState.hostServiceLink = hostServiceLink.next();
      startState.authEnabled = currentState.authEnabled;

      TaskUtils.startTaskAsync(
          this,
          ProvisionHostTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          ProvisionHostTaskService.State.class,
          currentState.taskPollDelay,
          new FutureCallback<ProvisionHostTaskService.State>() {
            @Override
            public void onSuccess(@Nullable ProvisionHostTaskService.State state) {
              processProvisionHostsSubStage(currentState, hostServiceLink);
            }

            @Override
            public void onFailure(Throwable throwable) {
              failTask(throwable);
            }
          }
      );
    } else {
      sendStageProgressPatch(TaskState.TaskStage.FINISHED);
    }
  }

  private void processProvisionHostsSubStage(State currentState,
                                             Set<String> hostServiceLinks,
                                             String aggregatorServiceLink) {

    Stream<Operation> taskStartOps = hostServiceLinks.stream().map((hostServiceLink) -> {
      ProvisionHostTaskService.State startState = new ProvisionHostTaskService.State();
      startState.parentTaskServiceLink = aggregatorServiceLink;
      startState.deploymentServiceLink = currentState.deploymentServiceLink;
      startState.hostServiceLink = hostServiceLink;
      return Operation.createPost(this, ProvisionHostTaskFactoryService.SELF_LINK).setBody(startState);
    });

    OperationJoin
        .create(taskStartOps)
        .setCompletion(
            (ops, exs) -> {
              try {
                if (exs != null && !exs.isEmpty()) {
                  failTask(exs.values());
                }
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failure));
  }

  private void failTask(Collection<Throwable> failures) {
    ServiceUtils.logSevere(this, failures);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failures.iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage,
                                    @Nullable Throwable failure) {

    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;

    if (null != failure) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
