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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceErrorResponse;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.NodeGroupBroadcastResponse;
import com.vmware.dcp.services.common.QueryTask;
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
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask.TaskState;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.templates.EtcdNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.KubernetesMasterNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.KubernetesSlaveNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.clustermanager.utils.ControlFlags;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Set;

/**
 * This class implements a DCP service representing a task to create a Kubernetes cluster.
 */
public class KubernetesClusterCreateTaskService extends StatefulService {

  private static final int MINIMUM_INITIAL_SLAVE_COUNT = 1;

  public KubernetesClusterCreateTaskService() {
    super(KubernetesClusterCreateTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    KubernetesClusterCreateTask startState = start.getBody(KubernetesClusterCreateTask.class);
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
    KubernetesClusterCreateTask currentState = getState(patch);
    KubernetesClusterCreateTask patchState = patch.getBody(KubernetesClusterCreateTask.class);
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

  private void processStateMachine(KubernetesClusterCreateTask currentState) {
    switch (currentState.taskState.subStage) {
      case ALLOCATE_RESOURCES:
        queryClusterConfiguration(currentState);
        break;

      case SETUP_ETCD:
        setupEtcds(currentState);
        break;

      case SETUP_MASTER:
        setupMaster(currentState);
        break;

      case SETUP_SLAVES:
        setupInitialSlaves(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method queries for the document link of the cluster configuration for the Kubernetes Cluster.
   * @param currentState
   */
  private void queryClusterConfiguration(final KubernetesClusterCreateTask currentState) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ClusterConfigurationService.State.class));

    QueryTask.Query idClause = new QueryTask.Query()
        .setTermPropertyName(ClusterConfigurationService.State.FIELD_NAME_SELF_LINK)
        .setTermMatchValue(
            ClusterConfigurationServiceFactory.SELF_LINK + "/" + ClusterType.KUBERNETES.toString().toLowerCase());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(idClause);

    HostUtils.getCloudStoreHelper(this).queryEntities(
        this,
        querySpecification,
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
                ClusterType.KUBERNETES.toString())));
            return;
          }

          retrieveClusterConfiguration(currentState, documentLinks.iterator().next());
        });
  }

  /**
   * This method retrieves the cluster configuration entity for the Kubernetes Cluster.
   * @param currentState
   * @param clusterConfigurationLink
   */
  private void retrieveClusterConfiguration(final KubernetesClusterCreateTask currentState,
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
   * This method creates a Kubernetes Cluster Service instance. On successful creation, the method moves the sub-stage
   * to SETUP_MASTER.
   *
   * @param currentState
   * @param imageId
   */
  private void createClusterService(final KubernetesClusterCreateTask currentState,
                                    final String imageId) {

    ClusterService.State cluster = new ClusterService.State();
    cluster.clusterState = ClusterState.CREATING;
    cluster.clusterName = currentState.clusterName;
    cluster.clusterType = ClusterType.KUBERNETES;
    cluster.imageId = imageId;
    cluster.projectId = currentState.projectId;
    cluster.diskFlavorName = currentState.diskFlavorName;
    cluster.masterVmFlavorName = currentState.masterVmFlavorName;
    cluster.otherVmFlavorName = currentState.otherVmFlavorName;
    cluster.vmNetworkId = currentState.vmNetworkId;
    cluster.slaveCount = currentState.slaveCount;
    cluster.extendedProperties = new HashMap<>();
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_DNS,
        currentState.dns);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY,
        currentState.gateway);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK,
        currentState.netmask);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK,
        currentState.containerNetwork);
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS,
        NodeTemplateUtils.serializeAddressList(currentState.etcdIps));
    cluster.extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP,
        currentState.masterIp);
    cluster.documentSelfLink = currentState.clusterId;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(ClusterServiceFactory.SELF_LINK)
        .setBody(cluster)
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          KubernetesClusterCreateTask patchState = buildPatch(
              TaskState.TaskStage.STARTED,
              TaskState.SubStage.SETUP_ETCD);
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
  private void setupEtcds(KubernetesClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.vmFlavorName = currentState.otherVmFlavorName;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.nodeCount = currentState.etcdIps.size();
    rolloutInput.nodeType = NodeType.KubernetesEtcd;
    rolloutInput.nodeProperties = EtcdNodeTemplate.createProperties(
        currentState.dns, currentState.gateway, currentState.netmask, currentState.etcdIps);

    NodeRollout rollout = new BasicNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        TaskUtils.sendSelfPatch(
            KubernetesClusterCreateTaskService.this,
            buildPatch(KubernetesClusterCreateTask.TaskState.TaskStage.STARTED,
                KubernetesClusterCreateTask.TaskState.SubStage.SETUP_MASTER));
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.SwarmEtcd, t);
      }
    });
  }

  /**
   * This method roll-outs Kubernetes Master Nodes. On successful roll-out,
   * the methods moves the task sub-stage to SETUP_SLAVES.
   *
   * @param currentState
   */
  private void setupMaster(final KubernetesClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmFlavorName = currentState.masterVmFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.nodeCount = ClusterManagerConstants.Kubernetes.MASTER_COUNT;
    rolloutInput.nodeType = NodeType.KubernetesMaster;
    rolloutInput.nodeProperties = KubernetesMasterNodeTemplate.createProperties(
        currentState.etcdIps,
        currentState.dns,
        currentState.gateway,
        currentState.netmask,
        currentState.masterIp,
        currentState.containerNetwork);

    NodeRollout rollout = new BasicNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        TaskUtils.sendSelfPatch(
            KubernetesClusterCreateTaskService.this,
            buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_SLAVES));
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.KubernetesMaster, t);
      }
    });
  }

  /**
   * This method roll-outs Kubernetes Slave Nodes. On successful roll-out,
   * the method sets the task's state as Finished.
   *
   * @param currentState
   */
  private void setupInitialSlaves(final KubernetesClusterCreateTask currentState) {
    NodeRolloutInput rolloutInput = new NodeRolloutInput();
    rolloutInput.clusterId = currentState.clusterId;
    rolloutInput.projectId = currentState.projectId;
    rolloutInput.imageId = currentState.imageId;
    rolloutInput.diskFlavorName = currentState.diskFlavorName;
    rolloutInput.vmFlavorName = currentState.otherVmFlavorName;
    rolloutInput.vmNetworkId = currentState.vmNetworkId;
    rolloutInput.nodeCount = MINIMUM_INITIAL_SLAVE_COUNT;
    rolloutInput.nodeType = NodeType.KubernetesSlave;
    rolloutInput.serverAddress = currentState.masterIp;
    rolloutInput.nodeProperties = KubernetesSlaveNodeTemplate.createProperties(
        currentState.etcdIps, currentState.containerNetwork, currentState.masterIp);

    NodeRollout rollout = new SlavesNodeRollout();
    rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
      @Override
      public void onSuccess(@Nullable NodeRolloutResult result) {
        setupRemainingSlaves(currentState);
      }

      @Override
      public void onFailure(Throwable t) {
        failTaskAndPatchDocument(currentState, NodeType.KubernetesSlave, t);
      }
    });
  }

  private void setupRemainingSlaves(final KubernetesClusterCreateTask currentState) {
    // Maintenance task should be singleton for any cluster.
    ClusterMaintenanceTaskService.State startState = new ClusterMaintenanceTaskService.State();
    startState.batchExpansionSize = currentState.slaveBatchExpansionSize;
    startState.documentSelfLink = currentState.clusterId;

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ClusterMaintenanceTaskFactoryService.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.KubernetesSlave, throwable);
            return;
          }

          // The handleStart method of the maintenance task does not push itself to STARTED automatically.
          // We need to patch the maintenance task manually to start the task immediately. Otherwise
          // the task will wait for one interval to start.
          startMaintenance(currentState);
        });
    sendRequest(postOperation);
  }

  private void startMaintenance(final KubernetesClusterCreateTask currentState) {
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

  private void validateStartState(KubernetesClusterCreateTask startState) {
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

  private void validatePatchState(KubernetesClusterCreateTask currentState,
                                  KubernetesClusterCreateTask patchState) {
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

  private KubernetesClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private KubernetesClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage,
                                                 @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private KubernetesClusterCreateTask buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    KubernetesClusterCreateTask state = new KubernetesClusterCreateTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final KubernetesClusterCreateTask currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    KubernetesClusterCreateTask patchState = buildPatch(
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

  private void updateStates(final KubernetesClusterCreateTask currentState,
                            final KubernetesClusterCreateTask patchState,
                            final ClusterService.State clusterPatchState) {

    HostUtils.getCloudStoreHelper(this).patchEntity(
        this,
        ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId,
        clusterPatchState,
        (Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          TaskUtils.sendSelfPatch(KubernetesClusterCreateTaskService.this, patchState);
        });
  }
}
