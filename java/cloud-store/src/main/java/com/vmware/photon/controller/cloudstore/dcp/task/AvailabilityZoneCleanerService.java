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
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.LuceneQueryTaskFactoryService;
import com.vmware.xenon.services.common.QueryTask;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Class implementing service to remove stale availability zones and associated tasks from the cloud store.
 */
public class AvailabilityZoneCleanerService extends StatefulService {

  private static final String DOCUMENT_UPDATE_TIME_MICROS = "documentUpdateTimeMicros";
  private static final String AVAILABILITY_ZONE_STATE_PENDING_DELETE = "PENDING_DELETE";
  private static final String AVAILABILITY_ZONE_ENTITY_KIND = "availability-zone";
  public static final int AVAILABILITY_ZONES_PROCESSING_BATCH_SIZE = 5;
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
      failTask(e, null);
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
                  String.format("Un-expected stage: %s", state.taskState.stage)),
              null
          );
      }
    } catch (Throwable e) {
      failTask(e, null);
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
          failTask(failure, null);
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
          failTask(ex, null);
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

    final Collection<Operation> queryOperations = new LinkedList<>();
    final Map<Long, String> queryOperationIdToAvailabilityZoneMap = new HashMap<>();
    final Collection<Throwable> exceptions = new LinkedList<>();

    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          exceptions.addAll(failures.values());
        }

        Collection<String> tombStoneAvailabilityZones = new LinkedList<>();
        if (ops != null && !ops.isEmpty()) {
          for (Operation operation : ops.values()) {
            if (QueryTaskUtils.getBroadcastQueryDocumentLinks(operation).isEmpty()) {
              tombStoneAvailabilityZones.add(queryOperationIdToAvailabilityZoneMap.get(operation.getId()));
            }
          }
        }

        if (!tombStoneAvailabilityZones.isEmpty()) {
          tombStoneAvailabilityZones(tombStoneAvailabilityZones, state, exceptions);
        } else {
          ServiceUtils.logInfo(AvailabilityZoneCleanerService.this,
              "No availability zone(s) found to perform tombstone.");
          endTask(state, exceptions);
        }
      }
    };

    URI queryUri = UriUtils.buildBroadcastRequestUri(
        UriUtils.buildUri(getHost(), com.vmware.xenon.services.common.ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
        ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    for (final AvailabilityZoneService.State availabilityZone : availabilityZoneList) {
      Operation queryOperation = Operation
          .createPost(queryUri)
          .setBody(buildHostsQuery(availabilityZone))
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));

      queryOperations.add(queryOperation);
      queryOperationIdToAvailabilityZoneMap.put(queryOperation.getId(), availabilityZone.documentSelfLink);
    }

    OperationJoin join = OperationJoin.create(queryOperations);
    join.setCompletion(handler);
    join.sendWith(this, AVAILABILITY_ZONES_PROCESSING_BATCH_SIZE);
  }

  /**
   * Create Tombstone entities.
   * @param availabilityZones
   * @param state
   * @param exceptions
   */
  private void tombStoneAvailabilityZones(final Collection<String> availabilityZones,
                                          State state,
                                          Collection<Throwable> exceptions) {
    Collection<Operation> postOperations = new LinkedList<>();
    final Map<Long, String> tombStoneOperationIdToAvailabilityZoneMap = new HashMap<>();

    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
            exceptions.addAll(failures.values());
        }

        Collection<String> deleteAvailabilityZones = new LinkedList<>();
        if (ops != null && !ops.isEmpty()) {
          for (Operation op : ops.values()) {
            if (op.getStatusCode() == Operation.STATUS_CODE_OK
                || op.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
              deleteAvailabilityZones.add(tombStoneOperationIdToAvailabilityZoneMap.get(op.getId()));
            }
          }
        }

        if (!deleteAvailabilityZones.isEmpty()) {
          deleteAvailabilityZones(deleteAvailabilityZones, state, exceptions);
        } else {
          ServiceUtils.logInfo(AvailabilityZoneCleanerService.this, "No availability zone(s) found to be deleted.");
          endTask(state, exceptions);
        }
      }
    };

    for (String availabilityZone : availabilityZones) {
      TombstoneService.State tombStoneState = createTombStoneState(availabilityZone);
      Operation postOperation = Operation
          .createPost(UriUtils.buildUri(getHost(), TombstoneServiceFactory.SELF_LINK))
          .setBody(tombStoneState)
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
      postOperations.add(postOperation);
      tombStoneOperationIdToAvailabilityZoneMap.put(postOperation.getId(), availabilityZone);
    }

    OperationJoin join = OperationJoin.create(postOperations);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  /**
   * Deletes availability zone entities.
   * @param availabilityZones
   * @param state
   * @param exceptions
   */
  private void deleteAvailabilityZones(final Collection<String> availabilityZones,
                                       State state,
                                       Collection<Throwable> exceptions) {
    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          exceptions.addAll(failures.values());
        }

        state.deletedAvailabilityZones = ops.size();
        endTask(state, exceptions);
      }
    };

    Collection<Operation> deleteOperations = new LinkedList<>();
    for (String availabilityZone : availabilityZones) {
      Operation deleteOperation = Operation
          .createDelete(UriUtils.buildUri(getHost(), availabilityZone))
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
      deleteOperations.add(deleteOperation);
    }

    OperationJoin join = OperationJoin.create(deleteOperations);
    join.setCompletion(handler);
    join.sendWith(this);
  }

  /**
   * Creates Tombstone state for availability zone.
   *
   * @param availabilityZone
   * @return
   */
  private TombstoneService.State createTombStoneState(String availabilityZone) {
    TombstoneService.State tombStoneState = new TombstoneService.State();
    tombStoneState.entityId = ServiceUtils.getIDFromDocumentSelfLink(availabilityZone);
    tombStoneState.entityKind = AVAILABILITY_ZONE_ENTITY_KIND;
    tombStoneState.tombstoneTime = System.currentTimeMillis();
    return tombStoneState;
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

  private void endTask(State state, Collection<Throwable> exceptions) {
    if (exceptions != null && !exceptions.isEmpty()) {
      exceptions.forEach((throwable) -> ServiceUtils.logSevere(this, throwable));
      failTask(new RuntimeException("Encountered exception(s). Please see logs for details."), state);
    } else {
      finishTask(state);
    }
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
  private void failTask(Throwable e, State state) {
    ServiceUtils.logSevere(this, e);
    this.sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, e, state));
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

    this.sendSelfPatch(buildPatch(stage, null, null));
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
   * @param patchState
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Throwable e, State patchState) {
    State state = (null != patchState ? patchState : new State());
    state.taskState = (null != state.taskState ? state.taskState : new TaskState());
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
        .setTermPropertyName(HostService.State.FIELD_NAME_AVAILABILITY_ZONE_ID)
        .setTermMatchValue(ServiceUtils.getIDFromDocumentSelfLink(availabilityZone.documentSelfLink));

    QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
    spec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(availabilityZoneClause);

    return QueryTask.create(spec).setDirect(true);
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
  }
}
