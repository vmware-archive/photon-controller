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
package com.vmware.photon.controller.servicesmanager.tasks;

import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.servicesmanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.servicesmanager.rolloutplans.WorkersNodeRollout;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants.Swarm;
import com.vmware.photon.controller.servicesmanager.servicedocuments.SwarmServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.SwarmServiceCreateTaskState.TaskState;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.servicesmanager.templates.SwarmEtcdNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.SwarmMasterNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.SwarmWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
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
 * This class implements a Xenon service representing a task to create a Swarm service.
 */
public class SwarmServiceCreateTask extends StatefulService {

  private static final int MINIMUM_INITIAL_WORKER_COUNT = 1;

  public SwarmServiceCreateTask() {
    super(SwarmServiceCreateTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    SwarmServiceCreateTaskState startState = start.getBody(SwarmServiceCreateTaskState.class);
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
    SwarmServiceCreateTaskState currentState = getState(patch);
    SwarmServiceCreateTaskState patchState = patch.getBody(SwarmServiceCreateTaskState.class);
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

  private void processStateMachine(SwarmServiceCreateTaskState currentState) {
    switch (currentState.taskState.subStage) {
      case SETUP_ETCD:
        setupEtcds(currentState);
        break;

      case SETUP_MASTER:
        setupMasters(currentState);
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
  private void setupEtcds(SwarmServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.vmFlavorName = service.otherVmFlavorName;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.nodeCount = NodeTemplateUtils.deserializeAddressList(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)).size();
          rolloutInput.nodeType = NodeType.SwarmEtcd;
          rolloutInput.nodeProperties = SwarmEtcdNodeTemplate.createProperties(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK),
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  SwarmServiceCreateTask.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_MASTER));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.SwarmEtcd, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs Swarm Master nodes. On successful
   * rollout, the methods moves the task sub-stage to SETUP_WORKERS.
   *
   * @param currentState
   */
  private void setupMasters(final SwarmServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.vmFlavorName = service.masterVmFlavorName;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.nodeCount = Swarm.MASTER_COUNT;
          rolloutInput.nodeType = NodeType.SwarmMaster;
          rolloutInput.nodeProperties = SwarmMasterNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              SwarmServiceCreateTaskState patchState = buildPatch(
                  TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_WORKERS);
              patchState.masterIps = result.nodeAddresses;
              TaskUtils.sendSelfPatch(SwarmServiceCreateTask.this, patchState);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.SwarmMaster, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs the initial Swarm Worker Nodes. On successful roll-out,
   * the method creates necessary tasks for service maintenance.
   *
   * @param currentState
   */
  private void setupInitialWorkers(SwarmServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);

          NodeRolloutInput rolloutInput = new NodeRolloutInput();
          rolloutInput.projectId = service.projectId;
          rolloutInput.imageId = service.imageId;
          rolloutInput.vmFlavorName = service.otherVmFlavorName;
          rolloutInput.diskFlavorName = service.diskFlavorName;
          rolloutInput.vmNetworkId = service.vmNetworkId;
          rolloutInput.serviceId = currentState.serviceId;
          rolloutInput.nodeCount = MINIMUM_INITIAL_WORKER_COUNT;
          rolloutInput.nodeType = NodeType.SwarmWorker;
          rolloutInput.serverAddress = currentState.masterIps.get(0);
          rolloutInput.nodeProperties = SwarmWorkerNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS)));

          NodeRollout rollout = new WorkersNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              setupRemainingWorkers(currentState, service);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.SwarmWorker, t);
            }
          });
        }));
  }

  private void setupRemainingWorkers(
      final SwarmServiceCreateTaskState currentState,
      final ServiceState.State service) {
    // Maintenance task should be singleton for any service.
    ServiceMaintenanceTask.State startState = new ServiceMaintenanceTask.State();
    startState.batchExpansionSize = currentState.workerBatchExpansionSize;
    startState.documentSelfLink = currentState.serviceId;

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceMaintenanceTaskFactory.SELF_LINK))
        .setBody(startState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTaskAndPatchDocument(currentState, NodeType.SwarmWorker, throwable);
            return;
          }
          if (service.workerCount == MINIMUM_INITIAL_WORKER_COUNT) {
            // We short circuit here and set the serviceState as READY, since the desired size has
            // already been reached. Maintenance will kick-in when the maintenance interval elapses.
            SwarmServiceCreateTaskState patchState =
                buildPatch(SwarmServiceCreateTaskState.TaskState.TaskStage.FINISHED, null);

            ServiceState.State servicePatch = new ServiceState.State();
            servicePatch.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;

            updateStates(currentState, patchState, servicePatch);
          } else {
            // The handleStart method of the maintenance task does not push itself to STARTED automatically.
            // We need to patch the maintenance task manually to start the task immediately. Otherwise
            // the task will wait for one interval to start.
            startMaintenance(currentState);
          }
        });
    sendRequest(postOperation);
  }

  private void startMaintenance(final SwarmServiceCreateTaskState currentState) {
    ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
    patchState.taskState = new SwarmServiceCreateTaskState.TaskState();
    patchState.taskState.stage = SwarmServiceCreateTaskState.TaskState.TaskStage.STARTED;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ServiceMaintenanceTaskFactory.SELF_LINK + "/" + currentState.serviceId))
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          // We ignore the failure here since maintenance task will kick in eventually.
          TaskUtils.sendSelfPatch(this, buildPatch(SwarmServiceCreateTaskState.TaskState.TaskStage.FINISHED, null));
        });
    sendRequest(patchOperation);
  }

  private void validateStartState(SwarmServiceCreateTaskState startState) {
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

  private void validatePatchState(SwarmServiceCreateTaskState currentState,
                                  SwarmServiceCreateTaskState patchState) {
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

  private SwarmServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private SwarmServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage,
                                            @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private SwarmServiceCreateTaskState buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    SwarmServiceCreateTaskState state = new SwarmServiceCreateTaskState();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final SwarmServiceCreateTaskState currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    SwarmServiceCreateTaskState patchState = buildPatch(
        TaskState.TaskStage.FAILED, null,
        new IllegalStateException(String.format("Failed to rollout %s. Error: %s",
            nodeType.toString(), throwable.toString())));

    ServiceState.State document = new ServiceState.State();
    document.serviceState = com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR;
    updateStates(currentState, patchState, document);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void updateStates(final SwarmServiceCreateTaskState currentState,
                            final SwarmServiceCreateTaskState patchState,
                            final ServiceState.State servicePatchState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
            .setBody(servicePatchState)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  TaskUtils.sendSelfPatch(SwarmServiceCreateTask.this, patchState);
                }
            ));
  }
}
