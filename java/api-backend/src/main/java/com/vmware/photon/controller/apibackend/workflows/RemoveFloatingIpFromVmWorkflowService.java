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

import com.vmware.photon.controller.apibackend.exceptions.RemoveFloatingIpFromVmException;
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmWorkflowDocument;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

import com.google.common.util.concurrent.FutureCallback;

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
        case REMOVE_NAT_RULE:
          removeNatRule(state);
          break;

        default:
          throw new RemoveFloatingIpFromVmException("Invalid task substage " + state.taskState.subStage);
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  private void removeNatRule(RemoveFloatingIpFromVmWorkflowDocument state) throws Throwable {
    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        state.nsxAddress,
        state.nsxUsername,
        state.nsxPassword)
        .getLogicalRouterApi();

    logicalRouterApi.deleteNatRule(state.taskServiceEntity.logicalRouterId,
        state.natRuleId,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            state.taskServiceEntity.natRuleToFloatingIpMap.remove(state.natRuleId);
            updateVirtualNetwork(state);
          }

          @Override
          public void onFailure(Throwable t) {
            fail(state, t);
          }
        });
  }

  /**
   * Gets a VirtualNetworkService.State from {@link com.vmware.photon.controller.cloudstore.xenon.entity
   * .VirtualNetworkService.State} entity in cloud-store.
   */
  private void getVirtualNetwork(RemoveFloatingIpFromVmWorkflowDocument state, Operation operation) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + state.networkId)
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
   * Updates a VirtualNetworkService.State entity in cloud-store.
   */
  private void updateVirtualNetwork(RemoveFloatingIpFromVmWorkflowDocument state) {
    VirtualNetworkService.State virtualNetworkPatchState = new VirtualNetworkService.State();
    virtualNetworkPatchState.natRuleToFloatingIpMap = state.taskServiceEntity.natRuleToFloatingIpMap;

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(state.taskServiceEntity.documentSelfLink)
        .setBody(virtualNetworkPatchState)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            RemoveFloatingIpFromVmWorkflowDocument patchState = buildPatch(
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
}
