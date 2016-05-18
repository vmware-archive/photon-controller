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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.cloudstore.CloudStoreModule;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import static com.google.common.base.Preconditions.checkState;

import java.util.List;

/**
 * Class ImageService is used for data persistence of image information.
 */
public class ImageService extends StatefulService {

  public ImageService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public OperationProcessingChain getOperationProcessingChain() {
    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter myRouter = new RequestRouter();
    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            DatastoreCountRequest.class, "kind",
            DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT),
        this::handlePatchAdjustDatastoreReplicationCount, "AdjustReplicationCount");
    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            DatastoreCountRequest.class, "kind",
            DatastoreCountRequest.Kind.ADJUST_SEEDING_AND_REPLICATION_COUNT),
        this::handlePatchAdjustDatastoreReplicationCount, "AdjustImageReplicationCount");
    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            DatastoreCountRequest.class, "kind",
            DatastoreCountRequest.Kind.ADJUST_SEEDING_COUNT),
        this::handlePatchAdjustDatastoreReplicationCount, "AdjustImageSeedingCount");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);

    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting ImageService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);

      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching ImageService %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);

      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);

    if (currentState.totalDatastore != null && currentState.replicatedDatastore != null) {
      checkState(
          currentState.replicatedDatastore <= currentState.totalDatastore,
          "Replicated datastore count exceeds total datastore count.");
    }

    if (currentState.totalDatastore != null && currentState.replicatedImageDatastore != null) {
      checkState(
          currentState.replicatedImageDatastore <= currentState.totalDatastore,
          "Replicated image datastore count exceeds total datastore count.");
      checkState(
          currentState.replicatedImageDatastore <= currentState.totalImageDatastore,
          "Replicated image datastore count exceeds total image datastore count.");
    }

    if (currentState.replicatedDatastore != null) {
      checkState(currentState.replicatedDatastore >= 0,
          "Replicated datastore count cannot be less than '0'.");
    }

    if (currentState.replicatedImageDatastore != null) {
      checkState(currentState.replicatedImageDatastore >= 0,
          "Replicated image datastore count cannot be less than '0'.");
    }
  }

  private void handlePatchAdjustDatastoreReplicationCount(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      DatastoreCountRequest patchState = patchOperation.getBody(DatastoreCountRequest.class);

      switch (patchState.kind) {
        case ADJUST_SEEDING_AND_REPLICATION_COUNT:
          currentState.replicatedImageDatastore += patchState.amount;
          currentState.replicatedDatastore += patchState.amount;
          break;
        case ADJUST_REPLICATION_COUNT:
          currentState.replicatedDatastore += patchState.amount;
          break;
        case ADJUST_SEEDING_COUNT:
          currentState.replicatedImageDatastore += patchState.amount;
          break;
      }
      validateState(currentState);

      setState(patchOperation, currentState);
      patchOperation.setBody(currentState);
      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  /**
   * The request to increment or decrement datastore fields.
   */
  public static class DatastoreCountRequest {

    /**
     * Indicating incrementing or decrementing fields.
     */
    public enum Kind {
      ADJUST_REPLICATION_COUNT,
      ADJUST_SEEDING_COUNT,
      ADJUST_SEEDING_AND_REPLICATION_COUNT
    }

    public Kind kind;
    public int amount;
  }

  /**
   * Durable service state data. Class encapsulating the data for image.
   */
  @MigrateDuringUpgrade(transformationServicePath = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = ImageServiceFactory.SELF_LINK,
      destinationFactoryServicePath = ImageServiceFactory.SELF_LINK,
      serviceName = CloudStoreModule.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = ImageServiceFactory.SELF_LINK,
      serviceName = CloudStoreModule.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    @NotNull
    @Immutable
    public String name;

    @NotNull
    @Immutable
    public ImageReplicationType replicationType;

    @NotNull
    public ImageState state;

    public Long size;

    public List<ImageSetting> imageSettings;

    @NotNull
    @DefaultInteger(value = 0)
    public Integer totalImageDatastore;

    @NotNull
    @DefaultInteger(value = 0)
    public Integer totalDatastore;

    @NotNull
    @DefaultInteger(value = 0)
    public Integer replicatedDatastore;

    @NotNull
    @DefaultInteger(value = 0)
    public Integer replicatedImageDatastore;

    /**
     * Data object for additional image configuration settings.
     */
    public static class ImageSetting {
      public String name;
      public String defaultValue;
    }
  }
}
