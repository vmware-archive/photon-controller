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

import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;

/**
 * This class implements a DCP micro-service which performs the task of
 * acquiring a certificate.
 */
public class GrantCertificateTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link GrantCertificateTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * ipAddress represents the ip of the host who wants the certificate.
     */
    @NotNull
    @Immutable
    public String ipAddress;

    /**
     * certificate represents the the certificate issued.
     */
    @Nullable
    public String certificate;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(TaskState.TaskStage.STARTED)
    public TaskState taskState;
  }

  public GrantCertificateTaskService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    validateStartState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    start.setBody(startState).complete();
    TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    patch.complete();

    try {
      grantCertificate(currentState);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void grantCertificate(State state) {
    // TODO(agui): Implement it
    state.certificate = "test-cert";
    state.taskState.stage = TaskState.TaskStage.FINISHED;
    TaskUtils.sendSelfPatch(this, state);
  }

  protected void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
  }

  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

}
