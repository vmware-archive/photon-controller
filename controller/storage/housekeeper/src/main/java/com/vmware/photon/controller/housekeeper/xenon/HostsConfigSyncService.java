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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class implementing service to trigger host services to sync configuration.
 */
public class HostsConfigSyncService extends StatefulService {

  public static final String FACTORY_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.HOUSEKEEPER_ROOT
      + "/hosts-config-sync";

  public static FactoryService createFactory() {
    return FactoryService.create(HostsConfigSyncService.class, HostsConfigSyncService.State.class);
  }

  public HostsConfigSyncService() {
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
      state.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(TimeUnit.DAYS.toMicros(1));
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
   */
  private void processPatch(final State current) {
    try {
      switch (current.taskState.stage) {
        case STARTED:
          getAllHosts(current);
          break;

        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;

        default:
          this.failTask(
              new IllegalStateException(
                  String.format("Unexpected stage: %s", current.taskState.stage))
          );
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * Get all the hosts in the system.
   */
  private void getAllHosts(final State current) {
    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(HostService.State.class)
            .build())
        .build();

    sendRequest(Operation
        .createPost(UriUtils.buildUri(getHost(),
            com.vmware.photon.controller.common.xenon.ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(queryTask)
        .setCompletion((completedOp, failure) -> {
          if (failure != null) {
            failTask(failure);
            return;
          }

          try {
            syncHostConfig(current, completedOp.getBody(QueryTask.class).results.documentLinks);
          } catch (Throwable ex) {
            failTask(ex);
          }
        }));
  }

  /**
   * sync host configuration.
   */
  private void syncHostConfig(final State current, List<String> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
      return;
    }

    try {
      final List<String> batch = hosts.subList(0, Math.min(hosts.size(), current.batchSize));
      final List<String> remainder = hosts.size() > current.batchSize ?
          hosts.subList(current.batchSize, hosts.size()) : null;
      final Queue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
      final AtomicInteger latch = new AtomicInteger(batch.size());

      final HostService.State patchState = new HostService.State();
      patchState.syncHostConfigTrigger = true;

      for (String hostLink : batch) {
        try {
          sendRequest(Operation
              .createPatch(UriUtils.buildUri(getHost(), hostLink))
              .setBody(patchState)
              .setCompletion((op, ex) -> {
                if (ex != null) {
                  ServiceUtils.logWarning(this, "Sync host config failed " + ex.getMessage());
                  exceptions.add(ex);
                }
                if (0 == latch.decrementAndGet()) {
                  if (0 == exceptions.size()) {
                    // handle next batch
                    syncHostConfig(current, remainder);
                  } else {
                    failTask(exceptions.peek());
                  }
                }
              }));
        } catch (Throwable t) {
          failTask(t);
          return;
        }
      }

    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * Determines if the task is in a final state.
   */
  private boolean isFinalStage(State s) {
    return s.taskState.stage == TaskState.TaskStage.FINISHED ||
        s.taskState.stage == TaskState.TaskStage.FAILED ||
        s.taskState.stage == TaskState.TaskStage.CANCELLED;
  }

  /**
   * Moves the service into the FAILED state.
   */
  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
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

    @Positive
    @Immutable
    @DefaultInteger(value = 5)
    public Integer batchSize;
  }
}
