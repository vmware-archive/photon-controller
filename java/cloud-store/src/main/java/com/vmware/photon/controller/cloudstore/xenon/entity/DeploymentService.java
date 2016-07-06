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

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.api.StatsStoreType;
import com.vmware.photon.controller.cloudstore.SystemConfig;
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
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Iterator;
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
  public OperationProcessingChain getOperationProcessingChain() {
    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter myRouter = new RequestRouter();
    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<HostListChangeRequest>(
            HostListChangeRequest.class, "kind",
            HostListChangeRequest.Kind.UPDATE_ZOOKEEPER_INFO),
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
      // handleRequest is not called for the initial creation
      setSystemConfig(startState);
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
  public void handleRequest(Operation request) {
    this.handleRequest(request, OperationProcessingStage.LOADING_STATE);
  }

  // handlePatch is called on the owner. handleRequest is called for all the nodes.
  @Override
  public void handleRequest(Operation request, OperationProcessingStage opProcessingStage) {
    setSystemConfig(request);
    super.handleRequest(request, opProcessingStage);
  }

  private void setSystemConfig(Operation request) {
    if (request.getAction() != null) {
      Action action = request.getAction();
      if (action == Action.PATCH || action == Action.POST || action == Action.PUT) {
        if (request.hasBody()) {
          State patchState = request.getBody(State.class);
          if (patchState.state != null) {
            ServiceUtils.logInfo(this, "SystemConfig update is needed for %s %s", patchState.documentSelfLink,
                patchState.state);
            try {
              // If this node is not an owner but a replicated node, we do not need to validate.
              if (!request.isFromReplication()) {
                if (getState(request) == null) {
                  // In some cases getState is not populated yet. It sends another patch to populate the body which
                  // will handle
                  return;
                }
                validateForSetSystemConfig(patchState, request, action);
              } else {
                ServiceUtils.logInfo(this, "SystemConfig update done without validation because isFromReplication is " +
                    "true %s %s", patchState.documentSelfLink, patchState.state);
              }
              setSystemConfig(patchState);
            } catch (Throwable t) {
              ServiceUtils.logSevere(this, t);
              // We do not fail this here. It will fail when super.handleRequest calls handlePatch
            }
          }
        }
      }
    }
  }

  private void validateForSetSystemConfig(State patchState, Operation request, Action action) {
    ServiceUtils.logInfo(this, "SystemConfig update requires validation for Action: %s  deploymentlink: %s ",
        action, patchState.documentSelfLink);
    if (action == Action.PATCH) {
      validatePatchState(getState(request), patchState);
    } else {
      validateState(patchState);
    }
  }

  private void setSystemConfig(State state) {
    if (state != null && state.state != null) {
      ServiceUtils.logInfo(this, "SystemConfig update is set for %s %s", state.documentSelfLink,
          state.state);
      if (SystemConfig.getInstance() != null) {
        SystemConfig.getInstance().markPauseStateLocally(state);
      } else {
        ServiceUtils.logInfo(this,
            "SystemConfig.getInstance is unexpectedly null: work may continue when it shouldn't %s %s", state
                .documentSelfLink, state.state);
      }
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation){
      ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  private void handlePatchUpdateHostListInfo(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      HostListChangeRequest patchState = patchOperation.getBody(HostListChangeRequest.class);

      currentState = updateHostListInfo(currentState, patchState);
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

  private State updateHostListInfo(State currentState, HostListChangeRequest patchState) {
    if (patchState.zookeeperIpToRemove != null) {
      ServiceUtils.logInfo(this, "Removing " + patchState.zookeeperIpToRemove + " from DeploymentService map");
      if (currentState.zookeeperIdToIpMap != null) {
        // Use iterator since it is safer to modify this map
        Iterator<Map.Entry<Integer, String>> it = currentState.zookeeperIdToIpMap.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<Integer, String> zkNode = it.next();
          if (zkNode.getValue().equals(patchState.zookeeperIpToRemove)) {
            currentState.zookeeperIdToIpMap.remove(zkNode.getKey());
            ServiceUtils.logInfo(this, "Removed " + zkNode.getKey() + " - " + zkNode.getValue());
            break;
          }
        }
      }
    }

    if (patchState.zookeeperIpsToAdd != null) {
      ServiceUtils.logInfo(this, "Adding " + patchState.zookeeperIpsToAdd.size() + " ips to DeploymentService map");
      if (currentState.zookeeperIdToIpMap == null) {
        currentState.zookeeperIdToIpMap = new HashMap<>();
      }

      for (String zookeeperIpToAdd : patchState.zookeeperIpsToAdd) {
        // Do we know about this zookeeper?
        if (currentState.zookeeperIdToIpMap.containsValue(zookeeperIpToAdd)) {
          ServiceUtils.logInfo(this, "zookeeperIdToMap already contains " + zookeeperIpToAdd);
        } else {
          int idx = 1;
          // Let's find an empty spot
          while (currentState.zookeeperIdToIpMap.containsKey(idx)) {
            idx++;
          }
          currentState.zookeeperIdToIpMap.put(idx, zookeeperIpToAdd);
          ServiceUtils.logInfo(this, "Found spot " + zookeeperIpToAdd + " in the map with this id " + idx);
        }
      }
    }

    return currentState;
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
    public Boolean virtualNetworkEnabled;

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

    public Map<Integer, String> zookeeperIdToIpMap;

    /**
     * This structure stores the image and flavor information we need
     * to deploy a dhcp server for a network.
     * A null value here indicates that dhcp feature is not supported.
     */
    public DhcpVmConfiguration dhcpVmConfiguration;
  }
}
