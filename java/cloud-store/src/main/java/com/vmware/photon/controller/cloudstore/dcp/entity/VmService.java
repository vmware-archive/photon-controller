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
import com.vmware.photon.controller.api.Iso;
import com.vmware.photon.controller.api.LocalitySpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.VmState;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class VmService is used for data persistence of vm information.
 */
public class VmService extends StatefulService {

  public VmService() {
    super(State.class);
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
      ServiceUtils.logSevere(this, t);
      ServiceUtils.failOperationAsBadRequest(startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    try {
      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();
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
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    return ServiceUtils.getDocumentTemplateWithIndexedFields(
        super.getDocumentTemplate(), State.FIELD_NAME_TAGS, State.FIELD_NAME_NETWORKS);
  }

  /**
   * Durable service state data. Class encapsulating the data for Task.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_TAGS = "tags";

    public static final String FIELD_NAME_NETWORKS = "networks";

    /**
     * This property specifies the name of the VM.
     */
    @NotNull
    public String name;

    /**
     * This property specifies the desired flavor of the VM.
     */
    @NotNull
    public String flavorId;

    /**
     * The id of the source image to be used to create VM from.
     */
    @NotNull
    public String imageId;

    /**
     * ids of networks to place vm on.
     */
    public List<String> networks;

    /**
     * Locality parameters provide a hint that may help the placement engine
     * optimize placement of a VM with respect to an independent disk.
     */
    public List<LocalitySpec> affinities;

    /**
     * This property specifies the set of tags to attach to the VM on creation.
     */
    public Set<String> tags;

    /**
     * VM metadata.
     */
    public Map<String, String> metadata;

    /**
     * ISOs attached to the VM.
     */
    public List<Iso> isos;

    /**
     * The id of the project the vm belongs to.
     */
    @NotNull
    public String projectId;

    /**
     * The state of the VM.
     */
    @NotNull
    public VmState vmState;

    /**
     * Id of the agent returned from root scheduler.
     */
    public String agent;

    /**
     * Id of datastore returned from root scheduler.
     */
    public String datastore;

    /**
     * Name of datastore returned form zookeeper.
     */
    public String datastoreName;

    /**
     * IP of the host, the vm is placed on.
     */
    public String host;

    /**
     * The cost associated with the VM.
     */
    public List<QuotaLineItem> cost;
  }
}
