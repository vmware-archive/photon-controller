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

package com.vmware.photon.controller.cloudstore.dcp.task;

import com.vmware.photon.controller.cloudstore.dcp.entity.AvailabilityZoneService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.util.Pair;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultBoolean;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultLong;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.LuceneQueryTaskFactoryService;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class implementing service to remove stale availability zones and associated tasks from the cloud store.
 */
public class AvailabilityZoneCleanerService extends StatefulService {

  private static final String DOCUMENT_UPDATE_TIME_MICROS = "documentUpdateTimeMicros";
  private static final String AVAILABILITY_ZONE_STATE_PENDING_DELETE = "PENDING_DELETE";
  public static final long DEFAULT_AVAILABILITY_ZONE_EXPIRATION_AGE_IN_MICROS = 5 * 60 * 1000 * 1000L; // 5 min

  public AvailabilityZoneCleanerService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State state = start.getBody(State.class);
    initializeState(state);
    validateState(state);
    start.setBody(state).complete();
    processStart(state);
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatch(currentState, patchState);
    currentState = applyPatch(currentState, patchState);
    validateState(currentState);
    patch.complete();
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
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
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
   * @param state
   */
  private void processPatch(final State state) {
    if (state.isSelfProgressionDisabled) {
      ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      return;
    }

    try {
      switch (state.taskState.stage) {
        case STARTED:
          this.queryStaleAvailabilityZones(state);
          break;

        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;

        default:
          this.failTask(
              new IllegalStateException(
                  String.format("Un-expected stage: %s", state.taskState.stage))
          );
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Retrieves the stale availability zones and kicks of the subsequent processing.
   *
   * @param state
   */
  private void queryStaleAvailabilityZones(final State state) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          failTask(failure);
          return;
        }

        try {
          List<AvailabilityZoneService.State> availabilityZoneList =
              parseAvailabilityZoneQueryResults(completedOp.getBody(QueryTask.class));
          if (availabilityZoneList.size() == 0) {
            ServiceUtils.logInfo(AvailabilityZoneCleanerService.this, "No stale availability zone(s) found.");
            finishTask(state);
            return;
          }

          processStaleAvailabilityZones(availabilityZoneList, state);
        } catch (Throwable ex) {
          failTask(ex);
        }
      }
    };

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(), LuceneQueryTaskFactoryService.SELF_LINK))
        .setBody(buildAvailabilityZoneQuery(state))
        .setCompletion(handler);

    this.sendRequest(queryPost);
  }

  private void processStaleAvailabilityZones(List<AvailabilityZoneService.State> availabilityZoneList, State state) {
    ServiceUtils.logInfo(this, "Count of stale availability zones = " + availabilityZoneList.size());
    state.staleAvailabilityZones = availabilityZoneList.size();

    final AtomicInteger pendingStaleAvailabilityZones = new AtomicInteger(availabilityZoneList.size());
    final Pair<Integer, Integer> deletedTasksAndAvailabilityZones = new Pair<>(0, 0);

    FutureCallback<Pair<Integer, Integer>> futureCallback = new FutureCallback<Pair<Integer, Integer>>() {
          @Override
          public void onSuccess(Pair<Integer, Integer> result) {
            synchronized (deletedTasksAndAvailabilityZones) {
              deletedTasksAndAvailabilityZones.setFirst(
                  deletedTasksAndAvailabilityZones.getFirst() + result.getFirst());
              deletedTasksAndAvailabilityZones.setSecond(
                  deletedTasksAndAvailabilityZones.getSecond() + result.getSecond());
            }

            if (0 == pendingStaleAvailabilityZones.decrementAndGet()) {
              state.deletedTasks = deletedTasksAndAvailabilityZones.getFirst();
              state.deletedAvailabilityZones = deletedTasksAndAvailabilityZones.getSecond();
              finishTask(state);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };


    for (AvailabilityZoneService.State availabilityZone : availabilityZoneList) {
      queryStaleHosts(availabilityZone, futureCallback);
    }
  }

  /**
   * Retrieves the list of stale tasks.
   * @param availabilityZone
   * @param futureCallback
   */
  private void queryStaleHosts(AvailabilityZoneService.State availabilityZone,
                               FutureCallback<Pair<Integer, Integer>> futureCallback) {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          if (!QueryTaskUtils.getQueryResultDocumentLinks(operation).isEmpty()) {
            futureCallback.onSuccess(new Pair<>(0, 0));
          } else {
            queryStaleTasks(availabilityZone, futureCallback);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    URI queryUri = UriUtils.buildBroadcastRequestUri(
        UriUtils.buildUri(getHost(), com.vmware.xenon.services.common.ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
        ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    Operation queryOperation = Operation
        .createPost(queryUri)
        .setBody(buildHostsQuery(availabilityZone))
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
        .setCompletion(completionHandler);

    sendRequest(queryOperation);
  }

  /**
   * Retrieves the list of stale tasks.
   * @param availabilityZone
   * @param futureCallback
   */
  private void queryStaleTasks(AvailabilityZoneService.State availabilityZone,
                               FutureCallback<Pair<Integer, Integer>> futureCallback) {
    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          if (QueryTaskUtils.getQueryResultDocumentLinks(operation).isEmpty()) {
            deleteAvailabilityZone(availabilityZone, 0, futureCallback);
          } else {
            Set<String> taskSet = QueryTaskUtils.getQueryResultDocumentLinks(operation);
            deleteTasks(taskSet, availabilityZone, futureCallback);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    URI queryUri = UriUtils.buildBroadcastRequestUri(
        UriUtils.buildUri(getHost(), com.vmware.xenon.services.common.ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
        ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    Operation queryOperation = Operation
        .createPost(queryUri)
        .setBody(buildTaskQuery(availabilityZone))
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
        .setCompletion(completionHandler);

    sendRequest(queryOperation);
  }

  /**
   * Deletes the stale task documents.
   * @param taskSet
   * @param availabilityZone
   * @param futureCallback
   */
  private void deleteTasks(Set<String> taskSet,
                           AvailabilityZoneService.State availabilityZone,
                           FutureCallback<Pair<Integer, Integer>> futureCallback) {
    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          failTask(failures.values().iterator().next());
          return;
        }

        deleteAvailabilityZone(availabilityZone, taskSet.size(), futureCallback);
      }
    };

    Collection<Operation> deletes = new LinkedList<>();
    for (String taskLink : taskSet) {
      Operation delete = Operation
          .createDelete(UriUtils.buildUri(getHost(), taskLink))
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));

      deletes.add(delete);
    }

    OperationJoin join = OperationJoin.create(deletes);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  /**
   * Deletes the the stale availability zone entities.
   * @param availabilityZone
   * @param deletedTasks
   * @param futureCallback
   */
  private void deleteAvailabilityZone(final AvailabilityZoneService.State availabilityZone,
                                      final Integer deletedTasks,
                                      final FutureCallback<Pair<Integer, Integer>> futureCallback) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          failTask(failure);
          return;
        }
        futureCallback.onSuccess(new Pair<>(deletedTasks, 1));
      }
    };

    Operation deleteOperation = Operation
      .createDelete(UriUtils.buildUri(getHost(), availabilityZone.documentSelfLink))
      .setCompletion(handler)
      .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));

    this.sendRequest(deleteOperation);
  }

  private List<AvailabilityZoneService.State> parseAvailabilityZoneQueryResults(QueryTask result) {
    ServiceUtils.logInfo(AvailabilityZoneCleanerService.this, "AvailabilityZone query: %s", Utils.toJson(result));

    List<AvailabilityZoneService.State> availabilityZoneList = new LinkedList<>();
    for (Map.Entry<String, Object> doc : result.results.documents.entrySet()) {
      availabilityZoneList.add(
          Utils.fromJson(doc.getValue(), AvailabilityZoneService.State.class));
    }

    return availabilityZoneList;
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
      ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
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
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (e != null) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  /**
   * Builds the query spec to retrieve all PENDING_DELETE availability zones.
   *
   * @param state
   * @return
   */
  private QueryTask buildAvailabilityZoneQuery(final State state) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(AvailabilityZoneService.State.class));

    QueryTask.Query stateClause = new QueryTask.Query()
        .setTermPropertyName(AvailabilityZoneService.State.FIELD_NAME_STATE)
        .setTermMatchValue(AVAILABILITY_ZONE_STATE_PENDING_DELETE);

    Long durationInMicros = Utils.getNowMicrosUtc() - state.availabilityZoneExpirationAgeInMicros;
    QueryTask.NumericRange range = QueryTask.NumericRange.createLessThanRange(durationInMicros);
    range.precisionStep = Integer.MAX_VALUE;
    QueryTask.Query timeClause = new QueryTask.Query()
        .setTermPropertyName(DOCUMENT_UPDATE_TIME_MICROS)
        .setNumericRange(range);

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(timeClause)
        .addBooleanClause(stateClause);

    return QueryTask.create(spec).setDirect(true);
  }

  private QueryTask buildHostsQuery(final AvailabilityZoneService.State availabilityZone) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.Query availabilityZoneClause = new QueryTask.Query()
        .setTermPropertyName(HostService.State.FIELD_NAME_AVAILABILITY_ZONE)
        .setTermMatchValue(ServiceUtils.getIDFromDocumentSelfLink(availabilityZone.documentSelfLink));

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(availabilityZoneClause);

    return QueryTask.create(spec).setDirect(true);
  }

  private QueryTask buildTaskQuery(final AvailabilityZoneService.State availabilityZone) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(TaskService.State.class));

    QueryTask.Query entityIdClause = new QueryTask.Query()
        .setTermPropertyName(TaskService.State.FIELD_NAME_ENTITY_ID)
        .setTermMatchValue(ServiceUtils.getIDFromDocumentSelfLink(availabilityZone.documentSelfLink));

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(entityIdClause);

    return QueryTask.create(spec).setDirect(true);
  }

  /**
   * Durable service state data.
   */
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
     * Age after which availability zones and associated task entities will be deleted. (milliseconds)
     */
    @DefaultLong(value = DEFAULT_AVAILABILITY_ZONE_EXPIRATION_AGE_IN_MICROS)
    public Long availabilityZoneExpirationAgeInMicros;

    /**
     * The number of availability zones to delete.
     */
    @DefaultInteger(value = 0)
    public Integer staleAvailabilityZones;

    /**
     * The number of availability zones that were deleted successfully.
     */
    @DefaultInteger(value = 0)
    public Integer deletedAvailabilityZones;

    /**
     * The number of tasks that were deleted successfully.
     */
    @DefaultInteger(value = 0)
    public Integer deletedTasks;
  }
}
