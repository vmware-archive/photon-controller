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
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalRouterTask;
import com.vmware.photon.controller.apibackend.servicedocuments.DeleteLogicalRouterTask.TaskState;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.TimeUnit;

/**
 * Implements an Xenon service that represents a task to delete a logical router.
 */
public class DeleteLogicalRouterTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/delete-logical-router-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(DeleteLogicalRouterTaskService.class, DeleteLogicalRouterTask.class);
  }

  public DeleteLogicalRouterTaskService() {
    super(DeleteLogicalRouterTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      DeleteLogicalRouterTask startState = startOperation.getBody(DeleteLogicalRouterTask.class);
      InitializationUtils.initialize(startState);
      validateStartState(startState);

      if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        startState.taskState.stage = TaskState.TaskStage.STARTED;
        startState.taskState.subStage = TaskState.SubStage.DELETE_ROUTER;
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
      DeleteLogicalRouterTask currentState = getState(patchOperation);
      DeleteLogicalRouterTask patchState = patchOperation.getBody(DeleteLogicalRouterTask.class);

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

  private void processPatch(DeleteLogicalRouterTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case DELETE_ROUTER:
          deleteLogicalRouter(currentState);
          break;

        case WAIT_DELETE_ROUTER:
          waitDeleteLogicalRouter(currentState);
          break;

        default:
          throw new ConfigureRoutingException("Invalid task sub-stage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteLogicalRouter(DeleteLogicalRouterTask currentState) {
    ServiceUtils.logInfo(this, "Deleting logical router %s", currentState.logicalRouterId);

    try {
      ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint, currentState.username,
          currentState.password).getLogicalRouterApi().deleteLogicalRouter(currentState.logicalRouterId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
              progressTask(TaskState.SubStage.WAIT_DELETE_ROUTER);
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

  private void waitDeleteLogicalRouter(DeleteLogicalRouterTask currentState) {
    getHost().schedule(() -> {
      ServiceUtils.logInfo(this, "Wait for deleting logical router %s", currentState.logicalRouterId);

      try {
        ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint, currentState.username,
            currentState.password).getLogicalRouterApi().checkLogicalRouterExistence(currentState.logicalRouterId,
            new FutureCallback<Boolean>() {
              @Override
              public void onSuccess(Boolean successful) {
                if (!successful) {
                  finishTask();
                } else {
                  waitDeleteLogicalRouter(currentState);
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

  private void validateStartState(DeleteLogicalRouterTask startState) {
    validateState(startState);

    // Disable restart
    checkState(startState.taskState.stage != TaskState.TaskStage.STARTED,
        "Service state is invalid (START). Restart is disabled.");
  }

  private void validateState(DeleteLogicalRouterTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(DeleteLogicalRouterTask currentState, DeleteLogicalRouterTask patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, currentState.taskState.subStage,
        patchState.taskState, patchState.taskState.subStage);
    validateSubStage(currentState.taskState.subStage, patchState.taskState.subStage);
  }

  private DeleteLogicalRouterTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private DeleteLogicalRouterTask buildPatch(TaskState.TaskStage stage, Throwable t) {
    return buildPatch(stage, null, t);
  }

  private DeleteLogicalRouterTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private DeleteLogicalRouterTask buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage,
                                             Throwable t) {
    DeleteLogicalRouterTask state = new DeleteLogicalRouterTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void progressTask(TaskState.SubStage subStage) {
    DeleteLogicalRouterTask patch = buildPatch(TaskState.TaskStage.STARTED, subStage);
    TaskUtils.sendSelfPatch(DeleteLogicalRouterTaskService.this, patch);
  }

  private void finishTask() {
    TaskUtils.sendSelfPatch(DeleteLogicalRouterTaskService.this, buildPatch(TaskState.TaskStage.FINISHED));
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void validateSubStage(TaskState.SubStage currentSubStage, TaskState.SubStage patchSubStage) {
    if (patchSubStage != null) {
      switch (patchSubStage) {
        case WAIT_DELETE_ROUTER:
          checkState(currentSubStage != null && currentSubStage == TaskState.SubStage.DELETE_ROUTER);
      }
    }

  }
}
