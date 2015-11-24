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
import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultBoolean;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;

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

  @Override
  public ServiceDocument getDocumentTemplate() {
    return ServiceUtils.getDocumentTemplateWithIndexedFields(super.getDocumentTemplate(), "imageDataStoreNames");
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
   * This class defines the document state associated with a single {@link DeploymentService} instance.
   */
  public static class State extends ServiceDocument {

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
  }
}
