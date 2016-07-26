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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.StatsStoreType;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a Xenon micro-service which provides a plain data object
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
  public void handleDelete(Operation deleteOperation){
      ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument template = super.getDocumentTemplate();
    ServiceUtils.setExpandedIndexing(template, State.FIELD_NAME_IMAGE_DATA_STORE_NAMES);
    return template;
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);

    if (currentState.dhcpVmConfiguration != null) {
      if (StringUtils.isBlank(currentState.dhcpVmConfiguration.vmImageId)) {
        throw new IllegalArgumentException("vmImageId should not be blank when dhcpVmConfiguration is not null");
      }
      if (StringUtils.isBlank(currentState.dhcpVmConfiguration.vmFlavorId)) {
        throw new IllegalArgumentException("vmFlavorId should not be blank when dhcpVmConfiguration is not null");
      }
      if (StringUtils.isBlank(currentState.dhcpVmConfiguration.vmDiskFlavorId)) {
        throw new IllegalArgumentException("vmDiskFlavorId should not be blank when dhcpVmConfiguration is not null");
      }
    }
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
    public Kind kind;

    public List<String> zookeeperIpsToAdd;

    public String zookeeperIpToRemove;

    /**
     * Defines the purpose of the patch.
     */
    public enum Kind {
      UPDATE_ZOOKEEPER_INFO,
    }
  }

  /**
   * This structure stores the image and flavor information we need
   * to deploy a dhcp server for a network.
   */
  public static class DhcpVmConfiguration {
    public String vmImageId;
    public String vmFlavorId;
    public String vmDiskFlavorId;
  }

  /**
   * This class defines the document state associated with a single {@link DeploymentService} instance.
   */
  @NoMigrationDuringUpgrade
  @MigrateDuringDeployment(
      factoryServicePath = DeploymentServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
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
     * The tenant name on LightWave.
     */
    @Immutable
    public String oAuthTenantName;

    /**
     * LightWave user name.
     */
    @Immutable
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
    public String oAuthUserName;

    /**
     * Password for the given LightWave user.
     */
    @Immutable
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
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
     * Endpoint to the oAuth login service for Swagger.
     */
    @WriteOnce
    public String oAuthSwaggerLoginEndpoint;

    /**
     * Endpoint to the oAuth logout service for Swagger.
     */
    @WriteOnce
    public String oAuthSwaggerLogoutEndpoint;

    /**
     * Endpoint to the oAuth login service for Mgmt UI.
     */
    @WriteOnce
    public String oAuthMgmtUiLoginEndpoint;

    /**
     * Endpoint to the oAuth logout service for Mgmt UI.
     */
    @WriteOnce
    public String oAuthMgmtUiLogoutEndpoint;

    /**
     * This value represents whether virtual network support is enabled for this deployment.
     */
    @DefaultBoolean(value = false)
    public Boolean sdnEnabled;

    /**
     * This value represents the IP address of the network manager.
     */
    public String networkManagerAddress;

    /**
     * This value represents the username for accessing the network manager.
     */
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
    public String networkManagerUsername;

    /**
     * This value represents the password for accessing the network manager.
     */
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.SENSITIVE)
    public String networkManagerPassword;

    /**
     * This value represents the ID of the router for accessing the outside network (i.e. Internet).
     */
    public String networkTopRouterId;

    /**
     * This value represents the network zone ID.
     */
    public String networkZoneId;

    /**
     * This value represents the ID of the DHCP relay profile.
     */
    public String dhcpRelayProfileId;

    /**
     * This value represents the ID of the DHCP relay service.
     */
    public String dhcpRelayServiceId;

    /**
     * This value represents the syslog endpoint for the deployment.
     */
    @Immutable
    public String syslogEndpoint;

    /**
     * This value represents whether Stats collection is enabled for the deployment.
     */
    @DefaultBoolean(value = false)
    public Boolean statsEnabled;

    /**
     * This value represents the stats store endpoint for the deployment.
     */
    public String statsStoreEndpoint;

    /**
     * This value represents the port used by the Stats store endpoint.
     */
    public Integer statsStorePort;

    /**
     * This value represents the type of stats store.
     */
    public StatsStoreType statsStoreType;

    /**
     * This value represents whether a loadbalancer will be deployed.
     */
    @Immutable
    @DefaultBoolean(value = true)
    public Boolean loadBalancerEnabled;

    /**
     * This value represents the load balancer endpoint for the deployment.
     */
    @WriteOnce
    public String loadBalancerAddress;

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

    /**
     * This structure stores the image and flavor information we need
     * to deploy a dhcp server for a network.
     * A null value here indicates that dhcp feature is not supported.
     */
    public DhcpVmConfiguration dhcpVmConfiguration;
  }
}
