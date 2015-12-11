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

import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.ImageTransferInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.TransferImageResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

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
 * Class implementing service to copy an image from a source image data store to a target image data store using
 * host-to-host image copy.
 */
public class ImageHostToHostCopyService extends StatefulService {

  /**
   * Default constructor.
   */
  public ImageHostToHostCopyService() {
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
    s.taskInfo.subStage = TaskState.SubStage.RETRIEVE_HOSTS;
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
        s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
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
        "Can not patch anymore when in final stage %s", current.taskInfo.stage);
    if (patch.taskInfo != null && patch.taskInfo.stage != null) {
      checkState(patch.taskInfo.stage.ordinal() >= current.taskInfo.stage.ordinal(),
          "Can not revert to %s from %s", patch.taskInfo.stage, current.taskInfo.stage);
    }

    checkArgument(patch.image == null, "Image cannot be changed.");
    checkArgument(patch.sourceDataStore == null, "Source datastore cannot be changed.");
    checkArgument(patch.destinationDataStore == null, "Destination datastore cannot be changed.");
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    checkNotNull(current.taskInfo);
    checkNotNull(current.taskInfo.stage);

    checkNotNull(current.image, "image not provided");
    checkNotNull(current.sourceDataStore, "source datastore not provided");
    checkNotNull(current.destinationDataStore, "destination datastore not provided");

    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");

    switch (current.taskInfo.stage) {
      case STARTED:
        checkState(current.taskInfo.subStage != null, "subStage cannot be null");
        switch (current.taskInfo.subStage) {
          case RETRIEVE_HOSTS:
            break;
          case TRANSFER_IMAGE:
            checkArgument(current.host != null, "host not found");
            checkArgument(current.destinationDataStore != null, "destination host not found");
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

    if (patchState.destinationHost != null) {
      currentState.destinationHost = patchState.destinationHost;
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
      case RETRIEVE_HOSTS:
        getHostsFromDataStores(current);
        break;
      case TRANSFER_IMAGE:
        copyImageHostToHost(current);
        break;
      default:
        throw new IllegalStateException("Un-supported substage" + current.taskInfo.subStage.toString());
    }
  }

  /**
   * Calls agents to copy an image from a source image datastore to a destination image datastore.
   *
   * @param current
   */
  private void copyImageHostToHost(final State current) {
    if (current.sourceDataStore.equals(current.destinationDataStore)) {
      ServiceUtils.logInfo(this, "Skip copying image to source itself");
      sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
      return;
    }

    ServiceUtils.logInfo(this, "Calling agent to do host to host image copy.");
    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          TransferImageResponse r = ((Host.AsyncClient.transfer_image_call) o).getResult();
          ServiceUtils.logInfo(ImageHostToHostCopyService.this, "TransferImageResponse %s", r);
          switch (r.getResult()) {
            case OK:
              sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
              ;
              break;
            case TRANSFER_IN_PROGRESS:
              throw new ImageTransferInProgressException(r.getError());
            case SYSTEM_ERROR:
              throw new SystemErrorException(r.getError());
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
      getHostClient(current).transferImage(current.image, current.sourceDataStore, current.destinationDataStore,
          current.destinationHost, callback);

    } catch (RpcException | IOException e) {
      failTask(e);
    }
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
   * Get a host client.
   *
   * @param current
   * @return
   */
  private HostClient getHostClient(final State current) throws IOException {
    HostClient client = ((HostClientProvider) getHost()).getHostClient();
    client.setHostIp(current.host);
    return client;
  }

  /**
   * Retrieve hosts that connect to the given source image datastore and destination datastore respectively.
   *
   * @param current
   */
  private void getHostsFromDataStores(final State current) {
    try {
      queryHost(current, current.sourceDataStore, (Operation completedOp, Throwable failure) -> {
        if (failure != null) {
          failTask(failure);
          return;
        }
        String host = getHostFromResponse(completedOp);
        if (host == null) {
          failTask(new Exception("No host found between source image " +
              "datastore " + current.sourceDataStore));
          return;
        }
        current.host = host;

        queryHost(current, current.destinationDataStore, (Operation operation, Throwable throwable) -> {
          ServerAddress destinationHost = getHostServerAddressFromResponse(operation);
          if (destinationHost == null) {
            failTask(new Exception("No host found between source image " +
                "datastore " + current.destinationDataStore));
            return;
          }
          current.destinationHost = destinationHost;

          // Patch self with the host and data store information.
          if (!current.isSelfProgressionDisabled) {
            ImageHostToHostCopyService.State patch = buildPatch(com.vmware.xenon.common.TaskState.TaskStage.STARTED,
                TaskState.SubStage.TRANSFER_IMAGE, null);
            patch.host = current.host;
            patch.destinationHost = current.destinationHost;
            this.sendSelfPatch(patch);
          }
        });
      });
    } catch (Exception e) {
      failTask(e);
    }
  }

  private String getHostFromResponse(Operation operation) {
    Set<String> hostSet = new HashSet<>();

    NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
    List<HostService.State> documentLinks = QueryTaskUtils
        .getBroadcastQueryDocuments(HostService.State.class, queryResponse);
    for (HostService.State state : documentLinks) {
      hostSet.add(state.hostAddress);
    }

    if (hostSet.size() == 0) {
      return null;
    }

    return ServiceUtils.selectRandomItem(hostSet);
  }

  private ServerAddress getHostServerAddressFromResponse(Operation operation) {
    Set<ServerAddress> hostSet = new HashSet<>();

    NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
    List<HostService.State> documentLinks = QueryTaskUtils
        .getBroadcastQueryDocuments(HostService.State.class, queryResponse);
    for (HostService.State state : documentLinks) {
      hostSet.add(new ServerAddress(state.hostAddress, state.agentPort));
    }

    if (hostSet.size() == 0) {
      return null;
    }

    return ServiceUtils.selectRandomItem(hostSet);
  }

  private void queryHost(final State current, String datastoreId,
                         Operation.CompletionHandler handler) {
    String reportedImageDatastorefieldName = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_REPORTED_IMAGE_DATASTORES);

    try {
      QueryTask.QuerySpecification querySpecification = buildHostQuery(current, reportedImageDatastorefieldName,
          datastoreId);
      sendRequest(
          ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
              .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
              .setBody(QueryTask.create(querySpecification).setDirect(true))
              .setCompletion(handler));
    } catch (Exception e) {
      failTask(e);
    }
  }

  /**
   * Build a QuerySpecification for querying host with access to both image datastore and destination datastore.
   *
   * @param current
   * @return
   */
  private QueryTask.QuerySpecification buildHostQuery(final State current, String fieldName, String matchValue) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.Query fieldNameClause = new QueryTask.Query()
        .setTermPropertyName(fieldName)
        .setTermMatchValue(matchValue);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(fieldNameClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return querySpecification;
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
      RETRIEVE_HOSTS,
      TRANSFER_IMAGE,
    }
  }

  /**
   * Durable service state data. Class encapsulating the data for image copy between hosts.
   */
  public static class State extends ServiceDocument {

    /**
     * Copy service stage.
     */
    public TaskState taskInfo;

    /**
     * Image to be copied.
     */
    public String image;

    /**
     * The store where the image is currently available.
     */
    public String sourceDataStore;

    /**
     * The store where the image will be copied to.
     */
    public String destinationDataStore;

    /**
     * The host connecting to the source image datastore.
     */
    public String host;

    /**
     * The host connecting to the destination image datastore.
     */
    public ServerAddress destinationHost;

    /**
     * When isSelfProgressionDisabled is true, the service does not automatically update its stages.
     */
    public boolean isSelfProgressionDisabled;
  }
}
