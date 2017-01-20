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
import com.vmware.photon.controller.servicesmanager.servicedocuments.MesosServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.MesosServiceCreateTaskState.TaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants.Mesos;
import com.vmware.photon.controller.servicesmanager.templates.MarathonNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.MesosMasterNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.MesosWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.servicesmanager.templates.ZookeeperNodeTemplate;
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
 * This class implements a Xenon service representing a task to create a Mesos service.
 */
public class MesosServiceCreateTask extends StatefulService {

  private static final int MINIMUM_INITIAL_WORKER_COUNT = 1;

  public MesosServiceCreateTask() {
    super(MesosServiceCreateTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    MesosServiceCreateTaskState startState = start.getBody(MesosServiceCreateTaskState.class);
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
    MesosServiceCreateTaskState currentState = getState(patch);
    MesosServiceCreateTaskState patchState = patch.getBody(MesosServiceCreateTaskState.class);
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

  private void processStateMachine(MesosServiceCreateTaskState currentState) {
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

      case SETUP_WORKERS:
        setupInitialWorkers(currentState);
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
  private void setupZookeepers(MesosServiceCreateTaskState currentState) {
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
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)).size();
          rolloutInput.nodeType = NodeType.MesosZookeeper;
          rolloutInput.nodeProperties = ZookeeperNodeTemplate.createProperties(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK),
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  MesosServiceCreateTask.this,
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
  private void setupMasters(final MesosServiceCreateTaskState currentState) {
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
          rolloutInput.nodeCount = Mesos.MASTER_COUNT;
          rolloutInput.nodeType = NodeType.MesosMaster;
          rolloutInput.nodeProperties = MesosMasterNodeTemplate.createProperties(
              Mesos.MASTER_COUNT,
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              MesosServiceCreateTaskState patchState = buildPatch(
                  TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_MARATHON);
              patchState.masterIps = result.nodeAddresses;
              TaskUtils.sendSelfPatch(MesosServiceCreateTask.this, patchState);
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
   * rollout, the methods moves the task sub-stage to SET_WORKERS.
   *
   * @param currentState
   */
  private void setupMarathon(MesosServiceCreateTaskState currentState) {
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
          rolloutInput.nodeCount = Mesos.MARATHON_COUNT;
          rolloutInput.nodeType = NodeType.MesosMarathon;
          rolloutInput.nodeProperties = MarathonNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new BasicNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              TaskUtils.sendSelfPatch(
                  MesosServiceCreateTask.this,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SETUP_WORKERS));
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosMarathon, t);
            }
          });
        }));
  }

  /**
   * This method roll-outs the initial Mesos Worker Nodes. On successful roll-out,
   * the method creates necessary tasks for service maintenance.
   *
   * @param currentState
   */
  private void setupInitialWorkers(MesosServiceCreateTaskState currentState) {
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
          rolloutInput.nodeType = NodeType.MesosWorker;
          rolloutInput.serverAddress = currentState.masterIps.get(0);
          rolloutInput.nodeProperties = MesosWorkerNodeTemplate.createProperties(
              NodeTemplateUtils.deserializeAddressList(
                  service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS)));

          NodeRollout rollout = new WorkersNodeRollout();
          rollout.run(this, rolloutInput, new FutureCallback<NodeRolloutResult>() {
            @Override
            public void onSuccess(@Nullable NodeRolloutResult result) {
              setupRemainingWorkers(currentState, service);
            }

            @Override
            public void onFailure(Throwable t) {
              failTaskAndPatchDocument(currentState, NodeType.MesosWorker, t);
            }
          });
        }));
  }

  private void setupRemainingWorkers(
      final MesosServiceCreateTaskState currentState,
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
            failTaskAndPatchDocument(currentState, NodeType.MesosWorker, throwable);
            return;
          }
          if (service.workerCount == MINIMUM_INITIAL_WORKER_COUNT) {
            // We short circuit here and set the serviceState as READY, since the desired size has
            // already been reached. Maintenance will kick-in when the maintenance interval elapses.
            MesosServiceCreateTaskState patchState = buildPatch(TaskState.TaskStage.FINISHED, null);

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

  private void startMaintenance(final MesosServiceCreateTaskState currentState) {
    ServiceMaintenanceTask.State patchState = new ServiceMaintenanceTask.State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = TaskState.TaskStage.STARTED;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(),
            ServiceMaintenanceTaskFactory.SELF_LINK + "/" + currentState.serviceId))
        .setBody(patchState)
        .setCompletion((Operation operation, Throwable throwable) -> {
          // We ignore the failure here since maintenance task will kick in eventually.
          TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        });
    sendRequest(patchOperation);
  }

  private void validateStartState(MesosServiceCreateTaskState startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);

    if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(startState.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (startState.taskState.subStage) {
        case SETUP_ZOOKEEPERS:
        case SETUP_MASTERS:
        case SETUP_MARATHON:
        case SETUP_WORKERS:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + startState.taskState.subStage.toString());
      }
    } else {
      checkState(startState.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  private void validatePatchState(MesosServiceCreateTaskState currentState,
                                  MesosServiceCreateTaskState patchState) {
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

  private MesosServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private MesosServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                            TaskState.SubStage subStage,
                                            @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private MesosServiceCreateTaskState buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    MesosServiceCreateTaskState state = new MesosServiceCreateTaskState();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final MesosServiceCreateTaskState currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    MesosServiceCreateTaskState patchState = buildPatch(
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

  private void updateStates(final MesosServiceCreateTaskState currentState,
                            final MesosServiceCreateTaskState patchState,
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

                  TaskUtils.sendSelfPatch(MesosServiceCreateTask.this, patchState);
                }
            ));
  }
}
