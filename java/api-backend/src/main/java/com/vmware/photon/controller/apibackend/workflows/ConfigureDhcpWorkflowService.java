/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.apibackend.exceptions.ConfigureDhcpException;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
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

import java.util.Set;

/**
 * Implements an Xenon service that represents a workflow to configure the DHCP service for logical networks.
 */
public class ConfigureDhcpWorkflowService extends BaseWorkflowService<ConfigureDhcpWorkflowDocument,
    ConfigureDhcpWorkflowDocument.TaskState, ConfigureDhcpWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-dhcp";

  public static FactoryService createFactory() {
    return FactoryService.create(ConfigureDhcpWorkflowService.class, ConfigureDhcpWorkflowDocument.class);
  }

  public ConfigureDhcpWorkflowService() {
    super(ConfigureDhcpWorkflowDocument.class,
        ConfigureDhcpWorkflowDocument.TaskState.class,
        ConfigureDhcpWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    ConfigureDhcpWorkflowDocument state = createOperation.getBody(ConfigureDhcpWorkflowDocument.class);

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
    ConfigureDhcpWorkflowDocument state = startOperation.getBody(ConfigureDhcpWorkflowDocument.class);

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
      ConfigureDhcpWorkflowDocument currentState = getState(patchOperation);
      ConfigureDhcpWorkflowDocument patchState = patchOperation.getBody(ConfigureDhcpWorkflowDocument.class);
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

  private void processPatch(ConfigureDhcpWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case CREATE_DHCP_RELAY_PROFILE:
          createDhcpRelayProfile(state);
          break;
        case CREATE_DHCP_RELAY_SERVICE:
          createDhcpRelayService(state);
          break;
        default:
          throw new ConfigureDhcpException("Invalid task substage " + state.taskState.subStage);
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Creates a DHCP relay profile in NSX, using the given DHCP server information.
   */
  private void createDhcpRelayProfile(ConfigureDhcpWorkflowDocument state) {
    DhcpRelayProfileCreateSpec request = new DhcpRelayProfileCreateSpec();
    request.setResourceType(ServiceProfileResourceType.DHCP_RELAY_PROFILE);
    request.setServerAddresses(state.dhcpServerAddresses);
    request.setDisplayName(NameUtils.getDhcpRelayProfileName(getDeploymentId(state)));
    request.setDescription(NameUtils.getDhcpRelayProfileDescription(getDeploymentId(state)));

    try {
      ServiceHostUtils.getNsxClient(getHost(), state.nsxAddress, state.nsxUsername, state.nsxPassword)
          .getDhcpServiceApi()
          .createDhcpRelayProfile(request,
              new FutureCallback<DhcpRelayProfile>() {
                @Override
                public void onSuccess(DhcpRelayProfile result) {
                  try {
                    ConfigureDhcpWorkflowDocument patchState = buildPatch(
                        TaskState.TaskStage.STARTED,
                        ConfigureDhcpWorkflowDocument.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE);
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
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Creates a DHCP relay service in NSX, using the relay profile created in the previous step.
   */
  private void createDhcpRelayService(ConfigureDhcpWorkflowDocument state) {
    DhcpRelayServiceCreateSpec request = new DhcpRelayServiceCreateSpec();
    request.setResourceType(LogicalServiceResourceType.DHCP_RELAY_SERVICE);
    request.setProfileId(state.taskServiceEntity.dhcpRelayProfileId);
    request.setDisplayName(NameUtils.getDhcpRelayServiceName(getDeploymentId(state)));
    request.setDescription(NameUtils.getDhcpRelayServiceDescription(getDeploymentId(state)));

    try {
      ServiceHostUtils.getNsxClient(getHost(), state.nsxAddress, state.nsxUsername, state.nsxPassword)
          .getDhcpServiceApi()
          .createDhcpRelayService(request,
              new FutureCallback<DhcpRelayService>() {
                @Override
                public void onSuccess(DhcpRelayService result) {
                  state.taskServiceEntity.dhcpRelayServiceId = result.getId();
                  updateDeployment(state);
                }

                @Override
                public void onFailure(Throwable t) {
                  fail(state, t);
                }
              });
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Updates the deployment state with the DHCP relay information.
   */
  private void updateDeployment(ConfigureDhcpWorkflowDocument state) {
    DeploymentService.State deploymentPatchState = new DeploymentService.State();
    deploymentPatchState.dhcpRelayProfileId = state.taskServiceEntity.dhcpRelayProfileId;
    deploymentPatchState.dhcpRelayServiceId = state.taskServiceEntity.dhcpRelayServiceId;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(state.taskServiceEntity.documentSelfLink)
        .setBody(deploymentPatchState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            ConfigureDhcpWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.FINISHED,
                null);
            patchState.taskServiceEntity = state.taskServiceEntity;
            finish(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Gets the DeploymentService.State entity in cloud-store.
   */
  private void getDeployment(
      ConfigureDhcpWorkflowDocument state,
      Operation operation) {

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
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
   * Gets NSX configuration from {@link DeploymentService.State} entity in cloud-store, and saves
   * the configuration in the document of the workflow service.
   */
  private void getDeployment(ConfigureDhcpWorkflowDocument state,
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
            DeploymentService.State deploymentState = op.getBody(DeploymentService.State.class);

            state.taskServiceEntity = deploymentState;
            state.nsxAddress = deploymentState.networkManagerAddress;
            state.nsxUsername = deploymentState.networkManagerUsername;
            state.nsxPassword = deploymentState.networkManagerPassword;

            create(state, operation);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Gets the ID of the deployment.
   */
  private String getDeploymentId(ConfigureDhcpWorkflowDocument state) {
    return ServiceUtils.getIDFromDocumentSelfLink(state.taskServiceEntity.documentSelfLink);
  }
}
