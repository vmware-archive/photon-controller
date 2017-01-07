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

package com.vmware.photon.controller.api.backend.tasks;

import com.vmware.photon.controller.api.backend.exceptions.ConfigureRoutingException;
import com.vmware.photon.controller.api.backend.servicedocuments.ConfigureRoutingTask;
import com.vmware.photon.controller.api.backend.servicedocuments.ConfigureRoutingTask.TaskState;
import com.vmware.photon.controller.api.backend.utils.ServiceHostUtils;
import com.vmware.photon.controller.api.model.RoutingType;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.photon.controller.nsxclient.builders.LogicalPortCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterDownLinkPortCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier0CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.LogicalRouterLinkPortOnTier1CreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.builders.RoutingAdvertisementUpdateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.LogicalServiceResourceType;
import com.vmware.photon.controller.nsxclient.datatypes.NatActionType;
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
import com.vmware.photon.controller.nsxclient.models.NatRule;
import com.vmware.photon.controller.nsxclient.models.NatRuleCreateSpec;
import com.vmware.photon.controller.nsxclient.models.ResourceReference;
import com.vmware.photon.controller.nsxclient.models.RoutingAdvertisement;
import com.vmware.photon.controller.nsxclient.models.RoutingAdvertisementUpdateSpec;
import com.vmware.photon.controller.nsxclient.models.ServiceBinding;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;
import com.vmware.photon.controller.nsxclient.utils.TagUtils;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implements an Xenon service that represents a task to configure the routing on a logical network.
 */
public class ConfigureRoutingTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-routing-tasks";
  private static final int NUM_RETRIES = 5;

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

        case ADD_SNAT_RULES:
          addSnatRules(currentState);
          break;

        case ENABLE_ROUTING_ADVERTISEMENT:
          List<Integer> retryCount = Arrays.asList(0);
          getRoutingAdvertisement(currentState, retryCount);
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

    LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
        currentState.nsxUsername, currentState.nsxPassword).getLogicalSwitchApi();

    LogicalPortCreateSpec spec = new LogicalPortCreateSpecBuilder()
        .logicalSwitchId(currentState.logicalSwitchId)
        .displayName(NameUtils.getLogicalSwitchUplinkPortName(currentState.networkId))
        .description(NameUtils.getLogicalSwitchUplinkPortDescription(currentState.networkId))
        .tags(TagUtils.getLogicalSwitchUplinkPortTags(currentState.networkId))
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

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
        currentState.nsxUsername, currentState.nsxPassword).getLogicalRouterApi();

    IPSubnet ipSubnet = new IPSubnet();
    ipSubnet.setIpAddresses(ImmutableList.of(currentState.logicalTier1RouterDownLinkPortIp));
    ipSubnet.setPrefixLength(currentState.logicalTier1RouterDownLinkPortIpPrefixLen);

    ResourceReference logicalSwitchPortReference = new ResourceReference();
    logicalSwitchPortReference.setTargetId(currentState.logicalSwitchPortId);

    List<ServiceBinding> serviceBindings = new ArrayList<>();
    if (currentState.dhcpRelayServiceId != null) {
      ResourceReference dhcpRelayServiceReference = new ResourceReference();
      dhcpRelayServiceReference.setTargetId(currentState.dhcpRelayServiceId);
      dhcpRelayServiceReference.setTargetType(LogicalServiceResourceType.DHCP_RELAY_SERVICE.getValue());

      ServiceBinding serviceBinding = new ServiceBinding();
      serviceBinding.setServiceId(dhcpRelayServiceReference);
      serviceBindings.add(serviceBinding);
    }

    LogicalRouterDownLinkPortCreateSpec spec = new LogicalRouterDownLinkPortCreateSpecBuilder()
        .displayName(NameUtils.getLogicalRouterDownlinkPortName(currentState.networkId))
        .description(NameUtils.getLogicalRouterDownlinkPortDescription(currentState.networkId))
        .logicalRouterId(currentState.logicalTier1RouterId)
        .resourceType(NsxRouter.PortType.DOWN_LINK_PORT)
        .subnets(ImmutableList.of(ipSubnet))
        .linkedLogicalSwitchPortId(logicalSwitchPortReference)
        .serviceBindings(serviceBindings)
        .tags(TagUtils.getLogicalRouterDownlinkPortTags(currentState.networkId))
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

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
        currentState.nsxUsername, currentState.nsxPassword).getLogicalRouterApi();

    LogicalRouterLinkPortOnTier0CreateSpec spec = new LogicalRouterLinkPortOnTier0CreateSpecBuilder()
        .displayName(NameUtils.getTier0RouterDownlinkPortName(currentState.networkId))
        .description(NameUtils.getTier0RouterDownlinkPortDescription(currentState.networkId))
        .logicalRouterId(currentState.logicalTier0RouterId)
        .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER0)
        .tags(TagUtils.getTier0RouterDownlinkPortTags(currentState.networkId))
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

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
        currentState.nsxUsername, currentState.nsxPassword).getLogicalRouterApi();

    ResourceReference resourceReference = new ResourceReference();
    resourceReference.setTargetId(currentState.logicalLinkPortOnTier0Router);

    LogicalRouterLinkPortOnTier1CreateSpec spec = new LogicalRouterLinkPortOnTier1CreateSpecBuilder()
        .displayName(NameUtils.getLogicalRouterUplinkPortName(currentState.networkId))
        .description(NameUtils.getLogicalRouterUplinkdPortDescription(currentState.networkId))
        .logicalRouterId(currentState.logicalTier1RouterId)
        .resourceType(NsxRouter.PortType.LINK_PORT_ON_TIER1)
        .linkedLogicalRouterPortId(resourceReference)
        .tags(TagUtils.getLogicalRouterUplinkPortTags(currentState.networkId))
        .build();

    logicalRouterApi.createLogicalRouterLinkPortTier1(spec,
        new FutureCallback<LogicalRouterLinkPortOnTier1>() {
          @Override
          public void onSuccess(@Nullable LogicalRouterLinkPortOnTier1 result) {
            ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.ADD_SNAT_RULES, null);
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

  private void addSnatRules(ConfigureRoutingTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Adding SNAT rules to tier-1 router %s", currentState.logicalTier1RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
        currentState.nsxUsername, currentState.nsxPassword).getLogicalRouterApi();

    NatRuleCreateSpec spec = new NatRuleCreateSpec();
    spec.setNatAction(NatActionType.SNAT);
    spec.setTranslatedNetwork(currentState.snatIp);
    spec.setRulePriority(1025);
    spec.setTranslatedPorts("");
    spec.setLoggingEnabled(false);
    spec.setEnabled(true);

    logicalRouterApi.createNatRule(currentState.logicalTier1RouterId, spec,
        new FutureCallback<NatRule>() {
          @Override
          public void onSuccess(@Nullable NatRule natRule) {
            ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.ENABLE_ROUTING_ADVERTISEMENT, null);

            TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void getRoutingAdvertisement(ConfigureRoutingTask currentState, List<Integer> retryCount) {
    ServiceUtils.logInfo(this, "Get current routing advertisement revision on tier-1 router %s",
        currentState.logicalTier1RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(
        getHost(),
        currentState.nsxAddress,
        currentState.nsxUsername,
        currentState.nsxPassword
    ).getLogicalRouterApi();

    try {
      logicalRouterApi.getRoutingAdvertisement(
          currentState.logicalTier1RouterId,
          new FutureCallback<RoutingAdvertisement>() {
            @Override
            public void onSuccess(RoutingAdvertisement routingAdvertisement) {
              enableRoutingAdvertisement(currentState, routingAdvertisement.getRevision(), retryCount);
            }

            @Override
            public void onFailure(Throwable t) {
              failTask(t);
            }
          }
      );
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void enableRoutingAdvertisement(ConfigureRoutingTask currentState, int revision, List<Integer> retryCount) {
    ServiceUtils.logInfo(this, "Enable routing advertisement on tier-1 router %s", currentState.logicalTier1RouterId);

    NsxClient nsxClient = ServiceHostUtils.getNsxClient(
        getHost(),
        currentState.nsxAddress,
        currentState.nsxUsername,
        currentState.nsxPassword
    );

    getHost().schedule(() -> {
      try {
        RoutingAdvertisementUpdateSpec spec = new RoutingAdvertisementUpdateSpecBuilder()
            .advertiseNatRoutes(true)
            .advertiseNsxConnectedRoutes(false)
            .advertiseNatRoutes(true)
            .advertiseStaticRoutes(false)
            .enabled(true)
            .revision(revision)
            .build();

        nsxClient.getLogicalRouterApi().configureRoutingAdvertisement(
            currentState.logicalTier1RouterId,
            spec,
            new FutureCallback<RoutingAdvertisement>() {
              @Override
              public void onSuccess(@Nullable RoutingAdvertisement routingAdvertisement) {
                ConfigureRoutingTask patch = buildPatch(TaskState.TaskStage.FINISHED);
                TaskUtils.sendSelfPatch(ConfigureRoutingTaskService.this, patch);
              }

              @Override
              public void onFailure(Throwable t) {
                // Technically speaking, if another user is updating the routing advertisement simultaneously,
                // this update would fail. So we need to retry reading the current revision, and do the update
                // again.
                int currNumTries = retryCount.get(0);
                if (currNumTries++ < NUM_RETRIES) {
                  ServiceUtils.logSevere(ConfigureRoutingTaskService.this,
                      "Enabling routing advertisement on tier-1 router %s failed with error %s, retrying ...",
                      currentState.logicalTier1RouterId,
                      t.getMessage());

                  retryCount.set(0, currNumTries);
                  getRoutingAdvertisement(currentState, retryCount);
                } else {
                  failTask(t);
                }
              }
            }
        );
      } catch (Throwable t) {
        failTask(t);
      }
    }, nsxClient.getEnableRoutingAdvertisementRetryDelay(), TimeUnit.MILLISECONDS);
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
            && startState.subStage == TaskState.SubStage.ENABLE_ROUTING_ADVERTISEMENT);
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
