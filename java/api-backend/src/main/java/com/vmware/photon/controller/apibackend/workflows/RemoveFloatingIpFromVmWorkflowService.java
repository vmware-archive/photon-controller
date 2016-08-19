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

import com.vmware.photon.controller.apibackend.exceptions.AssignFloatingIpToVmException;
import com.vmware.photon.controller.apibackend.exceptions.RemoveFloatingIpFromVmException;
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.CloudStoreUtils;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
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

      CloudStoreUtils.getCloudStoreEntityAndProcess(
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
        case REMOVE_NAT_RULE:
          removeNatRule(state);
          break;
        case RELEASE_VM_FLOATING_IP:
          releaseVmFloatingIp(state);
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
    CloudStoreUtils.getCloudStoreEntityAndProcess(
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
                RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.REMOVE_NAT_RULE);
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

  private void removeNatRule(RemoveFloatingIpFromVmWorkflowDocument state) throws Throwable {
    if (!state.taskServiceEntity.vmIdToNatRuleIdMap.containsKey(state.vmId)) {
      throw new RemoveFloatingIpFromVmException("VM " + state.vmId + " does not have a NAT rule associated");
    }

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        state.nsxAddress,
        state.nsxUsername,
        state.nsxPassword)
        .getLogicalRouterApi();

    logicalRouterApi.deleteNatRule(state.taskServiceEntity.logicalRouterId,
        state.taskServiceEntity.vmIdToNatRuleIdMap.get(state.vmId),
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            try {
              RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
                  TaskState.TaskStage.STARTED,
                  RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.RELEASE_VM_FLOATING_IP);
              patchState.taskServiceEntity = state.taskServiceEntity;
              patchState.taskServiceEntity.vmIdToNatRuleIdMap.remove(state.vmId);

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

    CloudStoreUtils.patchCloudStoreEntityAndProcess(
        this,
        DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK,
        releaseIp,
        DhcpSubnetService.IpOperationPatch.class,
        releaseIpResult -> {
          progress(state, RemoveFloatingIpFromVmWorkflowDocument.TaskState.SubStage.UPDATE_VM);
        },
        throwable -> {
          fail(state, throwable);
        }
    );
  }

  private void updateVm(RemoveFloatingIpFromVmWorkflowDocument state) {
    CloudStoreUtils.getCloudStoreEntityAndProcess(
        this,
        VmServiceFactory.SELF_LINK + "/" + state.vmId,
        VmService.State.class,
        vmState -> {
          VmService.NetworkInfo vmNetworkInfo = vmState.networkInfo.get(state.networkId);
          vmNetworkInfo.floatingIpAddress = null;
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
    CloudStoreUtils.patchCloudStoreEntityAndProcess(
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
    virtualNetworkPatchState.vmIdToNatRuleIdMap = state.taskServiceEntity.vmIdToNatRuleIdMap;

    CloudStoreUtils.patchCloudStoreEntityAndProcess(
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
