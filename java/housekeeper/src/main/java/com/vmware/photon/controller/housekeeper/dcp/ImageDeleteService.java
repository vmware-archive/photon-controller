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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.DatastoreNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidRefCountException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.host.gen.DeleteImageResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.host.gen.ImageInfoResponse;
import com.vmware.photon.controller.housekeeper.zookeeper.ZookeeperHostMonitorProvider;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;
import org.joda.time.DateTime;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Class implementing service to delete an image from a data store.
 */
public class ImageDeleteService extends StatefulService {

  /**
   * Default constructor.
   */
  public ImageDeleteService() {
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

      if (s.setTombstoneFlag == null) {
        s.setTombstoneFlag = new Boolean(true);
      }

      if (s.documentExpirationTimeMicros <= 0) {
        s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
      }

      validateState(s);
      start.setBody(s).complete();

      sendStageProgressPatch(s, s.taskInfo.stage);
    } catch (RuntimeException e) {
      logSevere(e);
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
    try {
      State currentState = getState(patch);
      State patchState = patch.getBody(State.class);
      URI referer = patch.getReferer();

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
    return client;
  }

  /**
   * Retrieves the ZookeeperHostMonitor from the host.
   *
   * @return
   */
  @VisibleForTesting
  protected ZookeeperHostMonitor getZookeeperHostMonitor() {
    return ((ZookeeperHostMonitorProvider) getHost()).getZookeeperHostMonitor();
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

    checkArgument(patch.parentLink == null, "parentLink cannot be changed.");
    checkArgument(patch.imageWatermarkTime == null, "imageWatermarkTime cannot be changed.");
    checkArgument(patch.image == null, "image cannot be changed.");
    checkArgument(patch.dataStore == null, "dataStore cannot be changed.");
    checkArgument(patch.setTombstoneFlag == null, "setTombstoneFlag cannot be changed.");
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
    checkNotNull(current.setTombstoneFlag, "setTombstoneFlag not provided");

    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");

    if (current.host == null || current.dataStore == null) {
      checkState(current.dataStore != null,
          "datastore not provided");
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

    if (patchState.dataStore != null) {
      currentState.dataStore = patchState.dataStore;
    }

    if (patchState.imageCreatedTime != null) {
      currentState.imageCreatedTime = patchState.imageCreatedTime;
    }
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  protected void handleStartedStage(final State current) {
    if (current.host == null || current.dataStore == null) {
      getHostFromDataStore(current.dataStore);
    } else if (current.imageWatermarkTime != null && current.imageCreatedTime == null) {
      getImageCreatedTime(current);
    } else if (current.imageWatermarkTime == null ||
        current.imageWatermarkTime > current.imageCreatedTime) {
      deleteImage(current);
    } else {
      this.sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
    }
  }

  /**
   * Retrieve host ip and datastore name.
   *
   * @param datastoreId
   */
  private void getHostFromDataStore(final String datastoreId) {
    try {
      Set<HostConfig> hosts = getZookeeperHostMonitor().getHostsForDatastore(datastoreId);
      checkState(hosts.size() > 0, "No hosts found for reference datastore. [%s].", datastoreId);

      HostConfig host = ServiceUtils.<HostConfig>selectRandomItem(hosts);

      // Patch self with the host and data store information.
      State state = buildPatch(TaskState.TaskStage.STARTED, null);
      state.host = host.getAddress().getHost();
      sendSelfPatch(state);
    } catch (Exception e) {
      failTask(e);
    }
  }

  /**
   * Retrieve the created time for the image.
   *
   * @param current
   */
  private void getImageCreatedTime(final State current) {
    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          ImageInfoResponse r = ((Host.AsyncClient.get_image_info_call) o).getResult();
          ServiceUtils.logInfo(ImageDeleteService.this, "DeleteImageResponse %s", r);

          switch (r.getResult()) {
            case OK:
              State patch = buildPatch(current.taskInfo.stage, null);
              patch.imageCreatedTime = DateTime.parse(r.getImage_info().getCreated_time()).getMillis();
              sendSelfPatch(patch);
              break;
            case IMAGE_NOT_FOUND:
              sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
              break;
            case DATASTORE_NOT_FOUND:
              throw new DatastoreNotFoundException(r.getError());
            case SYSTEM_ERROR:
              throw new SystemErrorException(r.getError());
            case INVALID_REF_COUNT_FILE:
              throw new InvalidRefCountException(r.getError());
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
      getHostClient(current).getImageInfo(current.image, current.dataStore, callback);
    } catch (IOException | RpcException e) {
      failTask(e);
    }
  }

  /**
   * Call agent to delete the image.
   *
   * @param current
   * @throws IOException
   */
  private void deleteImage(final State current) {
    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          DeleteImageResponse r = ((Host.AsyncClient.delete_image_call) o).getResult();
          ServiceUtils.logInfo(ImageDeleteService.this, "DeleteImageResponse %s", r);
          switch (r.getResult()) {
            case OK:
              try {
                sendPatchToDecrementImageReplicatedCount(current);
              } catch (Exception e) {
                ServiceUtils.logWarning(ImageDeleteService.this,
                    "Exception thrown while sending patch to image service to increment count: %s",
                    e);
              }
              break;
            case IMAGE_IN_USE:
            case IMAGE_NOT_FOUND:
              sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
              break;
            case SYSTEM_ERROR:
              throw new SystemErrorException(r.getError());
            case INVALID_REF_COUNT_FILE:
              throw new InvalidRefCountException(r.getError());
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
      getHostClient(current).deleteImage(current.image, current.dataStore, current.setTombstoneFlag, callback);
    } catch (IOException | RpcException e) {
      failTask(e);
    }
  }

  /**
   * Sends patch to update replicatedDatastore in image cloud store entity.
   * @param current
   */
  private void sendPatchToDecrementImageReplicatedCount(final State current) {
    CloudStoreHelper cloudStoreHelper = ((HousekeeperDcpServiceHost) getHost()).getCloudStoreHelper();
    ImageService.DatastoreCountRequest requestBody = constructDatastoreCountRequest(-1);
    cloudStoreHelper.patchEntity(ImageDeleteService.this,
        com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory.SELF_LINK + "/" + current.image,
        requestBody,
        (op, t) -> {
          sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
          if (t != null) {
            ServiceUtils.logWarning(this, "Could not decrement replicatedDatastore for image %s by %s: %s",
                current.image, requestBody.amount, t);
            return;
          }
        });
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
    this.sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, e));
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
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    sendSelfPatch(buildPatch(stage, null));
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
    State s = new ImageDeleteService.State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = stage;

    if (e != null) {
      s.taskInfo.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Durable service state data. Class encapsulating the data for image delete.
   */
  public static class State extends ServiceDocument {

    /**
     * Image delete service stage.
     */
    public TaskState taskInfo;

    /**
     * URI of the sender of the delete, if not null notify of delete end.
     */
    public String parentLink;

    /**
     * The timestamp indicating when the reference images were retrieved.
     */
    public Long imageWatermarkTime;

    /**
     * Image to be deleted.
     */
    public String image;

    /**
     * Flag indicating if image is on demand.
     */
    public Boolean setTombstoneFlag;

    /**
     * The id of datastore where the image will be deleted from.
     */
    public String dataStore;

    /**
     * Host with access to datastore.
     */
    public String host;

    /**
     * The time the image was created on the datastore we are trying to delete from.
     */
    public Long imageCreatedTime;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    public boolean isSelfProgressionDisabled;
  }
}
