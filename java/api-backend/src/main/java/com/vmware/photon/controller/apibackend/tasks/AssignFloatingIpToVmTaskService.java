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

import com.vmware.photon.controller.apibackend.servicedocuments.AssignFloatingIpToVmTask;
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
import com.vmware.photon.controller.nsxclient.models.NatRule;
import com.vmware.photon.controller.nsxclient.models.NatRuleCreateSpec;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements an Xenon service that assigns a floating IP to a VM.
 */
public class AssignFloatingIpToVmTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/assign-floating-ip-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(AssignFloatingIpToVmTaskService.class, AssignFloatingIpToVmTask.class);
  }

  public AssignFloatingIpToVmTaskService() {
    super(AssignFloatingIpToVmTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      AssignFloatingIpToVmTask startState = startOperation.getBody(AssignFloatingIpToVmTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == AssignFloatingIpToVmTask.TaskState.TaskStage.CREATED) {
        startState.taskState.stage = AssignFloatingIpToVmTask.TaskState.TaskStage.STARTED;
        startState.taskState.subStage = AssignFloatingIpToVmTask.TaskState.SubStage.CREATE_NAT_RULE;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (AssignFloatingIpToVmTask.TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
      AssignFloatingIpToVmTask currentState = getState(patchOperation);
      AssignFloatingIpToVmTask patchState = patchOperation.getBody(AssignFloatingIpToVmTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
        return;
      }

      if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(AssignFloatingIpToVmTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case CREATE_NAT_RULE:
          createNatRule(currentState);
          break;
        case UPDATE_VIRTUAL_NETWORK:
          getVirtualNetwork(currentState);
          break;

        default:
          throw new RuntimeException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void createNatRule(AssignFloatingIpToVmTask currentState) throws Throwable {
    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(),
        currentState.nsxManagerEndpoint,
        currentState.username,
        currentState.password)
        .getLogicalRouterApi();

    NatRuleCreateSpec natRuleCreateSpec = new NatRuleCreateSpec();
    natRuleCreateSpec.setDisplayName(NameUtils.getDnatRuleName(currentState.vmPrivateIpAddress));
    natRuleCreateSpec.setDescription(NameUtils.getDnatRuleDescription(currentState.vmPrivateIpAddress));
    natRuleCreateSpec.setMatchDestinationNetwork(currentState.vmFloatingIpAddress);
    natRuleCreateSpec.setTranslatedNetwork(currentState.vmPrivateIpAddress);

    logicalRouterApi.createNatRule(currentState.logicalTier1RouterId,
        natRuleCreateSpec,
        new FutureCallback<NatRule>() {
          @Override
          public void onSuccess(NatRule result) {
            AssignFloatingIpToVmTask patchState = buildPatch(TaskState.TaskStage.STARTED,
                AssignFloatingIpToVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
            patchState.natRuleId = result.getId();

            TaskUtils.sendSelfPatch(AssignFloatingIpToVmTaskService.this, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void getVirtualNetwork(AssignFloatingIpToVmTask currentState) {
    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createGet(VirtualNetworkService.FACTORY_LINK + "/" + currentState.networkId)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          VirtualNetworkService.State virtualNetwork = op.getBody(VirtualNetworkService.State.class);
          updateVirtualNetwork(virtualNetwork.documentSelfLink,
              virtualNetwork.floatingIpToNatRuleMap,
              currentState);
        })
        .sendWith(this);
  }

  private void updateVirtualNetwork(String virtualNetworkSelfLink,
                                    Map<String, String> floatingIpToNatRuleMap,
                                    AssignFloatingIpToVmTask currentState) {

    VirtualNetworkService.State virtualNetwork = new VirtualNetworkService.State();

    if (floatingIpToNatRuleMap == null) {
      virtualNetwork.floatingIpToNatRuleMap = new HashMap<>();
    } else {
      virtualNetwork.floatingIpToNatRuleMap = new HashMap<>(floatingIpToNatRuleMap);
    }
    virtualNetwork.floatingIpToNatRuleMap.put(currentState.vmFloatingIpAddress, currentState.natRuleId);

    ServiceHostUtils.getCloudStoreHelper(getHost())
        .createPatch(virtualNetworkSelfLink)
        .setBody(virtualNetwork)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            AssignFloatingIpToVmTask patch = buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage.FINISHED);
            TaskUtils.sendSelfPatch(AssignFloatingIpToVmTaskService.this, patch);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void validateStartState(AssignFloatingIpToVmTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != AssignFloatingIpToVmTask.TaskState.TaskStage.STARTED,
        "Service state is invalid (START). Restart is disabled.");
  }

  private void validateState(AssignFloatingIpToVmTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(AssignFloatingIpToVmTask currentState, AssignFloatingIpToVmTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == AssignFloatingIpToVmTask.TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
    }
  }

  private void validateTaskSubStageProgression(AssignFloatingIpToVmTask.TaskState startState,
                                               AssignFloatingIpToVmTask.TaskState patchState) {
    if (patchState.stage.ordinal() > AssignFloatingIpToVmTask.TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == AssignFloatingIpToVmTask.TaskState.TaskStage.FINISHED) {
      checkState(startState.subStage == AssignFloatingIpToVmTask.TaskState.SubStage.UPDATE_VIRTUAL_NETWORK);
    } else if (patchState.stage == AssignFloatingIpToVmTask.TaskState.TaskStage.STARTED) {
      checkState(startState.subStage.ordinal() + 1 == patchState.subStage.ordinal()
          || startState.subStage.ordinal() == patchState.subStage.ordinal());
    }
  }

  private AssignFloatingIpToVmTask buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private AssignFloatingIpToVmTask buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage stage,
                                              AssignFloatingIpToVmTask.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private AssignFloatingIpToVmTask buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage stage,
                                              Throwable t) {
    return buildPatch(stage, null, t);
  }

  private AssignFloatingIpToVmTask buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage stage,
                                              AssignFloatingIpToVmTask.TaskState.SubStage subStage,
                                              Throwable t) {
    AssignFloatingIpToVmTask state = new AssignFloatingIpToVmTask();
    state.taskState = new AssignFloatingIpToVmTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(AssignFloatingIpToVmTask.TaskState.TaskStage.FAILED, e));
  }
}
