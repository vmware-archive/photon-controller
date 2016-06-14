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

import com.vmware.photon.controller.apibackend.exceptions.ConfigureRoutingException;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalPortsTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalPortsTask.TaskState;
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
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPort;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterPortListResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.TimeUnit;

/**
* Implements an Xenon service that represents a task to delete the logical ports on a logical network.
*/
public class DeleteLogicalPortsTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/delete-logical-ports-tasks";
  public static final String LOGICAL_PORT = "LogicalPort";

  public static FactoryService createFactory() {
    return FactoryService.create(DeleteLogicalPortsTaskService.class, DeleteLogicalPortsTask.class);
  }

  public DeleteLogicalPortsTaskService() {
    super(DeleteLogicalPortsTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      DeleteLogicalPortsTask startState = startOperation.getBody(DeleteLogicalPortsTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.GET_LINK_PORTS;
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
      DeleteLogicalPortsTask currentState = getState(patchOperation);
      DeleteLogicalPortsTask patchState = patchOperation.getBody(DeleteLogicalPortsTask.class);

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

  private void processPatch(DeleteLogicalPortsTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case GET_LINK_PORTS:
          getTier1RouterPorts(currentState);
          break;

        case DELETE_TIER1_ROUTER_LINK_PORT:
          deleteTier1RouterLinkPort(currentState);
          break;

        case WAIT_DELETE_TIER1_ROUTER_LINK_PORT:
          waitDeleteTier1RouterLinkPort(currentState);
          break;

        case DELETE_TIER0_ROUTER_LINK_PORT:
          deleteTier0RouterLinkPort(currentState);
          break;

        case WAIT_DELETE_TIER0_ROUTER_LINK_PORT:
          waitDeleteTier0RouterLinkPort(currentState);
          break;

        case DELETE_TIER1_ROUTER_DOWN_LINK_PORT:
          deleteTier1RouterDownLinkPort(currentState);
          break;

        case WAIT_DELETE_TIER1_ROUTER_DOWN_LINK_PORT:
          waitDeleteTier1RouterDownLinkPort(currentState);
          break;

        case DELETE_SWITCH_PORT:
          deleteSwitchPort(currentState);
          break;

        default:
          throw new ConfigureRoutingException("Invalid task sub-stage " + currentState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void getTier1RouterPorts(DeleteLogicalPortsTask currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Getting link port on tier-1 router %s", currentState.logicalTier1RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    logicalRouterApi.listLogicalRouterPorts(currentState.logicalTier1RouterId,
        new FutureCallback<LogicalRouterPortListResult>() {
          @Override
          public void onSuccess(LogicalRouterPortListResult logicalRouterPortListResult) {
            if (logicalRouterPortListResult.getResultCount() == 0) {
              finishTask();
              return;
            }

            getLinkedLogicalPorts(logicalRouterPortListResult, currentState);

            DeleteLogicalPortsTask patchState = buildPatch(com.vmware.xenon.common.TaskState.TaskStage.STARTED,
                TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT);
            patchState.logicalLinkPortOnTier1Router = currentState.logicalLinkPortOnTier1Router;
            patchState.logicalLinkPortOnTier0Router = currentState.logicalLinkPortOnTier0Router;
            patchState.logicalDownLinkPortOnTier1Router = currentState.logicalDownLinkPortOnTier1Router;
            patchState.logicalPortOnSwitch = currentState.logicalPortOnSwitch;
            TaskUtils.sendSelfPatch(DeleteLogicalPortsTaskService.this, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void getLinkedLogicalPorts(
      LogicalRouterPortListResult logicalRouterPortListResult,
      DeleteLogicalPortsTask currentState) {
    for (LogicalRouterPort port : logicalRouterPortListResult.getLogicalRouterPorts()) {
      switch (port.getResourceType()) {
        case LINK_PORT_ON_TIER1:
          if (port.getLinkedLogicalRouterPortId() != null &&
              port.getLinkedLogicalRouterPortId().getTargetType()
                  .equals(NsxRouter.PortType.LINK_PORT_ON_TIER0.getValue())) {
            currentState.logicalLinkPortOnTier0Router = port.getLinkedLogicalRouterPortId().getTargetId();
            currentState.logicalLinkPortOnTier1Router = port.getId();
          }
          break;
        case DOWN_LINK_PORT:
          if (port.getLinkedLogicalSwitchPortId() != null &&
              port.getLinkedLogicalSwitchPortId().getTargetType().equals(LOGICAL_PORT)) {
            currentState.logicalPortOnSwitch = port.getLinkedLogicalSwitchPortId().getTargetId();
            currentState.logicalDownLinkPortOnTier1Router = port.getId();
          }
          break;

        default:
          break;
      }
    }
  }

  private void deleteTier1RouterLinkPort(DeleteLogicalPortsTask currentState) throws Throwable {
    if (currentState.logicalLinkPortOnTier1Router == null) {
      ServiceUtils.logInfo(this, "No link port found on tier-1 router %s", currentState.logicalTier1RouterId);
      progressTask(TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT);
      return;
    }

    ServiceUtils.logInfo(this, "Deleting link port %s on tier-1 router %s",
        currentState.logicalLinkPortOnTier1Router, currentState.logicalTier1RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    logicalRouterApi.deleteLogicalRouterPort(currentState.logicalLinkPortOnTier1Router,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void v) {
            progressTask(TaskState.SubStage.WAIT_DELETE_TIER1_ROUTER_LINK_PORT);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void waitDeleteTier1RouterLinkPort(DeleteLogicalPortsTask currentState) {
    getHost().schedule(() -> {
      try {
        ServiceUtils.logInfo(this, "Wait for deleting link port %s on tier-1 router %s",
            currentState.logicalLinkPortOnTier1Router, currentState.logicalTier1RouterId);

        LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
            currentState.username, currentState.password).getLogicalRouterApi();

        logicalRouterApi.checkLogicalRouterPortExistence(currentState.logicalLinkPortOnTier1Router,
            new FutureCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean successful) {
                if (!successful) {
                  progressTask(TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT);
                } else {
                  waitDeleteTier1RouterLinkPort(currentState);
                }
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
    }, currentState.executionDelay, TimeUnit.MILLISECONDS);
  }

  private void deleteTier0RouterLinkPort(DeleteLogicalPortsTask currentState) throws Throwable {
    if (currentState.logicalLinkPortOnTier0Router == null) {
      ServiceUtils.logInfo(this, "No link port found on tier-0 router %s", currentState.logicalTier0RouterId);
      progressTask(TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT);
      return;
    }

    ServiceUtils.logInfo(this, "Deleting link port %s on tier-0 router %s", currentState.logicalLinkPortOnTier0Router,
        currentState.logicalTier0RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    logicalRouterApi.deleteLogicalRouterPort(currentState.logicalLinkPortOnTier0Router,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void v) {
            progressTask(TaskState.SubStage.WAIT_DELETE_TIER0_ROUTER_LINK_PORT);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void waitDeleteTier0RouterLinkPort(DeleteLogicalPortsTask currentState) {
    getHost().schedule(() -> {
      try {
        ServiceUtils.logInfo(this, "Wait for deleting link port %s on tier-0 router %s",
            currentState.logicalLinkPortOnTier0Router, currentState.logicalTier0RouterId);

        LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
            currentState.username, currentState.password).getLogicalRouterApi();

        logicalRouterApi.checkLogicalRouterPortExistence(currentState.logicalLinkPortOnTier0Router,
            new FutureCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean successful) {
                if (!successful) {
                  progressTask(TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT);
                } else {
                  waitDeleteTier0RouterLinkPort(currentState);
                }
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
    }, currentState.executionDelay, TimeUnit.MILLISECONDS);
  }

  private void deleteTier1RouterDownLinkPort(DeleteLogicalPortsTask currentState) throws Throwable {
    if (currentState.logicalDownLinkPortOnTier1Router == null) {
      ServiceUtils.logInfo(this, "No down-link port found on tier-1 router %s", currentState.logicalTier1RouterId);
      progressTask(TaskState.SubStage.DELETE_SWITCH_PORT);
      return;
    }

    ServiceUtils.logInfo(this, "Deleting down-link port %s on tier-1 router %s",
        currentState.logicalDownLinkPortOnTier1Router,
        currentState.logicalTier1RouterId);

    LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalRouterApi();

    logicalRouterApi.deleteLogicalRouterPort(currentState.logicalDownLinkPortOnTier1Router,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void v) {
            progressTask(TaskState.SubStage.DELETE_SWITCH_PORT);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void waitDeleteTier1RouterDownLinkPort(DeleteLogicalPortsTask currentState) {
    getHost().schedule(() -> {
      try {
        ServiceUtils.logInfo(this, "Wait for deleting down-link port %s on tier-1 router %s",
            currentState.logicalDownLinkPortOnTier1Router, currentState.logicalTier1RouterId);

        LogicalRouterApi logicalRouterApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
            currentState.username, currentState.password).getLogicalRouterApi();

        logicalRouterApi.checkLogicalRouterPortExistence(currentState.logicalDownLinkPortOnTier1Router,
            new FutureCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean successful) {
                if (!successful) {
                  progressTask(TaskState.SubStage.DELETE_SWITCH_PORT);
                } else {
                  waitDeleteTier1RouterDownLinkPort(currentState);
                }
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
    }, currentState.executionDelay, TimeUnit.MILLISECONDS);
  }

  private void deleteSwitchPort(DeleteLogicalPortsTask currentState) throws Throwable {
    if (currentState.logicalPortOnSwitch == null) {
      ServiceUtils.logInfo(this, "No link port found on switch %s", currentState.logicalSwitchId);
      finishTask();
      return;
    }

    ServiceUtils.logInfo(this, "Deleting port %s on switch %s",
        currentState.logicalPortOnSwitch,
        currentState.logicalSwitchId);

    LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint,
        currentState.username, currentState.password).getLogicalSwitchApi();

    logicalSwitchApi.deleteLogicalPort(currentState.logicalPortOnSwitch,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void v) {
            finishTask();
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void validateStartState(DeleteLogicalPortsTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != TaskState.TaskStage.STARTED,
        "Service state is invalid (START). Restart is disabled.");
  }

  private void validateState(DeleteLogicalPortsTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(DeleteLogicalPortsTask currentState, DeleteLogicalPortsTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
    validateTaskSubStage(currentState.taskState.subStage, patchState.taskState.subStage);
  }

  private DeleteLogicalPortsTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private DeleteLogicalPortsTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private DeleteLogicalPortsTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private DeleteLogicalPortsTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    DeleteLogicalPortsTask state = new DeleteLogicalPortsTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void progressTask(TaskState.SubStage subStage) {
    DeleteLogicalPortsTask patch = buildPatch(TaskState.TaskStage.STARTED, subStage);
    TaskUtils.sendSelfPatch(DeleteLogicalPortsTaskService.this, patch);
  }

  private void finishTask() {
    DeleteLogicalPortsTask patch = buildPatch(TaskState.TaskStage.FINISHED);
    TaskUtils.sendSelfPatch(DeleteLogicalPortsTaskService.this, patch);
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    DeleteLogicalPortsTask patchState = buildPatch(TaskState.TaskStage.FAILED, t);
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private void validateTaskSubStage(TaskState.SubStage startSubStage, TaskState.SubStage patchSubStage) {
    if (patchSubStage != null) {
      switch (patchSubStage) {
        case WAIT_DELETE_TIER1_ROUTER_LINK_PORT:
          checkState(startSubStage != null && startSubStage == TaskState.SubStage.DELETE_TIER1_ROUTER_LINK_PORT);
          break;

        case WAIT_DELETE_TIER0_ROUTER_LINK_PORT:
          checkState(startSubStage != null && startSubStage == TaskState.SubStage.DELETE_TIER0_ROUTER_LINK_PORT);
          break;

        case WAIT_DELETE_TIER1_ROUTER_DOWN_LINK_PORT:
          checkState(startSubStage != null && startSubStage == TaskState.SubStage.DELETE_TIER1_ROUTER_DOWN_LINK_PORT);
      }
    }
  }
}
