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

import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.apibackend.exceptions.AssignFloatingIpToVmException;
import com.vmware.photon.controller.apibackend.exceptions.RemoveFloatingIpFromVmException;
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.CloudStoreUtils;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ProjectServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ResourceTicketServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;

/**
 * Implements an Xenon service that represents a workflow to remove the floating IP from a VM.
 */
public class RemoveFloatingIpFromVmWorkflowService extends BaseWorkflowService<RemoveFloatingIpFromVmWorkflowDocument,
    RemoveFloatingIpFromVmWorkflowDocument.TaskState, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage> {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/remove-floating-ip";

  public static FactoryService createFactory() {
    return FactoryService.create(RemoveFloatingIpFromVmWorkflowService.class,
        RemoveFloatingIpFromVmWorkflowDocument.class);
  }

  public RemoveFloatingIpFromVmWorkflowService() {
    super(RemoveFloatingIpFromVmWorkflowDocument.class,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.class,
        RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.class);
  }

  @Override
  public void handleCreate(Operation createOperation) {
    ServiceUtils.logInfo(this, "Creating service %s", getSelfLink());
    RemoveFloatingIpFromVmWorkflowDocument state =
        createOperation.getBody(RemoveFloatingIpFromVmWorkflowDocument.class);

    try {
      initializeState(state);
      validateState(state);

      if (ControlFlags.isOperationProcessingDisabled(state.controlFlags) ||
          ControlFlags.isHandleCreateDisabled(state.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping create operation processing (disabled)");
        createOperation.complete();
        return;
      }

      CloudStoreUtils.getAndProcess(
          this,
          VirtualNetworkService.FACTORY_LINK + "/" + state.networkId,
          VirtualNetworkService.State.class,
          virtualNetworkState -> {
            state.taskServiceEntity = virtualNetworkState;
            create(state, createOperation);
          },
          throwable -> {
            createOperation.fail(throwable);
            fail(state, throwable);
          }
      );
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
    RemoveFloatingIpFromVmWorkflowDocument state = startOperation.getBody(RemoveFloatingIpFromVmWorkflowDocument.class);

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
      RemoveFloatingIpFromVmWorkflowDocument currentState = getState(patchOperation);
      RemoveFloatingIpFromVmWorkflowDocument patchState =
          patchOperation.getBody(RemoveFloatingIpFromVmWorkflowDocument.class);
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

  private void processPatch(RemoveFloatingIpFromVmWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case GET_VM_MAC:
          getVmMac(state);
          break;
        case GET_NSX_CONFIGURATION:
          getNsxConfiguration(state);
          break;
        case REMOVE_DNAT_RULE:
          removeDnatRule(state);
          break;
        case REMOVE_SNAT_RULE:
          removeSnatRule(state);
          break;
        case RELEASE_VM_FLOATING_IP:
          releaseVmFloatingIp(state);
          break;
        case RELEASE_QUOTA:
          releaseQuota(state);
          break;
        case UPDATE_VM:
          updateVm(state);
          break;
        case UPDATE_VIRTUAL_NETWORK:
          updateVirtualNetwork(state);
          break;
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  private void getVmMac(RemoveFloatingIpFromVmWorkflowDocument state) {
    CloudStoreUtils.getAndProcess(
        this,
        VmServiceFactory.SELF_LINK + "/" + state.vmId,
        VmService.State.class,
        vmState -> {
          try {
            if (!vmState.networkInfo.containsKey(state.networkId)) {
              throw new RemoveFloatingIpFromVmException(
                  "VM " + state.vmId + " does not belong to network " + state.networkId);
            }

            VmService.NetworkInfo vmNetworkInfo = vmState.networkInfo.get(state.networkId);

            if (StringUtils.isBlank(vmNetworkInfo.macAddress)) {
              throw new AssignFloatingIpToVmException(
                  "VM " + state.vmId + " does not have a MAC address on network " + state.networkId);
            }

            RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.GET_NSX_CONFIGURATION);
            patchState.vmMacAddress = vmNetworkInfo.macAddress;
            patchState.vmFloatingIpAddress = vmNetworkInfo.floatingIpAddress;

            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void getNsxConfiguration(RemoveFloatingIpFromVmWorkflowDocument state) {
    CloudStoreUtils.queryAndProcess(
        this,
        DeploymentService.State.class,
        deploymentState -> {
          try {
            RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_DNAT_RULE);
            patchState.nsxAddress = deploymentState.networkManagerAddress;
            patchState.nsxUsername = deploymentState.networkManagerUsername;
            patchState.nsxPassword = deploymentState.networkManagerPassword;
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

  private void removeDnatRule(RemoveFloatingIpFromVmWorkflowDocument state) throws Throwable {
    if (!state.taskServiceEntity.vmIdToDnatRuleIdMap.containsKey(state.vmId)) {
      throw new RemoveFloatingIpFromVmException("VM " + state.vmId + " does not have a DNAT rule associated");
    }

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        state.nsxAddress,
        state.nsxUsername,
        state.nsxPassword)
        .getLogicalRouterApi();

    logicalRouterApi.deleteNatRule(state.taskServiceEntity.logicalRouterId,
        state.taskServiceEntity.vmIdToDnatRuleIdMap.get(state.vmId),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            try {
              RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                  TaskState.TaskStage.STARTED,
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_SNAT_RULE);
              patchState.taskServiceEntity = state.taskServiceEntity;
              patchState.taskServiceEntity.vmIdToDnatRuleIdMap.remove(state.vmId);

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

  private void removeSnatRule(RemoveFloatingIpFromVmWorkflowDocument state) throws Throwable {
    if (!state.taskServiceEntity.vmIdToSnatRuleIdMap.containsKey(state.vmId)) {
      throw new RemoveFloatingIpFromVmException("VM " + state.vmId + " does not have a SNAT rule associated");
    }

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        state.nsxAddress,
        state.nsxUsername,
        state.nsxPassword)
        .getLogicalRouterApi();

    logicalRouterApi.deleteNatRule(state.taskServiceEntity.logicalRouterId,
        state.taskServiceEntity.vmIdToSnatRuleIdMap.get(state.vmId),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            try {
              RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                  TaskState.TaskStage.STARTED,
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.RELEASE_VM_FLOATING_IP);
              patchState.taskServiceEntity = state.taskServiceEntity;
              patchState.taskServiceEntity.vmIdToSnatRuleIdMap.remove(state.vmId);

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

  private void releaseVmFloatingIp(RemoveFloatingIpFromVmWorkflowDocument state) {
    DhcpSubnetService.IpOperationPatch releaseIp = new DhcpSubnetService.IpOperationPatch(
        DhcpSubnetService.IpOperationPatch.Kind.ReleaseIp,
        state.vmId, null, state.vmFloatingIpAddress);

    CloudStoreUtils.patchAndProcess(
        this,
        DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK,
        releaseIp,
        DhcpSubnetService.IpOperationPatch.class,
        releaseIpResult -> {
          progress(state, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.RELEASE_QUOTA);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void releaseQuota(RemoveFloatingIpFromVmWorkflowDocument state) {
    CloudStoreUtils.getAndProcess(
        this,
        VmServiceFactory.SELF_LINK + "/" + state.vmId,
        VmService.State.class,
        vmState -> {
          VmService.NetworkInfo vmNetworkInfo = vmState.networkInfo.get(state.networkId);
          if (!vmNetworkInfo.isFloatingIpQuotaConsumed) {
            ServiceUtils.logInfo(RemoveFloatingIpFromVmWorkflowService.this,
                "The quota was not consumed. Skip releasing quota.");
            progress(state, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.UPDATE_VM);
            return;
          }

          releaseQuota(state, vmState.projectId);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void releaseQuota(RemoveFloatingIpFromVmWorkflowDocument state, String projectId) {
    CloudStoreUtils.getAndProcess(
        this,
        ProjectServiceFactory.SELF_LINK + "/" + projectId,
        ProjectService.State.class,
        projectState -> {
          returnQuota(state, projectState.resourceTicketId);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void returnQuota(RemoveFloatingIpFromVmWorkflowDocument state, String resourceTicketId) {

    ResourceTicketService.Patch patch = new ResourceTicketService.Patch();
    patch.patchtype = ResourceTicketService.Patch.PatchType.USAGE_RETURN;
    patch.cost = new HashMap<>();

    QuotaLineItem costItem = new QuotaLineItem();
    costItem.setKey(AssignFloatingIpToVmWorkflowService.SDN_FLOATING_IP_RESOURCE_TICKET_KEY);
    costItem.setValue(1);
    costItem.setUnit(QuotaUnit.COUNT);
    patch.cost.put(costItem.getKey(), costItem);

    CloudStoreUtils.patchAndProcess(
        this,
        ResourceTicketServiceFactory.SELF_LINK + "/" + resourceTicketId,
        patch,
        ResourceTicketService.Patch.class,
        resourceTicketPatch -> {
          // We don't need to update the VM immediately here as we do in AssignFloatingIp workflow,
          // since the next step is updating VM.
          progress(state, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.UPDATE_VM);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void updateVm(RemoveFloatingIpFromVmWorkflowDocument state) {
    CloudStoreUtils.getAndProcess(
        this,
        VmServiceFactory.SELF_LINK + "/" + state.vmId,
        VmService.State.class,
        vmState -> {
          VmService.NetworkInfo vmNetworkInfo = vmState.networkInfo.get(state.networkId);
          vmNetworkInfo.floatingIpAddress = null;
          // We set the quota consumption flag to false no matter if the quota release
          // was skipped or not - if we proceed to this step, it means the either IP has
          // been returned or it was not assigned successfully earlier.
          vmNetworkInfo.isFloatingIpQuotaConsumed = false;
          vmState.networkInfo.put(state.networkId, vmNetworkInfo);

          VmService.State vmPatchState = new VmService.State();
          vmPatchState.networkInfo = vmState.networkInfo;

          updateVm(state, vmPatchState);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void updateVm(RemoveFloatingIpFromVmWorkflowDocument state, VmService.State vmPatchState) {
    CloudStoreUtils.patchAndProcess(
        this,
        VmServiceFactory.SELF_LINK + "/" + state.vmId,
        vmPatchState,
        VmService.State.class,
        vmState -> {
          progress(state, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void updateVirtualNetwork(RemoveFloatingIpFromVmWorkflowDocument state) {
    VirtualNetworkService.State virtualNetworkPatchState = new VirtualNetworkService.State();
    virtualNetworkPatchState.vmIdToDnatRuleIdMap = state.taskServiceEntity.vmIdToDnatRuleIdMap;
    virtualNetworkPatchState.vmIdToSnatRuleIdMap = state.taskServiceEntity.vmIdToSnatRuleIdMap;

    CloudStoreUtils.patchAndProcess(
        this,
        VirtualNetworkService.FACTORY_LINK + "/" + state.networkId,
        virtualNetworkPatchState,
        VirtualNetworkService.State.class,
        virtualNetworkState -> {
          try {
            RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.FINISHED,
                null);
            patchState.taskServiceEntity = state.taskServiceEntity;
            finish(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }
}
