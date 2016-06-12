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

package com.vmware.photon.controller.cloudstore.xenon.task;

import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class implementing service to remove stale tombstones and associated tasks from the cloud store.
 */
public class TombstoneCleanerService extends StatefulService {
  public TombstoneCleanerService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting Service %s", getSelfLink());
    State s = startOperation.getBody(State.class);
    try {
      initializeState(s);
      validateState(s);
      startOperation.setBody(s).complete();
    } catch (IllegalStateException t) {
      ServiceUtils.logSevere(this, t);
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }

    processStart(s);
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    State currentState = getState(patchOperation);
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());

    try {
      State patchState = patchOperation.getBody(State.class);
      validatePatch(currentState, patchState);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }

    processPatch(currentState);
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(State current) {
    InitializationUtils.initialize(current);

    if (current.documentExpirationTimeMicros <= 0) {
      current.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private State applyPatch(State current, State patch) {
    PatchUtils.patchState(current, patch);
    return current;
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private void validatePatch(State current, State patch) {
    ValidationUtils.validatePatch(current, patch);
    ValidationUtils.validateTaskStageProgression(current.taskState, patch.taskState);
  }

  /**
   * Does any additional processing after the start operation has been completed.
   *
   * @param current
   */
  private void processStart(final State current) {
    try {
      if (!isFinalStage(current)) {
        sendStageProgressPatch(current, current.taskState.stage);
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(final State current) {
    try {
      switch (current.taskState.stage) {
        case STARTED:
          final State finishPatch = new State();
          this.queryStaleTombstones(current, finishPatch);
          break;

        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;

        default:
          this.failTask(
              new IllegalStateException(
                  String.format("Un-expected stage: %s", current.taskState.stage))
          );
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Retrieves the stale tombstones and kicks of the subsequent processing.
   *
   * @param current
   * @param finishPatch
   */
  private void queryStaleTombstones(final State current, final State finishPatch) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          failTask(failure);
          return;
        }

        try {
          List<TombstoneService.State> tombstoneList =
              parseTombstoneQueryResults(completedOp.getBody(QueryTask.class));
          if (tombstoneList.size() == 0) {
            ServiceUtils.logInfo(TombstoneCleanerService.this, "No stale tombstones found.");
            finishTask(finishPatch);
            return;
          }

          ServiceUtils.logInfo(TombstoneCleanerService.this,
              "Count of stale tombstones found = " + tombstoneList.size());
          finishPatch.staleTombstones = tombstoneList.size();
          queryStaleTasks(finishPatch, tombstoneList);
        } catch (Throwable ex) {
          failTask(ex);
        }
      }
    };

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(buildTombstoneQuery(current))
        .setCompletion(handler);

    this.sendRequest(queryPost);
  }

  /**
   * Retrieves the list of stale tasks.
   *
   * @param finishPatch
   * @param tombstoneList
   */
  private void queryStaleTasks(final State finishPatch,
                               List<TombstoneService.State> tombstoneList) {
    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          failTask(failures.values().iterator().next());
          return;
        }

        try {
          Set<String> taskSet = new HashSet<>();
          for (Operation op : ops.values()) {
            NodeGroupBroadcastResponse query = op.getBody(NodeGroupBroadcastResponse.class);
            ServiceUtils.logInfo(TombstoneCleanerService.this, "Task broadcast query: %s", Utils.toJson(query));
            if (!query.failures.isEmpty()) {
              failTask(new RuntimeException("Failures in broadcast query for stale tasks."));
              return;
            }

            for (Map.Entry<URI, String> entry : query.jsonResponses.entrySet()) {
              QueryTask queryTask = Utils.fromJson(entry.getValue(), QueryTask.class);
              if (queryTask != null && queryTask.results != null) {
                taskSet.addAll(queryTask.results.documentLinks);
              }
            }
          }

          finishPatch.staleTasks = taskSet.size();
          if (taskSet.size() == 0) {
            deleteTombstones(finishPatch, tombstoneList);
          } else {
            deleteTasks(finishPatch, tombstoneList, taskSet);
          }
        } catch (Throwable ex) {
          failTask(ex);
        }
      }
    };

    URI queryUri = UriUtils.buildBroadcastRequestUri(
        UriUtils.buildUri(getHost(), com.vmware.xenon.services.common.ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
        ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    Collection<Operation> posts = new LinkedList<>();
    for (TombstoneService.State tombstone : tombstoneList) {
      Operation post = Operation
          .createPost(queryUri)
          .setBody(buildTaskQuery(tombstone))
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
          .forceRemote();

      posts.add(post);
    }

    OperationJoin join = OperationJoin.create(posts);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  /**
   * Deletes the stale task documents.
   *
   * @param finishPatch
   * @param tombstoneList
   * @param taskSet
   */
  private void deleteTasks(final State finishPatch, List<TombstoneService.State> tombstoneList, Set<String> taskSet) {
    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          failTask(failures.values().iterator().next());
          return;
        }

        finishPatch.deletedTasks = taskSet.size();
        deleteTombstones(finishPatch, tombstoneList);
      }
    };

    Collection<Operation> deletes = new LinkedList<>();
    for (String taskLink : taskSet) {
      Operation delete = Operation
          .createDelete(UriUtils.buildUri(getHost(), taskLink))
          .setBody("{}")
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
          .forceRemote();

      deletes.add(delete);
    }

    OperationJoin join = OperationJoin.create(deletes);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  /**
   * Deletes the the stale tombstone entities.
   *
   * @param finishPatch
   * @param tombstoneList
   */
  private void deleteTombstones(final State finishPatch, List<TombstoneService.State> tombstoneList) {
    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          failTask(failures.values().iterator().next());
          return;
        }

        finishPatch.deletedTombstones = tombstoneList.size();
        finishTask(finishPatch);
      }
    };

    Collection<Operation> deletes = new LinkedList<>();
    for (TombstoneService.State tombstone : tombstoneList) {
      Operation delete = Operation
          .createDelete(UriUtils.buildUri(getHost(), tombstone.documentSelfLink))
          .setBody("{}")
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
          .forceRemote();

      deletes.add(delete);
    }

    OperationJoin join = OperationJoin.create(deletes);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  private List<TombstoneService.State> parseTombstoneQueryResults(QueryTask result) {
    ServiceUtils.logInfo(TombstoneCleanerService.this, "Tombstone query: %s", Utils.toJson(result));

    List<TombstoneService.State> tombstoneList = new LinkedList<>();
    for (Map.Entry<String, Object> doc : result.results.documents.entrySet()) {
      tombstoneList.add(
          Utils.fromJson(doc.getValue(), TombstoneService.State.class));
    }

    return tombstoneList;
  }

  /**
   * Determines if the task is in a final state.
   *
   * @param s
   * @return
   */
  private boolean isFinalStage(State s) {
    return s.taskState.stage == TaskState.TaskStage.FINISHED ||
        s.taskState.stage == TaskState.TaskStage.FAILED ||
        s.taskState.stage == TaskState.TaskStage.CANCELLED;
  }

  private void finishTask(State patch) {
    if (patch.taskState == null) {
      patch.taskState = new TaskState();
    }
    patch.taskState.stage = TaskState.TaskStage.FINISHED;

    this.sendSelfPatch(patch);
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    this.sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param stage
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    this.sendSelfPatch(buildPatch(stage, null));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param state
   */
  private void sendSelfPatch(State state) {
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(state);
    this.sendRequest(patch);
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Throwable e) {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = stage;

    if (e != null) {
      s.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Builds the query spec to retrieve all expired tombstones.
   *
   * @param current
   * @return
   */
  private QueryTask buildTombstoneQuery(final State current) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(TombstoneService.State.class));

    QueryTask.NumericRange range = QueryTask.NumericRange.createLessThanRange(
        System.currentTimeMillis() - current.tombstoneExpirationAgeMillis);
    range.precisionStep = Integer.MAX_VALUE;
    QueryTask.Query ageClause = new QueryTask.Query()
        .setTermPropertyName(TombstoneService.State.FIELD_NAME_TOMBSTONE_TIME)
        .setNumericRange(range);

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(ageClause);

    QueryTask task = QueryTask.create(spec)
        .setDirect(true);

    return task;
  }

  private QueryTask buildTaskQuery(final TombstoneService.State tombstone) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(TaskService.State.class));

    QueryTask.Query entityIdClause = new QueryTask.Query()
        .setTermPropertyName(TaskService.State.FIELD_NAME_ENTITY_ID)
        .setTermMatchValue(tombstone.entityId);

    QueryTask.Query entityKindClause = new QueryTask.Query()
        .setTermPropertyName(TaskService.State.FIELD_NAME_ENTITY_KIND)
        .setTermMatchValue(tombstone.entityKind);

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(entityIdClause)
        .addBooleanClause(entityKindClause);

    QueryTask task = QueryTask.create(spec)
        .setDirect(true);

    return task;
  }

  /**
   * Durable service state data.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * Service execution stage.
     */
    @DefaultTaskState(value = TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    @DefaultBoolean(value = false)
    public Boolean isSelfProgressionDisabled;

    /**
     * Age after which tombstones and associated task entities will be deleted. (milliseconds)
     */
    @Immutable
    @NotNull
    @Positive
    public Long tombstoneExpirationAgeMillis;

    /**
     * The number of tombstones to delete.
     */
    @DefaultInteger(value = 0)
    public Integer staleTombstones;

    /**
     * The number of tasks to delete.
     */
    @DefaultInteger(value = 0)
    public Integer staleTasks;

    /**
     * The number of tombstones that were deleted successfully.
     */
    @DefaultInteger(value = 0)
    public Integer deletedTombstones;

    /**
     * The number of tasks that were deleted successfully.
     */
    @DefaultInteger(value = 0)
    public Integer deletedTasks;
  }
}
