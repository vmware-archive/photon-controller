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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationProcessingChain;
import com.vmware.dcp.common.RequestRouter;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

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
        new RequestRouter.RequestBodyMatcher<DatastoreCountRequest>(
            DatastoreCountRequest.class, "kind",
            DatastoreCountRequest.Kind.ADJUST_REPLICATION_COUNT),
        this::handlePatchAdjustDatastoreReplicationCount, "AdjustReplicationCount");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);

    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
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
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
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

    if (currentState.replicatedDatastore != null) {
      checkState(currentState.replicatedDatastore >= 0,
          "Replicated datastore count cannot be less than '0'.");
    }
  }

  private void handlePatchAdjustDatastoreReplicationCount(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patch);
      DatastoreCountRequest patchState = patch.getBody(DatastoreCountRequest.class);

      currentState.replicatedDatastore += patchState.amount;
      validateState(currentState);

      setState(patch, currentState);
      patch.setBody(currentState);
      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
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
      ADJUST_REPLICATION_COUNT
    }

    public Kind kind;
    public int amount;
  }

  /**
   * Durable service state data. Class encapsulating the data for image.
   */
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

    /**
     * Data object for additional image configuration settings.
     */
    public static class ImageSetting {
      public String name;
      public String defaultValue;
    }
  }
}
