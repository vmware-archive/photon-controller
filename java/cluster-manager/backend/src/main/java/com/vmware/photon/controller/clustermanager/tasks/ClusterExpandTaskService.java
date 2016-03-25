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
package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Vm;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.clustermanager.rolloutplans.SlavesNodeRollout;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.templates.KubernetesSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.MesosSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.templates.SwarmSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.util.ClusterUtil;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * This class implements a DCP service representing a task to expand a cluster.
 */
public class ClusterExpandTaskService extends StatefulService {

  public ClusterExpandTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getCluster(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getCluster(final State currentState) throws IOException {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  ClusterService.State clusterDocument = operation.getBody(ClusterService.State.class);

                  try {
                    initializeExpandCluster(currentState, clusterDocument);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void initializeExpandCluster(final State currentState,
                                       final ClusterService.State clusterDocument) throws IOException {

    HostUtils.getApiClient(this).getClusterApi().getVmsInClusterAsync(
        currentState.clusterId,
        new FutureCallback<ResourceList<Vm>>() {
          @Override
          public void onSuccess(@Nullable ResourceList<Vm> result) {
            int currentSlaveCount = 0;
            String masterNodeTag;
            String slaveNodeTag;
            switch (clusterDocument.clusterType) {
              case KUBERNETES:
                masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.KubernetesMaster);
                slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.KubernetesSlave);
                break;
              case MESOS:
                masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.MesosMaster);
                slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.MesosSlave);
                break;
              case SWARM:
                masterNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.SwarmMaster);
                slaveNodeTag = ClusterUtil.createClusterNodeTag(currentState.clusterId, NodeType.SwarmSlave);
                break;
              default:
                throw new UnsupportedOperationException(
                    "ClusterType is not supported. ClusterType: " + clusterDocument.clusterType);
            }

            String masterVmId = null;
            for (Vm vm : result.getItems()) {
              if (vm.getTags().contains(slaveNodeTag)) {
                ++currentSlaveCount;
              } else if (vm.getTags().contains(masterNodeTag)) {
                if (masterVmId == null) {
                  masterVmId = vm.getId();
                }
              }
            }

            int slaveCountDelta = clusterDocument.slaveCount - currentSlaveCount;

            if (slaveCountDelta < 0) {
              String errorMessage = String.format(
                  "Slave count delta %d is negative. Target slave count is %d, current slave count is %d",
                  slaveCountDelta, clusterDocument.slaveCount, currentSlaveCount);
              ServiceUtils.logSevere(ClusterExpandTaskService.this, errorMessage);
              failTask(new IllegalStateException(errorMessage));
              return;
            }

            if (masterVmId == null) {
              String errorMessage = "No master vm is found.";
              ServiceUtils.logSevere(ClusterExpandTaskService.this, errorMessage);
              failTask(new IllegalStateException(errorMessage));
              return;
            }

            getMasterIp(currentState, clusterDocument, slaveCountDelta, masterVmId);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void getMasterIp(final State currentState,
                           final ClusterService.State clusterDocument,
                           final int slaveCountDelta,
                           final String masterVmId) {
    WaitForNetworkTaskService.State startState = new WaitForNetworkTaskService.State();
    startState.vmId = masterVmId;

    TaskUtils.startTaskAsync(
        this,
        WaitForNetworkTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        WaitForNetworkTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<WaitForNetworkTaskService.State>() {
          @Override
          public void onSuccess(@Nullable WaitForNetworkTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  expandCluster(currentState, clusterDocument, slaveCountDelta, result.vmIpAddress);
                } catch (Throwable t) {
                  failTask(t);
                }
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(ClusterExpandTaskService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED));
                break;
              case FAILED:
                TaskUtils.sendSelfPatch(ClusterExpandTaskService.this,
                    buildPatch(TaskState.TaskStage.FAILED, result.taskState.failure));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void expandCluster(State currentState,
                             ClusterService.State clusterDocument,
                             int slaveCountDelta,
                             String masterIp) {

    if (slaveCountDelta > 0) {
      ServiceUtils.logInfo(this, String.format(
          "Expected slave count is %d, delta is %d",
          clusterDocument.slaveCount,
          slaveCountDelta));

      NodeRolloutInput input = new NodeRolloutInput();
      input.clusterId = currentState.clusterId;
      input.nodeCount = Math.min(slaveCountDelta, currentState.batchExpansionSize);
      input.imageId = clusterDocument.imageId;
      input.diskFlavorName = clusterDocument.diskFlavorName;
      input.vmFlavorName = clusterDocument.otherVmFlavorName;
      input.vmNetworkId = clusterDocument.vmNetworkId;
      input.projectId = clusterDocument.projectId;

      switch (clusterDocument.clusterType) {
        case KUBERNETES: {
          List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(
              clusterDocument.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS));
          String cn = clusterDocument.extendedProperties.get(
              ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);

          input.serverAddress = masterIp;
          input.nodeProperties = KubernetesSlaveNodeTemplate.createProperties(etcdIps, cn, masterIp);
          input.nodeType = NodeType.KubernetesSlave;
          break;
        }
        case MESOS: {
          List<String> zkIps = NodeTemplateUtils.deserializeAddressList(
              clusterDocument.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS));

          input.serverAddress = masterIp;
          input.nodeProperties = MesosSlaveNodeTemplate.createProperties(zkIps);
          input.nodeType = NodeType.MesosSlave;
          break;
        }
        case SWARM: {
          List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(
              clusterDocument.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS));

          input.serverAddress = masterIp;
          input.nodeProperties = SwarmSlaveNodeTemplate.createProperties(etcdIps);
          input.nodeType = NodeType.SwarmSlave;
          break;
        }
        default:
          failTask(new UnsupportedOperationException(
              "ClusterType is not supported for resizing. ClusterType: " + clusterDocument.clusterType));
          return;
      }

      NodeRollout rollout = new SlavesNodeRollout();
      rollout.run(this, input, new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(@Nullable NodeRolloutResult result) {
          expandCluster(currentState, clusterDocument, slaveCountDelta - input.nodeCount, masterIp);
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } else {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED));
    }
  }

  private void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private State buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private State buildPatch(
      TaskState.TaskStage stage,
      @Nullable Throwable t) {
    return buildPatch(stage, t == null ? null : Utils.toServiceErrorResponse(t));
  }

  private State buildPatch(
      TaskState.TaskStage stage,
      @Nullable ServiceErrorResponse errorResponse) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * This class defines the document state associated with a single
   * {@link ClusterExpandTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    /**
     * The state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the task.
     */
    @DefaultInteger(0)
    @Immutable
    public Integer controlFlags;

    /**
     * The identifier of the cluster.
     */
    @NotBlank
    @Immutable
    public String clusterId;

    /**
     * The threshold for each expansion batch.
     */
    @DefaultInteger(value = ClusterManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
    @Immutable
    public Integer batchExpansionSize;
  }
}
