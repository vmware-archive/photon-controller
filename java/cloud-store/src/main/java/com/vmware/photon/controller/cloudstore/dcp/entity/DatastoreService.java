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
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Set;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing a datastore.
 */
public class DatastoreService extends StatefulService {

  public static final String TAGS_KEY =
      QueryTask.QuerySpecification.buildCollectionItemName(DatastoreService.State.FIELD_NAME_TAGS);

  public DatastoreService() {
    super(State.class);
    super.toggleOption(ServiceOption.EAGER_CONSISTENCY, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
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
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      validateState(currentState);
      State patchState = patchOperation.getBody(State.class);
      validatePatchState(currentState, patchState);
      applyPatch(currentState, patchState);
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
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument template = super.getDocumentTemplate();
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_TAGS);
    return template;
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateEntitySelfLink(this, currentState.id);
  }

  private void validatePatchState(State startState, State patchState) {
    checkNotNull(patchState, "patch can not be null");
    ValidationUtils.validatePatch(startState, patchState);
  }

  private void applyPatch(State currentState, State patchState) {
    PatchUtils.patchState(currentState, patchState);
  }

  /**
   * This class defines the document state associated with a single
   * {@link DatastoreService} instance.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_TAGS = "tags";
    public static final String FIELD_NAME_ID = "id";
    public static final String FIELD_NAME_TYPE = "type";

    /**
     * This value represents the hardware id, a.k.a the real name, of the datastore.
     */
    @NotNull
    @Immutable
    public String id;

    /**
     * This value represents the name of the datastore.
     */
    @NotNull
    @Immutable
    public String name;

    /**
     * This value represents the type of the datastore.
     */
    @NotNull
    @Immutable
    public String type;

    /**
     * This value represents the usage tag associated with the datastore.
     */
    public Set<String> tags;

    /**
     * Indicates whether this datastore is used as a image datastore. Chairman
     * sets this flag to true when it receives a register request from an agent
     * that specifies this datastore as the image datastore.
     * <p>
     * Note that chairman does not reset this field to false even when all the
     * agents that use this datastore as the image datastore un-register. If you
     * need to keep this field up-to-date, it must be cleaned up by a background
     * job..
     */
    public boolean isImageDatastore;
  }
}
