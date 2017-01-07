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

package com.vmware.photon.controller.api.backend.workflows;

import com.vmware.photon.controller.api.backend.servicedocuments.DeleteLogicalPortsTask;
import com.vmware.photon.controller.api.backend.servicedocuments.DeleteLogicalRouterTask;
import com.vmware.photon.controller.api.backend.servicedocuments.DeleteLogicalSwitchTask;
import com.vmware.photon.controller.api.backend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.api.backend.tasks.DeleteLogicalPortsTaskService;
import com.vmware.photon.controller.api.backend.tasks.DeleteLogicalRouterTaskService;
import com.vmware.photon.controller.api.backend.tasks.DeleteLogicalSwitchTaskService;
import com.vmware.photon.controller.api.backend.utils.CloudStoreUtils;
import com.vmware.photon.controller.api.backend.utils.ServiceHostUtils;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.api.model.VirtualSubnet;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneService;
import com.vmware.photon.controller.cloudstore.xenon.entity.TombstoneServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationService;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationTask;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashMap;
import java.util.Set;

/**
 * This class implements a Xenon service representing a workflow to delete a virtual network.
 */
public class DeleteVirtualNetworkWorkflowService extends BaseWorkflowService<DeleteVirtualNetworkWorkflowDocument,
    DeleteVirtualNetworkWorkflowDocument.TaskState, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/delete-virtual-network";

  public static FactoryService createFactory() {
    return FactoryService.create(DeleteVirtualNetworkWorkflowService.class, DeleteVirtualNetworkWorkflowDocument.class);
  }

  public DeleteVirtualNetworkWorkflowService() {
    super(DeleteVirtualNetworkWorkflowDocument.class,
        DeleteVirtualNetworkWorkflowDocument.TaskState.class,
        DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    DeleteVirtualNetworkWorkflowDocument state =
        createOperation.getBody(DeleteVirtualNetworkWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      getVirtualNetwork(state, createOperation);
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
    DeleteVirtualNetworkWorkflowDocument state = startOperation.getBody(DeleteVirtualNetworkWorkflowDocument.class);

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
    ServiceUtils.logInfo(this, "Handling patch %s", getSelfLink());

    try {
      DeleteVirtualNetworkWorkflowDocument currentState = getState(patchOperation);
      DeleteVirtualNetworkWorkflowDocument patchState =
          patchOperation.getBody(DeleteVirtualNetworkWorkflowDocument.class);
      validatePatchState(currentState, patchState);

      if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
      }

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

  /**
   * Validate the substage progresses correctly.
   */
  private void validateTaskSubStageProgression(DeleteVirtualNetworkWorkflowDocument.TaskState startState,
                                               DeleteVirtualNetworkWorkflowDocument.TaskState patchState) {

    if (patchState.stage == TaskState.TaskStage.FINISHED) {
      Preconditions.checkState(startState.stage == TaskState.TaskStage.STARTED &&
          (startState.subStage == DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_NETWORK_ENTITY
              || startState.subStage == DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.CHECK_VM_EXISTENCE));
    }
  }

  /**
   * Processes the sub-stages of the workflow.
   */
  private void processPatch(DeleteVirtualNetworkWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case CHECK_VM_EXISTENCE:
          checkVmExistence(state);
          break;
        case GET_NSX_CONFIGURATION:
          getNsxConfiguration(state);
          break;
        case RELEASE_IP_ADDRESS_SPACE:
          releaseIpAddressSpace(state);
          break;
        case RELEASE_QUOTA:
          releaseQuota(state);
          break;
        case RELEASE_SNAT_IP:
          releaseSnatIp(state);
          break;
        case DELETE_LOGICAL_PORTS:
          deleteLogicalPorts(state);
          break;
        case DELETE_LOGICAL_ROUTER:
          deleteLogicalRouter(state);
          break;
        case DELETE_LOGICAL_SWITCH:
          deleteLogicalSwitch(state);
          break;
        case DELETE_DHCP_OPTION:
          deleteDhcpOption(state);
          break;
        case DELETE_NETWORK_ENTITY:
          deleteVirtualNetwork(state);
          break;
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Check if any VMs still exist on this virtual network.
   * If there are, move this virtual network to PENDING_DELETE state.
   * If there are not, delete the related network equipments, and move the network to DELETED state.
   */
  private void checkVmExistence(DeleteVirtualNetworkWorkflowDocument state) {
    ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put(QueryTask.QuerySpecification.buildCollectionItemName(VmService.State.FIELD_NAME_NETWORKS),
        state.networkId);

    QueryTask.QuerySpecification querySpecification = QueryTaskUtils.buildQuerySpec(VmService.State.class,
        termsBuilder.build());
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

          try {
            if (documentLinks.size() != 0) {
              // VMs are still on this network, cannot continue deleting, and mark this task as finished.
              // After all related VMs are deleted, this network will be tombstoned.
              DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
              patchState.taskServiceEntity = state.taskServiceEntity;
              patchState.taskServiceEntity.state = SubnetState.PENDING_DELETE;

              finish(state, patchState);
            } else {
              DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                  TaskState.TaskStage.STARTED,
                  DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);
              progress(state, patchState);
            }
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Gets NSX configuration from {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.State}
   * entity in cloud-store, and save the configuration in the document of the workflow service.
   */
  private void getNsxConfiguration(DeleteVirtualNetworkWorkflowDocument state) {
    CloudStoreUtils.queryAndProcess(
        this,
        DeploymentService.State.class,
        deploymentState -> {
          try {
            getNsxConfiguration(state, deploymentState);
          } catch (Throwable t) {
            fail(state, t);
          }
        },
        t -> {
          fail(state, t);
        }
    );
  }

  private void getNsxConfiguration(DeleteVirtualNetworkWorkflowDocument state,
                                   DeploymentService.State deploymentState) {
    CloudStoreUtils.getAndProcess(
        this,
        DhcpSubnetService.FACTORY_LINK + "/" + state.networkId,
        DhcpSubnetService.State.class,
        subnet -> {
          try {
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.RELEASE_IP_ADDRESS_SPACE);

            patchState.nsxAddress = deploymentState.networkManagerAddress;
            patchState.nsxUsername = deploymentState.networkManagerUsername;
            patchState.nsxPassword = deploymentState.networkManagerPassword;

            patchState.dhcpAgentEndpoint = subnet.dhcpAgentEndpoint;

            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        },
        t -> {
          fail(state, t);
        }
    );
  }

  /**
   * Release IPs for the virtual network.
   */
  private void releaseIpAddressSpace(DeleteVirtualNetworkWorkflowDocument state) {
    if (!state.taskServiceEntity.isIpAddressSpaceConsumed) {
      ServiceUtils.logInfo(this, "The IP address space was not consumed. Skip releasing the address space.");
      progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.RELEASE_QUOTA);
      return;
    }

    SubnetAllocatorService.ReleaseSubnet releaseSubnet =
        new SubnetAllocatorService.ReleaseSubnet(state.networkId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(SubnetAllocatorService.SINGLETON_LINK)
        .setBody(releaseSubnet)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.RELEASE_QUOTA);
            patchState.taskServiceEntity = state.taskServiceEntity;
            patchState.taskServiceEntity.isIpAddressSpaceConsumed = false;

            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Releases resource ticket quota for the virtual network.
   */
  private void releaseQuota(DeleteVirtualNetworkWorkflowDocument state) {
    if (!state.taskServiceEntity.isSizeQuotaConsumed) {
      ServiceUtils.logInfo(this, "The quota was not consumed. Skip releasing quota.");
      progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
      return;
    }

    checkArgument(state.taskServiceEntity.parentId != null, "parentId should not be null.");

    switch (state.taskServiceEntity.parentKind) {
      case Project.KIND:
        String id = state.taskServiceEntity.parentId;
        ServiceHostUtils.getCloudStoreHelper(getHost())
            .createGet(ProjectServiceFactory.SELF_LINK + "/" + id)
            .setCompletion((op, ex) -> {
              if (ex != null) {
                fail(state, ex);
                return;
              }
              ProjectService.State project = op.getBody(ProjectService.State.class);
              String resourceTicketId = project.resourceTicketId;

              releaseQuota(state, resourceTicketId);
            }).sendWith(this);
        break;
      default:
        throw new IllegalArgumentException("Unknown parentKind: " + state.taskServiceEntity.parentKind);
    }
  }

  /**
   * Releases resource ticket quota for the virtual network.
   */
  private void releaseQuota(DeleteVirtualNetworkWorkflowDocument state, String resourceTicketId) {

    ResourceTicketService.Patch patch = new ResourceTicketService.Patch();
    patch.patchtype = ResourceTicketService.Patch.PatchType.USAGE_RETURN;
    patch.cost = new HashMap<>();

    QuotaLineItem costItem = new QuotaLineItem();
    costItem.setKey(CreateVirtualNetworkWorkflowService.SDN_RESOURCE_TICKET_KEY);
    costItem.setValue(state.taskServiceEntity.size);
    costItem.setUnit(QuotaUnit.COUNT);
    patch.cost.put(costItem.getKey(), costItem);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(ResourceTicketServiceFactory.SELF_LINK + "/" + resourceTicketId)
        .setBody(patch)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.RELEASE_SNAT_IP);
            patchState.taskServiceEntity = state.taskServiceEntity;
            patchState.taskServiceEntity.isSizeQuotaConsumed = false;
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Release floating IP that was used as SNAT IP on this virtual network's Tier1 router.
   */
  private void releaseSnatIp(DeleteVirtualNetworkWorkflowDocument state) {
    // In ISOLATED network, Tier1 router is not connected to Tier0 router hence we won't have SNAT IP.
    if (state.taskServiceEntity.routingType == RoutingType.ISOLATED) {
      ServiceUtils.logInfo(this, "ISOLATED network does not have SNAT IP hence moving to next step.");
      progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
      return;
    }

    DhcpSubnetService.IpOperationPatch releaseIp = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.ReleaseIp,
        DhcpSubnetService.VIRTUAL_NETWORK_SNAT_IP, null, state.taskServiceEntity.snatIp);

    CloudStoreUtils.patchAndProcess(
        this,
        DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK,
        releaseIp,
        DhcpSubnetService.IpOperationPatch.class,
        releaseIpResult -> {
          progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  /**
   * Deletes the NSX logical ports.
   */
  private void deleteLogicalPorts(DeleteVirtualNetworkWorkflowDocument state) {

    DeleteLogicalPortsTask deleteLogicalPortsTask = new DeleteLogicalPortsTask();
    deleteLogicalPortsTask.nsxAddress = state.nsxAddress;
    deleteLogicalPortsTask.nsxUsername = state.nsxUsername;
    deleteLogicalPortsTask.nsxPassword = state.nsxPassword;
    deleteLogicalPortsTask.logicalTier1RouterId = state.taskServiceEntity.logicalRouterId;
    deleteLogicalPortsTask.logicalTier0RouterId = state.taskServiceEntity.tier0RouterId;
    deleteLogicalPortsTask.logicalSwitchId = state.taskServiceEntity.logicalSwitchId;

    TaskUtils.startTaskAsync(
        this,
        DeleteLogicalPortsTaskService.FACTORY_LINK,
        deleteLogicalPortsTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        DeleteLogicalPortsTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<DeleteLogicalPortsTask>() {
          @Override
          public void onSuccess(DeleteLogicalPortsTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_ROUTER);
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to delete logical ports: %s", result.taskState.failure.message)));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            fail(state, t);
          }
        }
    );
  }

  /**
   * Deletes NSX logical router.
   */
  private void deleteLogicalRouter(DeleteVirtualNetworkWorkflowDocument state) {

    DeleteLogicalRouterTask deleteLogicalRouterTask = new DeleteLogicalRouterTask();
    deleteLogicalRouterTask.nsxAddress = state.nsxAddress;
    deleteLogicalRouterTask.nsxUsername = state.nsxUsername;
    deleteLogicalRouterTask.nsxPassword = state.nsxPassword;
    deleteLogicalRouterTask.logicalRouterId = state.taskServiceEntity.logicalRouterId;

    TaskUtils.startTaskAsync(
        this,
        DeleteLogicalRouterTaskService.FACTORY_LINK,
        deleteLogicalRouterTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        DeleteLogicalRouterTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<DeleteLogicalRouterTask>() {
          @Override
          public void onSuccess(DeleteLogicalRouterTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_SWITCH);
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to delete logical Tier-1 router: %s", result.taskState.failure.message)));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            fail(state, t);
          }
        }
    );
  }

  /**
   * Deletes a NSX logical switch.
   */
  private void deleteLogicalSwitch(DeleteVirtualNetworkWorkflowDocument state) {
    DeleteLogicalSwitchTask deleteLogicalSwitchTask = new DeleteLogicalSwitchTask();
    deleteLogicalSwitchTask.nsxAddress = state.nsxAddress;
    deleteLogicalSwitchTask.nsxUsername = state.nsxUsername;
    deleteLogicalSwitchTask.nsxPassword = state.nsxPassword;
    deleteLogicalSwitchTask.logicalSwitchId = state.taskServiceEntity.logicalSwitchId;

    TaskUtils.startTaskAsync(
        this,
        DeleteLogicalSwitchTaskService.FACTORY_LINK,
        deleteLogicalSwitchTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        DeleteLogicalSwitchTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<DeleteLogicalSwitchTask>() {
          @Override
          public void onSuccess(DeleteLogicalSwitchTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_DHCP_OPTION);
                } catch (Throwable t) {
                  fail(state, t);
                }
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to delete logical switch: %s", result.taskState.failure.message)));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            fail(state, t);
          }
        }
    );
  }

  /**
   * Deletes DHCP option.
   */
  private void deleteDhcpOption(DeleteVirtualNetworkWorkflowDocument state) {
    SubnetConfigurationTask subnetConfigurationTask = new SubnetConfigurationTask();
    subnetConfigurationTask.subnetConfiguration = new SubnetConfigurationTask.SubnetConfiguration();
    subnetConfigurationTask.subnetConfiguration.subnetId = state.networkId;
    subnetConfigurationTask.subnetConfiguration.subnetOperation = SubnetConfigurationTask.SubnetOperation.DELETE;

    Operation.createPost(UriUtils.buildUri(state.dhcpAgentEndpoint + SubnetConfigurationService.FACTORY_LINK))
        .setBody(subnetConfigurationTask)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_NETWORK_ENTITY);
        })
        .sendWith(this);
  }

  /**
   * Deletes a {@link com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService.State} entity
   * from cloud-store.
   */
  private void deleteVirtualNetwork(DeleteVirtualNetworkWorkflowDocument state) {

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createDelete(state.taskServiceEntity.documentSelfLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          createTombstoneTask(state);
        })
        .sendWith(this);
  }

  /**
   * Create a tombstone task for this virtual network.
   */
  private void createTombstoneTask(DeleteVirtualNetworkWorkflowDocument state) {
    TombstoneService.State tombstoneStartState = new TombstoneService.State();
    tombstoneStartState.entityId = state.networkId;
    tombstoneStartState.entityKind = VirtualSubnet.KIND;
    tombstoneStartState.tombstoneTime = System.currentTimeMillis();

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPost(TombstoneServiceFactory.SELF_LINK)
        .setBody(tombstoneStartState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.FINISHED,
                null);
            patchState.taskServiceEntity = state.taskServiceEntity;
            patchState.taskServiceEntity.state = SubnetState.DELETED;
            finish(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Gets a VirtualNetworkService.State from {@link com.vmware.photon.controller.cloudstore.xenon.entity
   * .VirtualNetworkService.State} entity in cloud-store.
   */
  private void getVirtualNetwork(DeleteVirtualNetworkWorkflowDocument state, Operation operation) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + state.networkId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            operation.fail(new Exception("Failed to get network " + state.networkId));
            return;
          }

          state.taskServiceEntity = op.getBody(VirtualNetworkService.State.class);
          create(state, operation);
        })
        .sendWith(this);
  }
}
