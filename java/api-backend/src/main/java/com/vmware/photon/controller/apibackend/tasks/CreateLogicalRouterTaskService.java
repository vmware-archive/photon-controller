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
package com.vmware.photon.controller.apibackend.tasks;

import com.vmware.photon.controller.apibackend.servicedocuments.CreateLogicalRouterTask;
import com.vmware.photon.controller.apibackend.utils.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.nsxclient.datatypes.NsxRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouter;
import com.vmware.photon.controller.nsxclient.models.LogicalRouterCreateSpec;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

/**
 * This class implements a DCP service representing a task to create a logical router.
 */
public class CreateLogicalRouterTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/create-logical-router-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(CreateLogicalRouterTaskService.class, CreateLogicalRouterTask.class);
  }

  public CreateLogicalRouterTaskService() {
    super(CreateLogicalRouterTask.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    CreateLogicalRouterTask startState = start.getBody(CreateLogicalRouterTask.class);
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
    CreateLogicalRouterTask currentState = getState(patch);
    CreateLogicalRouterTask patchState = patch.getBody(CreateLogicalRouterTask.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        createLogicalRouter(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void createLogicalRouter(CreateLogicalRouterTask currentState) {
    ServiceUtils.logInfo(this, "Create Logical Router");

    try {
      LogicalRouterCreateSpec logicalRouterCreateSpec = new LogicalRouterCreateSpec();
      logicalRouterCreateSpec.setDisplayName(currentState.displayName);
      logicalRouterCreateSpec.setDescription(currentState.description);
      logicalRouterCreateSpec.setRouterType(NsxRouter.RouterType.TIER1);
      logicalRouterCreateSpec.setEdgeClusterId(currentState.edgeClusterId);

      ServiceHostUtils.getNsxClient(getHost(), currentState.nsxManagerEndpoint, currentState.username,
          currentState.password).getLogicalRouterApi().createLogicalRouter(logicalRouterCreateSpec,
          new FutureCallback<LogicalRouter>() {
            @Override
            public void onSuccess(@Nullable LogicalRouter result) {
              // TODO(ysheng): there is no getState API for logical router. Not sure
              // what is the correct way to wait for the completion of the router creation.
              CreateLogicalRouterTask patchState = buildPatch(TaskState.TaskStage.FINISHED);
              patchState.id = result.getId();
              TaskUtils.sendSelfPatch(CreateLogicalRouterTaskService.this, patchState);
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

  private void validateStartState(CreateLogicalRouterTask startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(CreateLogicalRouterTask currentState, CreateLogicalRouterTask patchState) {
    checkNotNull(patchState, "patch cannot be null");
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private CreateLogicalRouterTask buildPatch(TaskState.TaskStage stage) {
    return buildPatch(stage, (Throwable) null);
  }

  private CreateLogicalRouterTask buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    return buildPatch(stage, t == null ? null : Utils.toServiceErrorResponse(t));
  }

  private CreateLogicalRouterTask buildPatch(TaskState.TaskStage stage, @Nullable ServiceErrorResponse errorResponse) {
    CreateLogicalRouterTask state = new CreateLogicalRouterTask();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
