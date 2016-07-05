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

import com.vmware.photon.controller.apibackend.exceptions.ConfigureDhcpException;
import com.vmware.photon.controller.apibackend.servicedocuments.ConfigureDhcpTask;
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
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Implements an Xenon service that represents a task to configure the DHCP service for logical networks.
 */
public class ConfigureDhcpTaskService extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.APIBACKEND_ROOT + "/configure-dhcp-tasks";

  public static FactoryService createFactory() {
    return FactoryService.create(ConfigureDhcpTaskService.class, ConfigureDhcpTask.class);
  }

  public ConfigureDhcpTaskService() {
    super(ConfigureDhcpTask.class);

    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      ConfigureDhcpTask startState = startOperation.getBody(ConfigureDhcpTask.class);
      InitializationUtils.initialize(startState);

      validateStartState(startState);

      if (startState.taskState.stage == ConfigureDhcpTask.TaskState.TaskStage.CREATED) {
        startState.taskState.stage = ConfigureDhcpTask.TaskState.TaskStage.STARTED;
        startState.taskState.subStage = ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_PROFILE;
      }

      if (startState.documentExpirationTimeMicros <= 0) {
        startState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils
            .DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      startOperation.setBody(startState).complete();

      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ConfigureDhcpTask.TaskState.TaskStage.STARTED == startState.taskState.stage) {
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
      ConfigureDhcpTask currentState = getState(patchOperation);
      ConfigureDhcpTask patchState = patchOperation.getBody(ConfigureDhcpTask.class);

      validatePatchState(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);

      patchOperation.complete();

      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (ConfigureDhcpTask.TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processPatch(currentState);
      }
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
  }

  private void processPatch(ConfigureDhcpTask currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case CREATE_DHCP_RELAY_PROFILE:
          TaskUtils.sendSelfPatch(this,
              buildPatch(TaskState.TaskStage.STARTED, ConfigureDhcpTask.TaskState.SubStage.CREATE_DHCP_RELAY_SERVICE));
          break;
        case CREATE_DHCP_RELAY_SERVICE:
          TaskUtils.sendSelfPatch(this,
              buildPatch(TaskState.TaskStage.FINISHED));
          break;
        default:
          throw new ConfigureDhcpException("Invalid task substage " + currentState.taskState.subStage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateStartState(ConfigureDhcpTask state) {
    validateState(state);

    // Disallow restarting the service.
    checkState(state.taskState.stage != ConfigureDhcpTask.TaskState.TaskStage.STARTED);
  }

  private void validateState(ConfigureDhcpTask state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
  }

  private void validatePatchState(ConfigureDhcpTask currentState, ConfigureDhcpTask patchState) {
    checkNotNull(patchState, "patch cannot be null");

    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.stage == ConfigureDhcpTask.TaskState.TaskStage.STARTED) {
      validateTaskSubStageProgression(currentState.taskState, patchState.taskState);
    }
  }

  private void validateTaskSubStageProgression(ConfigureDhcpTask.TaskState startState,
                                               ConfigureDhcpTask.TaskState patchState) {
    if (patchState.stage.ordinal() > ConfigureDhcpTask.TaskState.TaskStage.FINISHED.ordinal()) {
      return;
    }

    if (patchState.stage == ConfigureDhcpTask.TaskState.TaskStage.STARTED) {
      checkState(patchState.subStage.ordinal() == startState.subStage.ordinal()
          || patchState.subStage.ordinal() == startState.subStage.ordinal() + 1);
    }
  }

  private ConfigureDhcpTask buildPatch(ConfigureDhcpTask.TaskState.TaskStage stage) {
    return buildPatch(stage, null, null);
  }

  private ConfigureDhcpTask buildPatch(ConfigureDhcpTask.TaskState.TaskStage stage,
                                       ConfigureDhcpTask.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  private ConfigureDhcpTask buildPatch(ConfigureDhcpTask.TaskState.TaskStage stage,
                                       Throwable t) {
    return buildPatch(stage, null, t);
  }

  private ConfigureDhcpTask buildPatch(ConfigureDhcpTask.TaskState.TaskStage stage,
                                       ConfigureDhcpTask.TaskState.SubStage subStage,
                                       Throwable t) {
    ConfigureDhcpTask state = new ConfigureDhcpTask();
    state.taskState = new ConfigureDhcpTask.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = t == null ? null : Utils.toServiceErrorResponse(t);

    return state;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);

    try {
      ConfigureDhcpTask patchState = buildPatch(ConfigureDhcpTask.TaskState.TaskStage.FAILED, t);
      TaskUtils.sendSelfPatch(this, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, "Failed to send self-patch: " + e.toString());
    }
  }
}
