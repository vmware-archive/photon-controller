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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Class TaskService is used for data persistence of task information.
 */
public class TaskService extends StatefulService {

  public TaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
  }

  @Override
  public OperationProcessingChain getOperationProcessingChain() {
    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter myRouter = new RequestRouter();

    myRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<StepUpdate>(
            StepUpdate.class, "kind", StepUpdate.KIND),
        this::handleStepUpdatePatch, "Step Update");

    OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);
    opProcessingChain.add(myRouter);
    setOperationProcessingChain(opProcessingChain);
    return opProcessingChain;
  }

  public void handleStepUpdatePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patch);
      StepUpdate stepUpdate = patch.getBody(StepUpdate.class);

      if (stepUpdate.step == null) {
        throw new IllegalArgumentException("Null step is not allowed for StepUpdate patch");
      }

      if (stepUpdate.step.operation == null) {
        throw new IllegalArgumentException("Null step.operation is not allowed for StepUpdate patch");
      }

      State.Step step = null;
      if (currentState.steps != null) {
        for (State.Step currentStep : currentState.steps) {
          if (currentStep.operation.equals(stepUpdate.step.operation)) {
            step = currentStep;
          }
        }
      }

      if (step == null) {
        throw new IllegalArgumentException("Cannot update a step that does not exist");
      }

      currentState.steps.remove(step);
      currentState.steps.add(stepUpdate.step);

      validateState(currentState);

      setState(patch, currentState);
      patch.setBody(currentState);
      patch.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    try {
      State currentState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);

      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  /**
   * Class for updating step.
   */
  public static class StepUpdate extends ServiceDocument {
    public static final String KIND = StepUpdate.class.getCanonicalName();
    public final String kind;
    public State.Step step;

    //We do not want to allow creating this update patch without setting "kind".
    //That will lead to handlePatch to be invoked with an empty patch wiping the state of the document clean.
    //We also cannot set "kind" in default constructor as that would lead to all patches getting matched to StepUpdate
    //in RequestRouter.RequestBodyMatcher
    //That will lead to all patches other than StepUpdate to fail.
    //Hence we make the default constructor private and provide a constructor that ensures this object
    //is properly constructed.

    private StepUpdate() {
      kind = null;
    }

    public StepUpdate(State.Step step) {
      if (step == null) {
        throw new IllegalArgumentException("step cannot be null");
      }

      if (step.operation == null) {
        throw new IllegalArgumentException("Null step.operation is not allowed");
      }

      this.kind = KIND;
      this.step = step;
    }
  }

  /**
   * Durable service state data. Class encapsulating the data for Task.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_ENTITY_ID = "entityId";
    public static final String FIELD_NAME_ENTITY_KIND = "entityKind";

    public String entityId;

    public String entityKind;

    public String projectId;

    public TaskState state;

    public com.vmware.photon.controller.api.Operation operation;

    public Date startedTime;
    public Date queuedTime;
    public Date endTime;

    public String resourceProperties;

    public List<Step> steps;

    /**
     * Task state.
     */
    public enum TaskState {
      QUEUED,
      STARTED,
      ERROR,
      COMPLETED
    }

    /**
     * Step state.
     */
    public enum StepState {
      QUEUED,
      STARTED,
      ERROR,
      COMPLETED
    }

    /**
     * Class encapsulating the data for Step.
     */
    public static class Step {
      public int sequence;
      public StepState state;
      public com.vmware.photon.controller.api.Operation operation;
      public String options;
      public Date startedTime;
      public Date queuedTime;
      public Date endTime;
      public List<StepError> warnings;
      public List<StepError> errors;
      public List<StepResource> resources;
    }

    /**
     * Class encapsulating the data for StepResource.
     */
    public static class StepResource {
      public String resourceId;
      public String resourceKind;
    }

    /**
     * Class encapsulating the data for StepError.
     */
    public static class StepError {
      public String code;
      public String message;
      public Map<String, String> data;
    }
  }
}
