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
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ImageTransferInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerService;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceHelper;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.CopyImageResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.TransferImageResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    s.taskInfo.subStage = TaskState.SubStage.CHECK_IMAGE_TO_IMAGE_DATASTORE_MAPPING_DOC;
    return s;
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      // Initialize the task state.
      State s = start.getBody(State.class);
      InitializationUtils.initialize(s);

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
          sendTaskSchedulerServicePatch();
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

    if (patch.taskInfo != null) {
      checkState(patch.taskInfo.stage != null, "Invalid stage update. 'stage' can not be null in patch");
      checkState(patch.taskInfo.stage.ordinal() >= current.taskInfo.stage.ordinal(),
          "Invalid stage update. Can not revert to %s from %s", patch.taskInfo.stage, current.taskInfo.stage);

      if (patch.taskInfo.subStage != null && current.taskInfo.subStage != null) {
        checkState(patch.taskInfo.subStage.ordinal() >= current.taskInfo.subStage.ordinal(),
            "Invalid stage update. 'subStage' cannot move back.");
      }
    }

    checkArgument(patch.parentLink == null, "parentLink cannot be changed.");
    checkArgument(patch.image == null, "Image cannot be changed.");
    checkArgument(patch.sourceDatastore == null, "Source datastore cannot be changed.");
    checkArgument(patch.destinationDatastore == null, "Destination datastore cannot be changed.");
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
    checkNotNull(current.sourceDatastore, "source datastore not provided");
    checkNotNull(current.destinationDatastore, "destination datastore not provided");
    checkNotNull(current.parentLink, "parentLink not provided");

    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");

    switch (current.taskInfo.stage) {
      case STARTED:
        checkState(current.taskInfo.subStage != null, "subStage cannot be null");
        switch (current.taskInfo.subStage) {
          case CHECK_IMAGE_TO_IMAGE_DATASTORE_MAPPING_DOC:
          case RETRIEVE_HOSTS:
            break;
          case TRANSFER_IMAGE:
            checkArgument(current.host != null, "host cannot be null");
            checkArgument(current.destinationDatastore != null, "destination host cannot be null");
            break;
          case UPDATE_IMAGE_REPLICATION_DOCUMENT:
            checkArgument(current.image != null, "image cannot be null");
            checkArgument(current.destinationDatastore != null, "destination host cannot be null");
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
        ServiceUtils.logInfo(this, "moving to stage %s, parentLink %s", patchState.taskInfo.stage,
            currentState.parentLink);
      }

      currentState.taskInfo = patchState.taskInfo;
    }

    if (patchState.host != null) {
      currentState.host = patchState.host;
    }

    if (patchState.destinationHost != null) {
      currentState.destinationHost = patchState.destinationHost;
    }

    if (patchState.imageExistsFlag != null) {
      currentState.imageExistsFlag = patchState.imageExistsFlag;
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
      case CHECK_IMAGE_TO_IMAGE_DATASTORE_MAPPING_DOC:
        checkImageToImageDatastoreMappingDocument(current);
        break;
      case RETRIEVE_HOSTS:
        getHostsFromDataStores(current);
        break;
      case TRANSFER_IMAGE:
        copyImageHostToHost(current);
        break;
      case UPDATE_IMAGE_REPLICATION_DOCUMENT:
        updateDocumentsAndTriggerCopy(current);
        break;
      default:
        throw new IllegalStateException("Un-supported substage" + current.taskInfo.subStage.toString());
    }
  }

  /**
   * Check if the ImageToImageDatastoreMappingService has already created for the destination image datastore, if so,
   * go to FINISHED stage.
   *
   * @param current
   */
  private void checkImageToImageDatastoreMappingDocument(final State current) {
    Operation getimageToImageDatastoreMappingService =
        ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper().createGet
            (ImageToImageDatastoreMappingServiceFactory.SELF_LINK + "/" + current.image
                + "_" + current.destinationDatastore)
            .setCompletion(
                (operation, throwable) -> {
                  if (operation.getStatusCode() != 404) {
                    sendStageProgressPatch(current, com.vmware.xenon.common.TaskState.TaskStage.FINISHED, null);
                  } else {
                    sendStageProgressPatch(current, com.vmware.xenon.common.TaskState.TaskStage.STARTED,
                        TaskState.SubStage.RETRIEVE_HOSTS);
                  }
                }
            );
    sendRequest(getimageToImageDatastoreMappingService);
  }

  /**
   * Calls agents to copy an image from a source image datastore to a destination image datastore.
   *
   * @param current
   */
  private void copyImageHostToHost(final State current) {
    ServiceUtils.logInfo(this, "Start to copy image: %s from host %s to host %s", current.image, current.host,
        current.destinationHost.getHost());

    if (current.sourceDatastore.equals(current.destinationDatastore)) {
      ServiceUtils.logInfo(this, "Skip copying image to source itself");
      sendStageProgressPatch(current, TaskState.TaskStage.FINISHED, null);
      return;
    }

    if (current.host.equals(current.destinationHost.getHost())) {
      invokeCopyImage(current);
    } else {
      invokeTransferImage(current);
    }
  }

  /**
   * Calls agents to copy an image from a source image datastore to a destination image datastore when there is a host
   * connecting two image datastores (copy image).
   *
   * @param current
   */
  private void invokeCopyImage(final State current) {
    ServiceUtils.logInfo(this, "Calling agent to do image copy between image datastores on the same host.");
    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          CopyImageResponse r = ((Host.AsyncClient.copy_image_call) o).getResult();
          ServiceUtils.logInfo(ImageHostToHostCopyService.this, "CopyImageResponse %s", r);
          switch (r.getResult()) {
            case OK:
              sendStageProgressPatch(current, TaskState.TaskStage.STARTED,
                  TaskState.SubStage.UPDATE_IMAGE_REPLICATION_DOCUMENT);
              break;
            case DESTINATION_ALREADY_EXIST:
              State s = new State();
              s.taskInfo = new TaskState();
              s.taskInfo.stage = TaskState.TaskStage.STARTED;
              s.taskInfo.subStage = TaskState.SubStage.UPDATE_IMAGE_REPLICATION_DOCUMENT;
              s.imageExistsFlag = true;
              sendSelfPatch(s);
              break;
            case IMAGE_NOT_FOUND:
              throw new ImageNotFoundException(r.getError());
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
      getHostClient(current).copyImage(current.image, current.sourceDatastore, current.destinationDatastore,
          callback);

    } catch (RpcException | IOException e) {
      failTask(e);
    }
  }

  /**
   * Calls agents to copy an image from a source image datastore to a destination image datastore when there is no host
   * connecting two image datastores (transfer image).
   *
   * @param current
   */
  private void invokeTransferImage(final State current) {
    ServiceUtils.logInfo(this, "Calling agent to do host to host image copy.");
    AsyncMethodCallback callback = new AsyncMethodCallback() {
      @Override
      public void onComplete(Object o) {
        try {
          TransferImageResponse r = ((Host.AsyncClient.transfer_image_call) o).getResult();
          ServiceUtils.logInfo(ImageHostToHostCopyService.this, "TransferImageResponse %s", r);
          switch (r.getResult()) {
            case OK:
              sendStageProgressPatch(current, TaskState.TaskStage.STARTED,
                  TaskState.SubStage.UPDATE_IMAGE_REPLICATION_DOCUMENT);
              break;
            case DESTINATION_ALREADY_EXIST:
              State s = new State();
              s.taskInfo = new TaskState();
              s.taskInfo.stage = TaskState.TaskStage.STARTED;
              s.taskInfo.subStage = TaskState.SubStage.UPDATE_IMAGE_REPLICATION_DOCUMENT;
              s.imageExistsFlag = true;
              sendSelfPatch(s);
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
      getHostClient(current).transferImage(current.image, current.sourceDatastore, current.destinationDatastore,
          current.destinationHost, callback);

    } catch (RpcException | IOException e) {
      failTask(e);
    }
  }

  private ImageService.DatastoreCountRequest buildAdjustSeedingAndReplicationCountRequest(final State current,
                                                                                          int adjustCount) {
    ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
    requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_SEEDING_AND_REPLICATION_COUNT;
    requestBody.amount = adjustCount;
    return requestBody;
  }

  private ImageService.DatastoreCountRequest buildAdjustSeedingCountRequest(final State current, int adjustCount) {
    ImageService.DatastoreCountRequest requestBody = new ImageService.DatastoreCountRequest();
    requestBody.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_SEEDING_COUNT;
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
   * Build a state object for ImageToImageDatastoreMappingService to submit a post request to the service.
   *
   * @param imageId
   * @param imageDatastoreId
   * @return
   */
  private ImageToImageDatastoreMappingService.State
  buildImageToImageDatastoreMappingServiceState(String imageId, String imageDatastoreId) {
    ImageToImageDatastoreMappingService.State imageToImageDatastoreMappingService
        = new ImageToImageDatastoreMappingService.State();
    imageToImageDatastoreMappingService.imageId = imageId;
    imageToImageDatastoreMappingService.imageDatastoreId = imageDatastoreId;
    imageToImageDatastoreMappingService.documentSelfLink = imageId + "_" + imageDatastoreId;

    return imageToImageDatastoreMappingService;
  }

  /**
   * Build a state object for ImageReplicatorService to submit a post request to the service.
   *
   * @param imageId
   * @param imageDatastoreId
   * @return
   */
  private ImageReplicatorService.State buildImageReplicatorServiceState(String imageId, String imageDatastoreId) {
    ImageReplicatorService.State imageReplicatorService = new ImageReplicatorService.State();
    imageReplicatorService.image = imageId;
    imageReplicatorService.datastore = imageDatastoreId;

    return imageReplicatorService;
  }

  /**
   * Check if image is eager copy.
   *
   * @param current
   * @return
   */
  private void updateDocumentsAndTriggerCopy(final State current) {
    Operation imageQuery = buildImageQuery(current);
    imageQuery.setCompletion((operation, throwable) -> {
      if (throwable != null) {
        failTask(throwable);
      }
      ImageService.State imageState = operation.getBody(ImageService.State.class);
      boolean isEagerCopy = (imageState.replicationType == ImageReplicationType.EAGER);
      updateDocumentsAndTriggerCopy(current, isEagerCopy);
    });
    sendRequest(imageQuery);
  }

  /**
   * Sends post request to trigger TaskSchedulerService.
   */
  private void sendTaskSchedulerServicePatch() {
    TaskSchedulerService.State s = new TaskSchedulerService.State();
    TaskSchedulerServiceHelper.sendPatchToOwner(this, getHost(), HousekeeperServiceGroup
        .getTaskSchedulerImageHostToHostServiceUri(), s, UriUtils.buildUri(getHost(), getSelfLink()));
  }

  /**
   * Sends post request to imageToImageDatastoreMappingService to
   * create a document with imageId and destination datastore.
   *
   * @param current
   */
  private void updateDocumentsAndTriggerCopy(final State current, boolean isEagerCopy) {
    ImageToImageDatastoreMappingService.State postState =
        buildImageToImageDatastoreMappingServiceState(current.image, current.destinationDatastore);
    Operation createimageToImageDatastoreMappingServicePatch =
        ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper().createPost
            (ImageToImageDatastoreMappingServiceFactory.SELF_LINK)
            .setBody(postState);

    ImageReplicatorService.State replicatorServiceState =
        buildImageReplicatorServiceState(current.image, current.destinationDatastore);
    Operation createImageReplicatorServicePatch = Operation
        .createPost(UriUtils.buildUri(getHost(), ImageReplicatorServiceFactory.SELF_LINK))
        .setBody(replicatorServiceState);

    Operation adjustReplicationCountPatch = ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createPatch(ImageServiceFactory.SELF_LINK + "/" + current.image);
    ImageService.DatastoreCountRequest adjustSeedingAndReplicationCountRequest =
        buildAdjustSeedingAndReplicationCountRequest(current, 1);
    ImageService.DatastoreCountRequest adjustSeedingCountRequest =
        buildAdjustSeedingCountRequest(current, 1);

    try {
      createimageToImageDatastoreMappingServicePatch.setCompletion(
          (operation, throwable) -> {
            if (operation.getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
              if (throwable != null) {
                failTask(throwable);
                return;
              }
              // If image has been copied to image datastore via ImageCopyService, only adjust seeding count.
              if (current.imageExistsFlag) {
                adjustReplicationCountPatch.setBody(adjustSeedingCountRequest);
              } else {
                adjustReplicationCountPatch.setBody(adjustSeedingAndReplicationCountRequest);
              }
              adjustReplicationCountPatch.setCompletion((op, t) -> {
                if (t != null) {
                  ServiceUtils.logWarning(this,
                      "Could not increment replicatedImageDatastore for image %s by %s: %s",
                      current.image, 1, t);
                }
                ImageService.State createdImage = op.getBody(ImageService.State.class);
                ServiceUtils.logInfo(this, "Image document has been updated with replicatedImageDatastore count: %s," +
                        " ReplciatedDatastore count: %s.", createdImage.replicatedImageDatastore,
                    createdImage.replicatedDatastore);
              });
              sendRequest(adjustReplicationCountPatch);
            }
          });
      sendRequest(createimageToImageDatastoreMappingServicePatch);

      OperationSequence operationSequence = null;
      State s = this.buildPatch(TaskState.TaskStage.FINISHED, null, null);
      Operation progress = Operation
          .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
          .setBody(s);

      if (isEagerCopy) {
        operationSequence = operationSequence
            .create(createImageReplicatorServicePatch)
            .setCompletion((operation, throwable) -> {
              if (throwable != null) {
                failTask(throwable.values().iterator().next());
              }
            });
        if (!current.isSelfProgressionDisabled) {
          // move to next stage
          operationSequence.next(progress);
        }
      } else if (!current.isSelfProgressionDisabled) {
        // move to next stage
        operationSequence = operationSequence.create(progress);
      }

      if (operationSequence != null) {
        operationSequence.sendWith(this);
      }
    } catch (Exception e) {
      failTask(e);
    }
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
    if (LoggingUtils.getRequestId() == null) {
      LoggingUtils.setRequestId(current.documentSelfLink);
    }
    
    return client;
  }

  /**
   * Retrieve hosts that connect to the given source image datastore and destination datastore respectively.
   *
   * @param current
   */
  private void getHostsFromDataStores(final State current) {
    Operation sourceHostOp = this.buildHostQuery(current, current.sourceDatastore);
    Operation destinationHostOp = this.buildHostQuery(current, current.destinationDatastore);

    OperationJoin.JoinedCompletionHandler handler = (Map<Long, Operation> ops, Map<Long, Throwable> failures) -> {
      if (failures != null && !failures.isEmpty()) {
        failTask(failures.values().iterator().next());
        return;
      }

      try {
        NodeGroupBroadcastResponse sourceQueryResponse =
            ops.get(sourceHostOp.getId()).getBody(NodeGroupBroadcastResponse.class);
        List<HostService.State> sourceHosts = QueryTaskUtils
            .getBroadcastQueryDocuments(HostService.State.class, sourceQueryResponse);

        NodeGroupBroadcastResponse destinationQueryResponse =
            ops.get(destinationHostOp.getId()).getBody(NodeGroupBroadcastResponse.class);
        List<HostService.State> destinationHosts = QueryTaskUtils
            .getBroadcastQueryDocuments(HostService.State.class, destinationQueryResponse);

        ServerAddress commonHost = getCommonHost(sourceHosts, destinationHosts);
        if (commonHost == null) {
          String host = getHostFromResponse(sourceHosts);
          if (host == null) {
            failTask(new Exception("No host found for source image " +
                "datastore " + current.sourceDatastore));
            return;
          }
          current.host = host;

          ServerAddress destinationHost = getHostServerAddressFromResponse(destinationHosts);
          if (destinationHost == null) {
            failTask(new Exception("No host found for destination image " +
                "datastore " + current.destinationDatastore));
            return;
          }
          current.destinationHost = destinationHost;
        } else {
          current.host = commonHost.getHost();
          current.destinationHost = commonHost;
        }

        // Patch self with the host and data store information.
        if (!current.isSelfProgressionDisabled) {
          ImageHostToHostCopyService.State patch = buildPatch(com.vmware.xenon.common.TaskState.TaskStage.STARTED,
              TaskState.SubStage.TRANSFER_IMAGE, null);
          patch.host = current.host;
          patch.destinationHost = current.destinationHost;
          this.sendSelfPatch(patch);
        }

      } catch (Throwable e) {
        failTask(e);
      }
    };

    OperationJoin
        .create(sourceHostOp, destinationHostOp)
        .setCompletion(handler)
        .sendWith(this);
  }

  private ServerAddress getCommonHost(List<HostService.State> sourceHosts, List<HostService.State> destinationHosts) {
    List<HostService.State> common = new ArrayList<>(sourceHosts);
    common.retainAll(destinationHosts);
    Set<HostService.State> commonSet = new HashSet<>(common);
    if (!common.isEmpty()) {
      HostService.State state = ServiceUtils.selectRandomItem(commonSet);
      return new ServerAddress(state.hostAddress, state.agentPort);
    } else {
      return null;
    }
  }

  private String getHostFromResponse(List<HostService.State> hosts) {
    Set<String> hostSet = new HashSet<>();
    for (HostService.State state : hosts) {
      hostSet.add(state.hostAddress);
    }

    if (hostSet.size() == 0) {
      return null;
    }

    return ServiceUtils.selectRandomItem(hostSet);
  }

  private ServerAddress getHostServerAddressFromResponse(List<HostService.State> hosts) {
    Set<ServerAddress> hostSet = new HashSet<>();
    for (HostService.State state : hosts) {
      hostSet.add(new ServerAddress(state.hostAddress, state.agentPort));
    }

    if (hostSet.size() == 0) {
      return null;
    }

    return ServiceUtils.selectRandomItem(hostSet);
  }

  /**
   * Build a query for querying image.
   *
   * @param current
   * @return
   */
  private Operation buildImageQuery(final State current) {
    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createGet(ImageServiceFactory.SELF_LINK + "/" + current.image);
  }

  /**
   * Build a query for querying host with access to the image datastore.
   *
   * @param current
   * @return
   */
  private Operation buildHostQuery(final State current, String datastoreId) {
    String reportedImageDatastoreFieldName = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_REPORTED_IMAGE_DATASTORES);

    QueryTask.QuerySpecification querySpecification = buildHostQuerySpec(current, reportedImageDatastoreFieldName,
        datastoreId);

    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  /**
   * Build a QuerySpecification for querying host with access to image data store.
   *
   * @param current
   * @return
   */
  private QueryTask.QuerySpecification buildHostQuerySpec(final State current, String fieldName, String matchValue) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.Query fieldNameClause = new QueryTask.Query()
        .setTermPropertyName(fieldName)
        .setTermMatchValue(matchValue);
    QueryTask.Query stateClause = new QueryTask.Query()
        .setTermPropertyName("state")
        .setTermMatchValue(HostState.READY.toString());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(fieldNameClause);
    querySpecification.query.addBooleanClause(stateClause);
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
      CHECK_IMAGE_TO_IMAGE_DATASTORE_MAPPING_DOC,
      RETRIEVE_HOSTS,
      TRANSFER_IMAGE,
      UPDATE_IMAGE_REPLICATION_DOCUMENT
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
    public String sourceDatastore;

    /**
     * The store where the image will be copied to.
     */
    public String destinationDatastore;

    /**
     * The host connecting to the source image datastore.
     */
    public String host;

    /**
     * The host connecting to the destination image datastore.
     */
    public ServerAddress destinationHost;

    /**
     * The imageExistsFlag indicates if getting DESTINATION_ALREADY_EXISTS from agent.
     */
    @DefaultBoolean(value = false)
    public Boolean imageExistsFlag;

    /**
     * URI of the sender of the copy, if not null notify of copy end.
     */
    public String parentLink;

    /**
     * When isSelfProgressionDisabled is true, the service does not automatically update its stages.
     */
    public boolean isSelfProgressionDisabled;
  }
}
