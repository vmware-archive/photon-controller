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

import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class implementing service to trigger datastore delete task for all the datastores in batches.
 */
public class DatastoreCleanerService extends StatefulService {

  public DatastoreCleanerService() {
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

    if (state.documentExpirationTimeMicros <= 0) {
      state.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(TimeUnit.DAYS.toMicros(5));
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
          getAllDatastores(current);
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
   * Get all the datastores in the system.
   *
   * @param current
   */
  private void getAllDatastores(final State current) {
    Operation.CompletionHandler handler = (completedOp, failure) -> {
      if (failure != null) {
        failTask(failure);
        return;
      }

      try {
        triggerDatastoreDeleteTasks(current, completedOp.getBody(QueryTask.class).results.documentLinks);
      } catch (Throwable ex) {
        failTask(ex);
      }
    };

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(DatastoreService.State.class)
            .build())
        .build();

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(),
            com.vmware.photon.controller.common.xenon.ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(queryTask)
        .setCompletion(handler);

    this.sendRequest(queryPost);
  }

  /**
   * Batch trigger datastore delete tasks for all the datastores.
   *
   * @param current
   */
  private void triggerDatastoreDeleteTasks(final State current, List<String> datastoreLinks) {
    int count = 0;

    if (datastoreLinks != null && datastoreLinks.size() > 0) {
      for (List<String> batch : Lists.partition(datastoreLinks, current.batchSize)) {
        getHost().schedule(() -> {
          for (String datastoreLink : batch) {
            DatastoreDeleteService.State startState = new DatastoreDeleteService.State();
            startState.parentServiceLink = getSelfLink();
            String[] components = datastoreLink.split("/");
            startState.datastoreId = components[components.length - 1];

            sendRequest(Operation
                .createPost(this, DatastoreDeleteFactoryService.SELF_LINK)
                .setBody(startState));
          }
        }, count * current.intervalBetweenBatchTriggersInSeconds, TimeUnit.SECONDS);
        count++;
      }
    }

    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
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
    @Positive
    @Immutable
    @DefaultInteger(value = 5)
    public Integer batchSize;

    /**
     * The document self link of the parent which triggers this task.
     */
    @Positive
    @Immutable
    @DefaultLong(value = 60)
    public Long intervalBetweenBatchTriggersInSeconds;
  }
}
