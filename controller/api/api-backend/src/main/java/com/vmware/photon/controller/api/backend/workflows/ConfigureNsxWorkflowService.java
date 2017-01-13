/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.backend.workflows;

import com.vmware.photon.controller.api.backend.servicedocuments.ConfigureNsxWorkflowDocument;
import com.vmware.photon.controller.api.backend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.nsxclient.datatypes.LogicalServiceResourceType;
import com.vmware.photon.controller.nsxclient.datatypes.ServiceProfileResourceType;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfile;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfileCreateSpec;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayService;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayServiceCreateSpec;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implements an Xenon service that represents a workflow to configure NSX.
 */
public class ConfigureNsxWorkflowService extends BaseWorkflowService<ConfigureNsxWorkflowDocument,
    ConfigureNsxWorkflowDocument.TaskState, ConfigureNsxWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-nsx";

  public static FactoryService createFactory() {
    return FactoryService.create(ConfigureNsxWorkflowService.class, ConfigureNsxWorkflowDocument.class);
  }

  public ConfigureNsxWorkflowService() {
    super(ConfigureNsxWorkflowDocument.class,
        ConfigureNsxWorkflowDocument.TaskState.class,
        ConfigureNsxWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    ConfigureNsxWorkflowDocument state = createOperation.getBody(ConfigureNsxWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      getDeployment(state, createOperation);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(createOperation)) {
        createOperation.fail(t);
      }
    }
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    ConfigureNsxWorkflowDocument state = startOperation.getBody(ConfigureNsxWorkflowDocument.class);

    try {
      initializeState(state);
      validateStartState(state);

      startOperation.setBody(state).complete();

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleStartDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        return;
      }

      start(state);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      ConfigureNsxWorkflowDocument currentState = getState(patchOperation);
      ConfigureNsxWorkflowDocument patchState = patchOperation.getBody(ConfigureNsxWorkflowDocument.class);
      validatePatchState(currentState, patchState);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags) ||
          ControlFlags.isHandlePatchDisabled(currentState.controlFlags) ||
          TaskState.TaskStage.STARTED != currentState.taskState.stage) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
        return;
      }

      processPatch(currentState);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  @Override
  protected void progress(
      ConfigureNsxWorkflowDocument state,
      ConfigureNsxWorkflowDocument patchState) {
    updateDeployment(patchState,
        success -> {
          super.progress(state, patchState);
        },
        failure -> {
          super.fail(state, failure);
        });
  }

  @Override
  protected void finish(
      ConfigureNsxWorkflowDocument state,
      ConfigureNsxWorkflowDocument patchState) {
    updateDeployment(patchState,
        success -> {
          super.finish(state, patchState);
        },
        failure -> {
          super.fail(state, failure);
        });
  }

  private void processPatch(ConfigureNsxWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case CHECK_NSX_CONFIGURED:
          checkNsxConfigured(state);
          break;
        case CREATE_SUBNET_ALLOCATOR:
          createSubnetAllocator(state);
          break;
        case CREATE_FLOATING_IP_ALLOCATOR:
          createFloatingIpAllocator(state);
          break;
        case CREATE_DHCP_RELAY_PROFILE:
          createDhcpRelayProfile(state);
          break;
        case CREATE_DHCP_RELAY_SERVICE:
          createDhcpRelayService(state);
          break;
        case SET_NSX_CONFIGURED:
          setNsxConfigured(state);
          break;
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Checks if NSX has already been configured. If so, move the workflow to FINISHED state.
   */
  private void checkNsxConfigured(ConfigureNsxWorkflowDocument state) throws Throwable {
    if (!state.taskServiceEntity.sdnEnabled) {
      ServiceUtils.logInfo(this, "SDN is not enabled for this deployment");
      finish(state);
      return;
    }

    if (state.taskServiceEntity.nsxConfigured) {
      ServiceUtils.logInfo(this, "NSX has already been configured for this deployment");
      finish(state);
      return;
    }

    ConfigureNsxWorkflowDocument patchState = buildPatch(
        TaskState.TaskStage.STARTED,
        ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_SUBNET_ALLOCATOR);
    patchState.taskServiceEntity = state.taskServiceEntity;
    patchState.taskServiceEntity.networkManagerAddress = state.nsxAddress;
    patchState.taskServiceEntity.networkManagerUsername = state.nsxUsername;
    patchState.taskServiceEntity.networkManagerPassword = state.nsxPassword;
    patchState.taskServiceEntity.dhcpServers = new ArrayList<>(state.dhcpServerAddresses.values());
    progress(state, patchState);
  }

  /**
   * Creates a global subnet allocator that manages subnet IP range allocations.
   */
  private void createSubnetAllocator(ConfigureNsxWorkflowDocument state) throws Throwable {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(SubnetAllocatorService.SINGLETON_LINK)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            if (op.getStatusCode() != Operation.STATUS_CODE_NOT_FOUND) {
              fail(state, ex);
              return;
            }

            SubnetAllocatorService.State subnetAllocatorServiceState = new SubnetAllocatorService.State();
            subnetAllocatorServiceState.rootCidr = state.nonRoutableIpRootCidr;
            subnetAllocatorServiceState.dhcpAgentEndpoint = String.format(
                "http://%s:%d",
                // Selecting first index in Dhcp server list since expecting only one entry in this iteration
                state.dhcpServerAddresses.values().iterator().next(),
                Constants.DHCP_AGENT_PORT);
            subnetAllocatorServiceState.documentSelfLink = SubnetAllocatorService.SINGLETON_LINK;

            ServiceHostUtils.getCloudStoreHelper(getHost())
                .createPost(SubnetAllocatorService.FACTORY_LINK)
                .setBody(subnetAllocatorServiceState)
                .setCompletion((iop, iex) -> {
                  if (iex != null) {
                    fail(state, iex);
                    return;
                  }

                  try {
                    ConfigureNsxWorkflowDocument patchState = buildPatch(
                        TaskState.TaskStage.STARTED,
                        ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_FLOATING_IP_ALLOCATOR);
                    patchState.taskServiceEntity = state.taskServiceEntity;
                    patchState.taskServiceEntity.ipRange = state.nonRoutableIpRootCidr;
                    progress(state, patchState);
                  } catch (Throwable t) {
                    fail(state, t);
                  }
                })
                .sendWith(this);
            return;
          }

          ServiceUtils.logInfo(this, "Global subnet allocator has already been created");
          progress(state, ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_FLOATING_IP_ALLOCATOR);
        })
        .sendWith(this);
  }

  /**
   * Creates a global floating IP allocator that manages floating IP allocations.
   */
  private void createFloatingIpAllocator(ConfigureNsxWorkflowDocument state) throws Throwable {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            if (op.getStatusCode() != Operation.STATUS_CODE_NOT_FOUND) {
              fail(state, ex);
              return;
            }

            DhcpSubnetService.State dhcpSubnetServiceState = new DhcpSubnetService.State();
            dhcpSubnetServiceState.subnetId = getDeploymentId(state);
            dhcpSubnetServiceState.lowIp = IpHelper.ipStringToLong(state.floatingIpRootRange.getStart());
            dhcpSubnetServiceState.highIp = IpHelper.ipStringToLong(state.floatingIpRootRange.getEnd());
            dhcpSubnetServiceState.dhcpAgentEndpoint = String.format(
                "http://%s:%d",
                state.dhcpServerAddresses.values().iterator().next(),
                Constants.DHCP_AGENT_PORT);
            dhcpSubnetServiceState.isFloatingIpSubnet = true;
            dhcpSubnetServiceState.documentSelfLink = DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK;

            ServiceHostUtils.getCloudStoreHelper(getHost())
                .createPost(DhcpSubnetService.FACTORY_LINK)
                .setBody(dhcpSubnetServiceState)
                .setCompletion((iop, iex) -> {
                  if (iex != null) {
                    fail(state, iex);
                    return;
                  }

                  try {
                    ConfigureNsxWorkflowDocument patchState = buildPatch(
                        TaskState.TaskStage.STARTED,
                        ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE);
                    patchState.taskServiceEntity = state.taskServiceEntity;
                    patchState.taskServiceEntity.floatingIpRange = state.floatingIpRootRange;
                    progress(state, patchState);
                  } catch (Throwable t) {
                    fail(state, t);
                  }
                })
                .sendWith(this);
            return;
          }

          ServiceUtils.logInfo(this, "Global floating IP allocator has already been created");
          progress(state, ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE);
        })
        .sendWith(this);
  }

  /**
   * Creates a DHCP relay profile in NSX, using the given DHCP server information.
   */
  private void createDhcpRelayProfile(ConfigureNsxWorkflowDocument state) throws Throwable {
    if (state.taskServiceEntity.dhcpRelayProfileId != null) {
      ServiceUtils.logInfo(this, "DHCP Relay Profile has already been created");
      progress(state, ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE);
      return;
    }

    DhcpRelayProfileCreateSpec request = new DhcpRelayProfileCreateSpec();
    request.setResourceType(ServiceProfileResourceType.DHCP_RELAY_PROFILE);
    request.setServerAddresses(new ArrayList<>(state.dhcpServerAddresses.keySet()));
    request.setDisplayName(NameUtils.getDhcpRelayProfileName(getDeploymentId(state)));
    request.setDescription(NameUtils.getDhcpRelayProfileDescription(getDeploymentId(state)));

    ServiceHostUtils.getNsxClient(getHost(), state.nsxAddress, state.nsxUsername, state.nsxPassword)
        .getDhcpServiceApi()
        .createDhcpRelayProfile(request,
            new FutureCallback<DhcpRelayProfile>() {
              @Override
              public void onSuccess(DhcpRelayProfile result) {
                try {
                  ConfigureNsxWorkflowDocument patchState = buildPatch(
                      TaskState.TaskStage.STARTED,
                      ConfigureNsxWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE);
                  patchState.taskServiceEntity = state.taskServiceEntity;
                  patchState.taskServiceEntity.dhcpRelayProfileId = result.getId();
                  progress(state, patchState);
                } catch (Throwable t) {
                  fail(state, t);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                fail(state, t);
              }
            });
  }

  /**
   * Creates a DHCP relay service in NSX, using the relay profile created in the previous step.
   */
  private void createDhcpRelayService(ConfigureNsxWorkflowDocument state) throws Throwable {
    if (state.taskServiceEntity.dhcpRelayServiceId != null) {
      ServiceUtils.logInfo(this, "DHCP Relay Service has already been created");
      progress(state, ConfigureNsxWorkflowDocument.TaskState.SubStage.SET_NSX_CONFIGURED);
      return;
    }

    DhcpRelayServiceCreateSpec request = new DhcpRelayServiceCreateSpec();
    request.setResourceType(LogicalServiceResourceType.DHCP_RELAY_SERVICE);
    request.setProfileId(state.taskServiceEntity.dhcpRelayProfileId);
    request.setDisplayName(NameUtils.getDhcpRelayServiceName(getDeploymentId(state)));
    request.setDescription(NameUtils.getDhcpRelayServiceDescription(getDeploymentId(state)));

    ServiceHostUtils.getNsxClient(getHost(), state.nsxAddress, state.nsxUsername, state.nsxPassword)
        .getDhcpServiceApi()
        .createDhcpRelayService(request,
            new FutureCallback<DhcpRelayService>() {
              @Override
              public void onSuccess(DhcpRelayService result) {
                try {
                  ConfigureNsxWorkflowDocument patchState = buildPatch(
                      TaskState.TaskStage.STARTED,
                      ConfigureNsxWorkflowDocument.TaskState.SubStage.SET_NSX_CONFIGURED);
                  patchState.taskServiceEntity = state.taskServiceEntity;
                  patchState.taskServiceEntity.dhcpRelayServiceId = result.getId();
                  progress(state, patchState);
                } catch (Throwable t) {
                  fail(state, t);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                fail(state, t);
              }
            });
  }

  /**
   * Sets the NSX configured flag to true.
   */
  private void setNsxConfigured(ConfigureNsxWorkflowDocument state) throws Throwable {
    ConfigureNsxWorkflowDocument patchState = buildPatch(
        TaskState.TaskStage.FINISHED,
        null);
    patchState.taskServiceEntity = state.taskServiceEntity;
    patchState.taskServiceEntity.nsxConfigured = true;

    finish(state, patchState);
  }

  /**
   * Gets the {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.State}
   * entity in cloud-store.
   */
  private void getDeployment(
      ConfigureNsxWorkflowDocument state,
      Operation operation) {

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createBroadcastPost(ServiceUriPaths.XENON.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.XENON.DEFAULT_NODE_SELECTOR)
        .setBody(queryTask)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
          Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
          if (documentLinks.size() != 1) {
            fail(state, new IllegalStateException(
                String.format("Found %d deployment service(s).", documentLinks.size())));
          }

          getDeployment(state, operation, documentLinks.iterator().next());
        })
        .sendWith(this);
  }

  /**
   * Gets NSX configuration from {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.State}
   * entity in cloud-store, and saves the configuration in the document of the workflow service.
   */
  private void getDeployment(ConfigureNsxWorkflowDocument state,
                             Operation operation,
                             String deploymentServiceStateLink) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(deploymentServiceStateLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            state.taskServiceEntity = op.getBody(DeploymentService.State.class);
            create(state, operation);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Updates the {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService}
   * entity in cloud-store.
   */
  private void updateDeployment(ConfigureNsxWorkflowDocument state,
                                Consumer<Void> successHandler,
                                Consumer<Throwable> failureHandler) {
    DeploymentService.State deploymentPatchState = new DeploymentService.State();
    deploymentPatchState.nsxConfigured = state.taskServiceEntity.nsxConfigured;
    deploymentPatchState.networkManagerAddress = state.taskServiceEntity.networkManagerAddress;
    deploymentPatchState.networkManagerUsername = state.taskServiceEntity.networkManagerUsername;
    deploymentPatchState.networkManagerPassword = state.taskServiceEntity.networkManagerPassword;
    deploymentPatchState.dhcpRelayProfileId = state.taskServiceEntity.dhcpRelayProfileId;
    deploymentPatchState.dhcpRelayServiceId = state.taskServiceEntity.dhcpRelayServiceId;
    deploymentPatchState.dhcpServers = state.taskServiceEntity.dhcpServers;
    deploymentPatchState.ipRange = state.taskServiceEntity.ipRange;
    deploymentPatchState.floatingIpRange = state.taskServiceEntity.floatingIpRange;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(state.taskServiceEntity.documentSelfLink)
        .setBody(deploymentPatchState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failureHandler.accept(ex);
            return;
          }

          try {
            successHandler.accept(null);
          } catch (Throwable t) {
            failureHandler.accept(ex);
          }
        })
        .sendWith(this);
  }

  /**
   * Extracts the ID of the {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService}
   * entity in cloud-store.
   */
  private String getDeploymentId(ConfigureNsxWorkflowDocument state) {
    return ServiceUtils.getIDFromDocumentSelfLink(state.taskServiceEntity.documentSelfLink);
  }
}
