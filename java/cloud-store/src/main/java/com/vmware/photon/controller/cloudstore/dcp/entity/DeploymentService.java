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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultBoolean;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a DCP micro-service which provides a plain data object
 * representing deployment metadata.
 */
public class DeploymentService extends StatefulService {

  public DeploymentService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
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
        new RequestRouter.RequestBodyMatcher<HostListChangeRequest>(
            HostListChangeRequest.class, "kind",
            HostListChangeRequest.PatchType.UPDATE_ZOOKEEPER_INFO),
        this::handlePatchUpdateHostListInfo, "UpdateHostListInfo");

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
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    try {
      State startState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);
      validatePatchState(startState, patchState);
      State currentState = applyPatch(startState, patchState);
      validateState(currentState);
      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  private void handlePatchUpdateHostListInfo(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      HostListChangeRequest patchState = patchOperation.getBody(HostListChangeRequest.class);

      // Implement this with the next change

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

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument template = super.getDocumentTemplate();
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_IMAGE_DATA_STORE_NAMES);
    return template;
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
  }

  private State applyPatch(State startState, State patchState) {
    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * The request to change host list related fields.
   */
  public static class HostListChangeRequest {
    public PatchType patchtype = PatchType.NONE;

    public String zookeeperIpToAdd;

    public String zookeeperIpToRemove;

    /**
     * Defines the purpose of the patch.
     */
    public enum PatchType {
      NONE,
      UPDATE_ZOOKEEPER_INFO,
    }
  }

  /**
   * This class defines the document state associated with a single {@link DeploymentService} instance.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_IMAGE_DATA_STORE_NAMES = "imageDataStoreNames";

    /**
     * This value represents the current state of the Deployment.
     */
    @NotNull
    public DeploymentState state;

    /**
     * This value represents the list of security groups to be configured for this deployment.
     */
    public List<String> oAuthSecurityGroups;

    /**
     * This value represents the name of the image data store.
     */
    @NotNull
    public Set<String> imageDataStoreNames;

    /**
     * This value represents whether the image data store can be used to create VMs.
     */
    @NotNull
    @Immutable
    public Boolean imageDataStoreUsedForVMs;

    /**
     * Id of image entity in cloudstore.
     */
    @WriteOnce
    public String imageId;

    /**
     * Id of project created for this deployment.
     */
    @WriteOnce
    public String projectId;

    /**
     * This value represents the NTP endpoint for the deployment.
     */
    @Immutable
    public String ntpEndpoint;

    /**
     * This value represents whether authflow is to be enabled.
     */
    @Immutable
    @DefaultBoolean(value = false)
    public Boolean oAuthEnabled;

    /**
     * The tenant name on lotus.
     */
    @Immutable
    public String oAuthTenantName;

    /**
     * Lotus user name.
     */
    @Immutable
    public String oAuthUserName;

    /**
     * Password for the given lotus user.
     */
    @Immutable
    public String oAuthPassword;

    /**
     * This value represents the OAuth server address.
     */
    public String oAuthServerAddress;

    /**
     * This value represents the OAuth server port.
     */
    public Integer oAuthServerPort;

    /**
     * Endpoint to the lotus authentication service.
     */
    @WriteOnce
    public String oAuthResourceLoginEndpoint;

    /**
     * Endpoint to the lotus logout service.
     */
    @WriteOnce
    public String oAuthLogoutEndpoint;

    /**
     * This value represents the syslog endpoint for the deployment.
     */
    @Immutable
    public String syslogEndpoint;

    /**
     * This value represents the list of chairman servers.
     */
    public Set<String> chairmanServerList;

    /**
     * This value represents the zookeeper quorum.
     */
    public String zookeeperQuorum;

    /**
     * This value represents whether a loadbalancer will be deployed.
     */
    @Immutable
    @DefaultBoolean(value = true)
    public Boolean loadBalancerEnabled;

    /**
     * This value represent the data migration progress for upgrade.
     */
    public Map<String, Integer> dataMigrationProgress;

    /**
     * This value represents the number of vibs already uploaded.
     */
    public Long vibsUploaded;

    /**
     * This value represents the number of vibs still uploading.
     */
    public Long vibsUploading;

    public Map<Integer, String> zookeeperIdToIpMap;
  }
}
