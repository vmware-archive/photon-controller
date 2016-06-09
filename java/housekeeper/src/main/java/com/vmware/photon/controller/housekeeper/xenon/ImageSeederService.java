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

import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
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
 * Class ImageSeederService implements a service to propagate an image available on a single data store to all
 * data stores. The copy is performed by creating ImageHostToHostCopyService, TaskSchedulerService will move those to
 * STARTED stage, and wait for the copy to finish. Client will poll until task state is FINISH or FAIL. CANCELLED is not
 * supported.
 */
public class ImageSeederService extends StatefulService {
  /**
   * Time to delay query task executions.
   */
  private static final int DEFAULT_QUERY_POLL_DELAY = 10000;

  /**
   * Default constructor.
   */
  public ImageSeederService() {
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
      State s = this.initializeTaskState(start);
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

    if (current.failedOrCancelledCopies != null) {
      checkState(current.failedOrCancelledCopies >= 0, "failedOrCanceledCopies needs to be >= 0");
    }

    if (current.triggeredCopies != null) {
      checkState(current.triggeredCopies >= 0, "triggeredCopies needs to be >= 0");
    }

    switch (current.taskInfo.stage) {
      case STARTED:
        checkState(current.taskInfo.subStage != null, "subStage cannot be null");
        checkArgument(StringUtils.isNotBlank(current.image), "image not provided");
        checkArgument(StringUtils.isNotBlank(current.sourceImageDatastore), "sourceImageDatastore not provided");
        switch (current.taskInfo.subStage) {
          case UPDATE_DATASTORE_COUNTS:
            break;
          case TRIGGER_COPIES:
            break;
          case AWAIT_COMPLETION:
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
    checkArgument(patch.sourceImageDatastore == null, "sourceImageDatastore field cannot be updated in a patch");
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

    if (patchState.triggeredCopies != null) {
      currentState.triggeredCopies = patchState.triggeredCopies;
    }

    if (patchState.finishedCopies != null) {
      currentState.finishedCopies = patchState.finishedCopies;
    }

    if (patchState.failedOrCancelledCopies != null) {
      currentState.failedOrCancelledCopies = patchState.failedOrCancelledCopies;
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
      case UPDATE_DATASTORE_COUNTS:
        updateTotalImageDatastore(current);
        break;
      case TRIGGER_COPIES:
        handleTriggerCopies(current);
        break;
      case AWAIT_COMPLETION:
        processAwaitCompletion(current);
        break;
      default:
        throw new IllegalStateException("Un-supported substage" + current.taskInfo.subStage.toString());
    }
  }

  /**
   * Gets image entity and sends patch to update total datastore and total image datastore field.
   *
   * @param current
   */
  protected void updateTotalImageDatastore(final State current) {
    try {
      Operation datastoreSetQuery = buildDatastoreSetQuery(current);
      // build the image entity update patch
      ImageService.State imageServiceState = new ImageService.State();
      Operation datastoreCountPatch = getCloudStoreHelper()
          .createPatch(ImageServiceFactory.SELF_LINK + "/" + current.image);

      OperationSequence operationSequence = OperationSequence
          .create(datastoreSetQuery)
          .setCompletion(
              (operations, throwable) -> {
                if (throwable != null) {
                  failTask(throwable.values().iterator().next());
                  return;
                }

                imageServiceState.totalImageDatastore = 0;
                imageServiceState.totalDatastore = 0;
                Operation op = operations.get(datastoreSetQuery.getId());
                NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
                List<DatastoreService.State> documentLinks = QueryTaskUtils
                    .getBroadcastQueryDocuments(DatastoreService.State.class, queryResponse);
                imageServiceState.totalDatastore = documentLinks.size();
                for (DatastoreService.State state : documentLinks) {
                  if (state.isImageDatastore) {
                    imageServiceState.totalImageDatastore++;
                  }
                }
                datastoreCountPatch.setBody(imageServiceState);
              })
          .next(datastoreCountPatch)
          .setCompletion(
              (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
                if (failures != null && failures.size() > 0) {
                  failTask(failures.values().iterator().next());
                  return;
                }
              });

      if (!current.isSelfProgressionDisabled) {
        // move to next stage
        Operation progress = this.buildSelfPatchOperation(
            this.buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.TRIGGER_COPIES, null));

        operationSequence.next(progress);
      }

      operationSequence.sendWith(this);
    } catch (Exception e) {
      failTask(e);
    }
  }

  /**
   * This method queries the list of image data stores available in this ESX cloud instance and, on query completion,
   * creates a set of ImageHostToHostCopyService instances and transitions the current service instance to the
   * AWAIT_COMPLETION sub-state.
   *
   * @param current
   */
  protected void handleTriggerCopies(final State current) {
    ServiceUtils.logInfo(this, "Start to trigger ImageHostToHostCopyService for image: %s", current.image);

    Set<String> datastoreSet = new HashSet<>();

    Operation queryImageDatastoreSet = buildImageDatastoreSetQuery(current);

    OperationSequence
        .create(queryImageDatastoreSet)
        .setCompletion((operations, throwable) -> {
          if (throwable != null) {
            failTask(throwable.values().iterator().next());
            return;
          }

          Operation op = operations.get(queryImageDatastoreSet.getId());
          NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
          List<DatastoreService.State> documentLinks = QueryTaskUtils
              .getBroadcastQueryDocuments(DatastoreService.State.class, queryResponse);
          for (DatastoreService.State state : documentLinks) {
            datastoreSet.add(state.id);
          }

          if (!this.validateImageDatastore(current, datastoreSet)) {
            return;
          }

          ServiceUtils.logInfo(this, "All target image datastores: %s", Utils.toJson(false, false, datastoreSet));
          this.triggerHostToHostCopyServices(current, datastoreSet);

          // Patch self with the new subStage and the count of triggered ImageHostToHostCopyService instances
          // to copy images.
          ImageSeederService.State newState = new ImageSeederService.State();
          newState.taskInfo = new TaskState();
          newState.taskInfo.stage = com.vmware.xenon.common.TaskState.TaskStage.STARTED;
          newState.taskInfo.subStage = TaskState.SubStage.AWAIT_COMPLETION;
          newState.triggeredCopies = datastoreSet.size();
          this.sendSelfPatch(newState);
        })
        .sendWith(this);
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  protected void processAwaitCompletion(final State current) {
    ServiceUtils.logInfo(this, "Checking status:  finishedCopies is %s, failedOrCancelledCopies is %s," +
        "triggeredCopies is %s", current.finishedCopies, current.failedOrCancelledCopies, current.triggeredCopies);

    if (current.finishedCopies != null
        && current.triggeredCopies.equals(current.finishedCopies)) {
      // all copies have completed successfully
      this.sendSelfPatch(buildPatch(TaskState.TaskStage.FINISHED, null, null));
      return;
    }

    if (current.finishedCopies != null
        && current.failedOrCancelledCopies != null
        && current.triggeredCopies.equals(current.finishedCopies + current.failedOrCancelledCopies)) {
      // all copies have completed, but some of them have failed
      RuntimeException e = new RuntimeException(
          String.format("Image seeding failed: %s image seeding succeeded, %s image seeding failed or cancelled",
              current.finishedCopies,
              current.failedOrCancelledCopies)
      );
      this.failTask(e);
      return;
    }

    getHost().schedule(() -> {
      this.checkStatus(current);
    }, current.queryPollDelay, TimeUnit.MILLISECONDS);
  }

  /**
   * Triggers ImageHostToHostCopyService for the image data stores passed as a parameter.
   *
   * @param current
   * @param datastores
   */
  protected void triggerHostToHostCopyServices(final State current, final Set<String> datastores) {
    for (String datastore : datastores) {
      this.triggerHostToHostCopyService(current, datastore);
    }
  }

  /**
   * Triggers an ImageHostToHostCopyService for the image datastore passed as a parameter.
   *
   * @param current
   * @param datastore
   */
  protected void triggerHostToHostCopyService(final State current, final String datastore) {
    // build completion handler
    Operation.CompletionHandler handler = (Operation acknowledgeOp, Throwable failure) -> {
      if (failure != null) {
        // we could not start an ImageHostToHostCopyService task. Something went horribly wrong. Fail
        // the current task and stop processing.
        RuntimeException e = new RuntimeException(
            String.format("Failed to send host to host copy request %s", failure));
        failTask(e);
      }

      ServiceUtils.logInfo(ImageSeederService.this, "ImageHostToHostCopyService %s, is triggered for image: %s",
          acknowledgeOp.getBody(ImageHostToHostCopyService.class).getSelfLink(), current.image);
    };

    // build copy service start state
    ImageHostToHostCopyService.State imageHostToHostCopyServiceStartState =
        this.buildImageHostToHostCopyServiceStartState(current, datastore);

    // start service
    this.startImageHostToHostCopyService(imageHostToHostCopyServiceStartState, handler);
  }


  protected CloudStoreHelper getCloudStoreHelper() {
    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper();
  }

  /**
   * Issues the queries to determine the status of the "child" ImageHostToHostService instances.
   *
   * @param current
   */
  private void checkStatus(final State current) {
    Operation finished = this.buildChildQueryOperation(TaskState.TaskStage.FINISHED);
    Operation failedOrCanceled = this.buildChildQueryOperation(
        TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED);

    OperationJoin.JoinedCompletionHandler handler = (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
      if (failures != null && !failures.isEmpty()) {
        failTask(failures.values().iterator().next());
        return;
      }

      try {
        QueryTask finishedRsp = ops.get(finished.getId()).getBody(QueryTask.class);
        QueryTask failedOrCanceledRsp = ops.get(failedOrCanceled.getId()).getBody(QueryTask.class);

        State s = buildPatch(current.taskInfo.stage, current.taskInfo.subStage, null);
        s.finishedCopies = finishedRsp.results.documentLinks.size();
        ServiceUtils.logInfo(ImageSeederService.this, "Finished %s",
            Utils.toJson(false, false, finishedRsp.results.documentLinks));

        s.failedOrCancelledCopies = failedOrCanceledRsp.results.documentLinks.size();
        ServiceUtils.logInfo(ImageSeederService.this, "FailedOrCanceledRsp %s",
            Utils.toJson(false, false, failedOrCanceledRsp.results.documentLinks));

        sendSelfPatch(s);
      } catch (Throwable e) {
        failTask(e);
      }
    };

    OperationJoin
        .create(finished, failedOrCanceled)
        .setCompletion(handler)
        .sendWith(this);
  }

  /**
   * Creates a query operation for ImageDeleteServices in the specified states.
   *
   * @param stages
   * @return
   */
  private Operation buildChildQueryOperation(TaskState.TaskStage... stages) {
    QueryTask.QuerySpecification spec =
        QueryTaskUtils.buildChildServiceTaskStatusQuerySpec(
            this.getSelfLink(),
            ImageHostToHostCopyService.State.class,
            stages);

    QueryTask task = QueryTask.create(spec)
        .setDirect(true);

    return Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(task);
  }

  /**
   * Builds ImageHostToHostCopy service start state.
   *
   * @param current
   * @param datastore
   * @return
   */
  private ImageHostToHostCopyService.State buildImageHostToHostCopyServiceStartState(
      final State current,
      final String datastore) {
    ImageHostToHostCopyService.State startState = new ImageHostToHostCopyService.State();
    startState.image = current.image;
    startState.sourceDatastore = current.sourceImageDatastore;
    startState.destinationDatastore = datastore;
    startState.parentLink = this.getSelfLink();

    startState.documentExpirationTimeMicros = current.documentExpirationTimeMicros;

    return startState;
  }

  /**
   * Starts ImageHostToHostCopy service.
   *
   * @param startState
   * @param handler
   * @return
   */
  private void startImageHostToHostCopyService(final ImageHostToHostCopyService.State startState,
                                               final Operation.CompletionHandler handler) {
    Operation copyOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ImageHostToHostCopyServiceFactory.SELF_LINK))
        .setBody(startState)
        .setCompletion(handler);
    this.sendRequest(copyOperation);
  }

  /**
   * Initialize task state.
   *
   * @param start
   * @return
   */
  private State initializeTaskState(final Operation start) {
    State s = start.getBody(State.class);

    if (s.taskInfo == null || s.taskInfo.stage == TaskState.TaskStage.CREATED) {
      s.taskInfo = new TaskState();
      s.taskInfo.stage = TaskState.TaskStage.STARTED;
      s.taskInfo.subStage = TaskState.SubStage.UPDATE_DATASTORE_COUNTS;
    }

    if (s.documentExpirationTimeMicros <= 0) {
      s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
          ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    if (s.queryPollDelay == null) {
      s.queryPollDelay = DEFAULT_QUERY_POLL_DELAY;
    }

    return s;
  }

  /**
   * Validate image data stores.
   *
   * @param current
   * @param datastoreSet
   * @return
   */
  private boolean validateImageDatastore(final State current, Set<String> datastoreSet) {
    if (datastoreSet.size() == 0) {
      failTask(new Exception("No image datastore found"));
      return false;
    }

    if (datastoreSet.size() == 1 && !datastoreSet.contains(current.sourceImageDatastore)) {
      String datastore = datastoreSet.iterator().next();
      failTask(new Exception("No image datastore found, sourceImageDatastore is " + current.sourceImageDatastore +
          ", image datastore in CloudStore is " + datastore));
      return false;
    }

    return true;
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
   * Build a QuerySpecification for querying image data store.
   *
   * @param current
   * @return
   */
  private Operation buildImageDatastoreSetQuery(final State current) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DatastoreService.State.class));

    QueryTask.Query imageDatastoreClause = new QueryTask.Query()
        .setTermPropertyName("isImageDatastore")
        .setTermMatchValue("true");

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(imageDatastoreClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
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
      UPDATE_DATASTORE_COUNTS,
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
    @DefaultInteger(value = DEFAULT_QUERY_POLL_DELAY)
    public Integer queryPollDelay;

    /**
     * Source image data store.
     */
    public String sourceImageDatastore;

    /**
     * Triggered copies.
     */
    public Integer triggeredCopies;

    /**
     * Finished copies.
     */
    public Integer finishedCopies;

    /**
     * Failed or canceled copies.
     */
    public Integer failedOrCancelledCopies;
  }
}
