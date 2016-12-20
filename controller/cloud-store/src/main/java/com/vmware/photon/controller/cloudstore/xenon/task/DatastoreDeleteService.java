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

import com.vmware.photon.controller.api.model.AgentState;
import com.vmware.photon.controller.api.model.HostState;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Range;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import java.util.List;

/**
 * Class implementing service to check if a datastore is missing and then delete it.
 * Missing datastores are the datastores which are no longer reported by any hosts.
 */
public class DatastoreDeleteService extends StatefulService {

  public static final int DATASTORE_MAPPING_DELETE_BATCH_SIZE = 5;

  public DatastoreDeleteService() {
    super(State.class);
    // We do not need PERSISTENCE as we don't want to query these tasks. We also don't need REPLICATION and
    // OWNER_SELECTION as we don't care about where this task runs and we will have batching at the top level task
    // which triggers this so we will not over load a specific Xenon node.
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State state = start.getBody(State.class);
    InitializationUtils.initialize(state);
    validateState(state);

    if (state.documentExpirationTimeMicros <= 0) {
      state.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(state).complete();
    processStart(state);
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);

    validatePatch(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    processPatch(currentState);
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
        TaskUtils.sendSelfPatch(this, buildPatch(current.taskState.stage, null, null));
      }
    } catch (Throwable e) {
      failTask(e, null);
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
          queryHostsForDatastore(current);
          break;

        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;

        default:
          throw new IllegalStateException(String.format("Un-expected stage: %s", current.taskState.stage));
      }
    } catch (Throwable e) {
      failTask(e, null);
    }
  }

  /**
   * Queries the hosts which have the datastore id in reported datastores.
   *
   * @param current
   */
  private void queryHostsForDatastore(final State current) {
    Operation.CompletionHandler handler = (op, ex) -> {
      if (ex != null) {
        failTask(ex, null);
        return;
      }

      try {
        List<String> documentLinks = op.getBody(QueryTask.class).results.documentLinks;
        if (documentLinks.size() == 0) {
          ServiceUtils.logInfo(this, "No active hosts found for datastore %s, will remove record of datastore",
              current.datastoreId);
          deleteDatastore(current);
        } else {
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, documentLinks.size(), null));
        }
      } catch (Throwable e) {
        failTask(e, null);
      }
    };

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(),
            com.vmware.photon.controller.common.xenon.ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(buildHostQuery(current))
        .setCompletion(handler);

    this.sendRequest(queryPost);
  }

  /**
   * Builds the query to retrieve all the hosts which satisfies the following constraints.
   * 1 -> state = READY
   * 2 -> agentState = ACTIVE
   * 3 -> reportedDatastores contains datastoreId
   *
   * @param state
   * @return
   */
  private QueryTask buildHostQuery(final State state) {
    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(HostService.State.class);

    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_STATE, HostState.READY);
    queryBuilder.addFieldClause(HostService.State.FIELD_NAME_AGENT_STATE, AgentState.ACTIVE);
    queryBuilder.addCollectionItemClause(HostService.State.FIELD_NAME_REPORTED_DATASTORES, state.datastoreId);

    QueryTask.Query query = queryBuilder.build();
    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(query);
    QueryTask queryTask = queryTaskBuilder.build();
    return queryTask;
  }

  /**
   * Builds the query to retrieve all the ImageToImageDatastoreMappings with the specific datastoreId.
   *
   * @param state
   * @return
   */
  private QueryTask buildImageToImageDatastoreQuery(final State state) {
    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(ImageToImageDatastoreMappingService.State.class);

    queryBuilder.addFieldClause(ImageToImageDatastoreMappingService.State.FIELD_NAME_IMAGE_DATASTORE_ID,
        state.datastoreId);

    QueryTask.Query query = queryBuilder.build();
    QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
        .setQuery(query);
    QueryTask queryTask = queryTaskBuilder.build();
    return queryTask;
  }

  /**
   * Deletes the datastore with the id datastoreId.
   *
   * @param current
   */
  private void deleteDatastore(final State current) {
    Operation.CompletionHandler handler = (op, ex) -> {
      if (ex != null) {
        failTask(ex, 0);
        return;
      }

      try {
        ServiceUtils.logInfo(this, "Deleted datastore " + current.datastoreId);
        queryImageToImageDatastoreMappings(current);
      } catch (Throwable e) {
        ServiceUtils.logWarning(this, "Attempted to delete datastore %s because no hosts refer to it, but delete " +
                "failed. This may cause the datastore to be considered for image replication, VM placement, etc. " +
                "incorrectly",
            current.datastoreId);
        failTask(e, 0);
      }
    };

    Operation deleteOperation = Operation
        .createDelete(UriUtils.buildUri(getHost(), DatastoreServiceFactory.SELF_LINK + "/" + current.datastoreId))
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
        .setCompletion(handler);
    this.sendRequest(deleteOperation);
  }

  /**
   * Queries the ImageToImageDatastoreMappings which have the specific datastore id.
   *
   * @param current
   */
  private void queryImageToImageDatastoreMappings(final State current) {
    Operation.CompletionHandler handler = (op, ex) -> {
      if (ex != null) {
        failTask(ex, 0);
        return;
      }

      try {
        List<String> documentLinks = op.getBody(QueryTask.class).results.documentLinks;
        deleteImageToImageDatastoreMappings(documentLinks);
      } catch (Throwable e) {
        failTask(e, 0);
      }
    };

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(),
            com.vmware.photon.controller.common.xenon.ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(buildImageToImageDatastoreQuery(current))
        .setCompletion(handler);

    this.sendRequest(queryPost);
  }

  /**
   * Deletes all the ImageToImageDatastoreMappings for the specific datastore that got deleted.
   *
   * @param documentLinks
   */
  private void deleteImageToImageDatastoreMappings(List<String> documentLinks) {
    if (documentLinks == null || documentLinks.size() == 0) {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, 0, null));
      return;
    }

    OperationJoin
        .create(documentLinks.stream().map(documentLink ->
            Operation.createDelete(UriUtils.buildUri(getHost(), documentLink))
                .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
            failTask(exs.values().iterator().next(), 0);
          } else {
            TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, 0, null));
          }
        })
        .sendWith(this, DATASTORE_MAPPING_DELETE_BATCH_SIZE);
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

  /**
   * Moves the service into the FAILED state.
   *
   * @param e
   */
  private void failTask(Throwable e, Integer activeHosts) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, activeHosts, e));
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Integer activeHostCount, Throwable e) {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = stage;

    if (activeHostCount != null) {
      s.activeHostCount = activeHostCount;
    }

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
     * The datastore service link for the datastore that has to be deleted.
     */
    @NotNull
    @Immutable
    public String datastoreId;

    /**
     * The document self link of the parent which triggers this task.
     */
    @NotNull
    @Immutable
    public String parentServiceLink;

    /**
     * The count of active hosts which reports this datastore.
     */
    @WriteOnce
    @Range(min = 0, max = Integer.MAX_VALUE)
    public Integer activeHostCount;
  }
}
