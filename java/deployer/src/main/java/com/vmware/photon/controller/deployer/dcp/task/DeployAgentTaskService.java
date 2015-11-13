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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.DefaultUuid;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class implements a DCP micro-service which performs the task of deploying an agent instance to an ESX
 * hypervisor.
 */
public class DeployAgentTaskService extends StatefulService {

  @VisibleForTesting
  public static final String SCRIPT_NAME = "esx-install-agent2";

  private static final String VMFS_VOLUMES = "/vmfs/volumes";

  /**
   * This class defines the document state associated with a single {@link DeployAgentTaskService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link DeploymentService} in whose context the task operation is
     * being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link HostService} which represents the host on which the agent
     * should be deployed.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the path to the VIB on the specified data store.
     */
    @NotNull
    @Immutable
    public String vibPath;

    /**
     * This value represents the status of the current task.
     */
    @DefaultTaskState(TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the unique ID of the current task.
     */
    @DefaultUuid
    @Immutable
    public String uniqueId;
  }

  public DeployAgentTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processDeployAgent(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void processDeployAgent(final State currentState) {

    Operation deploymentOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink);
    Operation hostOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.hostServiceLink);

    OperationJoin
        .create(deploymentOp, hostOp)
        .setCompletion(
            (ops, failures) -> {
              if (null != failures && failures.size() > 0) {
                failTask(failures);
                return;
              }

              try {
                processDeployAgent(currentState,
                    ops.get(deploymentOp.getId()).getBody(DeploymentService.State.class),
                    ops.get(hostOp.getId()).getBody(HostService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }
        )
        .sendWith(this);
  }

  private void processDeployAgent(State currentState,
                                  DeploymentService.State deploymentState,
                                  HostService.State hostState) {

    List<String> command = new ArrayList<>();
    command.add("./" + SCRIPT_NAME);
    command.add(hostState.hostAddress);
    command.add(hostState.userName);
    command.add(hostState.password);

    String vibPath = VMFS_VOLUMES + "/" +
        StringUtils.strip(deploymentState.imageDataStoreNames.iterator().next(), "/") + "/" +
        StringUtils.strip(currentState.vibPath, "/");

    command.add(vibPath);

    if (null != deploymentState.syslogEndpoint) {
      command.add("-l");
      command.add(deploymentState.syslogEndpoint);
    }

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);

    File scriptLogFile = new File(deployerContext.getScriptLogDirectory(),
        SCRIPT_NAME + "-" + currentState.uniqueId + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
      @Override
      public void onSuccess(@Nullable Integer result) {
        if (null == result) {
          failTask(new IllegalStateException(SCRIPT_NAME + " returned null"));
        } else if (0 != result) {
          failTask(new IllegalStateException(SCRIPT_NAME + " returned " + result));
        } else {
          sendStageProgressPatch(TaskState.TaskStage.FINISHED);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch with stage %s", taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void failTask(Map<Long, Throwable> failures) {
    failures.values().forEach(failure -> ServiceUtils.logSevere(this, failure));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failures.values().iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
