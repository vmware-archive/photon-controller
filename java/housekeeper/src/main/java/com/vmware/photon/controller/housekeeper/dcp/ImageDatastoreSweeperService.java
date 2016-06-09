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


import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.clients.exceptions.OperationInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.host.gen.GetDeletedImagesResponse;
import com.vmware.photon.controller.host.gen.GetInactiveImagesResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.StartImageScanResponse;
import com.vmware.photon.controller.host.gen.StartImageSweepResponse;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Class scans a datastore for unused images and deletes them.
 */
public class ImageDatastoreSweeperService extends StatefulService {

  /**
   * Default value for the interval between polling calls to the host in milliseconds.
   */
  @VisibleForTesting
  protected static final int DEFAULT_HOST_POLL_INTERVAL = 30 * 1000;

  /**
   * Default constructor.
   */
  public ImageDatastoreSweeperService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    State s = start.getBody(State.class);
    initializeState(s);
    validateState(s);
    start.setBody(s).complete();

    processStart(s);
  }

  @Override
  public void handlePatch(Operation patch) {
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);

    validatePatch(currentState, patchState);
    applyPatch(currentState, patchState);
    validateState(currentState);
    patch.complete();

    processPatch(currentState);
  }

  /**
   * Retrieves the host client from the host.
   *
   * @return
   */
  @VisibleForTesting
  protected HostClient getHostClient() {
    return ((HostClientProvider) getHost()).getHostClient();
  }

  /**
   * Retrieves the CloudStoreHelper from the host.
   *
   * @return
   */
  @VisibleForTesting
  protected CloudStoreHelper getCloudStoreHelper() {
    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper();
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(State current) {
    InitializationUtils.initialize(current);

    if (current.taskState.stage == TaskState.TaskStage.CREATED) {
      current.taskState.stage = TaskState.TaskStage.STARTED;
      current.taskState.subStage = TaskState.SubStage.GET_HOST_INFO;
    }

    if (current.taskState.stage == TaskState.TaskStage.STARTED &&
        current.taskState.subStage == null) {
      current.taskState.subStage = TaskState.SubStage.GET_HOST_INFO;
    }

    if (current.documentExpirationTimeMicros <= 0) {
      current.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    if (null == current.scanTimeout) {
      current.scanTimeout = TimeUnit.MICROSECONDS.toSeconds(current.documentExpirationTimeMicros);
    }

    if (null == current.sweepTimeout) {
      current.sweepTimeout = TimeUnit.MICROSECONDS.toSeconds(current.documentExpirationTimeMicros);
    }
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);

    if (current.host != null) {
      checkState(StringUtils.isNotBlank(current.host), "host cannot be blank");
    }

    ValidationUtils.validateTaskStage(current.taskState);
    switch (current.taskState.stage) {
      case STARTED:
        checkState(current.taskState.subStage != null, "Invalid subStage " +
            Utils.toJson(false, false, current.taskState));
        switch (current.taskState.subStage) {
          case TRIGGER_SCAN:
            checkNotNull(current.host, "host cannot be null");
        }
        break;

      default:
        // only STARTED can have sub stages
        checkState(current.taskState.subStage == null, "Invalid subStage " +
            Utils.toJson(false, false, current.taskState));
    }
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

    if (null != current.taskState.subStage && null != patch.taskState.subStage) {
      checkState(patch.taskState.subStage.ordinal() >= current.taskState.subStage.ordinal());
    }
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private State applyPatch(State current, State patch) {
    if (patch.taskState.stage != current.taskState.stage
        || patch.taskState.subStage != current.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving from %s:%s to stage %s:%s",
          current.taskState.stage, current.taskState.subStage,
          patch.taskState.stage, patch.taskState.subStage);
    }

    PatchUtils.patchState(current, patch);
    return current;
  }

  /**
   * Does any additional processing after the start operation has been completed.
   *
   * @param current
   */
  private void processStart(final State current) {
    try {
      if (!isFinalStage(current)) {
        sendStageProgressPatch(current, current.taskState.stage, current.taskState.subStage);
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
          this.handleStartedStage(current);
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
   * Process patch requests when service is in STARTED stage.
   *
   * @param current
   */
  private void handleStartedStage(final State current) throws RpcException {
    switch (current.taskState.subStage) {
      case GET_HOST_INFO:
        this.getHostInfo(current);
        break;

      case TRIGGER_SCAN:
        this.triggerImageScan(current);
        break;

      case WAIT_FOR_SCAN_COMPLETION:
        this.waitForImageScanCompletion(current);
        break;

      case TRIGGER_DELETE:
        this.triggerImageDelete(current);
        break;

      case WAIT_FOR_DELETE_COMPLETION:
        this.waitForImageDeleteCompletion(current);
        break;

      default:
        this.failTask(
            new RuntimeException(
                String.format("Un-expected stage: %s", current.taskState.stage))
        );
    }
  }

  /**
   * Retrieves the host information for the datastore.
   *
   * @param current
   */
  private void getHostInfo(final State current) {
    try {
      Operation queryHostSet = buildHostQuery(current.datastore, current.isImageDatastore);

      OperationSequence.create(queryHostSet)
          .setCompletion((operations, throwable) -> {
                if (throwable != null) {
                  failTask(throwable.values().iterator().next());
                  return;
                }

                Operation op = operations.get(queryHostSet.getId());
                NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
                ServiceUtils.logInfo(this, "Host query result: %s", Utils.toJson(true, true, queryResponse));
                List<HostService.State> documentLinks = QueryTaskUtils
                    .getBroadcastQueryDocuments(HostService.State.class, queryResponse);

                Set<String> hostSet = new HashSet<>();
                documentLinks.forEach(state -> hostSet.add(state.hostAddress));

                if (hostSet.size() == 0) {
                  failTask(
                      new IllegalArgumentException(
                          String.format("Could not find any hosts for datastore %s.", current.datastore)));
                }
                ServiceUtils.logInfo(this, "GetHostsForDatastore '%s' returned '%s'", current.datastore,
                    Utils.toJson(false, false, hostSet));

                if (current.isSelfProgressionDisabled) {
                  // not sending patch to move to next stage
                  return;
                }

                State patch = this.buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.TRIGGER_SCAN, null);
                patch.host = ServiceUtils.selectRandomItem(hostSet);
                this.sendSelfPatch(patch);
              }
          ).sendWith(this);
    } catch (Exception e) {
      failTask(e);
    }
  }

  /**
   * Triggers an scan for un-used images for the datastore on the selected host.
   *
   * @param current
   */
  private void triggerImageScan(final State current) throws RpcException {
    final AsyncMethodCallback<Host.AsyncClient.start_image_scan_call> callback =
        new AsyncMethodCallback<Host.AsyncClient.start_image_scan_call>() {
          @Override
          public void onComplete(Host.AsyncClient.start_image_scan_call call) {
            try {
              StartImageScanResponse response = call.getResult();
              ServiceUtils.logInfo(ImageDatastoreSweeperService.this, "Received: %s", response);
              HostClient.ResponseValidator.checkStartImageScanResponse(response);

              sendStageProgressPatch(
                  current, TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_SCAN_COMPLETION);
            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    HostClient hostClient = getHostClient();
    hostClient.setHostIp(current.host);
    hostClient.startImageScan(
        current.datastore, current.scanRate, current.scanTimeout, callback);
  }

  /**
   * Polls the host to await the image scan completion.
   *
   * @param current
   */
  private void waitForImageScanCompletion(final State current) throws RpcException {
    final AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call> callback =
        new AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call>() {
          @Override
          public void onComplete(Host.AsyncClient.get_inactive_images_call call) {
            try {

              GetInactiveImagesResponse response = call.getResult();
              ServiceUtils.logInfo(ImageDatastoreSweeperService.this, "Received: %s", response);
              HostClient.ResponseValidator.checkGetInactiveImagesResponse(response);

              if (current.isSelfProgressionDisabled) {
                return;
              }

              State patch = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.TRIGGER_DELETE, null);
              patch.inactiveImagesCount = response.getImage_descsSize();
              sendSelfPatch(patch);

            } catch (OperationInProgressException e) {

              // schedule the next poll
              getHost().schedule(() -> {
                try {
                  waitForImageScanCompletion(current);
                } catch (RpcException ex) {
                  onError(ex);
                }
              }, current.hostPollIntervalMilliSeconds, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    HostClient hostClient = getHostClient();
    hostClient.setHostIp(current.host);
    hostClient.getInactiveImages(current.datastore, callback);
  }

  /**
   * Evaluates what images should be deleted and calls agent to kick off the deletion.
   *
   * @param current
   * @throws RpcException
   */
  private void triggerImageDelete(final State current) throws RpcException {
    final AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call> callback =
        new AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call>() {
          @Override
          public void onComplete(Host.AsyncClient.get_inactive_images_call call) {
            try {

              GetInactiveImagesResponse response = call.getResult();
              ServiceUtils.logInfo(ImageDatastoreSweeperService.this, "Received: %s", response);
              HostClient.ResponseValidator.checkGetInactiveImagesResponse(response);

              if (!response.isSetImage_descs()) {
                // no inactive images - we can go straight to FINISHED
                if (current.isSelfProgressionDisabled) {
                  return;
                }

                State patch = buildPatch(TaskState.TaskStage.FINISHED, null, null);
                patch.deletedImagesCount = 0;
                sendSelfPatch(patch);

                return;
              }

              // retrieve the list of images found from cloud store
              fetchReferenceImages(current, response.getImage_descs());

            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    HostClient hostClient = getHostClient();
    hostClient.setHostIp(current.host);
    hostClient.getInactiveImages(current.datastore, callback);
  }

  /**
   * Polls the host to await the image scan completion.
   *
   * @param current
   */
  private void waitForImageDeleteCompletion(final State current) throws RpcException {
    final AsyncMethodCallback<Host.AsyncClient.get_deleted_images_call> callback =
        new AsyncMethodCallback<Host.AsyncClient.get_deleted_images_call>() {
          @Override
          public void onComplete(Host.AsyncClient.get_deleted_images_call call) {
            try {

              GetDeletedImagesResponse response = call.getResult();
              ServiceUtils.logInfo(ImageDatastoreSweeperService.this, "Received: %s", response);
              HostClient.ResponseValidator.checkGetDeletedImagesResponse(response);

              for (InactiveImageDescriptor descriptor : response.getImage_descs()) {
                updateReplicatedDatastoreCount(current, descriptor.getImage_id(),
                    (operation, throwable) -> {
                      if (throwable != null) {
                        logWarning("Image update replicated datastore count failed for image %s.",
                            descriptor.getImage_id());
                      }
                    }
                );
                if (current.isImageDatastore) {
                  deleteImageToImageDatastoreMapping(current, descriptor.getImage_id(),
                      (operation, throwable) -> {
                        if (throwable != null) {
                          logWarning(" Deleting ImageToImageDatastoreMappingService failed for image %s, " +
                                  "image datastore %s.",
                              descriptor.getImage_id(), current.datastore);
                        }
                      });
                }
              }

              if (current.isSelfProgressionDisabled) {
                return;
              }

              State patch = buildPatch(TaskState.TaskStage.FINISHED, null, null);
              patch.deletedImagesCount = response.getImage_descsSize();
              sendSelfPatch(patch);

            } catch (OperationInProgressException e) {

              // schedule the next poll
              getHost().schedule(() -> {
                try {
                  waitForImageDeleteCompletion(current);
                } catch (RpcException ex) {
                  onError(ex);
                }
              }, current.hostPollIntervalMilliSeconds, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    HostClient hostClient = getHostClient();
    hostClient.setHostIp(current.host);
    hostClient.getDeletedImages(current.datastore, callback);
  }

  /**
   * Retrieves the list of reference images from cloud store.
   *
   * @param current
   * @param inactiveImages
   */
  private void fetchReferenceImages(final State current, final List<InactiveImageDescriptor> inactiveImages) {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(
        ImageService.State.class, termsBuilder.build());
    spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    sendRequest(
        getCloudStoreHelper()
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(spec).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (failure != null) {
                    failTask(failure);
                    return;
                  }
                  NodeGroupBroadcastResponse queryResponse = completedOp.getBody(NodeGroupBroadcastResponse.class);
                  List<ImageService.State> documents = QueryTaskUtils.getBroadcastQueryDocuments(
                      ImageService.State.class, queryResponse);
                  Map<String, ImageService.State> imageMap = new HashMap<>();

                  for (ImageService.State image : documents) {
                    imageMap.put(ServiceUtils.getIDFromDocumentSelfLink(image.documentSelfLink), image);
                  }

                  try {
                    startImageDelete(current, inactiveImages, imageMap);
                  } catch (Exception e) {
                    failTask(e);
                  }
                }
            ));
  }

  /**
   * Determines what images should be deleted and calls agent to delete them.
   *
   * @param current
   * @param inactiveImages
   * @param referenceImages
   */
  private void startImageDelete(final State current,
                                final List<InactiveImageDescriptor> inactiveImages,
                                final Map<String, ImageService.State> referenceImages) throws RpcException {
    final AsyncMethodCallback<Host.AsyncClient.start_image_sweep_call> callback =
        new AsyncMethodCallback<Host.AsyncClient.start_image_sweep_call>() {
          @Override
          public void onComplete(Host.AsyncClient.start_image_sweep_call call) {
            try {

              StartImageSweepResponse response = call.getResult();
              ServiceUtils.logInfo(ImageDatastoreSweeperService.this, "Received: %s", response);
              HostClient.ResponseValidator.checkStartImageSweepResponse(response);

              sendStageProgressPatch(
                  current, TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_DELETE_COMPLETION);

            } catch (Exception e) {
              onError(e);
            }
          }

          @Override
          public void onError(Exception e) {
            failTask(e);
          }
        };

    HostClient hostClient = getHostClient();
    hostClient.setHostIp(current.host);
    hostClient.startImageSweep(
        current.datastore,
        this.filterInactiveImages(current, inactiveImages, referenceImages),
        current.sweepRate,
        current.sweepTimeout,
        callback);
  }

  /**
   * Update replicatedDatastore in ImageService within Cloudstore.
   *
   * @param imageId
   * @param completionHandler
   */
  private void updateReplicatedDatastoreCount(final State current, String imageId,
                                              Operation.CompletionHandler completionHandler) {
    ImageService.DatastoreCountRequest datastoreCountRequest = new ImageService.DatastoreCountRequest();
    datastoreCountRequest.amount = -1;
    if (current.isImageDatastore) {
      datastoreCountRequest.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_SEEDING_AND_REPLICATION_COUNT;
    } else {
      datastoreCountRequest.kind = ImageService.DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT;
    }

    sendRequest(
        getCloudStoreHelper()
            .createPatch(ServiceUriPaths.CLOUDSTORE_ROOT + "/images/" + imageId)
            .setBody(datastoreCountRequest)
            .setCompletion(completionHandler));
  }

  /**
   * Delete the corresponding ImageToImageDatastoreMappingService Cloudstore.
   *
   * @param current
   * @param imageId
   * @param completionHandler
   */
  private void deleteImageToImageDatastoreMapping(final State current, String imageId,
                                                  Operation.CompletionHandler completionHandler) {
    sendRequest(
        getCloudStoreHelper()
            .createDelete(ServiceUriPaths.CLOUDSTORE_ROOT + "/images-to-image-datastore-mapping/" + imageId + "_" +
                current.datastore)
            .setBody("{}")
            .setCompletion(completionHandler));
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
   * Filters the list of inactive images down to the list of images to be deleted.
   *
   * @return
   */
  private List<InactiveImageDescriptor> filterInactiveImages(final State current,
                                                             final List<InactiveImageDescriptor> inactiveImages,
                                                             final Map<String, ImageService.State> referenceImages) {
    List<InactiveImageDescriptor> imagesToDelete = new LinkedList<>();
    for (InactiveImageDescriptor image : inactiveImages) {
      ImageService.State referenceImage = referenceImages.get(image.getImage_id());
      ServiceUtils.logInfo(this, Utils.toJson(false, false, referenceImage));

      if (image.getTimestamp() > current.imageCreateWatermarkTime ||
          image.getTimestamp() > current.imageDeleteWatermarkTime) {
        // we only want to delete images that have been not used for a
        // period longer than the watermark times
        continue;
      }

      if (null != referenceImage) {
        // cloud store has the image reference

        if (referenceImage.state == ImageState.PENDING_DELETE) {
          // if image is tombstoned then we delete the un-used image right away
          imagesToDelete.add(image);
          continue;
        }

        if (referenceImage.replicationType == ImageReplicationType.EAGER || current.isImageDatastore) {
          // we do not delete unused images:
          // a) of replication type EAGER
          // b) stored on image datastore
          continue;
        }

      }

      // image is:
      //  - ON_DEMAND and not used on a regular datastore
      imagesToDelete.add(image);
    }

    return imagesToDelete;
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
   * @param stage
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage, TaskState.SubStage substage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    this.sendSelfPatch(buildPatch(stage, substage, null));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param state
   */
  private void sendSelfPatch(State state) {
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(state);
    sendRequest(patch);
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, TaskState.SubStage substage, Throwable e) {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = stage;
    s.taskState.subStage = substage;

    if (e != null) {
      s.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Build a QuerySpecification for querying hosts with access to datastore.
   *
   * @param dataStore
   * @param isImageDatastore
   * @return
   */
  private Operation buildHostQuery(final String dataStore, final boolean isImageDatastore) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    String fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
        HostService.State.FIELD_NAME_REPORTED_DATASTORES);

    if (isImageDatastore) {
      fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
          HostService.State.FIELD_NAME_REPORTED_IMAGE_DATASTORES);
    }

    QueryTask.Query datastoreClause = new QueryTask.Query()
        .setTermPropertyName(fieldName)
        .setTermMatchValue(dataStore);

    QueryTask.Query stateClause = new QueryTask.Query()
        .setTermPropertyName("state")
        .setTermMatchValue(HostState.READY.toString());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(datastoreClause);
    querySpecification.query.addBooleanClause(stateClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
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
    public enum SubStage {
      GET_HOST_INFO,
      TRIGGER_SCAN,
      WAIT_FOR_SCAN_COMPLETION,
      TRIGGER_DELETE,
      WAIT_FOR_DELETE_COMPLETION
    }
  }

  /**
   * Class encapsulating the state of the service.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    @DefaultBoolean(value = false)
    public Boolean isSelfProgressionDisabled;

    /**
     * The rate at which to scan datastore for unused images.
     */
    @Positive
    @Immutable
    public Long scanRate;

    /**
     * The timeout for the unused images scan.
     */
    @Positive
    @Immutable
    public Long scanTimeout;

    /**
     * The rate at which to delete images from a datastore.
     */
    @Positive
    @Immutable
    public Long sweepRate;

    /**
     * The timeout for the delete images operation.
     */
    @Positive
    @Immutable
    public Long sweepTimeout;

    /**
     * The time interval to poll agent for status of operations.
     */
    @DefaultInteger(value = DEFAULT_HOST_POLL_INTERVAL)
    @Positive
    public Integer hostPollIntervalMilliSeconds;

    /**
     * Self-link for the service that triggered this service.
     */
    @Immutable
    public String parentLink;

    /**
     * The timestamp indicating when the reference images were retrieved.
     */
    @NotNull
    @Positive
    @Immutable
    public Long imageCreateWatermarkTime;

    /**
     * The timestamp indicating how long images need to be found as un-used before we should delete them
     * from the local datastore.
     */
    @NotNull
    @Positive
    @Immutable
    public Long imageDeleteWatermarkTime;

    /**
     * The datastore id corresponding to dataStoreInventoryName.
     */
    @NotBlank
    @Immutable
    public String datastore;

    /**
     * Flag indicating if the datastore being processed is the image datastore.
     */
    @DefaultBoolean(value = false)
    public Boolean isImageDatastore;

    /**
     * IP address of host having access to datastore.
     */
    public String host;

    /**
     * Count of inactive images found on the datastore.
     */
    public Integer inactiveImagesCount;

    /**
     * Count of deleted images.
     */
    public Integer deletedImagesCount;
  }
}
