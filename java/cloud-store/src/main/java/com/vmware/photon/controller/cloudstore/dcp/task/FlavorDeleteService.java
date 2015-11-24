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

package com.vmware.photon.controller.cloudstore.dcp.task;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;

/**
 * Class implementing service to delete a flavor from cloud store.
 */
public class FlavorDeleteService extends StatefulService {

  /**
   * Default constructor.
   */
  public FlavorDeleteService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public static State buildStartPatch() {
    State s = new State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = TaskState.TaskStage.STARTED;
    return s;
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    try {
      // Initialize the task state.
      State s = startOperation.getBody(State.class);
      if (s.taskInfo == null || s.taskInfo.stage == null) {
        s.taskInfo = new TaskState();
        s.taskInfo.stage = TaskState.TaskStage.CREATED;
      }

      if (s.documentExpirationTimeMicros <= 0) {
        s.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
      }

      validateState(s);
      startOperation.setBody(s).complete();

      sendStageProgressPatch(s, s.taskInfo.stage);
    } catch (IllegalStateException t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
      }
    } catch (RuntimeException e) {
      logSevere(e);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(e);
      }
    }
  }

  /**
   * Handle service requests.
   *
   * @param patchOperation
   */
  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    URI referer = patchOperation.getReferer();

    try {
      ValidationUtils.validatePatch(currentState, patchState);
      validateStatePatch(currentState, patchState, referer);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      switch (currentState.taskInfo.stage) {
        case CREATED:
          break;
        case STARTED:
          handleStartedStage(currentState);
          break;
        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;
        default:
          throw new IllegalStateException(
              String.format("Invalid stage %s", currentState.taskInfo.stage));
      }
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(e);
      }
    }
  }

  /**
   * Validate patch correctness.
   *
   * @param current
   * @param patch
   */
  private void validateStatePatch(State current, State patch, URI referer) {
    if (current.taskInfo.stage != TaskState.TaskStage.CREATED &&
        referer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage, ignores patch from TaskSchedulerService");
    }

    checkState(current.taskInfo.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal(),
        "Can not patch anymore when in final stage %s", current.taskInfo.stage);
    if (patch.taskInfo != null && patch.taskInfo.stage != null) {
      checkState(patch.taskInfo.stage.ordinal() >= current.taskInfo.stage.ordinal(),
          "Can not revert to %s from %s", patch.taskInfo.stage, current.taskInfo.stage);
    }
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);
    checkNotNull(current.taskInfo.stage);
    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  private void handleStartedStage(final State current) {
    this.sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    this.sendSelfPatch(buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param s
   */
  private void sendSelfPatch(State s) {
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(s);
    sendRequest(patch);
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param stage
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    sendSelfPatch(buildPatch(stage, null));
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Throwable e) {
    State s = new FlavorDeleteService.State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = stage;

    if (e != null) {
      s.taskInfo.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Durable service state data. Class encapsulating the data for flavor delete.
   */
  public static class State extends ServiceDocument {

    /**
     * Flavor delete service stage.
     */
    @NotNull
    public TaskState taskInfo;

    /**
     * URI of the sender of the delete, if not null notify of delete end.
     */
    @Immutable
    public String parentLink;

    /**
     * Flavor to be deleted.
     */
    @NotNull
    @Immutable
    public String flavor;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    public Boolean isSelfProgressionDisabled;
  }
}
