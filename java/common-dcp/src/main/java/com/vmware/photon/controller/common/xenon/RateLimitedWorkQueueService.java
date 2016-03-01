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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceSubscriptionState;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon service which provides a rate-limited work queue for a work item class.
 * <p>
 * This class registers a continuous query for documents of the user-specified work item type, and transitions work
 * items from CREATED to STARTED state as they arrive up to a user-specified concurrency limit. The concurrency limit
 * is per-node, not global.
 * <p>
 * ** This service does not check service ownership and should not be used for OWNER_SELECTED services. **
 */
public class RateLimitedWorkQueueService extends StatefulService {

  /**
   * This class defines the document state associated with a {@link RateLimitedWorkQueueService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document kind of the work item class.
     */
    @NotNull
    public String workItemKind;

    /**
     * This value represents the concurrency limit for work item tasks.
     */
    @NotNull
    @Positive
    public Integer concurrencyLimit;

    /**
     * This value represents the number of work items in the CREATED state.
     */
    @DefaultInteger(value = 0)
    public Integer createdWorkItemCount;

    /**
     * This value represents the number of work items in the STARTED state.
     */
    @DefaultInteger(value = 0)
    public Integer startedWorkItemCount;
  }

  /**
   * This class defines the message format used when sending self-patches.
   */
  public static class PatchState extends ServiceDocument {

    /**
     * This value represents the change in the number of work items in CREATED state.
     */
    public Integer createdWorkItemCountDelta = 0;

    /**
     * This value represents the change in the number of work items in STARTED state.
     */
    public Integer startedWorkItemCountDelta = 0;

    /**
     * If not null, this value represents the document self-link of a work item to be started.
     */
    public String workItemServiceLink;
  }

  public RateLimitedWorkQueueService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation postOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = postOp.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);
    registerWorkItemQuery(postOp, startState);
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    checkState(currentState.createdWorkItemCount >= 0);
    checkState(currentState.startedWorkItemCount >= 0);
    checkState(currentState.startedWorkItemCount <= currentState.concurrencyLimit);
  }

  private void registerWorkItemQuery(Operation postOp, State startState) {

    QueryTask queryTask = QueryTask.Builder.create()
        .setQuery(QueryTask.Query.Builder.create()
            .addFieldClause(ServiceDocument.FIELD_NAME_KIND, startState.workItemKind)
            .build())
        .addOptions(EnumSet.of(
            QueryTask.QuerySpecification.QueryOption.CONTINUOUS,
            QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
        .build();

    //
    // N.B. It is currently impossible to specify that a QueryTask document should not expire.
    // Until this capability is supported in Xenon, this workaround is necessary.
    //

    long now = Utils.getNowMicrosUtc();
    queryTask.documentExpirationTimeMicros = now + TimeUnit.DAYS.toMicros(1000 * 365);
    checkState(queryTask.documentExpirationTimeMicros > now);

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logSevere(this, e);
                postOp.fail(e);
                return;
              }

              try {
                registerSubscription(postOp, o.getBody(QueryTask.class).documentSelfLink);
              } catch (Throwable t) {
                ServiceUtils.logSevere(this, t);
                postOp.fail(t);
              }
            }
        ));
  }

  private void registerSubscription(Operation postOp, String queryServiceLink) {

    Operation subscriptionOp = Operation
        .createPost(this, queryServiceLink)
        .setReferer(getUri())
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logSevere(this, e);
                postOp.fail(e);
              } else {
                postOp.complete();
              }
            });

    getHost().startSubscriptionService(
        subscriptionOp,
        (notifyOp) -> {
          try {
            QueryTask queryTask = notifyOp.getBody(QueryTask.class);
            if (queryTask.results == null || queryTask.results.documentLinks.isEmpty()) {
              ServiceUtils.logInfo(this, "Ignoring notification with no query results");
              return;
            }

            //
            // N.B. Because Xenon can return the actual task state object instead of its JSON
            // representation here and because our task state classes are not subclassed from
            // TaskServiceState, it is necessary to serialize to JSON and then immediately
            // deserialize. The serialization operation is a no-op in the common case.
            //

            Set<PatchState> patchStates = queryTask.results.documents.values().stream()
                .map((document) -> Utils.fromJson(Utils.toJson(document), TaskServiceState.class))
                .map((serviceState) -> {
                  switch (serviceState.taskState.stage) {
                    case CREATED: {
                      PatchState patchState = new PatchState();
                      patchState.createdWorkItemCountDelta++;
                      patchState.workItemServiceLink = serviceState.documentSelfLink;
                      return patchState;
                    }
                    case FINISHED:
                    case FAILED:
                    case CANCELLED: {
                      PatchState patchState = new PatchState();
                      patchState.startedWorkItemCountDelta--;
                      return patchState;
                    }
                    default:
                      return null;
                  }
                })
                .filter((patchState) -> patchState != null)
                .collect(Collectors.toSet());

            if (patchStates.size() != 0) {

              OperationJoin
                  .create(patchStates.stream().map(
                      (patchState) -> Operation.createPatch(this, getSelfLink()).setBody(patchState)))
                  .setCompletion(
                      (ops, exs) -> {
                        if (exs != null && !exs.isEmpty()) {
                          ServiceUtils.logSevere(this, exs.values());
                        }
                      })
                  .sendWith(this);
            }
          } catch (Throwable t) {
            ServiceUtils.logSevere(this, t);
          }
        },
        ServiceSubscriptionState.ServiceSubscriber.create(true));
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(patchOp);
    PatchState patchState = patchOp.getBody(PatchState.class);
    validatePatchState(patchState);
    currentState.createdWorkItemCount += patchState.createdWorkItemCountDelta;
    currentState.startedWorkItemCount += patchState.startedWorkItemCountDelta;
    validateState(currentState);

    if (currentState.createdWorkItemCount == 0) {
      ServiceUtils.logTrace(this, "No pending work items");
      patchOp.complete();
      return;
    }

    if (currentState.startedWorkItemCount >= currentState.concurrencyLimit) {
      ServiceUtils.logTrace(this, "Concurrency limit reached");
      patchOp.complete();
      return;
    }

    if (patchState.workItemServiceLink != null) {
      startWorkItems(patchOp, currentState, Collections.singleton(patchState.workItemServiceLink));
    } else {
      queryCreatedWorkItems(patchOp, currentState);
    }
  }

  private void validatePatchState(PatchState patchState) {
    checkState(patchState.createdWorkItemCountDelta != null);
    checkState(patchState.startedWorkItemCountDelta != null);
  }

  private void startWorkItems(Operation patchOp, State currentState, Collection<String> workItemServiceLinks) {

    if (workItemServiceLinks.isEmpty()) {
      ServiceUtils.logWarning(this, "Found no work items in CREATED state");
      patchOp.complete();
      return;
    }

    TaskServiceState patchState = new TaskServiceState();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    OperationJoin
        .create(workItemServiceLinks.stream()
            .limit(currentState.concurrencyLimit - currentState.startedWorkItemCount)
            .map((serviceLink) -> Operation.createPatch(this, serviceLink).setBody(patchState)))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                ServiceUtils.logSevere(this, exs.values());
              }

              for (Operation op : ops.values()) {
                if (op.getStatusCode() == Operation.STATUS_CODE_OK) {
                  currentState.startedWorkItemCount++;
                  currentState.createdWorkItemCount--;
                }
              }

              boolean triggerQuery = currentState.createdWorkItemCount > 0 &&
                  currentState.startedWorkItemCount < currentState.concurrencyLimit;

              patchOp.complete();

              //
              // If there are still free executor slots and outstanding work items, send another
              // self-patch to trigger a query for the outstanding work items.
              //

              if (triggerQuery) {
                TaskUtils.sendSelfPatch(this, new PatchState());
              }
            })
        .sendWith(this);
  }

  private void queryCreatedWorkItems(Operation patchOp, State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addFieldClause(ServiceDocument.FIELD_NAME_KIND, currentState.workItemKind)
            .addCompositeFieldClause("taskState", "stage",
                QueryTask.QuerySpecification.toMatchValue(TaskState.TaskStage.CREATED))
            .build())
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logSevere(this, e);
                patchOp.fail(e);
                return;
              }

              try {
                startWorkItems(patchOp, currentState, o.getBody(QueryTask.class).results.documentLinks);
              } catch (Throwable t) {
                ServiceUtils.logSevere(this, t);
                patchOp.fail(t);
              }
            }
        ));
  }
}
