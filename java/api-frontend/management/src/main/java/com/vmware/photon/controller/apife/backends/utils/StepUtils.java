/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apife.backends.utils;

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.Step;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepErrorEntity;
import com.vmware.photon.controller.apife.entities.StepResourceEntity;
import com.vmware.photon.controller.apife.entities.StepWarningEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class related to task step.
 *
 * Currently we have three layers in API: front-end, middle-end, and back-end. In each end we have a class
 * defining the task step. The task step is defined as {@link Step} in front-end, {@link StepEntity} in
 * middle-end, and {@link TaskService.State.Step} in back-end.
 *
 * This class defines utility functions to convert between layers. Note that with the new Xenon backend model,
 * we will eventually phase out the middle-end. Hence some functions related to middle-end are marked Deprecated
 * here.
 */
public class StepUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Converts task step from back-end representation to front-end representation.
   *
   * See details in {@link TaskUtils#convertBackEndToFrontEnd}.
   */
  public static Step convertBackEndToFrontEnd(TaskService.State.Step taskStepState) {
    Step step = new Step();

    step.setSequence(taskStepState.sequence);
    step.setState(taskStepState.state.toString());
    step.setOperation(taskStepState.operation);
    step.setQueuedTime(taskStepState.queuedTime);
    step.setStartedTime(taskStepState.startedTime);
    step.setEndTime(taskStepState.endTime);

    if (taskStepState.errors != null && !taskStepState.errors.isEmpty()) {
      taskStepState.errors.forEach(error -> {
        ApiError apiError = new ApiError();
        apiError.setCode(error.code);
        apiError.setMessage(error.message);
        apiError.setData(error.data);

        step.addError(apiError);
      });
    }

    if (taskStepState.warnings != null && !taskStepState.warnings.isEmpty()) {
      taskStepState.warnings.forEach(warning -> {
        ApiError apiError = new ApiError();
        apiError.setCode(warning.code);
        apiError.setMessage(warning.message);
        apiError.setData(warning.data);

        step.addWarning(apiError);
      });
    }

    if (taskStepState.resources != null && !taskStepState.resources.isEmpty()) {
      Map<String, String> options = new HashMap<>();
      taskStepState.resources.forEach(resource -> options.put(resource.resourceId, resource.resourceKind));
      step.setOptions(options);
    }

    if (StringUtils.isNotBlank(taskStepState.options)) {
      try {
        step.setOptions(objectMapper.readValue(taskStepState.options, HashMap.class));
      } catch (IOException e) {
        throw new IllegalArgumentException(String.format("Error deserializing step options: %s",
            e.getMessage()));
      }
    }

    return step;
  }

  /**
   * Converts task step from back-end representation to middle-end representation.
   *
   * See details in {@link TaskUtils#convertBackEndToMiddleEnd}.
   */
  @Deprecated
  public static StepEntity convertBackEndToMiddleEnd(
      TaskEntity taskEntity,
      TaskService.State.Step taskStepState) {
    StepEntity stepEntity = new StepEntity();

    stepEntity.setTask(taskEntity);
    stepEntity.setSequence(taskStepState.sequence);
    stepEntity.setOperation(Operation.parseOperation(taskStepState.operation));
    stepEntity.setStartedTime(taskStepState.startedTime);
    stepEntity.setQueuedTime(taskStepState.queuedTime);
    stepEntity.setEndTime(taskStepState.endTime);
    stepEntity.setOptions(taskStepState.options);

    switch (taskStepState.state) {
      case QUEUED:
        stepEntity.setState(StepEntity.State.QUEUED);
        break;
      case STARTED:
        stepEntity.setState(StepEntity.State.STARTED);
        break;
      case ERROR:
        stepEntity.setState(StepEntity.State.ERROR);
        break;
      case COMPLETED:
        stepEntity.setState(StepEntity.State.COMPLETED);
        break;
      default:
        String errorMessage = String.format(
            "Unknown step state {%s} found for task id {%s}",
            taskStepState.state,
            taskEntity.getId());
        throw new IllegalStateException(errorMessage);
    }

    if (taskStepState.errors != null) {
      taskStepState.errors.forEach(error -> {
        StepErrorEntity stepErrorEntity = new StepErrorEntity();
        stepErrorEntity.setCode(error.code);
        stepErrorEntity.setMessage(error.message);
        stepErrorEntity.setData(error.data);
        stepEntity.getErrors().add(stepErrorEntity);
      });
    }

    if (taskStepState.warnings != null) {
      taskStepState.warnings.forEach(warning -> {
        StepWarningEntity stepErrorWarning = new StepWarningEntity();
        stepErrorWarning.setCode(warning.code);
        stepErrorWarning.setMessage(warning.message);
        stepErrorWarning.setData(warning.data);
        stepEntity.getWarnings().add(stepErrorWarning);
      });
    }

    if (taskStepState.resources != null) {
      taskStepState.resources.forEach(resource -> {
        StepResourceEntity stepResourceEntity = new StepResourceEntity();
        stepResourceEntity.setEntityId(resource.resourceId);
        stepResourceEntity.setEntityKind(resource.resourceKind);
        stepEntity.getResources().add(stepResourceEntity);
      });
    }

    return stepEntity;
  }

  /**
   * Converts task step from middle-end representation to front-end representation.
   *
   * See details in {@link TaskUtils#convertMiddleEndToFrontEnd}.
   */
  @Deprecated
  public static Step convertMiddleEndToFrontEnd(StepEntity stepEntity) {
    Step step = new Step();

    step.setSequence(stepEntity.getSequence());
    step.setState(stepEntity.getState().toString());
    step.setOperation(stepEntity.getOperation().toString());
    step.setQueuedTime(stepEntity.getQueuedTime());
    step.setStartedTime(stepEntity.getStartedTime());
    step.setEndTime(stepEntity.getEndTime());

    if (!stepEntity.getErrors().isEmpty()) {
      stepEntity.getErrors().forEach(error -> step.addError(error.toApiError()));
    }

    if (!stepEntity.getWarnings().isEmpty()) {
      stepEntity.getWarnings().forEach(warning -> step.addWarning(warning.toApiError()));
    }

    if (StringUtils.isNotBlank(stepEntity.getOptions())) {
      try {
        step.setOptions(objectMapper.readValue(stepEntity.getOptions(), HashMap.class));
      } catch (IOException e) {
        throw new IllegalArgumentException(String.format("Error deserializing step %s options: %s",
            stepEntity.getId(), e.getMessage()));
      }
    }

    return step;
  }

  /**
   * Converts task step from middle-end representation to back-end representation.
   *
   * See details in {@link TaskUtils#convertMiddleEndToBackEnd}.
   */
  @Deprecated
  public static TaskService.State.Step convertMiddleEndToBackEnd(StepEntity stepEntity) {
    TaskService.State.Step taskStepState = new TaskService.State.Step();

    taskStepState.sequence = stepEntity.getSequence();
    taskStepState.operation = stepEntity.getOperation().toString();
    taskStepState.queuedTime = stepEntity.getQueuedTime();
    taskStepState.startedTime = stepEntity.getStartedTime();
    taskStepState.endTime = stepEntity.getEndTime();
    taskStepState.options = stepEntity.getOptions();

    switch (stepEntity.getState()) {
      case QUEUED:
        taskStepState.state = TaskService.State.StepState.QUEUED;
        break;
      case STARTED:
        taskStepState.state = TaskService.State.StepState.STARTED;
        break;
      case ERROR:
        taskStepState.state = TaskService.State.StepState.ERROR;
        break;
      case COMPLETED:
        taskStepState.state = TaskService.State.StepState.COMPLETED;
        break;
      default:
        String errorMessage = String.format(
            "Unknown step state {%s} found for task id {%s}",
            taskStepState.state,
            stepEntity.getTask().getId());
        throw new IllegalStateException(errorMessage);
    }

    if (stepEntity.getErrors() != null) {
      taskStepState.errors = new ArrayList<>();
      stepEntity.getErrors().forEach(error -> {
        TaskService.State.StepError stepError = new TaskService.State.StepError();
        stepError.code = error.getCode();
        stepError.message = error.getMessage();
        stepError.data = error.getData();

        taskStepState.errors.add(stepError);
      });
    }

    if (stepEntity.getWarnings() != null) {
      taskStepState.warnings = new ArrayList<>();
      stepEntity.getWarnings().forEach(warning -> {
        TaskService.State.StepError stepError = new TaskService.State.StepError();
        stepError.code = warning.getCode();
        stepError.message = warning.getMessage();
        stepError.data = warning.getData();

        taskStepState.warnings.add(stepError);
      });
    }

    if (stepEntity.getResources() != null) {
      taskStepState.resources = new ArrayList<>();
      stepEntity.getResources().forEach(resource -> {
        TaskService.State.StepResource stepResource = new TaskService.State.StepResource();
        stepResource.resourceId = resource.getEntityId();
        stepResource.resourceKind = resource.getEntityKind();

        taskStepState.resources.add(stepResource);
      });
    }

    return taskStepState;
  }
}
