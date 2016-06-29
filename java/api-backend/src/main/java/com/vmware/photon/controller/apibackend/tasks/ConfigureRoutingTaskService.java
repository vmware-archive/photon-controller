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

import com.vmware.photon.controller.api.RoutingType;
import com.vmware.photon.controller.apibackend.exceptions.ConfigureRoutingException;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureRoutingTask;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureRoutingTask.TaskState;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.builders.LogicalPortCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterDownLinkPortCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier0CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier1CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.IPSubnet;
import com.vmware.photon.controller.nsxclient.models.LogicalPort;
import com.vmware.photon.controller.nsxclient.models.LogicalPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterDownLinkPortCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier0CreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterLinkPortOnTier1CreateSpec;
import com.vmware.photon.controller.nsxclient.models.ResourceReference;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

/**
 * Implements an Xenon service that represents a task to configure the routing on a logical network.
 */
public class ConfigureRoutingTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-routing-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(ConfigureRoutingTaskService.class, ConfigureRoutingTask.class);
  }

  public ConfigureRoutingTaskService() {
    super(ConfigureRoutingTask.class);

    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      ConfigureRoutingTask startState = startOperation.getBody(ConfigureRoutingTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.CREATE_SWITCH_PORT;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
      ConfigureRoutingTask currentState = getState(patchOperation);
      ConfigureRoutingTask patchState = patchOperation.getBody(ConfigureRoutingTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(ConfigureRoutingTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case CREATE_SWITCH_PORT:
          createLogicalSwitchPort(currentState);
          break;

        case CONNECT_TIER1_ROUTER_TO_SWITCH:
          connectTier1RouterToSwitch(currentState);
          break;

        case CREATE_TIER0_ROUTER_PORT:
          createTier0RouterPort(currentState);
          break;

        case CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER:
          connectTier1RouterToTier0Router(currentState);
          break;

        default:
          throw new ConfigureRoutingException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void createLogicalSwitchPort(ConfigureRoutingTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Creating port on logical switch %s", currentState.logicalSwitchId);

    LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalSwitchApi();

    LogicalPortCreateSpec spec = new LogicalPortCreateSpecBuilder()
        .logicalSwitchId(currentState.logicalSwitchId)
        .displayName(currentState.logicalSwitchPortDisplayName)
        .build();
    logicalSwitchApi.createLogicalPort(spec,
        new FutureCallback<LogicalPort>() {
          @Override
          public void onSuccess(@Nullable LogicalPort result) {
            ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH,
                null);
            patch.logicalSwitchPortId = result.getId();

            TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void connectTier1RouterToSwitch(ConfigureRoutingTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Connecting router %s to switch %s", currentState.logicalTier1RouterId,
        currentState.logicalSwitchId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    IPSubnet ipSubnet = new IPSubnet();
    ipSubnet.setIpAddresses(ImmutableList.of(currentState.logicalTier1RouterDownLinkPortIp));
    ipSubnet.setPrefixLength(currentState.logicalTier1RouterDownLinkPortIpPrefixLen);

    ResourceReference resourceReference = new ResourceReference();
    resourceReference.setTargetId(currentState.logicalSwitchPortId);

    LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpecBuilder()
        .displayName(currentState.logicalTier1RouterDownLinkPortDisplayName)
        .logicalRouterId(currentState.logicalTier1RouterId)
        .resourceType(NsxRouter.PortType.DOWN_LINK_PORT)
        .subnets(ImmutableList.of(ipSubnet))
        .linkedLogicalSwitchPortId(resourceReference)
        .build();

    logicalRouterApi.createLogicalRouterDownLinkPort(spec,
        new FutureCallback<LogicalRouterDownLinkPort>() {
          @Override
          public void onSuccess(@Nullable LogicalRouterDownLinkPort result) {
            ConfigureRoutingTask patch = null;
            if (currentState.routingType == RoutingType.ROUTED) {
              patch = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TIER0_ROUTER_PORT, null);
            } else {
              patch = buildPatch(TaskState.TaskStage.FINISHED);
            }

            patch.logicalTier1RouterDownLinkPort = result.getId();

            TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void createTier0RouterPort(ConfigureRoutingTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Creating port on tier-0 router %s", currentState.logicalTier0RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    LogicalRouterLinkPortOnTier0CreateSpec spec = new LogicalRouterLinkPortOnTier0CreateSpecBuilder()
        .displayName(currentState.logicalLinkPortOnTier0RouterDisplayName)
        .logicalRouterId(currentState.logicalTier0RouterId)
        .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
        .build();

    logicalRouterApi.createLogicalRouterLinkPortTier0(spec,
        new FutureCallback<LogicalRouterLinkPortOnTier0>() {
          @Override
          public void onSuccess(@Nullable LogicalRouterLinkPortOnTier0 result) {
            ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER, null);
            patch.logicalLinkPortOnTier0Router = result.getId();

            TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void connectTier1RouterToTier0Router(ConfigureRoutingTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Connecting tier-1 router %s to tier-0 router %s", currentState.logicalTier1RouterId,
        currentState.logicalTier0RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    ResourceReference resourceReference = new ResourceReference();
    resourceReference.setTargetId(currentState.logicalLinkPortOnTier0Router);

    LogicalRouterLinkPortOnTier1CreateSpec spec = new LogicalRouterLinkPortOnTier1CreateSpecBuilder()
        .displayName(currentState.logicalLinkPortOnTier1RouterDisplayName)
        .logicalRouterId(currentState.logicalTier1RouterId)
        .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER1)
        .linkedLogicalRouterPortId(resourceReference)
        .build();

    logicalRouterApi.createLogicalRouterLinkPortTier1(spec,
        new FutureCallback<LogicalRouterLinkPortOnTier1>() {
          @Override
          public void onSuccess(@Nullable LogicalRouterLinkPortOnTier1 result) {
            ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.FINISHED);
            patch.logicalLinkPortOnTier1Router = result.getId();

            TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void validateStartState(ConfigureRoutingTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != TaskState.TaskStage.STARTED);
  }

  private void validateState(ConfigureRoutingTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(ConfigureRoutingTask currentState, ConfigureRoutingTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState, currentState.routingType);
    }
  }

  private void validateTaskSubStageProgression(TaskState startState, TaskState patchState, RoutingType routingType) {
    if (patchState.stage.ordinal() > TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == TaskState.TaskStage.FINISHED) {
      if (routingType == RoutingType.ROUTED) {
        checkState(startState.stage == TaskState.TaskStage.STARTED
            && startState.subStage == TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_TIER0_ROUTER);
      } else {
        checkState(startState.stage == TaskState.TaskStage.STARTED
            && startState.subStage == TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH);
      }
    }

    if (patchState.stage == TaskState.TaskStage.STARTED) {
      checkState(patchState.subStage.ordinal() == startState.subStage.ordinal()
          || patchState.subStage.ordinal() == startState.subStage.ordinal() + 1);

      if (routingType == RoutingType.ISOLATED) {
        checkState(patchState.subStage.ordinal() <= TaskState.SubStage.CONNECT_TIER1_ROUTER_TO_SWITCH.ordinal());
      }
    }
  }

  private ConfigureRoutingTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private ConfigureRoutingTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private ConfigureRoutingTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private ConfigureRoutingTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    ConfigureRoutingTask state = new ConfigureRoutingTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      ConfigureRoutingTask patchState = buildPatch(TaskState.TaskStage.FAILED, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + e.toString());
    }
  }
}
