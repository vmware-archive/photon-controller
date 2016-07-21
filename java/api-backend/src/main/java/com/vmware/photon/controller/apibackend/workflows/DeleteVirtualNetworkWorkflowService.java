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

import com.vmware.photon.controller.api.model.SubnetState;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalPortsTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalRouterTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteVirtualNetworkWorkflowDocument;
import com.vmware.photon.controller.apibackend.tasks.DeleteLogicalPortsTaskService;
import com.vmware.photon.controller.apibackend.tasks.DeleteLogicalRouterTaskService;
import com.vmware.photon.controller.apibackend.tasks.DeleteLogicalSwitchTaskService;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
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
   * Processes the sub-stages of the workflow.
   */
  private void processPatch(DeleteVirtualNetworkWorkflowDocument state) {
    try {
      switch (state.taskState.subStage) {
        case GET_NSX_CONFIGURATION:
          getNsxConfiguration(state);
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
        case RELEASE_IP_ADDRESS_SPACE:
          releaseIpAddressSpace(state);
          break;
      }
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  /**
   * Gets NSX configuration from {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.State}
   * entity in cloud-store, and save the configuration in the document of the workflow service.
   */
  private void getNsxConfiguration(DeleteVirtualNetworkWorkflowDocument state) {

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
   * Deletes the NSX logical ports.
   */
  private void deleteLogicalPorts(DeleteVirtualNetworkWorkflowDocument state) {

    DeleteLogicalPortsTask deleteLogicalPortsTask = new DeleteLogicalPortsTask();
    deleteLogicalPortsTask.nsxManagerEndpoint = state.nsxManagerEndpoint;
    deleteLogicalPortsTask.username = state.username;
    deleteLogicalPortsTask.password = state.password;
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
   * Gets NSX configuration from {@link com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService.State}
   * entity in cloud-store, and saves the configuration in the document of the workflow service.
   */
  private void getNsxConfiguration(DeleteVirtualNetworkWorkflowDocument state, String deploymentServiceStateLink) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(deploymentServiceStateLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            DeploymentService.State deploymentState = op.getBody(DeploymentService.State.class);
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(
                TaskState.TaskStage.STARTED,
                DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.DELETE_LOGICAL_PORTS);
            patchState.nsxManagerEndpoint = deploymentState.networkManagerAddress;
            patchState.username = deploymentState.networkManagerUsername;
            patchState.password = deploymentState.networkManagerPassword;
            progress(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }

  /**
   * Deletes NSX logical router.
   */
  private void deleteLogicalRouter(DeleteVirtualNetworkWorkflowDocument state) {

    DeleteLogicalRouterTask deleteLogicalRouterTask = new DeleteLogicalRouterTask();
    deleteLogicalRouterTask.nsxManagerEndpoint = state.nsxManagerEndpoint;
    deleteLogicalRouterTask.username = state.username;
    deleteLogicalRouterTask.password = state.password;
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
    deleteLogicalSwitchTask.nsxManagerEndpoint = state.nsxManagerEndpoint;
    deleteLogicalSwitchTask.username = state.username;
    deleteLogicalSwitchTask.password = state.password;
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
                  progress(state, DeleteVirtualNetworkWorkflowDocument.TaskState.SubStage.RELEASE_IP_ADDRESS_SPACE);
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
   * Release IPs for the virtual network.
   */
  private void releaseIpAddressSpace(DeleteVirtualNetworkWorkflowDocument state) {

    SubnetAllocatorService.ReleaseSubnet releaseSubnet =
        new SubnetAllocatorService.ReleaseSubnet(state.virtualNetworkId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(SubnetAllocatorService.SINGLETON_LINK)
        .setBody(releaseSubnet)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            fail(state, ex);
            return;
          }

          try {
            deleteVirtualNetwork(state);
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
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + state.virtualNetworkId)
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

          try {
            DeleteVirtualNetworkWorkflowDocument patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            patchState.taskServiceEntity = state.taskServiceEntity;
            patchState.taskServiceEntity.state = SubnetState.DELETED;
            finish(state, patchState);
          } catch (Throwable t) {
            fail(state, t);
          }
        })
        .sendWith(this);
  }
}
