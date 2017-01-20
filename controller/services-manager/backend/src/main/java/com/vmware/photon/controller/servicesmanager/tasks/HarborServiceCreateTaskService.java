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
import com.vmware.photon.controller.servicesmanager.clients.HarborClient;
import com.vmware.photon.controller.servicesmanager.rolloutplans.BasicNodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRollout;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutInput;
import com.vmware.photon.controller.servicesmanager.rolloutplans.NodeRolloutResult;
import com.vmware.photon.controller.servicesmanager.servicedocuments.HarborServiceCreateTaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.HarborServiceCreateTaskState.TaskState;
import com.vmware.photon.controller.servicesmanager.servicedocuments.NodeType;
import com.vmware.photon.controller.servicesmanager.servicedocuments.ServicesManagerConstants;
import com.vmware.photon.controller.servicesmanager.templates.HarborNodeTemplate;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon service representing a task to create a Harbor service.
 */
public class HarborServiceCreateTaskService extends StatefulService {

  private static final int HARBOR_NODE_COUNT = 1;

  public HarborServiceCreateTaskService() {
    super(HarborServiceCreateTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    HarborServiceCreateTaskState startState = start.getBody(HarborServiceCreateTaskState.class);
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
    HarborServiceCreateTaskState currentState = getState(patch);
    HarborServiceCreateTaskState patchState = patch.getBody(HarborServiceCreateTaskState.class);
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

  private void processStateMachine(HarborServiceCreateTaskState currentState) {
    ServiceUtils.logInfo(this, "Start %s with service id: %s", currentState.taskState.subStage, currentState.serviceId);
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
  private void setupHarbor(HarborServiceCreateTaskState currentState) {
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
          rolloutInput.nodeCount = HARBOR_NODE_COUNT;
          rolloutInput.nodeType = NodeType.Harbor;
          rolloutInput.nodeProperties = HarborNodeTemplate.createProperties(
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_DNS),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_GATEWAY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_NETMASK),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_SSH_KEY),
              service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD));

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

  private void startMaintenance(final HarborServiceCreateTaskState currentState) {
    ServiceMaintenanceTask.State startState = new ServiceMaintenanceTask.State();
    startState.documentSelfLink = currentState.serviceId;

    // Start the maintenance task async without waiting for its completion so that the creation task
    // can finish immediately.
    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceMaintenanceTaskFactory.SELF_LINK))
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

  private void updateExtendedProperties(HarborServiceCreateTaskState currentState) {
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(ServiceStateFactory.SELF_LINK + "/" + currentState.serviceId)
        .setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ServiceState.State service = operation.getBody(ServiceState.State.class);
          String harborAddress = service.extendedProperties.get(ServicesManagerConstants.EXTENDED_PROPERTY_MASTER_IP);
          HarborClient harborClient = HostUtils.getHarborClient(this);
          String connectionString = "https://" + harborAddress + ":" + ServicesManagerConstants.Harbor.HARBOR_PORT;
          getCACertAndUpdate(currentState, service, harborClient, connectionString);
        }));
  }

  private void getCACertAndUpdate(HarborServiceCreateTaskState currentState, ServiceState.State serviceState,
                                  HarborClient harborClient, String connectionString) {
      try {
        harborClient.getCACertificate(connectionString, new FutureCallback<String>() {
          @Override
          public void onSuccess(@Nullable String result) {
            Map<String, String> extendedProperties = new HashMap<>(serviceState.extendedProperties);
            extendedProperties.put(ServicesManagerConstants.EXTENDED_PROPERTY_CA_CERTIFICATE, result);
            // Remove admin password from extended properties since Harbor is already up and we don't need the
            // password any more.
            extendedProperties.remove(ServicesManagerConstants.EXTENDED_PROPERTY_ADMIN_PASSWORD);
            HarborServiceCreateTaskState desiredState = buildPatch(TaskState.TaskStage.FINISHED, null);

            ServiceState.State servicePatch = new ServiceState.State();
            servicePatch.serviceState = com.vmware.photon.controller.api.model.ServiceState.READY;
            servicePatch.extendedProperties = extendedProperties;

            updateStates(currentState, desiredState, servicePatch);
          }

          @Override
          public void onFailure(Throwable t) {
            if (currentState.updatePropsIterations >= currentState.updatePropsMaxIterations) {
              failTask(t);
            } else {
              getHost().schedule(
                  () -> {
                    HarborServiceCreateTaskState patchState = buildPatch(TaskState.TaskStage.STARTED,
                        TaskState.SubStage.UPDATE_EXTENDED_PROPERTIES);
                    patchState.updatePropsIterations = currentState.updatePropsIterations + 1;
                    TaskUtils.sendSelfPatch(HarborServiceCreateTaskService.this, patchState);
                  },
                  currentState.updatePropsPollDelay, TimeUnit.MILLISECONDS);
            }
          }
        });
      } catch (IOException e) {
        failTask(e);
      }
  }

  private void validateStartState(HarborServiceCreateTaskState startState) {
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

  private void validatePatchState(HarborServiceCreateTaskState currentState,
                                  HarborServiceCreateTaskState patchState) {
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

  private HarborServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private HarborServiceCreateTaskState buildPatch(TaskState.TaskStage stage,
                                                 TaskState.SubStage subStage,
                                                 @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private HarborServiceCreateTaskState buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    HarborServiceCreateTaskState state = new HarborServiceCreateTaskState();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTaskAndPatchDocument(
      final HarborServiceCreateTaskState currentState,
      final NodeType nodeType,
      final Throwable throwable) {
    ServiceUtils.logSevere(this, throwable);
    HarborServiceCreateTaskState patchState = buildPatch(
        TaskState.TaskStage.FAILED, null,
        new IllegalStateException(String.format("Failed to rollout %s. Error: %s",
            nodeType.toString(), throwable.toString())));

    ServiceState.State document = new ServiceState.State();
    document.serviceState = com.vmware.photon.controller.api.model.ServiceState.FATAL_ERROR;
    document.errorReason = throwable.toString();
    updateStates(currentState, patchState, document);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void updateStates(final HarborServiceCreateTaskState currentState,
                            final HarborServiceCreateTaskState patchState,
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

                  TaskUtils.sendSelfPatch(HarborServiceCreateTaskService.this, patchState);
                }
            ));
  }
}
