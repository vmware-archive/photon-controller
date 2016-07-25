/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.exceptions.RemoveFloatingIpFromVmException;
import com.vmware.photon.controller.apibackend.servicedocuments.RemoveFloatingIpFromVmTask;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.cloudstore.xenon.entity.VirtualNetworkService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements an Xenon service that represents a task to remove the floating IP from a VM.
 */
public class RemoveFloatingIpFromVmTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/remove-floating-ip-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(RemoveFloatingIpFromVmTaskService.class, RemoveFloatingIpFromVmTask.class);
  }

  public RemoveFloatingIpFromVmTaskService() {
    super(RemoveFloatingIpFromVmTask.class);

    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      RemoveFloatingIpFromVmTask startState = startOperation.getBody(RemoveFloatingIpFromVmTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == RemoveFloatingIpFromVmTask.TaskState.TaskStage.CREATED) {
        startState.taskState.stage = RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED;
        startState.taskState.subStage = RemoveFloatingIpFromVmTask.TaskState.SubStage.REMOVE_NAT_RULE;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage));
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      RemoveFloatingIpFromVmTask currentState = getState(patchOperation);
      RemoveFloatingIpFromVmTask patchState = patchOperation.getBody(RemoveFloatingIpFromVmTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(RemoveFloatingIpFromVmTask currentState) {
    try {

      switch (currentState.taskState.subStage) {
        case REMOVE_NAT_RULE:
          removeNatRule(currentState);
          break;
        case UPDATE_VIRTUAL_NETWORK:
          getVirtualNetwork(currentState);
          break;

        default:
          throw new RemoveFloatingIpFromVmException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void removeNatRule(RemoveFloatingIpFromVmTask currentState) throws Throwable {
    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        currentState.nsxAddress,
        currentState.nsxUsername,
        currentState.nsxPassword)
        .getLogicalRouterApi();

    logicalRouterApi.deleteNatRule(currentState.logicalTier1RouterId,
        currentState.natRuleId,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            RemoveFloatingIpFromVmTask patchState = buildPatch(TaskState.TaskStage.STARTED,
                RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
            TaskUtils.sendSelfPatch(RemoveFloatingIpFromVmTaskService.this, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void getVirtualNetwork(RemoveFloatingIpFromVmTask currentState) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + currentState.virtualNetworkId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          VirtualNetworkService.State virtualNetwork = op.getBody(VirtualNetworkService.State.class);
          updateVirtualNetwork(virtualNetwork.documentSelfLink,
              virtualNetwork.natRuleToFloatingIpMap,
              currentState);
        })
        .sendWith(this);
  }

  private void updateVirtualNetwork(String virtualNetworkSelfLink,
                                    Map<String, String> natRuleToFloatingIpMap,
                                    RemoveFloatingIpFromVmTask currentState) {
    checkState(!natRuleToFloatingIpMap.isEmpty());
    checkState(natRuleToFloatingIpMap.containsKey(currentState.natRuleId));

    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();
    virtualNetwork.natRuleToFloatingIpMap = new HashMap<>(natRuleToFloatingIpMap);
    virtualNetwork.natRuleToFloatingIpMap.remove(currentState.natRuleId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(virtualNetworkSelfLink)
        .setBody(virtualNetwork)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            RemoveFloatingIpFromVmTask patch = buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED);
            TaskUtils.sendSelfPatch(RemoveFloatingIpFromVmTaskService.this, patch);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }


  private void validateStartState(RemoveFloatingIpFromVmTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED,
        "Service state is invalid (START). Restart is disabled.");
  }

  private void validateState(RemoveFloatingIpFromVmTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(RemoveFloatingIpFromVmTask currentState, RemoveFloatingIpFromVmTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
    }
  }

  private void validateTaskSubStageProgression(RemoveFloatingIpFromVmTask.TaskState startState,
                                               RemoveFloatingIpFromVmTask.TaskState patchState) {
    if (patchState.stage.ordinal() > RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == RemoveFloatingIpFromVmTask.TaskState.TaskStage.FINISHED) {
      checkState(startState.subStage == RemoveFloatingIpFromVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
    } else if (patchState.stage == RemoveFloatingIpFromVmTask.TaskState.TaskStage.STARTED) {
      checkState(patchState.subStage.ordinal() == startState.subStage.ordinal()
          || patchState.subStage.ordinal() == startState.subStage.ordinal() + 1);
    }
  }

  private RemoveFloatingIpFromVmTask buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private RemoveFloatingIpFromVmTask buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage stage,
                                                RemoveFloatingIpFromVmTask.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private RemoveFloatingIpFromVmTask buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage stage,
                                                Throwable t) {
    return buildPatch(stage, null, t);
  }

  private RemoveFloatingIpFromVmTask buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage stage,
                                                RemoveFloatingIpFromVmTask.TaskState.SubStage subStage,
                                                Throwable t) {
    RemoveFloatingIpFromVmTask state = new RemoveFloatingIpFromVmTask();
    state.taskState = new RemoveFloatingIpFromVmTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      RemoveFloatingIpFromVmTask patchState = buildPatch(RemoveFloatingIpFromVmTask.TaskState.TaskStage.FAILED, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + e.toString());
    }
  }
}
