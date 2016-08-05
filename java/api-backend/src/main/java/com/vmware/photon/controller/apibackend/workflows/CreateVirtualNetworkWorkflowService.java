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

package com.vmware.photon.controller.apibackend.workflows;

import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureRoutingTask;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalRouterTask;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.tasks.ConfigureRoutingTaskService;
import com.vmware.photon.controller.apibackend.tasks.CreateLogicalRouterTaskService;
import com.vmware.photon.controller.apibackend.tasks.CreateLogicalSwitchTaskService;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashMap;
import java.util.Set;

/**
 * This class implements a Xenon service representing a workflow to create a virtual network.
 */
public class CreateVirtualNetworkWorkflowService extends BaseWorkflowService<CreateVirtualNetworkWorkflowDocument,
    CreateVirtualNetworkWorkflowDocument.TaskState, CreateVirtualNetworkWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-virtual-network";

  public static final String DEFAULT_TIER1_ROUTER_DOWNLINK_PORT_IP = "192.168.0.1";
  public static final String SDN_RESOURCE_TICKET_KEY = "sdn.size";

  public static final int DEFAULT_TIER1_ROUTER_DOWNLINK_PORT_IP_PREFIX_LEN = 16;

  public static FactoryService createFactory() {
    return FactoryService.create(CreateVirtualNetworkWorkflowService.class, CreateVirtualNetworkWorkflowDocument.class);
  }

  public CreateVirtualNetworkWorkflowService() {
    super(CreateVirtualNetworkWorkflowDocument.class,
        CreateVirtualNetworkWorkflowDocument.TaskState.class,
        CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    CreateVirtualNetworkWorkflowDocument state =
        createOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      createVirtualNetwork(state, createOperation);
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

    try {
      CreateVirtualNetworkWorkflowDocument state =
          startOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);

      initializeState(state);
      validateStartState(state);

      startOperation.setBody(state).complete();

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleStartDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled");
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
      CreateVirtualNetworkWorkflowDocument currentState = getState(patchOperation);
      ServiceUtils.logInfo(this, "Service document before patching %s", currentState.toString());

      CreateVirtualNetworkWorkflowDocument patchState =
          patchOperation.getBody(CreateVirtualNetworkWorkflowDocument.class);
      validatePatchState(currentState, patchState);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      ServiceUtils.logInfo(this, "Service document after patching %s", currentState.toString());

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
   * Processes the sub-stages of the workflow.
   */
  private void processPatch(CreateVirtualNetworkWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case ENFORCE_QUOTA:
          enforceQuotas(state);
          break;
        case ALLOCATE_IP_ADDRESS_SPACE:
          allocateIpAddressSpace(state);
          break;
        case GET_IP_ADDRESS_SPACE:
          getIpAddressSpace(state);
          break;
        case GET_NSX_CONFIGURATION:
          getNsxConfiguration(state);
          break;
        case CREATE_LOGICAL_SWITCH:
          createLogicalSwitch(state);
          break;
        case CREATE_LOGICAL_ROUTER:
          createLogicalRouter(state);
          break;
        case SET_UP_LOGICAL_ROUTER:
          setUpLogicalRouter(state);
          break;
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Enforce quotas for allocations.
   */
  private void enforceQuotas(CreateVirtualNetworkWorkflowDocument state) {
    checkArgument(state.parentId != null, "parentId should not be null.");

    switch (state.parentKind) {
      case Project.KIND:
        String id = state.parentId;
        ServiceHostUtils.getCloudStoreHelper(getHost())
            .createGet(ProjectServiceFactory.SELF_LINK + "/" + id)
            .setCompletion((op, ex) -> {
              if (ex != null) {
                fail(state, ex);
                return;
              }
              ProjectService.State project = op.getBody(ProjectService.State.class);
              String resourceTicketId = project.resourceTicketId;

              consumeQuota(state, resourceTicketId);
            }).sendWith(this);
        break;
      default:
        throw new IllegalArgumentException("Unknown parentKind: " + state.parentKind);
    }
  }

  /**
   * Consume quota.
   */
  private void consumeQuota(CreateVirtualNetworkWorkflowDocument state, String resourceTicketId) {
    ResourceTicketService.Patch patch = new ResourceTicketService.Patch();
    patch.patchtype = ResourceTicketService.Patch.PatchType.USAGE_CONSUME;
    patch.cost = new HashMap<>();

    QuotaLineItem costItem = new QuotaLineItem();
    costItem.setKey(SDN_RESOURCE_TICKET_KEY);
    costItem.setValue(state.size);
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
            CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.ALLOCATE_IP_ADDRESS_SPACE);
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Allocate IPs for the virtual network.
   */
  private void allocateIpAddressSpace(CreateVirtualNetworkWorkflowDocument state) {

    SubnetAllocatorService.AllocateSubnet allocateSubnet =
        new SubnetAllocatorService.AllocateSubnet(
            ServiceUtils.getIDFromDocumentSelfLink(state.taskServiceEntity.documentSelfLink),
            (long) state.size, (long) state.reservedStaticIpSize);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(SubnetAllocatorService.SINGLETON_LINK)
        .setBody(allocateSubnet)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_IP_ADDRESS_SPACE);
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Get IP range allocated for the virtual network.
   */
  private void getIpAddressSpace(CreateVirtualNetworkWorkflowDocument state) {

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(DhcpSubnetService.FACTORY_LINK + "/" +
            ServiceUtils.getIDFromDocumentSelfLink(state.taskServiceEntity.documentSelfLink))
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            DhcpSubnetService.State subnet = op.getBody(DhcpSubnetService.State.class);
            CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);
            patchState.taskServiceEntity = state.taskServiceEntity;
            patchState.taskServiceEntity.cidr = subnet.cidr;
            patchState.taskServiceEntity.lowIpDynamic = convertLongToDottedIp(subnet.lowIpDynamic);
            patchState.taskServiceEntity.highIpDynamic = convertLongToDottedIp(subnet.highIpDynamic);
            patchState.taskServiceEntity.lowIpStatic = convertLongToDottedIp(subnet.lowIpStatic);
            patchState.taskServiceEntity.highIpStatic = convertLongToDottedIp(subnet.highIpStatic);
            if (subnet.reservedIpList != null && subnet.reservedIpList.isEmpty()) {
              for (Long ip : subnet.reservedIpList) {
                patchState.taskServiceEntity.reservedIpList.add(convertLongToDottedIp(ip));
              }
            }
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Gets NSX configuration from {@link DeploymentService.State} entity in cloud-store, and save
   * the configuration in the document of the workflow service.
   */
  private void getNsxConfiguration(CreateVirtualNetworkWorkflowDocument state) {

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

          getNsxConfiguration(state, documentLinks.iterator().next());
        })
        .sendWith(this);
  }

  /**
   * Gets NSX configuration from {@link DeploymentService.State} entity in cloud-store, and saves
   * the configuration in the document of the workflow service.
   */
  private void getNsxConfiguration(CreateVirtualNetworkWorkflowDocument state,
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
            CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_SWITCH);
            patchState.nsxManagerEndpoint = deploymentState.networkManagerAddress;
            patchState.username = deploymentState.networkManagerUsername;
            patchState.password = deploymentState.networkManagerPassword;
            patchState.transportZoneId = deploymentState.networkZoneId;
            patchState.tier0RouterId = deploymentState.networkTopRouterId;
            patchState.dhcpRelayServiceId = deploymentState.dhcpRelayServiceId;
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Creates a NSX logical switch, and saves the ID of the logical switch in the document of
   * the workflow service.
   */
  private void createLogicalSwitch(CreateVirtualNetworkWorkflowDocument state) {
    CreateLogicalSwitchTask createLogicalSwitchTask = new CreateLogicalSwitchTask();
    createLogicalSwitchTask.nsxAddress = state.nsxManagerEndpoint;
    createLogicalSwitchTask.nsxUsername = state.username;
    createLogicalSwitchTask.nsxPassword = state.password;
    createLogicalSwitchTask.virtualNetworkId = getVirtualNetworkId(state);
    createLogicalSwitchTask.transportZoneId = state.transportZoneId;
    createLogicalSwitchTask.executionDelay = state.executionDelay;

    TaskUtils.startTaskAsync(
        this,
        CreateLogicalSwitchTaskService.FACTORY_LINK,
        createLogicalSwitchTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        CreateLogicalSwitchTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<CreateLogicalSwitchTask>() {
          @Override
          public void onSuccess(CreateLogicalSwitchTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                      TaskState.TaskStage.STARTED,
                      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.CREATE_LOGICAL_ROUTER);
                  patchState.taskServiceEntity = state.taskServiceEntity;
                  patchState.taskServiceEntity.logicalSwitchId = result.logicalSwitchId;
                  progress(state, patchState);
                } catch (Throwable t) {
                  fail(state, t);
                }
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to create logical switch: %s", result.taskState.failure.message)));
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
   * Creates a NSX logical router, and saves the ID of the logical router in the document of
   * the workflow service.
   */
  private void createLogicalRouter(CreateVirtualNetworkWorkflowDocument state) {
    CreateLogicalRouterTask createLogicalRouterTask = new CreateLogicalRouterTask();
    createLogicalRouterTask.nsxAddress = state.nsxManagerEndpoint;
    createLogicalRouterTask.nsxUsername = state.username;
    createLogicalRouterTask.nsxPassword = state.password;
    createLogicalRouterTask.virtualNetworkId = getVirtualNetworkId(state);

    TaskUtils.startTaskAsync(
        this,
        CreateLogicalRouterTaskService.FACTORY_LINK,
        createLogicalRouterTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        CreateLogicalRouterTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<CreateLogicalRouterTask>() {
          @Override
          public void onSuccess(CreateLogicalRouterTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  CreateVirtualNetworkWorkflowDocument patchState = buildPatch(
                      TaskState.TaskStage.STARTED,
                      CreateVirtualNetworkWorkflowDocument.TaskState.SubStage.SET_UP_LOGICAL_ROUTER);
                  patchState.taskServiceEntity = state.taskServiceEntity;
                  patchState.taskServiceEntity.logicalRouterId = result.logicalRouterId;
                  progress(state, patchState);
                } catch (Throwable t) {
                  fail(state, t);
                }
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to create logical switch: %s", result.taskState.failure.message)));
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
   * Configures the NSX logical router.
   */
  private void setUpLogicalRouter(CreateVirtualNetworkWorkflowDocument state) {
    ConfigureRoutingTask configureRoutingTask = new ConfigureRoutingTask();
    configureRoutingTask.routingType = state.routingType;
    configureRoutingTask.nsxAddress = state.nsxManagerEndpoint;
    configureRoutingTask.nsxUsername = state.username;
    configureRoutingTask.nsxPassword = state.password;
    configureRoutingTask.virtualNetworkId = getVirtualNetworkId(state);
    configureRoutingTask.dhcpRelayServiceId = state.dhcpRelayServiceId;
    configureRoutingTask.logicalSwitchId = state.taskServiceEntity.logicalSwitchId;
    configureRoutingTask.logicalTier1RouterId = state.taskServiceEntity.logicalRouterId;
    configureRoutingTask.logicalTier1RouterDownLinkPortIp = DEFAULT_TIER1_ROUTER_DOWNLINK_PORT_IP;
    configureRoutingTask.logicalTier1RouterDownLinkPortIpPrefixLen = DEFAULT_TIER1_ROUTER_DOWNLINK_PORT_IP_PREFIX_LEN;
    configureRoutingTask.logicalTier0RouterId = state.tier0RouterId;

    TaskUtils.startTaskAsync(
        this,
        ConfigureRoutingTaskService.FACTORY_LINK,
        configureRoutingTask,
        (st) -> TaskUtils.finalTaskStages.contains(st.taskState.stage),
        ConfigureRoutingTask.class,
        state.subTaskPollIntervalInMilliseconds,
        new FutureCallback<ConfigureRoutingTask>() {
          @Override
          public void onSuccess(ConfigureRoutingTask result) {
            switch (result.taskState.stage) {
              case FINISHED:
                state.taskServiceEntity.logicalSwitchUplinkPortId = result.logicalSwitchPortId;
                state.taskServiceEntity.logicalRouterDownlinkPortId = result.logicalTier1RouterDownLinkPort;
                state.taskServiceEntity.logicalRouterUplinkPortId = result.logicalLinkPortOnTier1Router;
                state.taskServiceEntity.tier0RouterDownlinkPortId = result.logicalLinkPortOnTier0Router;
                state.taskServiceEntity.tier0RouterId = state.tier0RouterId;
                finish(state);
                break;
              case FAILED:
              case CANCELLED:
                fail(state, new IllegalStateException(
                    String.format("Failed to configure routing: %s", result.taskState.failure.message)));
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
   * Updates the VirtualNetwork entity in cloud-store and Moves the service to the FAILED state.
   */
  protected void fail(CreateVirtualNetworkWorkflowDocument state, Throwable throwable) {
    updateVirtualNetwork(state, SubnetState.ERROR);
    super.fail(state, throwable);
  }

  /**
   * Updates the VirtualNetwork entity in cloud-store and Moves the service to the FINISHED state.
   */
  protected void finish(CreateVirtualNetworkWorkflowDocument state) {
    updateVirtualNetwork(state, SubnetState.READY);
    try {
      CreateVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
      patchState.taskServiceEntity = state.taskServiceEntity;
      patchState.taskServiceEntity.state = SubnetState.READY;
      finish(state, patchState);
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Updates the VirtualNetwork entity in cloud-store.
   */
  private void updateVirtualNetwork(CreateVirtualNetworkWorkflowDocument state, SubnetState subnetState) {
    VirtualNetworkService.State virtualNetworkPatchState = new VirtualNetworkService.State();
    virtualNetworkPatchState.state = subnetState;
    virtualNetworkPatchState.logicalSwitchId = state.taskServiceEntity.logicalSwitchId;
    virtualNetworkPatchState.logicalRouterId = state.taskServiceEntity.logicalRouterId;
    virtualNetworkPatchState.logicalSwitchUplinkPortId = state.taskServiceEntity.logicalSwitchUplinkPortId;
    virtualNetworkPatchState.logicalRouterDownlinkPortId = state.taskServiceEntity.logicalRouterDownlinkPortId;
    virtualNetworkPatchState.logicalRouterUplinkPortId = state.taskServiceEntity.logicalRouterUplinkPortId;
    virtualNetworkPatchState.tier0RouterDownlinkPortId = state.taskServiceEntity.tier0RouterDownlinkPortId;
    virtualNetworkPatchState.tier0RouterId = state.taskServiceEntity.tier0RouterId;
    virtualNetworkPatchState.cidr = state.taskServiceEntity.cidr;
    virtualNetworkPatchState.lowIpDynamic = state.taskServiceEntity.lowIpDynamic;
    virtualNetworkPatchState.highIpDynamic = state.taskServiceEntity.highIpDynamic;
    virtualNetworkPatchState.lowIpStatic = state.taskServiceEntity.lowIpStatic;
    virtualNetworkPatchState.highIpStatic = state.taskServiceEntity.highIpStatic;
    virtualNetworkPatchState.reservedIpList = state.taskServiceEntity.reservedIpList;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(state.taskServiceEntity.documentSelfLink)
        .setBody(virtualNetworkPatchState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            ServiceUtils.logSevere(this, ex);
          }
        })
        .sendWith(this);
  }

  /**
   * Creates a VirtualNetwork entity in cloud-store.
   */
  private void createVirtualNetwork(
      CreateVirtualNetworkWorkflowDocument state,
      Operation operation) {

    VirtualNetworkService.State postState = new VirtualNetworkService.State();
    postState.parentId = state.parentId;
    postState.parentKind = state.parentKind;
    postState.name = state.name;
    postState.description = state.description;
    postState.state = SubnetState.CREATING;
    postState.routingType = state.routingType;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPost(VirtualNetworkService.FACTORY_LINK)
        .setBody(postState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            operation.fail(ex);
            fail(state, ex);
            return;
          }

          state.taskServiceEntity = op.getBody(VirtualNetworkService.State.class);
          create(state, operation);
        })
        .sendWith(this);
  }

  /**
   * Gets the ID of the virtual network.
   */
  private String getVirtualNetworkId(CreateVirtualNetworkWorkflowDocument state) {
    return ServiceUtils.getIDFromDocumentSelfLink(state.taskServiceEntity.documentSelfLink);
  }

  /**
   * Converts IP from long IP to dotted IP String.
   */
  private String convertLongToDottedIp(Long ip) {
    if (ip == null) {
      return null;
    }
    return IpHelper.longToIp(ip).getHostAddress();
  }
}
