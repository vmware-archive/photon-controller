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

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceState;
import com.vmware.photon.controller.cloudstore.xenon.entity.ServiceStateFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.servicesmanager.rolloutplans.WorkersNodeRollout;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.templates.KubernetesWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.MesosWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.templates.NodeTemplateUtils;
import com.vmware.photon.controller.servicesmanager.templates.SwarmWorkerNodeTemplate;
import com.vmware.photon.controller.servicesmanager.util.ServicesUtil;
import com.vmware.photon.controller.servicesmanager.utils.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
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
 * This class implements a Xenon service representing a task to expand a service.
 */
public class ServiceExpandTask extends StatefulService {

  public ServiceExpandTask() {
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
        getService(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getService(final State currentState) throws IOException {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
            .setCompletion(
                (Operation operation, Throwable throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  ServiceState.State serviceDocument = operation.getBody(ServiceState.State.class);

                  try {
                    initializeExpandService(currentState, serviceDocument);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void initializeExpandService(final State currentState,
                                       final ServiceState.State serviceDocument) throws IOException {

    Service service = this;
    HostUtils.getApiClient(this).getServiceApi().getVmsInServiceAsync(
        currentState.serviceId,
        new FutureCallback<ResourceList<Vm>>() {
          @Override
          public void onSuccess(@Nullable ResourceList<Vm> result) {
            int currentWorkerCount = 0;
            String masterNodeTag;
            String workerNodeTag;
            switch (serviceDocument.serviceType) {
              case KUBERNETES:
                masterNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.KubernetesMaster);
                workerNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.KubernetesWorker);
                break;
              case MESOS:
                masterNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.MesosMaster);
                workerNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.MesosWorker);
                break;
              case SWARM:
                masterNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.SwarmMaster);
                workerNodeTag = ServicesUtil.createServiceNodeTag(currentState.serviceId, NodeType.SwarmWorker);
                break;
              case HARBOR:
                // Harbor does not have any workers. Skip service expansion and mark this task as finished.
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED));
                return;
              default:
                throw new UnsupportedOperationException(
                    "ServiceType is not supported. ServiceType: " + serviceDocument.serviceType);
            }

            String masterVmId = null;
            for (Vm vm : result.getItems()) {
              if (vm.getTags().contains(workerNodeTag)) {
                ++currentWorkerCount;
              } else if (vm.getTags().contains(masterNodeTag)) {
                if (masterVmId == null) {
                  masterVmId = vm.getId();
                }
              }
            }

            int workerCountDelta = serviceDocument.workerCount - currentWorkerCount;

            if (workerCountDelta < 0) {
              String errorMessage = String.format(
                  "Worker count delta %d is negative. Target worker count is %d, current worker count is %d",
                  workerCountDelta, serviceDocument.workerCount, currentWorkerCount);
              ServiceUtils.logSevere(ServiceExpandTask.this, errorMessage);
              failTask(new IllegalStateException(errorMessage));
              return;
            }

            if (masterVmId == null) {
              String errorMessage = "No master vm is found.";
              ServiceUtils.logSevere(ServiceExpandTask.this, errorMessage);
              failTask(new IllegalStateException(errorMessage));
              return;
            }

            getMasterIp(currentState, serviceDocument, workerCountDelta, masterVmId);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void getMasterIp(final State currentState,
                           final ServiceState.State serviceDocument,
                           final int workerCountDelta,
                           final String masterVmId) {
    // If there is a master_ip field in extended properties and its not empty.
    // We use the master ip field instead of try to query the ip from the vm
    String masterIp = serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
    if (masterIp != null && !masterIp.isEmpty()) {
      try {
        expandService(currentState, serviceDocument, workerCountDelta, masterIp);
      } catch (Throwable t) {
        failTask(t);
      }
      return;
    }

    WaitForNetworkTaskService.State startState = new WaitForNetworkTaskService.State();
    startState.vmId = masterVmId;

    TaskUtils.startTaskAsync(
        this,
        WaitForNetworkTaskFactoryService.SELF_LINK,
        startState,
        state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        WaitForNetworkTaskService.State.class,
        ServicesManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<WaitForNetworkTaskService.State>() {
          @Override
          public void onSuccess(@Nullable WaitForNetworkTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                try {
                  expandService(currentState, serviceDocument, workerCountDelta, result.vmIpAddress);
                } catch (Throwable t) {
                  failTask(t);
                }
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(ServiceExpandTask.this,
                    buildPatch(TaskState.TaskStage.CANCELLED));
                break;
              case FAILED:
                TaskUtils.sendSelfPatch(ServiceExpandTask.this,
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

  private void expandService(State currentState,
                             ServiceState.State serviceDocument,
                             int workerCountDelta,
                             String masterIp) {

    if (workerCountDelta > 0) {
      ServiceUtils.logInfo(this, String.format(
          "Expected worker count is %d, delta is %d",
          serviceDocument.workerCount,
          workerCountDelta));

      NodeRolloutInput input = new NodeRolloutInput();
      input.serviceId = currentState.serviceId;
      input.nodeCount = Math.min(workerCountDelta, currentState.batchExpansionSize);
      input.imageId = serviceDocument.imageId;
      input.diskFlavorName = serviceDocument.diskFlavorName;
      input.vmFlavorName = serviceDocument.otherVmFlavorName;
      input.vmNetworkId = serviceDocument.vmNetworkId;
      input.projectId = serviceDocument.projectId;
      String sshKey = serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY);
      String caCert = serviceDocument.extendedProperties.get(
          ServicesManagerConstants.EXTENDED_PROPERTY_REGISTRY_CA_CERTIFICATE);

      switch (serviceDocument.serviceType) {
        case KUBERNETES: {
          List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(
              serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS));
          String cn = serviceDocument.extendedProperties.get(
              ServicesManagerConstants.EXTENDED_PROPERTY_CONTAINER_NETWORK);

          input.serverAddress = masterIp;
          input.nodeProperties = KubernetesWorkerNodeTemplate.createProperties(etcdIps, cn, masterIp, sshKey, caCert);
          input.nodeType = NodeType.KubernetesWorker;
          break;
        }
        case MESOS: {
          List<String> zkIps = NodeTemplateUtils.deserializeAddressList(
              serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ZOOKEEPER_IPS));

          input.serverAddress = masterIp;
          input.nodeProperties = MesosWorkerNodeTemplate.createProperties(zkIps);
          input.nodeType = NodeType.MesosWorker;
          break;
        }
        case SWARM: {
          List<String> etcdIps = NodeTemplateUtils.deserializeAddressList(
              serviceDocument.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ETCD_IPS));

          input.serverAddress = masterIp;
          input.nodeProperties = SwarmWorkerNodeTemplate.createProperties(etcdIps);
          input.nodeType = NodeType.SwarmWorker;
          break;
        }
        default:
          failTask(new UnsupportedOperationException(
              "ServiceType is not supported for resizing. ServiceType: " + serviceDocument.serviceType));
          return;
      }

      NodeRollout rollout = new WorkersNodeRollout();
      rollout.run(this, input, new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(@Nullable NodeRolloutResult result) {
          expandService(currentState, serviceDocument, workerCountDelta - input.nodeCount, masterIp);
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
   * {@link ServiceExpandTask} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
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
     * The identifier of the service.
     */
    @NotBlank
    @Immutable
    public String serviceId;

    /**
     * The threshold for each expansion batch.
     */
    @DefaultInteger(value = ServicesManagerConstants.DEFAULT_BATCH_EXPANSION_SIZE)
    @Immutable
    public Integer batchExpansionSize;
  }
}
