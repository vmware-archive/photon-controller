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

import com.vmware.photon.controller.api.ClusterState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.clustermanager.rolloutplans.SlavesNodeRollout;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants.Mesos;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.MesosClusterCreateTask.TaskState;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.templates.MarathonNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.MesosMasterNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.MesosSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.templates.ZookeeperNodeTemplate;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * This class implements a Xenon service representing a task to create a Mesos cluster.
 */
public class MesosClusterCreateTaskService extends StatefulService {

  private static final int MINIMUM_INITIAL_SLAVE_COUNT = 1;

  public MesosClusterCreateTaskService() {
    super(MesosClusterCreateTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    MesosClusterCreateTask startState = start.getBody(MesosClusterCreateTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.SETUP_ZOOKEEPERS;
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
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, TaskState.SubStage.SETUP_ZOOKEEPERS));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    MesosClusterCreateTask currentState = getState(patch);
    MesosClusterCreateTask patchState = patch.getBody(MesosClusterCreateTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(MesosClusterCreateTask currentState) {
    switch (currentState.taskState.subStage) {
      case SETUP_ZOOKEEPERS:
        setupZookeepers(currentState);
        break;

      case SETUP_MASTERS:
        setupMasters(currentState);
        break;

      case SETUP_MARATHON:
        setupMarathon(currentState);
        break;

      case SETUP_SLAVES:
        setupInitialSlaves(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method roll-outs Zookeeper nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_MASTERS.
   *
   * @param currentState
   */
  private void setupZookeepers(MesosClusterCreateTask currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ClusterService.State cluster = operation.getBody(ClusterService.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.vmFlavorName = cluster.otherVmFlavorName;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.nodeCount = NodeTemplateUtils.deserializeAddressList(
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)).size();
          rolloutInput.nodeType = NodeType.MesosZookeeper;
          rolloutInput.nodeProperties = ZookeeperNodeTemplate.createProperties(
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK),
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  MesosClusterCreateTaskService.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_MASTERS));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosZookeeper, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs Mesos Master nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_MARATHON.
   *
   * @param currentState
   */
  private void setupMasters(final MesosClusterCreateTask currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ClusterService.State cluster = operation.getBody(ClusterService.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.vmFlavorName = cluster.masterVmFlavorName;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.nodeCount = Mesos.MASTER_COUNT;
          rolloutInput.nodeType = NodeType.MesosMaster;
          rolloutInput.nodeProperties = MesosMasterNodeTemplate.createProperties(
              Mesos.MASTER_COUNT,
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              MesosClusterCreateTask patchState = buildPatch(
                  TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_MARATHON);
              patchState.masterIps = result.nodeAddresses;
              TaskUtils.sendSelfPatch(MesosClusterCreateTaskService.this, patchState);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosMaster, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs Marathon nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_SLAVES.
   *
   * @param currentState
   */
  private void setupMarathon(MesosClusterCreateTask currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ClusterService.State cluster = operation.getBody(ClusterService.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.vmFlavorName = cluster.otherVmFlavorName;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.nodeCount = Mesos.MARATHON_COUNT;
          rolloutInput.nodeType = NodeType.MesosMarathon;
          rolloutInput.nodeProperties = MarathonNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  MesosClusterCreateTaskService.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_SLAVES));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosMarathon, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs the initial Mesos Slave Nodes. On successful roll-out,
   * the method creates necessary tasks for cluster maintenance.
   *
   * @param currentState
   */
  private void setupInitialSlaves(MesosClusterCreateTask currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ClusterService.State cluster = operation.getBody(ClusterService.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.vmFlavorName = cluster.otherVmFlavorName;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.nodeCount = MINIMUM_INITIAL_SLAVE_COUNT;
          rolloutInput.nodeType = NodeType.MesosSlave;
          rolloutInput.serverAddress = currentState.masterIps.get(0);
          rolloutInput.nodeProperties = MesosSlaveNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new SlavesNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              setupRemainingSlaves(currentState, cluster);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosSlave, t);
            }
          });
        }));
  }

  private void setupRemainingSlaves(
      final MesosClusterCreateTask currentState,
      final ClusterService.State cluster) {
    // Maintenance task should be singleton for any cluster.
    ClusterMaintenanceTaskService.State startState = new ClusterMaintenanceTaskService.State();
    startState.batchExpansionSize = currentState.slaveBatchExpansionSize;
    startState.documentSelfLink = currentState.clusterId;

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ClusterMaintenanceTaskFactoryService.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.MesosSlave, throwable);
            return;
          }
          if (cluster.slaveCount == MINIMUM_INITIAL_SLAVE_COUNT) {
            // We short circuit here and set the clusterState as READY, since the desired size has
            // already been reached. Maintenance will kick-in when the maintenance interval elapses.
            MesosClusterCreateTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);

            ClusterService.State clusterPatch = new ClusterService.State();
            clusterPatch.clusterState = ClusterState.READY;

            updateStates(currentState, patchState, clusterPatch);
          } else {
            // The handleStart method of the maintenance task does not push itself to STARTED automatically.
            // We need to patch the maintenance task manually to start the task immediately. Otherwise
            // the task will wait for one interval to start.
            startMaintenance(currentState);
          }
        });
    sendRequest(postOperation);
  }

  private void startMaintenance(final MesosClusterCreateTask currentState) {
    ClusterMaintenanceTaskService.State patchState = new ClusterMaintenanceTaskService.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ClusterMaintenanceTaskFactoryService.SELF_LINK + "/" + currentState.clusterId))
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          // We ignore the failure here since maintenance task will kick in eventually.
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        });
    sendRequest(patchOperation);
  }

  private void validateStartState(MesosClusterCreateTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case SETUP_ZOOKEEPERS:
        case SETUP_MASTERS:
        case SETUP_MARATHON:
        case SETUP_SLAVES:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(MesosClusterCreateTask currentState,
                                  MesosClusterCreateTask patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }

    if (patchState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkNotNull(patchState.taskState.subStage);
    }
  }

  private MesosClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private MesosClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage,
                                            @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private MesosClusterCreateTask buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    MesosClusterCreateTask state = new MesosClusterCreateTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final MesosClusterCreateTask currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    MesosClusterCreateTask patchState = buildPatch(
        TaskState.TaskStage.FAILED, null,
        new IllegalStateException(String.format("Failed to rollout %s. Error: %s",
            nodeType.toString(), throwable.toString())));

    ClusterService.State document = new ClusterService.State();
    document.clusterState = ClusterState.ERROR;
    updateStates(currentState, patchState, document);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void updateStates(final MesosClusterCreateTask currentState,
                            final MesosClusterCreateTask patchState,
                            final ClusterService.State clusterPatchState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
            .setBody(clusterPatchState)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  TaskUtils.sendSelfPatch(MesosClusterCreateTaskService.this, patchState);
                }
            ));
  }
}
