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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.apache.commons.lang3.StringUtils;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Class ImageReplicatorService implements a service to propagate an image available on a single data store to all
 * data stores. The copy is performed by create ImageCopyService, TaskSchedulerService will move those to STARTED
 * stage, and wait for the copy to finish. Client will poll until task state is FINISH or FAIL. CANCELLED is not
 * supported.
 */
public class ImageReplicatorService extends StatefulService {
  /**
   * Time to delay query task executions.
   */
  private static final int DEFAULT_QUERY_POLL_DELAY = 10000;

  /**
   * Default constructor.
   */
  public ImageReplicatorService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      // Initialize the task state.
      State s = start.getBody(State.class);

      if (s.taskInfo == null || s.taskInfo.stage == TaskState.TaskStage.CREATED) {
        s.taskInfo = new TaskState();
        s.taskInfo.stage = TaskState.TaskStage.STARTED;
        s.taskInfo.subStage = TaskState.SubStage.TRIGGER_COPIES;
      }

      if (s.documentExpirationTimeMicros <= 0) {
        s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
            ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      if (s.queryPollDelay == null) {
        s.queryPollDelay = DEFAULT_QUERY_POLL_DELAY;
      }

      validateState(s);
      start.setBody(s).complete();

      sendStageProgressPatch(s, s.taskInfo.stage, s.taskInfo.subStage);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(start)) {
        start.fail(e);
      }
    }
  }

  /**
   * Patch operation handler. Implements all logic to drive our state machine.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);

    try {
      validatePatch(currentState, patchState);
      applyPatch(currentState, patchState);

      validateState(currentState);
      patch.complete();

      switch (currentState.taskInfo.stage) {
        case STARTED:
          handleStartedStage(currentState, patchState);
          break;
        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;
        default:
          throw new IllegalStateException(
              String.format("Invalid stage %s", currentState.taskInfo.stage));
      }
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(e);
      }
    }
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    checkNotNull(current.taskInfo, "taskInfo cannot be null");
    checkNotNull(current.taskInfo.stage, "stage cannot be null");

    checkNotNull(current.queryPollDelay, "queryPollDelay cannot be null");
    checkState(current.queryPollDelay > 0, "queryPollDelay needs to be >= 0");

    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");

    if (current.finishedCopies != null) {
      checkState(current.finishedCopies >= 0, "finishedCopies needs to be >= 0");
    }

    if (current.failedOrCanceledCopies != null) {
      checkState(current.failedOrCanceledCopies >= 0, "failedOrCanceledCopies needs to be >= 0");
    }

    if (current.dataStoreCount != null) {
      checkState(current.dataStoreCount >= 0, "dataStoreCount needs to be >= 0");
    }

    switch (current.taskInfo.stage) {
      case STARTED:
        checkState(current.taskInfo.subStage != null, "subStage cannot be null");
        checkArgument(StringUtils.isNotBlank(current.image), "image not provided");
        checkArgument(StringUtils.isNotBlank(current.datastore), "datastore not provided");
        switch (current.taskInfo.subStage) {
          case TRIGGER_COPIES:
            break;
          case AWAIT_COMPLETION:
            checkArgument(current.dataStoreCount != null, "dataStoreCount not provided");
            break;
          default:
            checkState(false, "unsupported sub-state: " + current.taskInfo.subStage.toString());
        }
        break;
      case FAILED:
      case FINISHED:
      case CANCELLED:
        checkState(current.taskInfo.subStage == null, "Invalid stage update. subStage must be null");
        break;
      default:
        checkState(false, "cannot process patches in state: " + current.taskInfo.stage.toString());
    }
  }

  /**
   * Validate patch correctness.
   *
   * @param current
   * @param patch
   */
  protected void validatePatch(State current, State patch) {
    checkState(current.taskInfo.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal(),
        "Invalid stage update. Can not patch anymore when in final stage %s", current.taskInfo.stage);
    if (patch.taskInfo != null) {
      checkState(patch.taskInfo.stage != null, "Invalid stage update. 'stage' can not be null in patch");
      checkState(patch.taskInfo.stage.ordinal() >= current.taskInfo.stage.ordinal(),
          "Invalid stage update. Can not revert to %s from %s", patch.taskInfo.stage, current.taskInfo.stage);

      if (patch.taskInfo.subStage != null && current.taskInfo.subStage != null) {
        checkState(patch.taskInfo.subStage.ordinal() >= current.taskInfo.subStage.ordinal(),
            "Invalid stage update. 'subStage' cannot move back.");
      }
    }

    checkArgument(patch.image == null, "image field cannot be updated in a patch");
    checkArgument(patch.datastore == null, "datastore field cannot be updated in a patch");
  }

  /**
   * Applies patch to current document state.
   *
   * @param currentState
   * @param patchState
   */
  protected void applyPatch(State currentState, State patchState) {
    if (patchState.taskInfo != null) {
      if (patchState.taskInfo.stage != currentState.taskInfo.stage
          || patchState.taskInfo.subStage != currentState.taskInfo.subStage) {
        ServiceUtils.logInfo(this, "moving stage to %s:%s",
            patchState.taskInfo.stage, patchState.taskInfo.subStage);
      }

      if (patchState.taskInfo.subStage != null) {
        adjustStat(patchState.taskInfo.subStage.toString(), 1);
      }

      currentState.taskInfo = patchState.taskInfo;
    }

    if (patchState.dataStoreCount != null) {
      currentState.dataStoreCount = patchState.dataStoreCount;
    }

    if (patchState.finishedCopies != null) {
      currentState.finishedCopies = patchState.finishedCopies;
    }

    if (patchState.failedOrCanceledCopies != null) {
      currentState.failedOrCanceledCopies = patchState.failedOrCanceledCopies;
    }

  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  protected void handleStartedStage(final State current, final State patch) {
    // Handle task sub-state.
    switch (current.taskInfo.subStage) {
      case TRIGGER_COPIES:
        handleTriggerCopies(current);
        break;
      case AWAIT_COMPLETION:
        processAwaitCompletion(current, patch);
        break;
      default:
        throw new IllegalStateException("Un-supported substage" + current.taskInfo.subStage.toString());
    }
  }

  /**
   * This method queries the list of data stores available in this ESX cloud instance and, on query completion,
   * creates a set of ImageCopyService instances and transitions the current service instance to the
   * AWAIT_COMPLETION sub-state.
   *
   * @param current
   */
  protected void handleTriggerCopies(final State current) {
    try {
      Set<String> datastoreSet = new HashSet<>();
      Operation queryDatastoreSet = buildDatastoreSetQuery(current);
      Operation patchOperation = Operation
          .createPatch(UriUtils.buildUri(getHost(), getSelfLink()));
      ImageReplicatorService.State imageReplicatorServiceState = buildPatch(
          TaskState.TaskStage.STARTED, TaskState.SubStage.AWAIT_COMPLETION, null);

      OperationSequence operationSequence = OperationSequence
          .create(queryDatastoreSet)
          .setCompletion((operations, throwable) -> {
                if (throwable != null) {
                  failTask(throwable.values().iterator().next());
                  return;
                }

                Operation op = operations.get(queryDatastoreSet.getId());
                NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
                List<DatastoreService.State> documentLinks = QueryTaskUtils
                    .getBroadcastQueryDocuments(DatastoreService.State.class, queryResponse);
                for (DatastoreService.State state : documentLinks) {
                  datastoreSet.add(state.id);
                }

                imageReplicatorServiceState.dataStoreCount = datastoreSet.size();
                patchOperation.setBody(imageReplicatorServiceState);
                ServiceUtils.logInfo(this, "All target datastores: %s", Utils.toJson(false, false, datastoreSet));
                triggerCopyServices(datastoreSet, current);
              }
          );

      // move to next stage
      if (!current.isSelfProgressionDisabled) {
        operationSequence
            .next(patchOperation)
            .setCompletion(
                (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
                  if (failures != null && failures.size() > 0) {
                    failTask(failures.values().iterator().next());
                    return;
                  }
                });
        ;
      }
      operationSequence.sendWith(this);
    } catch (Exception e) {
      failTask(e);
    }
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  protected void processAwaitCompletion(final State current, final State patch) {
    if (current.finishedCopies != null
        && current.dataStoreCount.equals(current.finishedCopies)) {
      // all copies have completed successfully
      this.sendSelfPatch(buildPatch(TaskState.TaskStage.FINISHED, null, null));
      return;
    }

    if (current.finishedCopies != null
        && current.failedOrCanceledCopies != null
        && current.dataStoreCount.equals(current.finishedCopies + current.failedOrCanceledCopies)) {
      // all copies have completed, but some of them have failed
      RuntimeException e = new RuntimeException(
          String.format("Copy image failed: %s copies succeeded, %s copies failed",
              current.finishedCopies,
              current.failedOrCanceledCopies)
      );
      this.failTask(e);
      return;
    }

    // determine if we have already received answers from queries that check for completion
    // of ImageCopyService instances
    boolean isFirstCheck = current.finishedCopies == null
        && current.failedOrCanceledCopies == null;

    if (isFirstCheck || patch.finishedCopies != null) {
      // issue the query to get the count of finished ImageCopyService instances,
      // because we either have not yet run the query yet or we have just processed the patch
      // from the previous query on children in FINISHED stage
      getHost().schedule(new Runnable() {
        @Override
        public void run() {
          checkFailedOrCancelledCount(current);
        }
      }, current.queryPollDelay, TimeUnit.MILLISECONDS);
    }

    if (patch.failedOrCanceledCopies != null) {
      // issue the query to get the count of failed or cancelled ImageCopyService instances,
      // because we either have not run the query yet or we have just processed the patch
      // from the previous query on children in CANCELLED OR FAILED stage
      getHost().schedule(new Runnable() {
        @Override
        public void run() {
          checkFinishedCount(current);
        }
      }, current.queryPollDelay, TimeUnit.MILLISECONDS);
    }
  }

  protected CloudStoreHelper getCloudStoreHelper() {
    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper();
  }

  /**
   * This function creates a set of ImageCopyService instances parented to the current service instance.
   *
   * @param targetDataStoreSet
   * @param current
   * @return The number of batches created.
   */
  private void triggerCopyServices(Set<String> targetDataStoreSet, State current) {
    if (targetDataStoreSet.isEmpty()) {
      ServiceUtils.logInfo(this, "No copies to trigger!");
      return;
    }

    for (String targetDataStore : targetDataStoreSet) {
      triggerCopyService(current, targetDataStore);
    }
  }

  /**
   * Triggers an ImageCopyService for the datastore passed as a parameter.
   *
   * @param current
   * @param datastore
   */
  protected void triggerCopyService(final State current, String datastore) {
    // build completion handler
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation acknowledgeOp, Throwable failure) {
        if (failure != null) {
          // we could not start an ImageCopyService task. Something went horribly wrong. Fail
          // the current task and stop processing.
          RuntimeException e = new RuntimeException(
              String.format("Failed to send copy request %s", failure));
          failTask(e);
        }
      }
    };

    // build start state
    ImageCopyService.State copyState = new ImageCopyService.State();
    copyState.image = current.image;
    copyState.sourceImageDataStore = current.datastore;
    copyState.destinationDataStore = datastore;
    copyState.parentLink = getSelfLink();
    copyState.documentExpirationTimeMicros = current.documentExpirationTimeMicros;

    // start service
    Operation copyOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ImageCopyServiceFactory.SELF_LINK))
        .setBody(copyState)
        .setCompletion(handler);
    this.sendRequest(copyOperation);
  }

  /**
   * Triggers a query to retrieve the "child" ImageCopyService instances in FINISHED state.
   *
   * @param current
   */
  private void checkFinishedCount(final State current) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          // The query failed to execute. This most likely means that the
          // host is in a bad state and if we re-issue the query it is likely
          // to fail again. Terminate and fail the task early and delegate any
          // retry logic to the caller.
          failTask(failure);
          return;
        }

        QueryTask rsp = completedOp.getBody(QueryTask.class);

        State s = buildPatch(current.taskInfo.stage, current.taskInfo.subStage, null);
        ServiceUtils.logInfo(ImageReplicatorService.this, "Finished %s",
            Utils.toJson(false, false, rsp.results.documentLinks));
        s.finishedCopies = rsp.results.documentLinks.size();

        sendSelfPatch(s);
      }
    };

    QueryTask.QuerySpecification spec =
        QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
            this.getSelfLink(), ImageCopyService.State.class, TaskState.TaskStage.FINISHED);

    this.sendQuery(spec, handler);
  }

  /**
   * Triggers a query to retrieve the "child" ImageCopyService instances in FAILED or CANCELLED state.
   *
   * @param current
   */
  private void checkFailedOrCancelledCount(final State current) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          // The query failed to execute. This most likely means that the
          // host is in a bad state and if we re-issue the query it is likely
          // to fail again. Terminate and fail the task early and delegate any
          // retry logic to the caller.
          failTask(failure);
          return;
        }

        QueryTask rsp = completedOp.getBody(QueryTask.class);

        State s = buildPatch(current.taskInfo.stage, current.taskInfo.subStage, null);
        ServiceUtils.logInfo(ImageReplicatorService.this, "Failed %s",
            Utils.toJson(false, false, rsp.results.documentLinks));
        s.failedOrCanceledCopies = rsp.results.documentLinks.size();

        sendSelfPatch(s);
      }
    };

    QueryTask.QuerySpecification spec =
        QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
            this.getSelfLink(),
            ImageCopyService.State.class,
            TaskState.TaskStage.FAILED,
            TaskState.TaskStage.CANCELLED);

    this.sendQuery(spec, handler);
  }

  /**
   * Build a QuerySpecification for querying image data store.
   *
   * @param current
   * @return
   */
  private Operation buildDatastoreSetQuery(final State current) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }


  /**
   * Triggers a query task with the spec passed as parameters.
   *
   * @param spec
   */
  private void sendQuery(QueryTask.QuerySpecification spec, Operation.CompletionHandler handler) {
    QueryTask task = QueryTask.create(spec)
        .setDirect(true);

    Operation queryPost = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(task)
        .setCompletion(handler);
    sendRequest(queryPost);
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    this.sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param s
   */
  private void sendSelfPatch(State s) {
    sendRequest(buildSelfPatchOperation(s));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param stage
   * @param subStage
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage, TaskState.SubStage subStage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    sendSelfPatch(buildPatch(stage, subStage, null));
  }

  /**
   * Build an operation object that sends a patch to the service.
   *
   * @param s
   * @return
   */
  private Operation buildSelfPatchOperation(State s) {
    return Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(s);
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param subStage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable e) {
    State s = new State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = stage;
    s.taskInfo.subStage = subStage;

    if (e != null) {
      s.taskInfo.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Service execution stages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    /**
     * The execution substage.
     */
    public SubStage subStage;

    /**
     * Execution sub-stage.
     */
    public static enum SubStage {
      TRIGGER_COPIES,
      AWAIT_COMPLETION,
    }
  }

  /**
   * Durable service state data.
   */
  public static class State extends ServiceDocument {
    /**
     * Image to replicate.
     */
    public String image;

    /**
     * Datastore image is located on.
     */
    public String datastore;

    /**
     * Task stage information. Stores the current the stage and sub-stage.
     */
    public TaskState taskInfo;

    /**
     * When isSelfProgressionDisabled is true, the service does not automatically update its stages.
     */
    public boolean isSelfProgressionDisabled;

    /**
     * Time in milliseconds to delay before issuing query tasks.
     */
    public Integer queryPollDelay;

    /**
     * Count of datastores in the system. One ImageReplicatorService instance
     * is create per datastore to perform the image copy.
     */
    public Integer dataStoreCount;

    /**
     * Count of individual copies in FINISHED state.
     */
    public Integer finishedCopies;

    /**
     * Count of individual copies in FAILED or CANCELED state.
     */
    public Integer failedOrCanceledCopies;
  }
}
