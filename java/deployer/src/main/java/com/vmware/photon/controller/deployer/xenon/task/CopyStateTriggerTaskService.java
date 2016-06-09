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
package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultString;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.xenon.util.Pair;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class moves DCP state between two DCP clusters.
 */
public class CopyStateTriggerTaskService extends StatefulService {
  private static final long OWNER_SELECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  private static final long DEFAULT_TRIGGER_INTERVAL = TimeUnit.MINUTES.toMicros(5);

  /**
   * Service execution stages.
   */
  public static enum ExecutionState {
    RUNNING,
    STOPPED
  }

  /**
   * This class defines the document state associated with a single
   * {@link CopyStateTriggerTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    public ExecutionState executionState;

    @Immutable
    @NotNull
    public Set<Pair<String, Integer>> sourceServers;

    @Immutable
    @DefaultString(value = "http")
    public String sourceProtocol;

    @Immutable
    @NotNull
    public String destinationIp;

    @Immutable
    @NotNull
    public Integer destinationPort;

    @Immutable
    @DefaultString(value = "http")
    public String destinationProtocol;

    @Immutable
    @NotNull
    public String factoryLink;

    @Immutable
    @NotNull
    public String sourceFactoryLink;

    @Immutable
    @DefaultString(value = "taskState.stage")
    public String taskStateFieldName;

    @Immutable
    @DefaultInteger(value = 500)
    public Integer queryResultLimit;

    @DefaultLong(value = 0)
    public Long triggersSuccess;

    @DefaultLong(value = 0)
    public Long triggersError;

    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    @Immutable
    @DefaultBoolean(value = false)
    public Boolean performHostTransformation;

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean enableMaintenance;
  }

  public CopyStateTriggerTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    // Initialize the task stage
    State state = start.getBody(State.class);
    if (state.executionState == null) {
      state.executionState = ExecutionState.RUNNING;
    }
    if (!state.factoryLink.endsWith("/")) {
      state.factoryLink += "/";
    }
    if (!state.sourceFactoryLink.endsWith("/")) {
      state.sourceFactoryLink += "/";
    }
    InitializationUtils.initialize(state);

    try {
      validateState(state);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      start.fail(t);
      return;
    }

    if (state.enableMaintenance) {
      this.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
      this.setMaintenanceIntervalMicros(DEFAULT_TRIGGER_INTERVAL);
    }

    start.setBody(state).complete();
  }

  /**
   * Handle service patch.
   */
  @Override
  public void handlePatch(Operation patch) {
    try {
      State currentState = getState(patch);
      State patchState = patch.getBody(State.class);

      this.validatePatch(patchState);
      this.applyPatch(currentState, patchState);
      this.validateState(currentState);
      patch.complete();

      // Process and complete patch.
      processPatch(patch, currentState, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(e);
      }
    }
  }

  /**
   * Handle service periodic maintenance calls.
   */
  @Override
  public void handleMaintenance(Operation post) {
    post.complete();

    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation op, Throwable failure) {
        if (null != failure) {
          // query failed so abort and retry next time
          logFailure(failure);
          return;
        }

        NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
        if (!getHost().getId().equals(rsp.ownerNodeId)) {
          ServiceUtils.logInfo(CopyStateTriggerTaskService.this,
              "Host[%s]: Not owner of scheduler [%s] (Owner Info [%s])",
              getHost().getId(), getSelfLink(), Utils.toJson(true, false, rsp));
          return;
        }
        sendSelfPatch(new State());
      }
    };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(OWNER_SELECTION_TIMEOUT))
        .setCompletion(handler);
    getHost().selectOwner(null, getSelfLink(), selectOwnerOp);
  }

  /**
   * Process patch.
   */
  private void processPatch(Operation patch, final State currentState, final State patchState) {
    // If the triggered is stopped or this is not a pulse, exit.
    if (currentState.executionState != ExecutionState.RUNNING
        || patchState.triggersSuccess != null
        || patchState.triggersError != null) {
      return;
    }

    generateQueryCopyStateTaskQuery(currentState).setCompletion((o, t) -> {
      if (t != null) {
        failTrigger(currentState, t);
        return;
      }
      List<CopyStateTaskService.State> documents =
          QueryTaskUtils.getBroadcastQueryDocuments(CopyStateTaskService.State.class, o).stream()
        .filter((d) -> d.taskState.stage == TaskStage.CREATED || d.taskState.stage == TaskStage.STARTED)
        .collect(Collectors.toList());
      if (documents.isEmpty()) {
        startNewTask(patch, currentState);
      }
    })
    .sendWith(this);
  }

  private void startNewTask(Operation patch, State currentState) {
    Operation copyStateTaskQuery = generateQueryCopyStateTaskQuery(currentState);
    OperationSequence.create(copyStateTaskQuery)
        .setCompletion((os, ts) -> {
          if (ts != null && !ts.isEmpty()) {
            failTrigger(currentState, ts);
            return;
          }
          NodeGroupBroadcastResponse queryResponse = os.get(copyStateTaskQuery.getId())
              .getBody(NodeGroupBroadcastResponse.class);
          List<CopyStateTaskService.State> copyStates = QueryTaskUtils
              .getBroadcastQueryDocuments(CopyStateTaskService.State.class, queryResponse);
          List<CopyStateTaskService.State> runningStates = copyStates
              .stream()
              .filter(state -> !TaskUtils.finalTaskStages.contains(state.taskState.stage))
              .collect(Collectors.toList());

          if (runningStates.isEmpty()) {
            long latestUpdateTime = copyStates.stream()
                .filter(state -> state.taskState.stage == TaskStage.FINISHED)
                .mapToLong(state -> state.lastDocumentUpdateTimeEpoc.longValue())
                .max()
                .orElse(0);
            CopyStateTaskService.State startState = buildCopyStateStartState(currentState, latestUpdateTime);
            startCopyStateTask(currentState, startState);
          }
        })
        .sendWith(this);
  }

  private void startCopyStateTask(State currentState, CopyStateTaskService.State startState) {
    sendRequest(
        Operation.createPost(UriUtils.buildUri(getHost(), CopyStateTaskFactoryService.SELF_LINK, null))
            .setBody(startState)
            .setCompletion((o, t) -> {
              if (t != null) {
                failTrigger(currentState, t);
                return;
              }
              succeedTrigger(currentState);
            }));
  }

  private CopyStateTaskService.State buildCopyStateStartState(State currentState, long lastestUpdateTime) {
    CopyStateTaskService.State state = new CopyStateTaskService.State();
    state.destinationIp = currentState.destinationIp;
    state.destinationPort = currentState.destinationPort;
    state.destinationProtocol = currentState.destinationProtocol;
    state.factoryLink = currentState.factoryLink;
    state.queryDocumentsChangedSinceEpoc = lastestUpdateTime;
    state.queryResultLimit = currentState.queryResultLimit;
    state.sourceFactoryLink = currentState.sourceFactoryLink;
    state.sourceServers = currentState.sourceServers;
    state.sourceProtocol = currentState.sourceProtocol;
    state.taskStateFieldName = currentState.taskStateFieldName;
    state.performHostTransformation = currentState.performHostTransformation;
    return state;
  }

  private void failTrigger(State currentState, Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    failTrigger(currentState);
  }

  private void failTrigger(State currentState, Map<Long, Throwable> failures) {
    failures.values().forEach((throwable) -> ServiceUtils.logSevere(this, throwable));
    failTrigger(currentState);
  }

  private void failTrigger(State currentState) {
    State newState = new State();
    newState.triggersError = currentState.triggersError + 1;
    sendSelfPatch(newState);
  }

  private void succeedTrigger(State currentState) {
    State newState = new State();
    newState.triggersSuccess = currentState.triggersSuccess + 1;
    sendSelfPatch(newState);
  }

  private Operation generateQueryCopyStateTaskQuery(State currentState) {
    QueryTask.Query typeClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(CopyStateTaskService.State.class));
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(typeClause);
    querySpecification.query.addBooleanClause(
        buildTermQuery(CopyStateTaskService.State.FIELD_NAME_FACTORY_LINK, currentState.factoryLink));
    querySpecification.query.addBooleanClause(
        buildTermQuery(CopyStateTaskService.State.FIELD_NAME_SOURCE_FACTORY_LINK, currentState.sourceFactoryLink));
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  private QueryTask.Query buildTermQuery(String properyName, String matchValue) {
    return new QueryTask.Query()
        .setTermPropertyName(properyName)
        .setTermMatchValue(matchValue);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    ValidationUtils.validateState(current);
    checkNotNull(current.executionState, "ExecutionState cannot be null.");
    checkIsPositiveNumber(current.triggersSuccess, "triggersSuccess");
    checkIsPositiveNumber(current.triggersError, "triggersError");
  }

  /**
   * Validate patch correctness.
   *
   * @param patch
   */
  protected void validatePatch(State patch) {
  }

  /**
   * Applies patch to current document state.
   *
   * @param current
   * @param patch
   */
  protected void applyPatch(State current, State patch) {
    if (patch.executionState != null) {
      current.executionState = patch.executionState;
    }
    current.triggersSuccess = updateLongWithMax(current.triggersSuccess, patch.triggersSuccess);
    current.triggersError = updateLongWithMax(current.triggersError, patch.triggersError);
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private void checkIsPositiveNumber(Long value, String description) {
    checkNotNull(value == null, description + " cannot be null.");
    checkState(value >= 0, description + " cannot be negative.");
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private Long updateLongWithMax(Long previousValue, Long newValue) {
    if (newValue == null) {
      return previousValue;
    }
    if (newValue < 0) {
      return 0L;
    }
    return Math.max(previousValue, newValue);
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param s
   */
  private void sendSelfPatch(State s) {
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(s);
    sendRequest(patch);
  }

  /**
   * Log failed query.
   *
   * @param e
   */
  private void logFailure(Throwable e) {
    ServiceUtils.logSevere(this, e);
  }

}
