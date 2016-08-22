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

import com.vmware.photon.controller.api.model.ClusterState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ClusterServiceFactory;
import com.vmware.photon.controller.clustermanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.clustermanager.rolloutplans.WorkersNodeRollout;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.KubernetesClusterCreateTask.TaskState;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.templates.EtcdNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.KubernetesMasterNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.KubernetesWorkerNodeTemplate;
import com.vmware.photon.controller.clustermanager.templates.NodeTemplateUtils;
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
 * This class implements a Xenon service representing a task to create a Kubernetes cluster.
 */
public class KubernetesClusterCreateTaskService extends StatefulService {

  private static final int MINIMUM_INITIAL_WORKER_COUNT = 1;

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
      startState.taskState.subStage = TaskState.SubStage.SETUP_ETCD;
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
            buildPatch(startState.taskState.stage, TaskState.SubStage.SETUP_ETCD));
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
      case SETUP_ETCD:
        setupEtcds(currentState);
        break;

      case SETUP_MASTER:
        setupMaster(currentState);
        break;

      case SETUP_WORKERS:
        setupInitialWorkers(currentState);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method roll-outs Etcd nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_MASTER.
   *
   * @param currentState
   */
  private void setupEtcds(KubernetesClusterCreateTask currentState) {
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
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)).size();
          rolloutInput.nodeType = NodeType.KubernetesEtcd;
          rolloutInput.nodeProperties = EtcdNodeTemplate.createProperties(
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK),
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)));

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
              failTaskAndPatchDocument(currentState, NodeType.KubernetesEtcd, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs Kubernetes Master Nodes. On successful roll-out,
   * the methods moves the task sub-stage to SETUP_WORKERS.
   *
   * @param currentState
   */
  private void setupMaster(final KubernetesClusterCreateTask currentState) {
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
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmFlavorName = cluster.masterVmFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.nodeCount = ClusterManagerConstants.Kubernetes.MASTER_COUNT;
          rolloutInput.nodeType = NodeType.KubernetesMaster;
          rolloutInput.nodeProperties = KubernetesMasterNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  KubernetesClusterCreateTaskService.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_WORKERS));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.KubernetesMaster, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs the initial Kubernetes Worker Nodes. On successful roll-out,
   * the method creates necessary tasks for cluster maintenance.
   *
   * @param currentState
   */
  private void setupInitialWorkers(final KubernetesClusterCreateTask currentState) {
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
          rolloutInput.clusterId = currentState.clusterId;
          rolloutInput.projectId = cluster.projectId;
          rolloutInput.imageId = cluster.imageId;
          rolloutInput.diskFlavorName = cluster.diskFlavorName;
          rolloutInput.vmFlavorName = cluster.otherVmFlavorName;
          rolloutInput.vmNetworkId = cluster.vmNetworkId;
          rolloutInput.nodeCount = MINIMUM_INITIAL_WORKER_COUNT;
          rolloutInput.nodeType = NodeType.KubernetesWorker;
          rolloutInput.serverAddress =
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
          rolloutInput.nodeProperties = KubernetesWorkerNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP));

          NodeRollout rollout = new WorkersNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              setupRemainingWorkers(currentState, cluster);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.KubernetesWorker, t);
            }
          });
        }));
  }

  private void setupRemainingWorkers(
      final KubernetesClusterCreateTask currentState,
      final ClusterService.State cluster) {
    // Maintenance task should be singleton for any cluster.
    ClusterMaintenanceTaskService.State startState = new ClusterMaintenanceTaskService.State();
    startState.batchExpansionSize = currentState.workerBatchExpansionSize;
    startState.documentSelfLink = currentState.clusterId;

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ClusterMaintenanceTaskFactoryService.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.KubernetesWorker, throwable);
            return;
          }
          if (cluster.workerCount == MINIMUM_INITIAL_WORKER_COUNT) {
            // We short circuit here and set the clusterState as READY, since the desired size has
            // already been reached. Maintenance will kick-in when the maintenance interval elapses.
            KubernetesClusterCreateTask patchState = buildPatch(TaskState.TaskStage.FINISHED, null);

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
        case SETUP_ETCD:
        case SETUP_MASTER:
        case SETUP_WORKERS:
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

                  TaskUtils.sendSelfPatch(KubernetesClusterCreateTaskService.this, patchState);
                }
            ));
  }
}
