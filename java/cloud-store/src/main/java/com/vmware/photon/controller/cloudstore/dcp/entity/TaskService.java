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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.OperationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

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
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);
      startOperation.complete();

    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(t);
      }
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);

    try {
      ValidationUtils.validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(t);
      }
    }
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
   * Durable service state data. Class encapsulating the data for Task.
   */
  public static class State extends ServiceDocument {

    public static final String FIELD_NAME_ENTITY_ID = "entityId";
    public static final String FIELD_NAME_ENTITY_KIND = "entityKind";

    public String entityId;

    public String entityKind;

    public String projectId;

    @Enumerated(EnumType.STRING)
    public TaskState state;

    @Enumerated(EnumType.STRING)
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
      @Enumerated(EnumType.STRING)
      public StepState state;
      @Enumerated(EnumType.STRING)
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
