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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskServiceState;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * This class tracks the progress of one or more child tasks and notifies a parent task when all child tasks have
 * finished successfully or when a sufficient number of child tasks have failed to declare failure in the parent.
 */
public class ChildTaskAggregatorService extends StatefulService {

  /**
   * This class defines the document state associated with a {@link ChildTaskAggregatorService}.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the parent task service to be notified on completion.
     */
    @NotNull
    public String parentTaskLink;

    /**
     * This value represents the patch body to be sent to the parent task on successful completion.
     */
    @NotNull
    public String parentPatchBody;

    /**
     * This value represents the number of completion messages which are still expected for the current task.
     */
    @NotNull
    public Integer pendingCompletionCount;

    /**
     * This value represents the normalized error threshold for the current task (a value between 0.0 and 1.0).
     * <p>
     * So e.g.
     * - If no child tasks can fail without the parent task failing, specify a value of 0.0;
     * - If all child tasks can fail without the parent task failing, specify a value of 1.0;
     * - If 50% of child tasks can fail without the parent task failing, specify a value of 0.5;
     * etc.
     */
    @NotNull
    public Double errorThreshold;

    /**
     * This value represents the number of child tasks which have finished successfully.
     */
    @DefaultInteger(value = 0)
    public Integer finishedCount;

    /**
     * This value represents the number of child tasks which have failed.
     */
    @DefaultInteger(value = 0)
    public Integer failureCount;
  }

  public ChildTaskAggregatorService() {
    super(State.class);

    //UploadVibTask tries to call this service locally and fails since this task is not there on the host
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);
    ValidationUtils.validateState(startState);
    operation.complete();
  }

  @Override
  public void handlePatch(Operation operation) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(operation);
    TaskServiceState patchState = operation.getBody(TaskServiceState.class);
    switch (patchState.taskState.stage) {
      case FAILED:
      case CANCELLED:
        currentState.pendingCompletionCount--;
        currentState.failureCount++;

        double failureRatio = (double) currentState.failureCount / (double)
            (currentState.finishedCount + currentState.failureCount + currentState.pendingCompletionCount);
        if (failureRatio > currentState.errorThreshold) {
          ServiceUtils.logInfo(this, "Notifying parent task %s of failure: %s", currentState.parentTaskLink,
              Utils.toJson(false, true, patchState.taskState.failure));
          currentState.parentPatchBody = Utils.toJson(false, false, patchState);
          currentState.pendingCompletionCount = 0;
        }
        break;

      case FINISHED:
        currentState.pendingCompletionCount--;
        currentState.finishedCount++;
        break;

      default:
        throw new IllegalStateException("Unexpected patch from child task: " +
            Utils.toJson(false, true, patchState));
    }

    operation.complete();

    if (currentState.pendingCompletionCount == 0) {
      Operation.createPatch(this, currentState.parentTaskLink).setBody(currentState.parentPatchBody)
        .setCompletion((o, e) -> {
          if (e != null) {
            ServiceUtils.logSevere(this, "Failed to patch parent task %s", currentState.parentTaskLink);
            ServiceUtils.logSevere(this, e);
          }
        })
        .sendWith(this);;
      Operation.createDelete(this, getSelfLink()).sendWith(this);
    }
  }
}
