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
import com.vmware.photon.controller.api.ClusterType;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterConfigurationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.clustermanager.rolloutplans.SlavesNodeRollout;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants.Swarm;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.SwarmClusterCreateTask.TaskState;
import com.vmware.photon.controller.clustermanager.templates.EtcdNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.templates.SwarmMasterNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.SwarmSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.utils.ControlFlags;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Set;

/**
 * This class implements a DCP service representing a task to create a Swarm cluster.
 */
public class SwarmClusterCreateTaskService extends StatefulService {

  private static final int MINIMUM_INITIAL_SLAVE_COUNT = 1;

  public SwarmClusterCreateTaskService() {
    super(SwarmClusterCreateTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    SwarmClusterCreateTask startState = start.getBody(SwarmClusterCreateTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.ALLOCATE_RESOURCES;
    }
    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, TaskState.SubStage.ALLOCATE_RESOURCES));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    SwarmClusterCreateTask currentState = getState(patch);
    SwarmClusterCreateTask patchState = patch.getBody(SwarmClusterCreateTask.class);
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

  private void processStateMachine(SwarmClusterCreateTask currentState) {
    switch (currentState.taskState.subStage) {
      case ALLOCATE_RESOURCES:
        queryClusterConfiguration(currentState);
        break;

      case SETUP_ETCD:
        setupEtcds(currentState);
        break;

      case SETUP_MASTER:
        setupMasters(currentState);
        break;

      case SETUP_SLAVES:
        setupInitialSlaves(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method queries for the document link of the cluster configuration for the Swarm Cluster.
   *
   * @param currentState
   */
  private void queryClusterConfiguration(final SwarmClusterCreateTask currentState) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ClusterConfigurationService.State.class));

    QueryTask.Query idClause = new QueryTask.Query()
        .setTermPropertyName(ClusterConfigurationService.State.FIELD_NAME_SELF_LINK)
        .setTermMatchValue(
            ClusterConfigurationServiceFactory.SELF_LINK + "/" + ClusterType.SWARM.toString().toLowerCase());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(idClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(queryTask)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
                  Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryResults(queryResponse);
                  if (documentLinks.isEmpty()) {
                    failTask(new IllegalStateException(String.format(
                        "Cannot find cluster configuration for %s",
                        ClusterType.SWARM.toString())));
                    return;
                  }

                  retrieveClusterConfiguration(currentState, documentLinks.iterator().next());
                }
            ));
  }

  /**
   * This method retrieves the cluster configuration entity for the Swarm Cluster.
   *
   * @param currentState
   * @param clusterConfigurationLink
   */
  private void retrieveClusterConfiguration(final SwarmClusterCreateTask currentState,
                                            String clusterConfigurationLink) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(clusterConfigurationLink)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  ClusterConfigurationService.State clusterConfiguration = operation.getBody(
                      ClusterConfigurationService.State.class);
                  createClusterService(currentState, clusterConfiguration.imageId);
                }
            ));
  }

  /**
   * This method creates a Swarm Cluster Service instance. On successful creation, the method moves the sub-stage
   * to SETUP_ETCD.
   *
   * @param currentState
   * @param imageId
   */
  private void createClusterService(final SwarmClusterCreateTask currentState,
                                    final String imageId) {
    ClusterService.State cluster = new ClusterService.State();
    cluster.clusterState = ClusterState.CREATING;
    cluster.clusterName = currentState.clusterName;
    cluster.clusterType = ClusterType.SWARM;
    cluster.imageId = imageId;
    cluster.projectId = currentState.projectId;
    cluster.diskFlavorName = currentState.diskFlavorName;
    cluster.masterVmFlavorName = currentState.masterVmFlavorName;
    cluster.otherVmFlavorName = currentState.otherVmFlavorName;
    cluster.vmNetworkId = currentState.vmNetworkId;
    cluster.slaveCount = currentState.slaveCount;
    cluster.extendedProperties = new HashMap<>();
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS, currentState.dns);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY, currentState.gateway);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK, currentState.netmask);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS,
        NodeTemplateUtils.serializeAddressList(currentState.etcdIps));
    cluster.documentSelfLink = currentState.clusterId;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(ClusterServiceFactory.SELF_LINK)
        .setBody(cluster)
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          SwarmClusterCreateTask patchState = buildPatch(
              TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_ETCD);
          patchState.imageId = imageId;
          TaskUtils.sendSelfPatch(this, patchState);
        }));
  }

  /**
   * This method roll-outs Etcd nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_MASTER.
   *
   * @param currentState
   */
  private void setupEtcds(SwarmClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.vmFlavorName = currentState.otherVmFlavorName;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.nodeCount = currentState.etcdIps.size();
    rolloutInput.nodeType = NodeType.SwarmEtcd;
    rolloutInput.nodeProperties = EtcdNodeTemplate.createProperties(
        currentState.dns, currentState.gateway, currentState.netmask, currentState.etcdIps);

    NodeRollout rollout = new BasicNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        TaskUtils.sendSelfPatch(
            SwarmClusterCreateTaskService.this,
            buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_MASTER));
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.SwarmEtcd, t);
      }
    });
  }

  /**
   * This method roll-outs Swarm Master nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_SLAVES.
   *
   * @param currentState
   */
  private void setupMasters(final SwarmClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.vmFlavorName = currentState.masterVmFlavorName;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.nodeCount = Swarm.MASTER_COUNT;
    rolloutInput.nodeType = NodeType.SwarmMaster;
    rolloutInput.nodeProperties = SwarmMasterNodeTemplate.createProperties(currentState.etcdIps);

    NodeRollout rollout = new BasicNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        SwarmClusterCreateTask patchState = buildPatch(
            TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_SLAVES);
        patchState.masterIps = result.nodeAddresses;
        TaskUtils.sendSelfPatch(SwarmClusterCreateTaskService.this, patchState);
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.SwarmMaster, t);
      }
    });
  }

  /**
   * This method roll-outs Swarm Slave nodes. On successful
   * rollout, the methods moves the task FINISHED stage and updates the clusterState to READY.
   *
   * @param currentState
   */
  private void setupInitialSlaves(SwarmClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.vmFlavorName = currentState.otherVmFlavorName;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.nodeCount = MINIMUM_INITIAL_SLAVE_COUNT;
    rolloutInput.nodeType = NodeType.SwarmSlave;
    rolloutInput.serverAddress = currentState.masterIps.get(0);
    rolloutInput.nodeProperties = SwarmSlaveNodeTemplate.createProperties(currentState.etcdIps);

    NodeRollout rollout = new SlavesNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        setupRemainingSlaves(currentState);
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.SwarmSlave, t);
      }
    });
  }

  private void setupRemainingSlaves(final SwarmClusterCreateTask currentState) {
    // Maintenance task should be singleton for any cluster.
    ClusterMaintenanceTaskService.State startState = new ClusterMaintenanceTaskService.State();
    startState.batchExpansionSize = currentState.slaveBatchExpansionSize;
    startState.documentSelfLink = currentState.clusterId;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ClusterMaintenanceTaskFactoryService.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.SwarmSlave, throwable);
            return;
          }

          // The handleStart method of the maintenance task does not push itself to STARTED automatically.
          // We need to patch the maintenance task manually to start the task immediately. Otherwise
          // the task will wait for one interval to start.
          startMaintenance(currentState);
        });
    sendRequest(postOperation);
  }

  private void startMaintenance(final SwarmClusterCreateTask currentState) {
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

  private void validateStartState(SwarmClusterCreateTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case ALLOCATE_RESOURCES:
        case SETUP_ETCD:
        case SETUP_MASTER:
        case SETUP_SLAVES:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(SwarmClusterCreateTask currentState,
                                  SwarmClusterCreateTask patchState) {
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

  private SwarmClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private SwarmClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage,
                                            @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private SwarmClusterCreateTask buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    SwarmClusterCreateTask state = new SwarmClusterCreateTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final SwarmClusterCreateTask currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    SwarmClusterCreateTask patchState = buildPatch(
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

  private void updateStates(final SwarmClusterCreateTask currentState,
                            final SwarmClusterCreateTask patchState,
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

                  TaskUtils.sendSelfPatch(SwarmClusterCreateTaskService.this, patchState);
                }
            ));
  }
}
