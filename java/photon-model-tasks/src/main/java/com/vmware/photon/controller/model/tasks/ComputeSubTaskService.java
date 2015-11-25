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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

import java.util.List;

/**
 * Task tracking the progress of a single request to the host power or boot service. When the host
 * service completes the operation it issues a PATCH to this service with the taskInfo.stage set to
 * FINISHED, or if the operation fails, set to FAILED
 */
public class ComputeSubTaskService extends StatefulService {

  /**
   * Represent the state of a compute subtask.
   */
  public static class ComputeSubTaskState extends ServiceDocument {
    /**
     * Task state.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Number of tasks to track.
     */
    public long completionsRemaining = 1;

    /**
     * Number of tasks failed.
     */
    public long failCount;

    /**
     * Number of tasks finished successfully.
     */
    public long finishedCount;

    /**
     * Normalized error threshold between 0 and 1.0.
     */
    public double errorThreshold;

    /**
     * Link to parent task.
     */
    public String parentTaskLink;

    /**
     * Parent task document as json.
     */
    public String parentPatchBody;

    /**
     * Tenant links.
     */
    public List<String> tenantLinks;
  }

  public ComputeSubTaskService() {
    super(ComputeSubTaskState.class);
  }

  @Override
  public void handlePatch(Operation patch) {
    ComputeSubTaskState patchBody = patch.getBody(ComputeSubTaskState.class);
    ComputeSubTaskState currentState = getState(patch);

    if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
      String error = "taskInfo, taskInfo.stage are required";
      logWarning(error);
      patch.fail(new IllegalArgumentException(error));
      return;
    }

    String parentPatchBody = currentState.parentPatchBody;
    if (patchBody.taskInfo.stage == TaskStage.FAILED
        || patchBody.taskInfo.stage == TaskStage.CANCELLED) {
      currentState.failCount++;
      currentState.completionsRemaining--;

      double failedRatio = (double) currentState.failCount / (double) (currentState
          .finishedCount
          + currentState.failCount + currentState.completionsRemaining);

      if (currentState.errorThreshold == 0 ||
          failedRatio > currentState.errorThreshold) {
        logWarning("Notifying parent of task failure: %s (%s)",
            Utils.toJsonHtml(patchBody.taskInfo.failure),
            patchBody.taskInfo.stage);

        parentPatchBody = Utils.toJson(patchBody);
        currentState.completionsRemaining = 0;
      }
    } else if (patchBody.taskInfo.stage == TaskStage.FINISHED) {
      currentState.completionsRemaining--;
      currentState.finishedCount++;
    } else if (patchBody.taskInfo.stage == TaskStage.STARTED) {
      // don't decrement completions remaining.
    } else {
      logInfo("ignoring patch from %s", patch.getReferer());
      // ignore status updates from boot/power services
      patch.complete();
      return;
    }

    // any operation on state before a operation is completed, is guaranteed to be atomic
    // (service is synchronized)
    boolean isFinished = currentState.completionsRemaining == 0;
    patch.complete();

    logFine("Remaining %d", currentState.completionsRemaining);

    if (!isFinished) {
      return;
    }

    sendRequest(Operation.createPatch(this, currentState.parentTaskLink)
        .setBody(parentPatchBody));

    // we are a one shot task, self DELETE
    sendRequest(Operation.createDelete(getUri()).setBody(
        new ServiceDocument()));
  }
}
