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
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * Service implements a generic trigger for task services.
 */
public class TaskTriggerService extends StatefulService {

  /**
   * Timeout value for the owner selection operation.
   */
  private static final long OWNER_SELECTION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

  /**
   * Default value for the maintenance interval. (1 minute)
   */
  protected static final int DEFAULT_MAINTENANCE_INTERVAL_MILLIS = 60 * 1000;

  /**
   * Default value to set the triggered tasks expiration age. (5 hours)
   */
  protected static final int DEFAULT_TASK_EXPIRATION_AGE_MILLIS = 5 * 60 * 60 * 1000;

  /**
   * Default constructor.
   */
  public TaskTriggerService() {
    super(State.class);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(DEFAULT_MAINTENANCE_INTERVAL_MILLIS));
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    State s = start.getBody(State.class);
    this.initializeState(s);
    this.validateState(s);

    // set the maintenance interval to match the value in the state.
    this.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(s.triggerIntervalMillis));

    start.complete();
  }

  @Override
  public void handlePatch(Operation patch) {
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);

    this.validatePatch(currentState, patchState);
    this.applyPatch(currentState, patchState);
    this.validateState(currentState);

    // update the maintenance interval if we had a value for it in the patch
    if (patchState.triggerIntervalMillis != null) {
      this.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(patchState.triggerIntervalMillis));
    }

    patch.complete();

    this.processPatch(currentState);
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

    Operation.CompletionHandler handler = (Operation op, Throwable failure) -> {
      if (null != failure) {
        // query failed so abort and retry next time
        logFailure(failure);
        return;
      }

      NodeSelectorService.SelectOwnerResponse rsp = op.getBody(NodeSelectorService.SelectOwnerResponse.class);
      if (!getHost().getId().equals(rsp.ownerNodeId)) {
        ServiceUtils.logInfo(TaskTriggerService.this,
            "Host[%s]: Not owner of scheduler [%s] (Owner Info [%s])",
            getHost().getId(), getSelfLink(), Utils.toJson(false, false, rsp));
        return;
      }

      State state = new State();
      sendSelfPatch(state);
    };

    Operation selectOwnerOp = Operation
        .createPost(null)
        .setExpiration(ServiceUtils.computeExpirationTime(OWNER_SELECTION_TIMEOUT_MILLIS))
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
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(final State current) {
    try {
      Type stateType = Class.forName(current.triggerStateClassName);
      ServiceDocument postState = Utils.fromJson(current.serializedTriggerState, stateType);
      postState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(current.taskExpirationAgeMillis);

      Operation post = Operation
          .createPost(UriUtils.buildUri(getHost(), current.factoryServiceLink))
          .setBody(postState);
      this.sendRequest(post);
    } catch (ClassNotFoundException ex) {
      logFailure(ex);
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
    this.sendRequest(patch);
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
   * Class defines the durable state of the TaskTriggerService.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * The time interval to trigger the service. (This value will be used to set the
     * maintenance interval on the service.)
     */
    @DefaultInteger(value = DEFAULT_MAINTENANCE_INTERVAL_MILLIS)
    @Positive
    public Integer triggerIntervalMillis;

    /**
     * The expiration age of triggered tasks.
     */
    @DefaultInteger(value = DEFAULT_TASK_EXPIRATION_AGE_MILLIS)
    @Positive
    public Integer taskExpirationAgeMillis;

    /**
     * The initial state to trigger the task service with.
     */
    @NotBlank
    public String serializedTriggerState;

    /**
     * The type of the trigger state document.
     */
    @NotBlank
    public String triggerStateClassName;

    /**
     * The link for the factory service of the task to trigger.
     */
    @NotBlank
    public String factoryServiceLink;
  }
}
