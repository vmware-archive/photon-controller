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

import com.vmware.photon.controller.cloudstore.CloudStoreModule;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.QueryTask;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
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
  public void handlePut(Operation putOperation) {
    ServiceUtils.logInfo(this, "Handling put for service %s", getSelfLink());
    if (!putOperation.hasBody()) {
      putOperation.fail(new IllegalArgumentException("body is required"));
      return;
    }

    State currentState = getState(putOperation);
    State newState = putOperation.getBody(State.class);
    if (ServiceDocument.equals(getDocumentTemplate().documentDescription, currentState, newState)) {
      putOperation.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
      putOperation.complete();
      return;
    }

    try {
      validateState(newState);
      validatePut(currentState, newState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, putOperation, e);
      return;
    }

    setState(putOperation, newState);
    putOperation.complete();
  }

  private void validatePut(State currentState, State newState) {
    checkState(newState.id.equals(currentState.id));
    checkState(newState.name.equals(currentState.name));
    checkState(newState.type.equals(currentState.type));
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
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
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
  @MigrateDuringUpgrade(transformationServicePath = UpgradeUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = DatastoreServiceFactory.SELF_LINK,
      destinationFactoryServicePath = DatastoreServiceFactory.SELF_LINK,
      serviceName = CloudStoreModule.CLOUDSTORE_SERVICE_NAME)
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
    @NotNull
    @DefaultBoolean(value = false)
    public Boolean isImageDatastore;
  }
}
