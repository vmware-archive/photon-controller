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
package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.adapterapi.SnapshotRequest;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Snapshot Task Service.
 */
public class SnapshotTaskService extends StatefulService {

  /**
   * Represents the state of a snapshot task.
   */
  public static class SnapshotTaskState extends ServiceDocument {

    public static final long DEFAULT_EXPIRATION_MICROS = TimeUnit.MINUTES.toMicros(10);

    /**
     * The state of the current task.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Link to the snapshot service.
     */
    public String snapshotLink;

    /**
     * Link to the Snapshot adapter.
     */
    public URI snapshotAdapterReference;

    /**
     * Value indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    public boolean isMockRequest;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;
  }

  public SnapshotTaskService() {
    super(SnapshotTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        start.fail(new IllegalArgumentException("body is required"));
        return;
      }

      SnapshotTaskState state = start.getBody(SnapshotTaskState.class);
      validateState(state);

      URI computeHost = buildSnapshotUri(state);
      sendRequest(Operation
          .createGet(computeHost)
          .setTargetReplicated(true)
          .setCompletion((o, e) -> {
            validateSnapshotAndStart(start, o, e, state);
          }));
    } catch (Throwable e) {
      logSevere(e);
      start.fail(e);
    }
  }

  public void validateState(SnapshotTaskState state) {
    if (state.snapshotLink == null || state.snapshotLink.isEmpty()) {
      throw new IllegalArgumentException("snapshotLink is required");
    }

    if (state.snapshotAdapterReference == null) {
      throw new IllegalArgumentException("snapshotAdapterReference required");
    }

    if (state.taskInfo == null || state.taskInfo.stage == null) {
      state.taskInfo = new TaskState();
      state.taskInfo.stage = TaskState.TaskStage.CREATED;
    }

    if (state.documentExpirationTimeMicros == 0) {
      state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
          + SnapshotTaskState.DEFAULT_EXPIRATION_MICROS;
    }
  }

  private void validateSnapshotAndStart(Operation startPost,
                                        Operation get, Throwable e,
                                        SnapshotTaskState state) {
    if (e != null) {
      logWarning("Failure retrieving snapshot state (%s): %s", get.getUri(), e.toString());
      startPost.complete();
      failTask(e);
      return;
    }

    SnapshotService.SnapshotState snapshotState = get
        .getBody(SnapshotService.SnapshotState.class);
    if (snapshotState.name == null || snapshotState.name.isEmpty()) {
      failTask(new IllegalArgumentException("Invalid snapshot name"));
      return;
    }
    if (snapshotState.computeLink == null || snapshotState.computeLink.isEmpty()) {
      failTask(new IllegalArgumentException("Invalid computeLink"));
      return;
    }

    // we can complete start operation now, it will index and cache the
    // update state
    startPost.complete();

    SnapshotTaskState newState = new SnapshotTaskState();
    newState.taskInfo.stage = TaskState.TaskStage.STARTED;
    newState.snapshotLink = state.snapshotLink;
    newState.snapshotAdapterReference = state.snapshotAdapterReference;
    newState.isMockRequest = state.isMockRequest;
    // now we are ready to start our self-running state machine
    sendSelfPatch(newState);
  }

  private void sendSelfPatch(TaskState.TaskStage nextStage, Throwable error) {
    SnapshotTaskState body = new SnapshotTaskState();
    if (error != null) {
      body.taskInfo.stage = TaskState.TaskStage.FAILED;
      body.taskInfo.failure = Utils.toServiceErrorResponse(error);
    } else {
      body.taskInfo.stage = nextStage;
    }
    sendSelfPatch(body);
  }

  private void sendSelfPatch(Object body) {
    sendPatch(getUri(), body);
  }

  private void sendPatch(URI uri, Object body) {
    Operation patch = Operation.createPatch(uri).setBody(body).setCompletion((o, ex) -> {
      if (ex != null) {
        logWarning("Self patch failed: %s", Utils.toString(ex));
      }
    });
    sendRequest(patch);
  }

  @Override
  public void handlePatch(Operation patch) {
    SnapshotTaskState body = patch.getBody(SnapshotTaskState.class);
    SnapshotTaskState currentState = getState(patch);
    // this validates AND transitions the stage to the next state by using the patchBody.
    if (validateStageTransition(patch, body, currentState)) {
      return;
    }
    patch.complete();

    switch (currentState.taskInfo.stage) {
      case CREATED:
        break;
      case STARTED:
        createSnapshot(currentState, null);
        break;
      case FINISHED:
        logInfo("task is complete");
        break;
      case FAILED:
      case CANCELLED:
        break;
      default:
        break;
    }
    patch.complete();
  }

  private void createSnapshot(SnapshotTaskState updatedState, String subTaskLink) {
    if (subTaskLink == null) {
      createSubTaskForSnapshotCallback(updatedState);
      return;
    }
    logInfo("Starting to create snapshot using sub task %s", subTaskLink);

    SnapshotRequest sr = new SnapshotRequest();
    sr.snapshotReference = UriUtils.buildUri(getHost(), updatedState.snapshotLink);
    sr.snapshotTaskReference = UriUtils.buildUri(getHost(), subTaskLink);
    sr.isMockRequest = updatedState.isMockRequest;
    sendRequest(Operation.createPatch(updatedState.snapshotAdapterReference)
        .setBody(sr)
        .setCompletion((o, e) -> {
          if (e != null) {
            SnapshotTaskState subTaskPatchBody = new SnapshotTaskState();
            subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.FAILED;
            sendPatch(UriUtils.buildUri(getHost(), subTaskLink),
                subTaskPatchBody);
            failTask(e);
            return;
          }
        }));
  }

  private void createSubTaskForSnapshotCallback(SnapshotTaskState currentState) {
    ComputeSubTaskService.ComputeSubTaskState subTaskInitState = new ComputeSubTaskService.ComputeSubTaskState();
    SnapshotTaskState subTaskPatchBody = new SnapshotTaskState();
    subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.FINISHED;
    // tell the sub task with what to patch us, on completion
    subTaskInitState.parentPatchBody = Utils.toJson(subTaskPatchBody);
    subTaskInitState.parentTaskLink = getSelfLink();
    Operation startPost =
        Operation.createPost(this, UUID.randomUUID().toString()).setBody(subTaskInitState)
            .setCompletion((o, e) -> {
              if (e != null) {
                logWarning("Failure creating sub task: %s", Utils.toString(e));
                this.sendSelfPatch(TaskState.TaskStage.FAILED, e);
                return;
              }
              ComputeSubTaskService.ComputeSubTaskState body =
                  o.getBody(ComputeSubTaskService.ComputeSubTaskState.class);
              createSnapshot(currentState, body.documentSelfLink);
            });
    getHost().startService(startPost, new ComputeSubTaskService());
  }

  public boolean validateStageTransition(Operation patch, SnapshotTaskState patchBody,
                                         SnapshotTaskState currentState) {

    if (patchBody.taskInfo != null && patchBody.taskInfo.failure != null) {
      logWarning("Task failed: %s", Utils.toJson(patchBody.taskInfo.failure));
      currentState.taskInfo.failure = patchBody.taskInfo.failure;
    } else {
      if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
        patch.fail(new IllegalArgumentException("taskInfo and taskInfo.stage are required"));
        return true;
      }
    }
    logFine("Current: %s. New: %s(%s)", currentState.taskInfo.stage, patchBody.taskInfo.stage);
    // update current stage to new stage
    currentState.taskInfo.stage = patchBody.taskInfo.stage;
    adjustStat(patchBody.taskInfo.stage.toString(), 1);

    return false;
  }

  private URI buildSnapshotUri(SnapshotTaskState updatedState) {
    URI snapshot = UriUtils.buildUri(getHost(), updatedState.snapshotLink);
    snapshot = SnapshotService.SnapshotState.buildUri(snapshot);
    return snapshot;
  }

  private void failTask(Throwable e) {
    logWarning("Self patching to FAILED, task failure: %s", e.toString());
    sendSelfPatch(TaskState.TaskStage.FAILED, e);
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
