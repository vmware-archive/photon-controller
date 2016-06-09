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

package com.vmware.photon.controller.housekeeper.dcp;

import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.TimeUnit;

/**
 * Class ImageCleannerTriggerService: periodically starts a new ImageCleanerService if there isn't a currently
 * running one.
 */
public class ImageCleanerTriggerService extends StatefulService {
  protected static final long DEFAULT_TRIGGER_INTERVAL = TimeUnit.HOURS.toMicros(1);
  protected static final long EXPIRATION_TIME_MULTIPLIER = 5;

  private static final long OWNER_SELECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

  private static final long UNUSED_IMAGE_AGE = TimeUnit.MINUTES.toSeconds(30);



  /**
   * Default constructor.
   */
  public ImageCleanerTriggerService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.setMaintenanceIntervalMicros(DEFAULT_TRIGGER_INTERVAL);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    // Initialize the task stage
    State state = start.getBody(State.class);
    if (state.executionState == null) {
      state.executionState = ExecutionState.RUNNING;
    }
    if (state.triggersSuccess == null) {
      state.triggersSuccess = 0L;
    }
    if (state.triggersError == null) {
      state.triggersError = 0L;
    }
    if (state.shouldTriggerTasks != null) {
      state.shouldTriggerTasks = null;
    }

    try {
      validateState(state);
      start.setBody(state).complete();
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

      this.validatePatch(patchState);
      this.applyPatch(currentState, patchState);
      this.validateState(currentState);
      patch.complete();

      // Process and complete patch.
      processPatch(patch, currentState, patchState);
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patch)) {
        patch.fail(e);
      }
    }
  }

  /**
   * Checks if service's background processing is in pause state.
   */
  private boolean isBackgroundPaused() {
    ServiceConfig serviceConfig = ((ServiceConfigProvider) getHost()).getServiceConfig();
    boolean backgroundPaused = true;
    try {
      backgroundPaused = serviceConfig.isBackgroundPaused();
    } catch (Exception ex) {
      ServiceUtils.logSevere(this, ex);
    }
    return backgroundPaused;
  }

  /**
   * Handle service periodic maintenance calls.
   */
  @Override
  public void handleMaintenance(Operation post) {
    post.complete();

    if (isBackgroundPaused()) {
      return;
    }

    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation op, Throwable failure) {
        if (null != failure) {
          // query failed so abort and retry next time
          logFailure(failure);
          return;
        }

        NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
        if (!getHost().getId().equals(rsp.ownerNodeId)) {
          ServiceUtils.logInfo(ImageCleanerTriggerService.this,
              "Host[%s]: Not owner of scheduler [%s] (Owner Info [%s])",
              getHost().getId(), getSelfLink(), Utils.toJson(true, false, rsp));
          return;
        }

        State state = new State();
        state.shouldTriggerTasks = true;
        sendSelfPatch(state);
      }
    };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(OWNER_SELECTION_TIMEOUT))
        .setCompletion(handler);
    getHost().selectOwner(null, getSelfLink(), selectOwnerOp);
  }

  /**
   * Process patch.
   */
  private void processPatch(Operation patch, final State currentState, final State patchState) {
    // If the triggered is stopped or this is not a shouldTriggerTasks, exit.
    if (currentState.executionState != ExecutionState.RUNNING ||
        patchState.shouldTriggerTasks == null || !patchState.shouldTriggerTasks) {
      return;
    }

    // Trigger cleaner service.
    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        // Note this is a race with maintenance calls. Some statistics may be lost.
        State newState = new State();
        if (throwable == null) {
          newState.triggersSuccess = currentState.triggersSuccess + 1;
        } else {
          ServiceUtils.logSevere(ImageCleanerTriggerService.this, throwable);
          newState.triggersError = currentState.triggersError + 1;
        }
        sendSelfPatch(newState);
      }
    };

    ImageCleanerService.State postState = new ImageCleanerService.State();
    postState.imageWatermarkTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    postState.imageDeleteWatermarkTime =
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - UNUSED_IMAGE_AGE;
    postState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
        EXPIRATION_TIME_MULTIPLIER * this.getMaintenanceIntervalMicros());

    Operation createImageOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), ImageCleanerServiceFactory.SELF_LINK))
        .setBody(postState)
        .setCompletion(handler);

    this.sendRequest(createImageOperation);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param current
   */
  protected void validateState(State current) {
    checkNotNull(current.executionState, "ExecutionState cannot be null.");
    checkIsPositiveNumber(current.triggersSuccess, "triggersSuccess");
    checkIsPositiveNumber(current.triggersError, "triggersError");
  }

  /**
   * Validate patch correctness.
   *
   * @param patch
   */
  protected void validatePatch(State patch) {
    if (patch.triggersSuccess == null &&
        patch.triggersError == null &&
        patch.shouldTriggerTasks == null) {
      checkArgument(patch.executionState != null, "ExecutionState cannot be null.");
    }
  }

  /**
   * Applies patch to current document state.
   *
   * @param current
   * @param patch
   */
  protected void applyPatch(State current, State patch) {
    if (patch.executionState != null) {
      current.executionState = patch.executionState;
    }
    current.triggersSuccess = updateLongWithMax(current.triggersSuccess, patch.triggersSuccess);
    current.triggersError = updateLongWithMax(current.triggersError, patch.triggersError);
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private void checkIsPositiveNumber(Long value, String description) {
    checkNotNull(value == null, description + " cannot be null.");
    checkState(value >= 0, description + " cannot be negative.");
  }

  /**
   * Update long value. Check for null and overflow.
   */
  private Long updateLongWithMax(Long previousValue, Long newValue) {
    if (newValue == null) {
      return previousValue;
    }
    if (newValue < 0) {
      return 0L;
    }
    return Math.max(previousValue, newValue);
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
   * Service execution stages.
   */
  public static enum ExecutionState {
    RUNNING,
    STOPPED
  }

  /**
   * Class defines the durable state of the ImageRemoverService.
   */
  public static class State extends ServiceDocument {
    public ExecutionState executionState;
    public Boolean shouldTriggerTasks;
    public Long triggersSuccess;
    public Long triggersError;
  }
}
