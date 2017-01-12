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

import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;

import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTriggerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTriggerTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTriggerTaskService.ExecutionState;
import com.vmware.photon.controller.deployer.xenon.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.MigrationStatusUpdateTriggerService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements a Xenon micro-service which performs the task of
 * initializing migration of an existing deployment to a new deployment.
 */
public class InitializeDeploymentMigrationWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link InitializeDeploymentMigrationWorkflowService} task.
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
      CONTINOUS_MIGRATE_DATA,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link InitializeDeploymentMigrationWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
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
     * the state of a Xenon task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents a reference to the node group to use as the source of the migration
     * operation. This is a URI with form {protocol}://{address}:{port}/core/node-groups/{id} where
     * the ID is usually "default".
     */
    @NotNull
    @Immutable
    public URI sourceNodeGroupReference;

    /**
     * This value represents the base URI of the nodes in the source node group.
     */
    @WriteOnce
    public List<URI> sourceURIs;

    /**
     * This value represents the id of the destination deployment.
     */
    @NotNull
    @Immutable
    public String destinationDeploymentId;
  }

  public InitializeDeploymentMigrationWorkflowService() {
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
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CONTINOUS_MIGRATE_DATA;
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
        if (currentState.sourceURIs == null) {
          populateCurrentState(currentState);
          return;
        }
        processStartedState(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void populateCurrentState(State currentState) {

    sendRequest(Operation
        .createGet(currentState.sourceNodeGroupReference)
        .setCompletion((o, e) -> {
          try {
            if (e != null) {
              failTask(e);
            } else {
              processNodeGroupState(currentState, o.getBody(NodeGroupService.NodeGroupState.class));
            }
          } catch (Throwable t) {
            failTask(t);
          }
        }));
  }

  private void processNodeGroupState(State currentState, NodeGroupService.NodeGroupState nodeGroupState) {
    List<URI> sourceURIs = nodeGroupState.nodes.values().stream()
        .map(this::extractBaseURI).collect(Collectors.toList());
    State patchState = buildPatch(currentState.taskState.stage, currentState.taskState.subStage, null);
    patchState.sourceURIs = sourceURIs;
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private URI extractBaseURI(NodeState nodeState) {
    return extractBaseURI(nodeState.groupReference);
  }

  private URI extractBaseURI(URI uri) {
    return UriUtils.buildUri(uri.getScheme(), uri.getHost(), uri.getPort(), null, null);
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case CONTINOUS_MIGRATE_DATA:
        migrateDataContinously(currentState);
        break;
    }
  }

  private Operation generateKindQuery(Class<?> clazz) {
    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(clazz));
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = typeClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(
                getHost(), ServiceUriPaths.XENON.CORE_LOCAL_QUERY_TASKS), ServiceUriPaths.XENON.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  private QueryTask.QuerySpecification buildHostQuerySpecification() {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query
        .addBooleanClause(kindClause)
        .addBooleanClause(
            Query.Builder.create()
                .addFieldClause(HostService.State.FIELD_NAME_STATE, HostState.READY.name())
                .build());
    return querySpecification;
  }

  private void migrateDataContinously(State currentState) {
    // Start MigrationStatusUpdateService
    MigrationStatusUpdateTriggerService.State startState = new MigrationStatusUpdateTriggerService.State();
    startState.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + currentState.destinationDeploymentId;
    startState.documentSelfLink = currentState.destinationDeploymentId;

    OperationSequence
        .create(createStartMigrationOperations(currentState))
        .setCompletion((os, ts) -> {
          if (ts != null) {
            failTask(ts.values());
          }
        })
        .next(Operation
            .createPost(UriUtils.buildUri(getHost(), MigrationStatusUpdateTriggerFactoryService.SELF_LINK, null))
            .setBody(startState))
        .setCompletion((os, ts) -> {
          if (ts != null) {
            failTask(ts.values());
            return;
          }
          sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        })
        .sendWith(this);
  }

  private OperationJoin createStartMigrationOperations(State currentState) {

    Stream<Operation> copyStateTriggerTaskStartOps = HostUtils.getDeployerContext(this)
        .getUpgradeInformation().stream().map((upgradeInfo) -> {
          CopyStateTriggerTaskService.State startState = new CopyStateTriggerTaskService.State();
          startState.executionState = ExecutionState.RUNNING;
          startState.sourceURIs = currentState.sourceURIs;
          startState.sourceFactoryLink = upgradeInfo.sourceFactoryServicePath;
          startState.destinationURI = getHost().getUri();
          startState.destinationFactoryLink = upgradeInfo.destinationFactoryServicePath;
          startState.performHostTransformation = true;
          return Operation.createPost(this, CopyStateTriggerTaskFactoryService.SELF_LINK).setBody(startState);
        });

    return OperationJoin.create(copyStateTriggerTaskStartOps);
  }

  private void waitUntilCopyStateTasksFinished(CompletionHandler handler, State currentState) {
    // wait until all the copy-state services are done
    generateQueryCopyStateTaskQuery()
        .setCompletion((op, t) -> {
          if (t != null) {
            handler.handle(op, t);
            return;
          }
          List<CopyStateTaskService.State> documents =
              QueryTaskUtils.getBroadcastQueryDocuments(CopyStateTaskService.State.class, op);
          List<CopyStateTaskService.State> runningServices = documents.stream()
              .filter((d) -> d.taskState.stage == TaskStage.CREATED || d.taskState.stage == TaskStage.STARTED)
              .collect(Collectors.toList());
          if (runningServices.isEmpty()) {
            handler.handle(op, t);
            return;
          }
          getHost().schedule(
              () -> waitUntilCopyStateTasksFinished(handler, currentState),
              currentState.taskPollDelay,
              TimeUnit.MILLISECONDS);
        })
        .sendWith(this);
  }

  private Operation generateQueryCopyStateTaskQuery() {
    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(CopyStateTaskService.State.class)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build();
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.XENON.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.XENON.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask);
  }

  private State applyPatch(State currentState, State patchState) {
    if (patchState.taskState.stage != currentState.taskState.stage
        || patchState.taskState.subStage != currentState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      currentState.taskState = patchState.taskState;
    }

    if (patchState.sourceURIs != null) {
      currentState.sourceURIs = patchState.sourceURIs;
    }

    return currentState;
  }


  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case CONTINOUS_MIGRATE_DATA:
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

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (null != currentState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  private void failTask(Collection<Throwable> failures) {
    failures.forEach((throwable) -> ServiceUtils.logSevere(this, throwable));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
  }

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
