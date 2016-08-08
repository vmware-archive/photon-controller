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
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalSwitchTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalSwitchTask.TaskState;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.TimeUnit;

/**
 * Implements an Xenon service that represents a task to delete a logical switch.
 */
public class DeleteLogicalSwitchTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/delete-logical-switch-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(DeleteLogicalSwitchTaskService.class, DeleteLogicalSwitchTask.class);
  }

  public DeleteLogicalSwitchTaskService() {
    super(DeleteLogicalSwitchTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      DeleteLogicalSwitchTask startState = startOperation.getBody(DeleteLogicalSwitchTask.class);
      InitializationUtils.initialize(startState);
      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.DELETE_SWITCH;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros =
            ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
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
      DeleteLogicalSwitchTask currentState = getState(patchOperation);
      DeleteLogicalSwitchTask patchState = patchOperation.getBody(DeleteLogicalSwitchTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
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

  private void processPatch(DeleteLogicalSwitchTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case DELETE_SWITCH:
          deleteLogicalSwitch(currentState);
          break;

        case WAIT_DELETE_SWITCH:
          waitDeleteLogicalSwitch(currentState);
          break;

        default:
           throw new ConfigureRoutingException("Invalid task sub-stage " + currentState.taskState.subStage);

      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteLogicalSwitch(DeleteLogicalSwitchTask currentState) {
    ServiceUtils.logInfo(this, "Deleting logical switch %s", currentState.logicalSwitchId);

    try {
      LogicalSwitchApi logicalSwitchApi = ServiceHostUtils.getNsxClient(getHost(), currentState.nsxAddress,
          currentState.nsxUsername, currentState.nsxPassword).getLogicalSwitchApi();

      logicalSwitchApi.deleteLogicalSwitch(currentState.logicalSwitchId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
              progressTask(TaskState.SubStage.WAIT_DELETE_SWITCH);
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

  private void waitDeleteLogicalSwitch(DeleteLogicalSwitchTask currentState) {
    NsxClient nsxClient = ServiceHostUtils.getNsxClient(
        getHost(),
        currentState.nsxAddress,
        currentState.nsxUsername,
        currentState.nsxPassword);

    getHost().schedule(() -> {
      ServiceUtils.logInfo(this, "Wait for deleting logical switch %s", currentState.logicalSwitchId);

      try {
        nsxClient.getLogicalSwitchApi().checkLogicalSwitchExistence(
            currentState.logicalSwitchId,
            new FutureCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean successful) {
                if (!successful) {
                  finishTask();
                } else {
                  waitDeleteLogicalSwitch(currentState);
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
    }, nsxClient.getDeleteLogicalSwitchPollDelay(), TimeUnit.MILLISECONDS);
  }

  private void validateStartState(DeleteLogicalSwitchTask startState) {
    validateState(startState);

    // Disable restart
    checkState(startState.taskState.stage != TaskState.TaskStage.STARTED,
        "Service state is invalid (START). Restart is disabled.");
  }

  private void validateState(DeleteLogicalSwitchTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(DeleteLogicalSwitchTask currentState, DeleteLogicalSwitchTask patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, currentState.taskState.subStage,
        patchState.taskState, patchState.taskState.subStage);
    validateTaskSubStage(currentState.taskState.subStage, patchState.taskState.subStage);
  }

  private DeleteLogicalSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private DeleteLogicalSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private DeleteLogicalSwitchTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private DeleteLogicalSwitchTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, Throwable t) {
    DeleteLogicalSwitchTask state = new DeleteLogicalSwitchTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);
    return state;
  }

  private void finishTask() {
    DeleteLogicalSwitchTask patch = buildPatch(TaskState.TaskStage.FINISHED);
    TaskUtils.sendSelfPatch(DeleteLogicalSwitchTaskService.this, patch);
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void progressTask(TaskState.SubStage subStage) {
    DeleteLogicalSwitchTask patch = buildPatch(TaskState.TaskStage.STARTED, subStage);
    TaskUtils.sendSelfPatch(DeleteLogicalSwitchTaskService.this, patch);
  }

  private void validateTaskSubStage(TaskState.SubStage startSubStage, TaskState.SubStage patchSubStage) {
    if (patchSubStage != null) {
      switch (patchSubStage) {
        case WAIT_DELETE_SWITCH:
          checkState(startSubStage != null && startSubStage == TaskState.SubStage.DELETE_SWITCH);
      }
    }
  }
}
