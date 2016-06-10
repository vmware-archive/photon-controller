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

import com.vmware.photon.controller.apibackend.exceptions.CreateLogicalSwitchException;
import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalSwitchTask;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.builders.LogicalSwitchCreateSpecBuilder;
import com.vmware.photon.controller.nsxclient.datatypes.NsxSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitch;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchCreateSpec;
import com.vmware.photon.controller.nsxclient.models.LogicalSwitchState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Implements an Xenon service that represents a task to create a logical switch.
 */
public class CreateLogicalSwitchTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-logical-switch-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(CreateLogicalSwitchTaskService.class, CreateLogicalSwitchTask.class);
  }

  public CreateLogicalSwitchTaskService() {
    super(CreateLogicalSwitchTask.class);

    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      CreateLogicalSwitchTask startState = start.getBody(CreateLogicalSwitchTask.class);
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

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage));
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(start)) {
        start.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      CreateLogicalSwitchTask currentState = getState(patch);
      CreateLogicalSwitchTask patchState = patch.getBody(CreateLogicalSwitchTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patch.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        createLogicalSwitch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(t);
      }
    }
  }

  private void createLogicalSwitch(CreateLogicalSwitchTask currentState) {
    ServiceUtils.logInfo(this, "Creating logical switch");

    try {
      LogicalSwitchCreateSpec logicalSwitchCreateSpec = new LogicalSwitchCreateSpecBuilder()
          .displayName(currentState.displayName)
          .transportZoneId(currentState.transportZoneId)
          .build();

      ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint, currentState.username,
          currentState.password).getLogicalSwitchApi().createLogicalSwitch(logicalSwitchCreateSpec,
          new FutureCallback<LogicalSwitch>() {
            @Override
            public void onSuccess(@Nullable LogicalSwitch result) {
              currentState.id = result.getId();
              waitForConfigurationComplete(currentState);
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

  private void validateStartState(CreateLogicalSwitchTask startState) {
    validateState(startState);

    // Disable restart
    checkState(startState.taskState.stage != TaskState.TaskStage.STARTED);
  }

  private void validateState(CreateLogicalSwitchTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(CreateLogicalSwitchTask currentState, CreateLogicalSwitchTask patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    return buildPatch(stage, t == null ? null : Utils.toServiceErrorResponse(t));
  }

  private CreateLogicalSwitchTask buildPatch(TaskState.TaskStage stage, @Nullable ServiceErrorResponse errorResponse) {
    CreateLogicalSwitchTask state = new CreateLogicalSwitchTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;

    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void finishTask(String logicalSwitchId) {
    CreateLogicalSwitchTask patch = buildPatch(TaskState.TaskStage.FINISHED);
    patch.id = logicalSwitchId;

    TaskUtils.sendSelfPatch(this, patch);
  }

  private void waitForConfigurationComplete(CreateLogicalSwitchTask currentState) {
    getHost().schedule(() -> {
      try {
        ServiceUtils.logInfo(this, "Checking the configuration status of logical switch");

        ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint, currentState.username,
            currentState.password).getLogicalSwitchApi().getLogicalSwitchState(
            currentState.id,
            new FutureCallback<LogicalSwitchState>() {
              @Override
              public void onSuccess(@Nullable LogicalSwitchState result) {
                NsxSwitch.State state = result.getState();
                switch (state) {
                  case IN_PROGRESS:
                  case PENDING:
                    waitForConfigurationComplete(currentState);
                    break;

                  case SUCCESS:
                    finishTask(result.getId());
                    break;

                  default:
                    failTask(new CreateLogicalSwitchException("Creating logical switch " + currentState.id +
                        " failed with state " + state));
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
}
