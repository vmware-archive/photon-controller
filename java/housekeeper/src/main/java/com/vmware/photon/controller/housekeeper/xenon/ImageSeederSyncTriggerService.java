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

import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class ImageSeederSyncTriggerService: periodically starts a new ImageSeederService if there isn't a currently
 * running one.
 */
public class ImageSeederSyncTriggerService extends StatefulService {
  private static final long OWNER_SELECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
  private static final long DEFAULT_TRIGGER_INTERVAL = TimeUnit.HOURS.toMicros(1);
  private static final long EXPIRATION_TIME_MULTIPLIER = 5;

  /**
   * Default constructor.
   */
  public ImageSeederSyncTriggerService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.setMaintenanceIntervalMicros(DEFAULT_TRIGGER_INTERVAL);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    // Initialize the task stage
    State state = start.getBody(State.class);
    if (state.triggersSuccess == null) {
      state.triggersSuccess = 0L;
    }
    if (state.triggersError == null) {
      state.triggersError = 0L;
    }
    if (state.shouldTriggerTasks != null) {
      state.shouldTriggerTasks = null;
    }

    try {
      validateState(state);
      start.setBody(state).complete();
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(start)) {
        start.fail(e);
      }
    }
  }

  /**
   * Handle service patch.
   */
  @Override
  public void handlePatch(Operation patch) {
    try {
      State currentState = getState(patch);
      State patchState = patch.getBody(State.class);

      this.applyPatch(currentState, patchState);
      this.validateState(currentState);

      // apply/persist the patch
      patch.complete();

      if (patchState.shouldTriggerTasks == null || !patchState.shouldTriggerTasks) {
        return;
      }
      // do post processing on the patch
      triggerTasks(patch, currentState, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(e);
      }
    }
  }

  /**
   * Checks if service's background processing is in pause state.
   */
  private boolean isBackgroundPaused() {
    ServiceConfig serviceConfig = ((ServiceConfigProvider) getHost()).getServiceConfig();
    boolean backgroundPaused = true;
    try {
      backgroundPaused = serviceConfig.isBackgroundPaused();
    } catch (Exception ex) {
      ServiceUtils.logSevere(this, ex);
    }
    return backgroundPaused;
  }

  /**
   * Handle service periodic maintenance calls.
   */
  @Override
  public void handleMaintenance(Operation post) {
    post.complete();

    if (isBackgroundPaused()) {
      return;
    }

    Operation.CompletionHandler handler = (op, failure) -> {
      if (null != failure) {
        // query failed so abort and retry next time
        logFailure(failure);
        return;
      }

      NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
      if (!getHost().getId().equals(rsp.ownerNodeId)) {
        ServiceUtils.logInfo(ImageSeederSyncTriggerService.this,
            "Host[%s]: Not owner of scheduler [%s] (Owner Info [%s])",
            getHost().getId(), getSelfLink(), Utils.toJson(true, false, rsp));
        return;
      }

      State state = new State();
      state.shouldTriggerTasks = true;
      sendSelfPatch(state);
    };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(OWNER_SELECTION_TIMEOUT))
        .setCompletion(handler);
    getHost().selectOwner(null, getSelfLink(), selectOwnerOp);
  }

  /**
   * Process patch.
   */
  private void triggerTasks(Operation patch, final State currentState, final State patchState) {
    sendRequest(buildGetAllImagesQuery()
        .setCompletion(
            (op, t) -> {
              if (t != null) {
                logFailure(t);
                return;
              }
              NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
              List<ImageService.State> documentLinks = QueryTaskUtils
                  .getBroadcastQueryDocuments(ImageService.State.class, queryResponse);
              for (ImageService.State image : documentLinks) {
                triggerImageSeederServices(currentState,
                    ServiceUtils.getIDFromDocumentSelfLink(image.documentSelfLink));
              }
            }
        ));
  }

  /**
   * Validate the service state for coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    checkIsPositiveNumber(current.triggersSuccess, "triggersSuccess");
    checkIsPositiveNumber(current.triggersError, "triggersError");
  }

  /**
   * Applies patch to current document state.
   *
   * @param current
   * @param patch
   */
  protected void applyPatch(State current, State patch) {
    current.triggersSuccess = updateLongWithMax(current.triggersSuccess, patch.triggersSuccess);
    current.triggersError = updateLongWithMax(current.triggersError, patch.triggersError);
  }

  private void triggerImageSeederServices(State current, String imageId) {
    sendRequest(
        buildImageToImageDatstoreQuery(imageId)
            .setCompletion(
                (op, t) -> {
                  if (t != null) {
                    logFailure(t);
                    return;
                  }
                  NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
                  List<ImageToImageDatastoreMappingService.State> documentLinks = QueryTaskUtils
                      .getBroadcastQueryDocuments(ImageToImageDatastoreMappingService.State.class, queryResponse);
                  if (documentLinks.isEmpty()) {
                    logFailure(new IllegalArgumentException("No Image Datastore has image " + imageId));
                    return;
                  }

                  triggerImageSeederService(current, imageId, documentLinks.get(0).imageDatastoreId);
                }));
  }

  private void triggerImageSeederService(State currentState, String imageId, String datastoreId) {
    // Trigger seeder service.
    Operation.CompletionHandler handler = (operation, throwable) -> {
      // Note this is a race with maintenance calls. Some statistics may be lost.
      State newState = new State();
      if (throwable == null) {
        newState.triggersSuccess = currentState.triggersSuccess + 1;
      } else {
        ServiceUtils.logSevere(ImageSeederSyncTriggerService.this, throwable);
        newState.triggersError = currentState.triggersError + 1;
      }
      //update stats only without setting the trigger tasks flag.
      sendSelfPatch(newState);
    };

    ImageSeederService.State postState = new ImageSeederService.State();
    postState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
        TimeUnit.MICROSECONDS.toMillis(EXPIRATION_TIME_MULTIPLIER * this.getMaintenanceIntervalMicros()));
    postState.image = imageId;
    postState.sourceImageDatastore = datastoreId;

    Operation createImageOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ImageSeederServiceFactory.SELF_LINK))
        .setBody(postState)
        .setCompletion(handler);

    this.sendRequest(createImageOperation);
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private void checkIsPositiveNumber(Long value, String description) {
    checkNotNull(value == null, description + " cannot be null.");
    checkState(value >= 0, description + " cannot be negative.");
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private Long updateLongWithMax(Long previousValue, Long newValue) {
    if (newValue == null) {
      return previousValue;
    }
    if (newValue < 0) {
      return 0L;
    }
    return Math.max(previousValue, newValue);
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
   * Log failed query.
   *
   * @param e
   */
  private void logFailure(Throwable e) {
    ServiceUtils.logSevere(this, e);
  }

  /**
   * Build a QuerySpecification for querying all images.
   *
   * @return
   */
  private Operation buildGetAllImagesQuery() {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ImageService.State.class));

    QueryTask.Query stateClause = new QueryTask.Query()
        .setTermPropertyName("state")
        .setTermMatchValue(ImageState.READY.toString());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(stateClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  /**
   * Build a QuerySpecification for querying ImageToImageDatastoreMappingService.
   *
   * @return
   */
  private Operation buildImageToImageDatstoreQuery(String imageId) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ImageToImageDatastoreMappingService.State.class));
    QueryTask.Query imageIdClause = new QueryTask.Query()
        .setTermPropertyName("imageId")
        .setTermMatchValue(imageId);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(imageIdClause);
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    return ((CloudStoreHelperProvider) getHost()).getCloudStoreHelper()
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
        .setBody(QueryTask.create(querySpecification).setDirect(true));
  }

  /**
   * Class defines the durable state of the ImageRemoverService.
   */
  public static class State extends ServiceDocument {

    // Patches to this field are never applied/persisted.
    // This field is only used to determine if the patch is being made to trigger services or update the other state
    // fields.
    public Boolean shouldTriggerTasks;

    public Long triggersSuccess;
    public Long triggersError;
  }
}
