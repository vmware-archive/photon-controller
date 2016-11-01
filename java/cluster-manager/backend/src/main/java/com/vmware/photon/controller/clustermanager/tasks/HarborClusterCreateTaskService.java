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
import com.vmware.photon.controller.clustermanager.clients.HarborClient;
import com.vmware.photon.controller.clustermanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.clustermanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.HarborClusterCreateTask;
import com.vmware.photon.controller.clustermanager.servicedocuments.HarborClusterCreateTask.TaskState;
import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;
import com.vmware.photon.controller.clustermanager.templates.HarborNodeTemplate;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon service representing a task to create a Harbor cluster.
 */
public class HarborClusterCreateTaskService extends StatefulService {

  private static final int HARBOR_NODE_COUNT = 1;

  public HarborClusterCreateTaskService() {
    super(HarborClusterCreateTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    HarborClusterCreateTask startState = start.getBody(HarborClusterCreateTask.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.SETUP_HARBOR;
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
            buildPatch(startState.taskState.stage, TaskState.SubStage.SETUP_HARBOR));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    HarborClusterCreateTask currentState = getState(patch);
    HarborClusterCreateTask patchState = patch.getBody(HarborClusterCreateTask.class);
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

  private void processStateMachine(HarborClusterCreateTask currentState) {
    ServiceUtils.logInfo(this, "Start %s with cluster id: %s", currentState.taskState.subStage, currentState.clusterId);
    switch (currentState.taskState.subStage) {
      case SETUP_HARBOR:
        setupHarbor(currentState);
        break;
      case UPDATE_EXTENDED_PROPERTIES:
        updateExtendedProperties(currentState);
        break;
      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  /**
   * This method rolls-out one Harbor node. On successful rollout, the methods moves to FINISHED stage.
   *
   * @param currentState
   */
  private void setupHarbor(HarborClusterCreateTask currentState) {
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
          rolloutInput.nodeCount = HARBOR_NODE_COUNT;
          rolloutInput.nodeType = NodeType.Harbor;
          rolloutInput.nodeProperties = HarborNodeTemplate.createProperties(
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_DNS),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_NETMASK),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_SSH_KEY),
              cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              startMaintenance(currentState);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.Harbor, t);
            }
          });
        }));
  }

  private void startMaintenance(final HarborClusterCreateTask currentState) {
    ClusterMaintenanceTaskService.State startState = new ClusterMaintenanceTaskService.State();
    startState.documentSelfLink = currentState.clusterId;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ClusterMaintenanceTaskFactoryService.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.Harbor, throwable);
            return;
          }

          TaskUtils.sendSelfPatch(this,
              buildPatch(currentState.taskState.stage, TaskState.SubStage.UPDATE_EXTENDED_PROPERTIES));
        });
    sendRequest(postOperation);
  }

  private void updateExtendedProperties(HarborClusterCreateTask currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ClusterServiceFactory.SELF_LINK + "/" + currentState.clusterId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ClusterService.State cluster = operation.getBody(ClusterService.State.class);
          String harborAddress = cluster.extendedProperties.get(ClusterManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
          HarborClient harborClient = HostUtils.getHarborClient(this);
          String connectionString = "https://" + harborAddress + ":" + ClusterManagerConstants.Harbor.HARBOR_PORT;
          getCACertAndUpdate(currentState, cluster, harborClient, connectionString);
        }));
  }

  private void getCACertAndUpdate(HarborClusterCreateTask currentState, ClusterService.State clusterState,
                                  HarborClient harborClient, String connectionString) {
      try {
        harborClient.getCACertificate(connectionString, new FutureCallback<String>() {
          @Override
          public void onSuccess(@Nullable String result) {
            Map<String, String> extendedProperties = new HashMap<>(clusterState.extendedProperties);
            extendedProperties.put(ClusterManagerConstants.EXTENDED_PROPERTY_CA_CERTIFICATE, result);
            // Remove admin password from extended properties since Harbor is already up and we don't need the
            // password any more.
            extendedProperties.remove(ClusterManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD);
            HarborClusterCreateTask desiredState = buildPatch(TaskState.TaskStage.FINISHED, null);

            ClusterService.State clusterPatch = new ClusterService.State();
            clusterPatch.clusterState = ClusterState.READY;
            clusterPatch.extendedProperties = extendedProperties;

            updateStates(currentState, desiredState, clusterPatch);
          }

          @Override
          public void onFailure(Throwable t) {
            if (currentState.updatePropsIterations >= currentState.updatePropsMaxIterations) {
              failTask(t);
            } else {
              getHost().schedule(
                  () -> {
                    HarborClusterCreateTask patchState = buildPatch(TaskState.TaskStage.STARTED,
                        TaskState.SubStage.UPDATE_EXTENDED_PROPERTIES);
                    patchState.updatePropsIterations = currentState.updatePropsIterations + 1;
                    TaskUtils.sendSelfPatch(HarborClusterCreateTaskService.this, patchState);
                  },
                  currentState.updatePropsPollDelay, TimeUnit.MILLISECONDS);
            }
          }
        });
      } catch (IOException e) {
        failTask(e);
      }
  }

  private void validateStartState(HarborClusterCreateTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case SETUP_HARBOR:
          break;
        case UPDATE_EXTENDED_PROPERTIES:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(HarborClusterCreateTask currentState,
                                  HarborClusterCreateTask patchState) {
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

  private HarborClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private HarborClusterCreateTask buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage,
                                                 @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private HarborClusterCreateTask buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    HarborClusterCreateTask state = new HarborClusterCreateTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final HarborClusterCreateTask currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    HarborClusterCreateTask patchState = buildPatch(
        TaskState.TaskStage.FAILED, null,
        new IllegalStateException(String.format("Failed to rollout %s. Error: %s",
            nodeType.toString(), throwable.toString())));

    ClusterService.State document = new ClusterService.State();
    document.clusterState = ClusterState.FATAL_ERROR;
    document.errorReason = throwable.toString();
    updateStates(currentState, patchState, document);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void updateStates(final HarborClusterCreateTask currentState,
                            final HarborClusterCreateTask patchState,
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

                  TaskUtils.sendSelfPatch(HarborClusterCreateTaskService.this, patchState);
                }
            ));
  }
}
