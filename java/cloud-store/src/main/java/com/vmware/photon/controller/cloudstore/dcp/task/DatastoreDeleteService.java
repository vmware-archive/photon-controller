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

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
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
import com.vmware.xenon.common.Operation;
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

  public DatastoreDeleteService() {
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
    InitializationUtils.initialize(state);
    validateState(state);
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
        TaskUtils.sendSelfPatch(this, buildPatch(current.taskState.stage, null));
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
          queryHostsForDatastore(current);
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
   * Queries the hosts which have the datastore id in reported datastores.
   *
   * @param current
   */
  private void queryHostsForDatastore(final State current) {
    Operation.CompletionHandler handler = (completedOp, failure) -> {
      if (failure != null) {
        failTask(failure);
        return;
      }

      try {
        List<String> documentLinks = completedOp.getBody(QueryTask.class).results.documentLinks;
        if (documentLinks.size() == 0) {
          ServiceUtils.logInfo(this, "No active hosts found for datastore " + current.datastoreId);
          deleteDatastore(current);
        } else {
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        }
      } catch (Throwable ex) {
        failTask(ex);
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
   * Deletes the datastore with the id datastoreId.
   *
   * @param current
   */
  private void deleteDatastore(final State current) {
    Operation.CompletionHandler handler = (completedOp, failure) -> {
      if (failure != null) {
        failTask(failure);
        return;
      }

      try {
        ServiceUtils.logInfo(this, "Deleted datastore " + current.datastoreId);
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
      } catch (Throwable ex) {
        failTask(ex);
      }
    };

    Operation deleteOperation = Operation
        .createDelete(UriUtils.buildUri(getHost(), DatastoreServiceFactory.SELF_LINK + "/" + current.datastoreId))
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
        .setCompletion(handler);
    this.sendRequest(deleteOperation);
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
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
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
     * The datastore service link for the datastore that has to be deleted.
     */
    @NotNull
    @Immutable
    public String datastoreId;
  }
}
