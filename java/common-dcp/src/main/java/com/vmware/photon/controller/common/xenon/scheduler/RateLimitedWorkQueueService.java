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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * This class implements a basic rate-limited work queue for task services.
 * <p>
 * Tasks which are scheduled through the work queue must have the following properties:
 * - They must be PERSISTED so that they can be identified with queries
 * - They must allow exactly one {@link RateLimitedWorkQueueService.State#startPatchBody} patch
 * per task service instance and reject subsequent patches with this payload
 * - They must *always* notify the associated work queue on arrival and on completion, whether
 * successful or otherwise.
 */
public class RateLimitedWorkQueueService extends StatefulService {

  /**
   * This class defines the document state associated with a {@link RateLimitedWorkQueueService}
   * instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    /**
     * This value represents the control flags for the service. See {@link ControlFlags}.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents a {@link QueryTask.Query} which can be used to identify pending task
     * services associated with the current work queue.
     */
    @NotNull
    @Immutable
    public QueryTask.Query pendingTaskServiceQuery;

    /**
     * This value represents the JSON patch body to send to pending task services in order to
     * transition them to the running state.
     */
    @NotNull
    @Immutable
    public String startPatchBody;

    /**
     * This value represents the concurrency limit associated with the work queue instance.
     */
    @NotNull
    @Immutable
    public Integer concurrencyLimit;

    /**
     * This value represents the number of task service instances associated with the work queue
     * which are currently in pending state.
     */
    @DefaultInteger(value = 0)
    public Integer pendingTaskServiceCount;

    /**
     * This value represents the number of task service instances associated with the work queue
     * which are currently in running state.
     */
    @DefaultInteger(value = 0)
    public Integer runningTaskServiceCount;
  }

  /**
   * This class defines the message format used to send patches to the work queue.
   */
  public static class PatchState {

    /**
     * This optional value represents the change in the number of task service instances in pending
     * state.
     */
    public Integer pendingTaskServiceDelta;

    /**
     * This optional value represents the change in the number of task service instances in running
     * state.
     */
    public Integer runningTaskServiceDelta;

    /**
     * This optional value represents the document self-link of the task service on whose behalf the
     * message is being sent.
     */
    public String taskServiceLink;
  }

  public RateLimitedWorkQueueService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      startOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    try {
      validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    startOp.setBody(startState).complete();
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    PatchState patchState = patchOp.getBody(PatchState.class);

    try {
      applyPatch(currentState, patchState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
      return;
    }

    if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
      ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      patchOp.complete();
      return;
    }

    if (currentState.pendingTaskServiceCount == 0) {
      ServiceUtils.logTrace(this, "No pending task services");
      patchOp.complete();
      return;
    }

    if (currentState.runningTaskServiceCount > currentState.concurrencyLimit) {
      ServiceUtils.logWarning(this, "Concurrency limit exceeded");
      patchOp.complete();
      return;
    }

    if (currentState.runningTaskServiceCount >= currentState.concurrencyLimit) {
      ServiceUtils.logTrace(this, "Concurrency limit reached");
      patchOp.complete();
      return;
    }

    if (patchState.taskServiceLink != null) {
      startTaskServices(patchOp, currentState, Collections.singleton(patchState.taskServiceLink));
    } else {
      queryPendingTaskServices(patchOp, currentState);
    }
  }

  private void validateState(State currentState) {

    ValidationUtils.validateState(currentState);

    /**
     * N.B. There is a possible sequence where a query for work items in pending state will find a
     * new work item and transition it to running state and then subsequently to finished state
     * before its initial patch to the work item queue is processed. In this case, it is possible
     * for the pending task service count to go below zero temporarily.
     */
    if (currentState.pendingTaskServiceCount < 0) {
      ServiceUtils.logWarning(this, "Pending task service count is negative");
    }

    checkState(currentState.runningTaskServiceCount >= 0);
    checkState(currentState.runningTaskServiceCount <= currentState.concurrencyLimit);
  }

  private void applyPatch(State currentState, PatchState patchState) {

    if (patchState.pendingTaskServiceDelta != null) {
      currentState.pendingTaskServiceCount += patchState.pendingTaskServiceDelta;
    }

    if (patchState.runningTaskServiceDelta != null) {
      currentState.runningTaskServiceCount += patchState.runningTaskServiceDelta;
    }

    validateState(currentState);
  }

  private void startTaskServices(Operation patchOp, State currentState, Collection<String> taskServiceLinks) {

    if (taskServiceLinks.isEmpty()) {
      ServiceUtils.logWarning(this, "Found no work items in pending state");
      patchOp.complete();
      return;
    }

    Stream<Operation> taskPatchOps = taskServiceLinks.stream()
        .limit(currentState.concurrencyLimit - currentState.runningTaskServiceCount)
        .map((taskServiceLink) ->
            Operation.createPatch(this, taskServiceLink).setBody(currentState.startPatchBody));

    OperationJoin
        .create(taskPatchOps)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                ServiceUtils.logSevere(this, exs.values());
              }

              for (Operation op : ops.values()) {
                if (op.getStatusCode() == Operation.STATUS_CODE_OK) {
                  currentState.pendingTaskServiceCount--;
                  currentState.runningTaskServiceCount++;
                }
              }

              boolean triggerQuery = currentState.pendingTaskServiceCount > 0 &&
                  currentState.runningTaskServiceCount < currentState.concurrencyLimit;

              patchOp.complete();

              if (triggerQuery) {
                sendRequest(Operation.createPatch(this, getSelfLink()).setBody(new PatchState()));
              }
            })
        .sendWith(this);
  }

  private void queryPendingTaskServices(Operation patchOp, State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(currentState.pendingTaskServiceQuery)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  ServiceUtils.logSevere(this, e);
                  patchOp.fail(e);
                } else {
                  startTaskServices(patchOp, currentState, o.getBody(QueryTask.class).results.documentLinks);
                }
              } catch (Throwable t) {
                ServiceUtils.logSevere(this, t);
                patchOp.fail(t);
              }
            }));
  }
}
