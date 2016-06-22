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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.host.gen.CopyImageResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class implementing service to copy an image from a source data store to a target data store.
 */
public class ImageCopyService extends StatefulService {

  /**
   * Default constructor.
   */
  public ImageCopyService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public static State buildStartPatch() {
    State s = new State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = TaskState.TaskStage.STARTED;
    s.taskInfo.subStage = TaskState.SubStage.RETRIEVE_HOST;
    return s;
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      // Initialize the task state.
      State s = start.getBody(State.class);
      if (s.taskInfo == null || s.taskInfo.stage == null) {
        s.taskInfo = new TaskState();
        s.taskInfo.stage = TaskState.TaskStage.CREATED;
      }

      if (s.documentExpirationTimeMicros <= 0) {
        s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
            ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      validateState(s);
      start.setBody(s).complete();

      sendStageProgressPatch(s, s.taskInfo.stage, s.taskInfo.subStage);
    } catch (RuntimeException e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(start)) {
        start.fail(e);
      }
    }
  }

  /**
   * Handle service requests.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    URI referer = patch.getReferer();

    try {
      // Validate input, persist and eager complete.
      validateStatePatch(currentState, patchState, referer);
      applyPatch(currentState, patchState);

      validateState(currentState);
      patch.complete();

      switch (currentState.taskInfo.stage) {
        case CREATED:
          break;
        case STARTED:
          handleStartedStage(currentState);
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

  @VisibleForTesting
  protected HostClient getHostClient(final State current) throws IOException {
    HostClient client = ((HostClientProvider) getHost()).getHostClient();
    client.setHostIp(current.host);
    if (LoggingUtils.getRequestId() == null) {
      LoggingUtils.setRequestId(ServiceUtils.getIDFromDocumentSelfLink(current.documentSelfLink));
    }

    return client;
  }

  /**
   * Validate patch correctness.
   *
   * @param current
   * @param patch
   */
  protected void validateStatePatch(State current, State patch, URI referer) {
    if (current.taskInfo.stage != TaskState.TaskStage.CREATED &&
        referer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage, ignores patch from TaskSchedulerService");
    }

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

    checkArgument(patch.parentLink == null, "ParentLink cannot be changed.");
    checkArgument(patch.image == null, "Image cannot be changed.");
    checkArgument(patch.sourceImageDataStore == null, "Source datastore cannot be changed.");
    checkArgument(patch.destinationDataStore == null, "Destination datastore cannot be changed.");
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    checkNotNull(current.taskInfo, "taskInfo cannot be null");
    checkNotNull(current.taskInfo.stage, "stage cannot be null");

    checkNotNull(current.image, "image not provided");
    checkNotNull(current.sourceImageDataStore, "source datastore not provided");
    checkNotNull(current.destinationDataStore, "destination datastore not provided");

    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");

    switch (current.taskInfo.stage) {
      case STARTED:
        checkState(current.taskInfo.subStage != null, "subStage cannot be null");
        switch (current.taskInfo.subStage) {
          case RETRIEVE_HOST:
            break;
          case COPY_IMAGE:
            checkArgument(current.host != null, "host not found");
            break;
          default:
            checkState(false, "unsupported sub-state: " + current.taskInfo.subStage.toString());
        }
        break;
      case CREATED:
      case FAILED:
      case FINISHED:
      case CANCELLED:
        checkState(current.taskInfo.subStage == null, "Invalid stage update. subStage must be null");
        break;
      default:
        checkState(false, "cannot process patches in state: " + current.taskInfo.stage.toString());
    }
  }

  protected void applyPatch(State currentState, State patchState) {
    if (patchState.taskInfo != null) {
      if (patchState.taskInfo.stage != currentState.taskInfo.stage) {
        ServiceUtils.logInfo(this, "moving to stage %s", patchState.taskInfo.stage);
      }

      currentState.taskInfo = patchState.taskInfo;
    }

    if (patchState.host != null) {
      currentState.host = patchState.host;
    }

    if (patchState.destinationDataStore != null) {
      currentState.destinationDataStore = patchState.destinationDataStore;
    }
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  protected void handleStartedStage(final State current) {
    // Handle task sub-state.
    switch (current.taskInfo.subStage) {
      case RETRIEVE_HOST:
        retrieveHost(current);
        break;
      case COPY_IMAGE:
        copyImage(current);
        break;
      default:
        throw new IllegalStateException("Un-supported substage" + current.taskInfo.subStage.toString());
    }
  }

  /**
   * Calls agent to copy an image from a source datastore to a destination datastore.
   *
   * @param current
   */
  private void copyImage(final State current) {
    if (current.sourceImageDataStore.equals(current.destinationDataStore)) {
      ServiceUtils.logInfo(this, "Skip copying image to source itself");
      sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
      return;
    }

    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          CopyImageResponse r = ((Host.AsyncClient.copy_image_call) o).getResult();
          ServiceUtils.logInfo(ImageCopyService.this, "CopyImageResponse %s", r);
          switch (r.getResult()) {
            case OK:
              sendPatchToIncrementImageReplicatedCount(current);
              break;
            case DESTINATION_ALREADY_EXIST:
              sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
              break;
            case SYSTEM_ERROR:
              throw new SystemErrorException(r.getError());
            case IMAGE_NOT_FOUND:
              throw new ImageNotFoundException(r.getError());
            default:
              throw new UnknownError(
                  String.format("Unknown result code %s", r.getResult()));
          }
        } catch (Exception e) {
          onError(e);
        }
      }

      @Override
      public void onError(Exception e) {
        failTask(e);
      }
    };

    try {
      getHostClient(current).copyImage(current.image, current.sourceImageDataStore,
          current.destinationDataStore, callback);
    } catch (IOException | RpcException e) {
      failTask(e);
    }
  }

  /**
   * Sends patch to update replicatedDatastore in image cloud store entity.
   *
   * @param current
   */
  private void sendPatchToIncrementImageReplicatedCount(final State current) {
    try {
      ImageService.DatastoreCountRequest requestBody = constructDatastoreCountRequest(1);
      sendRequest(
          ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
              .createPatch(ImageServiceFactory.SELF_LINK + "/" + current.image)
              .setBody(requestBody)
              .setCompletion(
                  (op, t) -> {
                    if (t != null) {
                      ServiceUtils.logWarning(this, "Could not increment replicatedDatastore for image %s by %s: %s",
                          current.image, requestBody.amount, t);
                    }
                    sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
                  }
              ));
    } catch (Exception e) {
      ServiceUtils.logSevere(this, "Exception thrown while sending patch to image service to increment count: %s",
          e);
    }
  }

  private ImageService.DatastoreCountRequest constructDatastoreCountRequest(int adjustCount) {
    ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
    requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
    requestBody.amount = adjustCount;
    return requestBody;
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
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(s);
    sendRequest(patch);
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
   * Build a QuerySpecification for querying host with access to both image datastore and destination datastore.
   *
   * @param current
   * @return
   */
  private QueryTask.QuerySpecification buildHostQuery(final State current) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    String fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_REPORTED_IMAGE_DATASTORES);
    QueryTask.Query imageDatastoreClause = new QueryTask.Query()
        .setTermPropertyName(fieldName)
        .setTermMatchValue(current.sourceImageDataStore);

    fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_REPORTED_DATASTORES);
    QueryTask.Query datastoreClause = new QueryTask.Query()
        .setTermPropertyName(fieldName)
        .setTermMatchValue(current.destinationDataStore);

    QueryTask.Query stateClause = new QueryTask.Query()
        .setTermPropertyName("state")
        .setTermMatchValue(HostState.READY.toString());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(imageDatastoreClause);
    querySpecification.query.addBooleanClause(datastoreClause);
    querySpecification.query.addBooleanClause(stateClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return querySpecification;
  }


  private void retrieveHost(final State current) {
    if (current.sourceImageDataStore.equals(current.destinationDataStore)) {
      ServiceUtils.logInfo(this, "Skip copying image to source itself");
      sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
      return;
    }

    Set<String> hostSet = new HashSet<>();
    try {

      QueryTask.QuerySpecification querySpecification = buildHostQuery(current);

      sendRequest(
          ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
              .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
              .setBody(QueryTask.create(querySpecification).setDirect(true))
              .setCompletion(
                  (operation, throwable) -> {
                    if (throwable != null) {
                      failTask(throwable);
                      return;
                    }
                    NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
                    List<HostService.State> documentLinks = QueryTaskUtils
                        .getBroadcastQueryDocuments(HostService.State.class, queryResponse);
                    for (HostService.State state : documentLinks) {
                      hostSet.add(state.hostAddress);
                    }

                    if (hostSet.size() == 0 && !current.isSelfProgressionDisabled) {
                      ImageCopyService.State patch = buildPatch(TaskState.TaskStage.FINISHED, null, null);
                      this.sendSelfPatch(patch);
                      ServiceUtils.logInfo(this, "ImageCopyService %s can't find host, moved to FINISHED stage",
                          getSelfLink());
                      return;
                    }

                    String hostIp = ServiceUtils.selectRandomItem(hostSet);

                    // Patch self with the host and data store information.
                    if (!current.isSelfProgressionDisabled) {
                      ImageCopyService.State patch = buildPatch(TaskState.TaskStage.STARTED,
                          TaskState.SubStage.COPY_IMAGE, null);
                      patch.host = hostIp;
                      this.sendSelfPatch(patch);
                    }
                  }
              ));
    } catch (Exception e) {
      failTask(e);
    }
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
      RETRIEVE_HOST,
      COPY_IMAGE,
    }
  }

  /**
   * Durable service state data. Class encapsulating the data for image copy between data stores in esx cloud. What
   * is specific to the ImageCopy concept is that in addition to <source, destination, object> it is possible to specify
   * a host to perform the copy.
   */
  public static class State extends ServiceDocument {

    /**
     * Copy service stage.
     */
    public TaskState taskInfo;

    /**
     * URI of the sender of the copy, if not null notify of copy end.
     */
    public String parentLink;

    /**
     * Image to be copied.
     */
    public String image;

    /**
     * The store where the image is currently available.
     */
    public String sourceImageDataStore;

    /**
     * The store where the image will be copied to.
     */
    public String destinationDataStore;

    /**
     * Host with access to both source and destination stores.
     */
    public String host;

    /**
     * When isSelfProgressionDisabled is true, the service does not automatically update its stages.
     */
    public boolean isSelfProgressionDisabled;
  }
}
