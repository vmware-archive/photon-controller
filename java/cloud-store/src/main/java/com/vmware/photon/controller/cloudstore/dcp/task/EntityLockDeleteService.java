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

import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockService;
import com.vmware.photon.controller.cloudstore.dcp.entity.EntityLockServiceFactory;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
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
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;
import static com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Class implementing service to delete dangling entity locks from the cloud store.
 * Service will query entity locks with pagination and deletes the entity locks associated with the deleted entities.
 */
public class EntityLockDeleteService extends StatefulService {

  public static final Integer DEFAULT_PAGE_LIMIT = 1000;
  public static final long DEFAULT_DELETE_WATERMARK_TIME_MILLIS = 5 * 60 * 1000L;
  private static final String DOCUMENT_UPDATE_TIME_MICROS = "documentUpdateTimeMicros";

  public EntityLockDeleteService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State s = start.getBody(State.class);
    start.complete();
    initializeState(s);
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);

    validatePatch(currentState, patchState);
    applyPatch(currentState, patchState);
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

    if (current.nextPageLink == null) {
      Operation queryEntityLocksPagination = Operation
          .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
          .setBody(buildEntityLockQuery(current));
      queryEntityLocksPagination
          .setCompletion(((op, failure) -> {
            if (failure != null) {
              failTask(failure);
              return;
            }
            ServiceDocumentQueryResult results = op.getBody(QueryTask.class).results;
            if (results.nextPageLink != null) {
              current.nextPageLink = results.nextPageLink;
            } else {
              ServiceUtils.logInfo(this, "No entityLocks found.");
            }

            validateState(current);
            processStart(current);
          })).sendWith(this);
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
    ServiceUtils.logInfo(this, "Moving to stage %s", patch.taskState.stage);
    if (patch.nextPageLink == null) {
      current.nextPageLink = null;
    }
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
        sendSelfPatch(current);
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
          processEntityLocksWithDeletedEntities(current);
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
   * Retrieves the first page of entity locks and kicks of the subsequent processing.
   *
   * @param current
   */
  private void processEntityLocksWithDeletedEntities(final State current) {
    if (current.nextPageLink == null) {
      finishTask(current);
      return;
    }

    Operation getFirstPageOfEntityLocks = Operation.createGet(UriUtils.buildUri(getHost(), current.nextPageLink));

    getFirstPageOfEntityLocks
        .setCompletion((op, throwable) -> {
          if (throwable != null) {
            failTask(throwable);
            return;
          }

          current.nextPageLink = op.getBody(QueryTask.class).results.nextPageLink;

          List<EntityLockService.State> entityLockList =
              parseEntityLockQueryResults(op.getBody(QueryTask.class));

          if (entityLockList.size() == 0) {
            ServiceUtils.logInfo(EntityLockDeleteService.this, "No entityLocks found any more.");
            sendSelfPatch(current);
            return;
          }

          deleteEntityLocksWithDeletedEntities(current, entityLockList);
        })
        .sendWith(this);
  }

  private QueryTask buildEntityLockQuery(final State current) {
    Long durationInMicros = Utils.getNowMicrosUtc() - current.entityLockDeleteWatermarkTimeInMicros;

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(EntityLockService.State.class));

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    QueryTask.NumericRange range = QueryTask.NumericRange.createLessThanRange(durationInMicros);
    range.precisionStep = Integer.MAX_VALUE;
    QueryTask.Query timeClause = new QueryTask.Query()
        .setTermPropertyName(DOCUMENT_UPDATE_TIME_MICROS)
        .setNumericRange(range);

    querySpec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(timeClause);

    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    querySpec.resultLimit = DEFAULT_PAGE_LIMIT;
    return QueryTask.create(querySpec).setDirect(true);
  }

  private void deleteEntityLocksWithDeletedEntities(final State current, List<EntityLockService.State> entityLockList) {
    Collection<Operation> getEntityOperations = getEntitiesAssociateWithDeletedEntities(entityLockList);

    if (getEntityOperations.isEmpty()) {
      ServiceUtils.logInfo(EntityLockDeleteService.this, "No entityLocks found associate with deleted entities with " +
          "this page.");
      sendSelfPatch(current);
      return;
    }
    OperationJoin join = OperationJoin.create(getEntityOperations);
    join.setCompletion(deleteEntityLocksAssociatedWithDeletedEntities(current));
    join.sendWith(this);
  }

  private Collection<Operation> getEntitiesAssociateWithDeletedEntities(
      List<EntityLockService.State> entityLockList) {
    Collection<Operation> getEntityOperations = new LinkedList<>();

    for (EntityLockService.State entityLock : entityLockList) {
      if (entityLock.ownerTaskId == null) {
        Operation getEntityOperation = Operation
            .createGet(UriUtils.buildUri(getHost(), entityLock.entitySelfLink))
            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
            .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));

        getEntityOperations.add(getEntityOperation);
      }
    }

    return getEntityOperations;
  }

  private JoinedCompletionHandler deleteEntityLocksAssociatedWithDeletedEntities(final State current) {
    return (ops, failures) -> {
      Collection<Operation> deleteLockOperations = getDeleteLockOperationsForEntityLocks(ops);

      if (deleteLockOperations.size() == 0) {
        ServiceUtils.logInfo(this, "No unreleased entityLocks found with this page.");
        sendSelfPatch(current);
        return;
      }

      current.danglingEntityLocksWithDeletedEntities += deleteLockOperations.size();
      OperationJoin join = OperationJoin.create(deleteLockOperations);
      join.setCompletion(getEntityLockDeleteResponseHandler(current));
      join.sendWith(this);
    };
  }

  private Collection<Operation> getDeleteLockOperationsForEntityLocks(Map<Long, Operation> ops) {
    Collection<Operation> deleteLockOperations = new LinkedList<>();
    for (Operation op : ops.values()) {
      if (op.getStatusCode() == 404) {
        Operation deleteLockOperation = Operation
            .createDelete(UriUtils.buildUri(getHost(), EntityLockServiceFactory.SELF_LINK + "/"
                + UriUtils.getLastPathSegment(op.getUri())))
            .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
        ServiceUtils.logInfo(this, "Deleting a dangling EntityLock associate with deleted entities. EntityLock Id: %s",
            UriUtils.getLastPathSegment(op.getUri()));

        deleteLockOperations.add(deleteLockOperation);
      }
    }
    return deleteLockOperations;
  }

  private JoinedCompletionHandler getEntityLockDeleteResponseHandler(final State current) {
    return (ops, failures) -> {
      current.deletedEntityLocks += ops.size();
      this.sendSelfPatch(current);
    };
  }

  private List<EntityLockService.State> parseEntityLockQueryResults(QueryTask result) {
    List<EntityLockService.State> entityLockList = new LinkedList<>();

    if (result != null && result.results != null && result.results.documentCount > 0) {
      for (Map.Entry<String, Object> doc : result.results.documents.entrySet()) {
        entityLockList.add(
            Utils.fromJson(doc.getValue(), EntityLockService.State.class));
      }
    }

    return entityLockList;
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

  private void finishTask(final State patch) {
    ServiceUtils.logInfo(this, "Finished deleting unreleased entityLocks.");
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
  private void sendStageProgressPatch(final State current, TaskState.TaskStage stage) {
    if (current.isSelfProgressionDisabled) {
      ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
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
     * The number of entity locks to delete.
     */
    @DefaultInteger(value = 0)
    public Integer danglingEntityLocksWithDeletedEntities;

    /**
     * The number of entity locks that were deleted successfully.
     */
    @DefaultInteger(value = 0)
    public Integer deletedEntityLocks;

    /**
     * The link to next page.
     */
    public String nextPageLink;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    @DefaultBoolean(value = false)
    public Boolean isSelfProgressionDisabled;

    /**
     * Duration that controls how old the entity locks should be for cleaning.
     */
    @DefaultLong(value = DEFAULT_DELETE_WATERMARK_TIME_MILLIS)
    public Long entityLockDeleteWatermarkTimeInMicros;

  }
}
