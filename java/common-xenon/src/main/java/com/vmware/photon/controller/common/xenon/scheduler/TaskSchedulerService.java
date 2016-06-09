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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Class TaskSchedulerService: periodically starts new services based on the threshold of how many services
 * can be running simultaneously.
 */
public class TaskSchedulerService extends StatefulService {

  /**
   * Default constructor.
   */
  public TaskSchedulerService() {
    super(State.class);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.setMaintenanceIntervalMicros(TaskSchedulerServiceStateBuilder.triggerInterval);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

      State s = start.getBody(State.class);
      this.initializeState(s);
      this.validateState(s);

      start.complete();
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(start)) {
        start.fail(e);
      }
    }
  }

  /**
   * Handle service patch.
   */
  @Override
  public void handlePatch(Operation patch) {
    try {
      State currentState = getState(patch);
      State patchState = patch.getBody(State.class);

      this.validatePatch(currentState, patchState);
      this.applyPatch(currentState, patchState);
      this.validateState(currentState);

      patch.complete();

      this.processPatch(currentState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(e);
      }
    }
  }

  /**
   * Handle service periodic maintenance calls.
   */
  @Override
  public void handleMaintenance(Operation post) {
    post.complete();

    Operation.CompletionHandler handler = (Operation op, Throwable failure) -> {
      if (null != failure) {
        // query failed so abort and retry next time
        logFailure(failure);
        return;
      }

      NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
      if (!getHost().getId().equals(rsp.ownerNodeId)) {
        ServiceUtils.logInfo(TaskSchedulerService.this,
            "Host[%s]: Not owner of scheduler [%s] (Owner Info [%s])",
            getHost().getId(), getSelfLink(), Utils.toJson(false, false, rsp));
        return;
      }

      State state = new State();
      sendSelfPatch(state);
    };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(TaskSchedulerServiceHelper.OWNER_SELECTION_TIMEOUT))
        .setCompletion(handler);
    getHost().selectOwner(null, getSelfLink(), selectOwnerOp);
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(State current) {
    InitializationUtils.initialize(current);
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private void validatePatch(State current, State patch) {
    ValidationUtils.validatePatch(current, patch);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private State applyPatch(State current, State patch) {
    PatchUtils.patchState(current, patch);
    return current;
  }

  /**
   * Process patch. Triggers a query task for started service, and if the count of services in STARTED is less than
   * the pre-defined threshold, it will move the services in CREATED stage to STARTED stage, until the count
   * reached the threshold.
   */
  private void processPatch(final State current) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          // The serivce log the failed query, and let the next handleMaintenance function trigger
          // another one.
          logFailure(failure);
          return;
        }

        int serviceCount = completedOp.getBody(QueryTask.class).results.documentLinks.size();
        int runningTasksLimit = current.tasksLimits;
        if (serviceCount >= runningTasksLimit) {
          return;
        }

        startServices(current, runningTasksLimit - serviceCount);
      }
    };

    sendTaskStateQuery(current, TaskState.TaskStage.STARTED, handler);
  }

  /**
   * Triggers a query task for started service in CREATED stage and moves servicesToStartCount service to STARTED stage.
   */
  private void startServices(final State current, final int servicesToStartCount) {
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          logFailure(failure);
          return;
        }

        ServiceDocumentQueryResult results = completedOp.getBody(QueryTask.class).results;
        if (results.documentLinks.size() == 0) {
          return;
        }

        ServiceUtils.logInfo(TaskSchedulerService.this,
            "Host[%s]: Services to start: %s", getHost().getId(), Utils.toJson(false, false, results.documentLinks));
        for (int count = 0; count < Math.min(servicesToStartCount, results.documentLinks.size()); count++) {
          String docLink = results.documentLinks.get(count);
          sendStartPatch(current, docLink);
        }
      }
    };

    sendTaskStateQuery(current, TaskState.TaskStage.CREATED, handler);
  }

  /**
   * Sends a patch to move the service indicated by documentSelfLink from CREATED to STARTED stage.
   *
   * @param docSelfLink
   */
  private void sendStartPatch(final State current, String docSelfLink) {
    try {
      ServiceDocument startedPatch =
          TaskSchedulerServiceStateBuilder
              .getStartPatch(Class.forName(current.schedulerServiceClassName));
      Operation patch = Operation.createPatch(UriUtils.buildUri(getHost(), docSelfLink))
          .setBody(startedPatch)
          .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
      sendRequest(patch);

      ServiceUtils.logInfo(this,
          "Host[%s]: TaskSchedulerService moving service %s from CREATED to STARTED", getHost().getId(), docSelfLink);
    } catch (Exception e) {
      logFailure(e);
    }
  }

  /**
   * Triggers a query task with the spec passed as parameters and calls the handler param on success.
   *
   * @param stage
   * @param handler
   */
  private void sendTaskStateQuery(final State current, final TaskState.TaskStage stage,
                                  final Operation.CompletionHandler handler) {
    try {
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(Class.forName(current.schedulerServiceClassName)
              .getDeclaredClasses()[0], stage);

      QueryTask query = QueryTask.create(spec).setDirect(true);
      Operation queryPost = Operation
          .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_QUERY_TASKS))
          .setBody(query)
          .setCompletion(handler);
      sendRequest(queryPost);
    } catch (ClassNotFoundException e) {
      logFailure(e);
    }
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
   * Log failed query.
   *
   * @param e
   */
  private void logFailure(Throwable e) {
    ServiceUtils.logSevere(this, e);
  }

  /**
   * Class defines the durable state of the TaskSchedulerService.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * The type of the schedule service document.
     */
    @NotBlank
    public String schedulerServiceClassName;

    @NotNull
    @Positive
    public Integer tasksLimits;
  }
}
