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

package com.vmware.photon.controller.api.frontend.backends.utils;

import com.vmware.photon.controller.api.frontend.entities.StepEntity;
import com.vmware.photon.controller.api.frontend.entities.TaskEntity;
import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Step;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.cloudstore.xenon.entity.TaskService;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class related to Task.
 *
 * Currently we have three layers in API: front-end, middle-end, and back-end. In each end we have a class
 * defining the task. The task is defined as {@link Task} in front-end, {@link TaskEntity} in middle-end, and
 * {@link TaskService.State} in back-end.
 *
 * This class defines utility functions to convert between layers. Note that with the new Xenon backend model,
 * we will eventually phase out the middle-end. Hence some functions related to middle-end are marked Deprecated
 * here.
 */
public class TaskUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Creates a task object in back-end representation.
   */
  public static TaskService.State assembleBackEndTask(
      Date currentTime,
      TaskService.State.TaskState taskState,
      String taskOperation,
      String taskEntityId,
      String taskEntityKind,
      String taskProjectId,
      List<TaskService.State.Step> taskSteps) {
    TaskService.State taskServiceState = new TaskService.State();

    taskServiceState.entityId = taskEntityId;
    taskServiceState.entityKind = taskEntityKind;
    taskServiceState.projectId = taskProjectId;
    taskServiceState.state = taskState;
    taskServiceState.operation = taskOperation;
    taskServiceState.steps = taskSteps;
    taskServiceState.queuedTime = currentTime;

    if (taskState == TaskService.State.TaskState.COMPLETED) {
      taskServiceState.startedTime = currentTime;
      taskServiceState.endTime = currentTime;
    }

    return taskServiceState;
  }

  /**
   * Converts task from back-end representation to front-end representation.
   *
   * This function is usually called by a front-end client. The client starts a back-end task, and back-end
   * returns a task object in back-end representation. Then the task object is converted to front-end
   * representation and returned to the end user.
   */
  public static Task convertBackEndToFrontEnd(TaskService.State taskState) {
    Task task = new Task();

    Task.Entity entity = new Task.Entity();
    entity.setId(taskState.entityId);
    entity.setKind(taskState.entityKind);
    task.setEntity(entity);

    task.setId(ServiceUtils.getIDFromDocumentSelfLink(taskState.documentSelfLink));
    task.setState(taskState.state.toString());
    task.setOperation(taskState.operation);
    task.setQueuedTime(taskState.queuedTime);
    task.setStartedTime(taskState.startedTime);
    task.setEndTime(taskState.endTime);

    if (StringUtils.isNotBlank(taskState.resourceProperties)) {
      try {
        Object resourceProperties = objectMapper.readValue(
            taskState.resourceProperties,
            Object.class);
        task.setResourceProperties(resourceProperties);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            String.format("Error deserializing resourceProperties %s, error %s",
                taskState.resourceProperties, e.getMessage()));
      }
    }

    if (taskState.steps != null && !taskState.steps.isEmpty()) {
      List<Step> steps = new ArrayList<>();
      taskState.steps
          .stream()
          .sorted((taskStepState1, taskStepState2) -> Integer.compare(taskStepState1.sequence, taskStepState2.sequence))
          .forEach(taskStepState -> steps.add(StepUtils.convertBackEndToFrontEnd(taskStepState)));
      task.setSteps(steps);
    }

    return task;
  }

  /**
   * Converts task from back-end representation to middle-end representation.
   *
   * Legacy code. This function is usually called by TaskBackend. A task is created in back-end and returned
   * in back-end representation. Then the task object is converted to middle-end representation so that the
   * legacy command execution scheduler can schedule commands.
   *
   * Another case is when we poll task status. A task object is returned by back-end in back-end
   * representation. Then the task object is converted to middle-end representation. Normally a second conversion
   * from middle-end to front-end is involved.
   */
  @Deprecated
  public static TaskEntity convertBackEndToMiddleEnd(TaskService.State taskState) {
    TaskEntity taskEntity = new TaskEntity();
    taskEntity.setId(ServiceUtils.getIDFromDocumentSelfLink(taskState.documentSelfLink));
    taskEntity.setEntityId(taskState.entityId);
    taskEntity.setEntityKind(taskState.entityKind);
    taskEntity.setQueuedTime(taskState.queuedTime);
    taskEntity.setStartedTime(taskState.startedTime);
    taskEntity.setEndTime(taskState.endTime);
    taskEntity.setProjectId(taskState.projectId);
    taskEntity.setOperation(Operation.parseOperation(taskState.operation));
    taskEntity.setResourceProperties(taskState.resourceProperties);

    switch (taskState.state) {
      case COMPLETED:
        taskEntity.setState(TaskEntity.State.COMPLETED);
        break;
      case STARTED:
        taskEntity.setState(TaskEntity.State.STARTED);
        break;
      case QUEUED:
        taskEntity.setState(TaskEntity.State.QUEUED);
        break;
      case ERROR:
        taskEntity.setState(TaskEntity.State.ERROR);
        break;
      default:
        String errorMessage = String.format(
            "Unknown task state {%s} found for task link {%s}",
            taskState.state,
            taskState.documentSelfLink);
        throw new IllegalStateException(errorMessage);
    }

    taskEntity.setSteps(new ArrayList<>());

    if (taskState.steps != null) {
      for (TaskService.State.Step step : taskState.steps) {
        StepEntity stepEntity = StepUtils.convertBackEndToMiddleEnd(taskEntity, step);
        taskEntity.addStep(stepEntity);
      }
    }
    return taskEntity;
  }

  /**
   * Converts a list of tasks from back-end representation to middle-end representation.
   *
   * Legacy code. This function is usually called by task command to get a list of tasks related to certain object.
   */
  @Deprecated
  public static ResourceList<TaskEntity> convertBackEndToMiddleEnd(ResourceList<TaskService.State> taskServiceStates) {
    ResourceList<TaskEntity> taskEntities = new ResourceList<>();

    taskEntities.setItems(taskServiceStates.getItems()
        .stream()
        .map(taskServiceState -> convertBackEndToMiddleEnd(taskServiceState))
        .collect(Collectors.toList()));

    taskEntities.setNextPageLink(taskServiceStates.getNextPageLink());
    taskEntities.setPreviousPageLink(taskServiceStates.getPreviousPageLink());

    return taskEntities;
  }

  /**
   * Converts task from middle-end representation to front-end representation.
   *
   * Legacy code. This function is usually called by front-end clients. Once a middle-end represented task object
   * is created or returned, the front-end client converts the task object to front-end representation to
   * be returned to end user.
   */
  @Deprecated
  public static Task convertMiddleEndToFrontEnd(TaskEntity taskEntity) {
    Task task = new Task();

    Task.Entity entity = new Task.Entity();
    entity.setId(taskEntity.getEntityId());
    entity.setKind(taskEntity.getEntityKind());
    task.setEntity(entity);

    task.setId(taskEntity.getId());
    task.setQueuedTime(taskEntity.getQueuedTime());
    task.setStartedTime(taskEntity.getStartedTime());
    task.setEndTime(taskEntity.getEndTime());
    task.setOperation(taskEntity.getOperation().toString());
    task.setState(taskEntity.getState().toString());

    if (StringUtils.isNotBlank(taskEntity.getResourceProperties())) {
      try {
        Object resourceProperties = objectMapper.readValue(
            taskEntity.getResourceProperties(),
            Object.class);
        task.setResourceProperties(resourceProperties);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            String.format("Error deserializing taskEntity resourceProperties %s, error %s",
                taskEntity.getResourceProperties(), e.getMessage())
        );
      }
    }

    List<Step> steps = new ArrayList<>();
    taskEntity.getSteps()
        .stream()
        .sorted((stepEntity1, stepEntity2) -> Integer.compare(stepEntity1.getSequence(), stepEntity2.getSequence()))
        .forEach(stepEntity -> steps.add(StepUtils.convertMiddleEndToFrontEnd(stepEntity)));
    task.setSteps(steps);

    return task;
  }

  /**
   * Converts a list of tasks from middle-end representation to front-end representation.
   *
   * Legacy code. This function is usually called by front-end clients to get a list of tasks related to
   * other object.
   */
  @Deprecated
  public static ResourceList<Task> convertMiddleEndToFrontEnd(ResourceList<TaskEntity> taskEntities) {
    ResourceList<Task> tasks = new ResourceList<>();

    tasks.setItems(taskEntities.getItems()
        .stream()
        .map(taskEntity -> convertMiddleEndToFrontEnd(taskEntity))
        .collect(Collectors.toList()));

    tasks.setNextPageLink(taskEntities.getNextPageLink());
    tasks.setPreviousPageLink(taskEntities.getPreviousPageLink());

    return tasks;
  }

  /**
   * Converts task from middle-end representation to back-end representation.
   *
   * Legacy code. This function is usually called by task command. Once a command has updates of a task,
   * it passes in a middle-end represented task object to TaskBackend. Then TaskBackend converts the object
   * to back-end representation and updates back-end.
   */
  @Deprecated
  public static TaskService.State convertMiddleEndToBackEnd(TaskEntity taskEntity) {
    TaskService.State taskServiceState = new TaskService.State();

    taskServiceState.entityId = taskEntity.getEntityId();
    taskServiceState.entityKind = taskEntity.getEntityKind();
    taskServiceState.queuedTime = taskEntity.getQueuedTime();
    taskServiceState.startedTime = taskEntity.getStartedTime();
    taskServiceState.endTime = taskEntity.getEndTime();
    taskServiceState.projectId = taskEntity.getProjectId();
    taskServiceState.operation = taskEntity.getOperation() != null ?
      taskEntity.getOperation().toString() : null;

    switch (taskEntity.getState()) {
      case COMPLETED:
        taskServiceState.state = TaskService.State.TaskState.COMPLETED;
        break;
      case STARTED:
        taskServiceState.state = TaskService.State.TaskState.STARTED;
        break;
      case QUEUED:
        taskServiceState.state = TaskService.State.TaskState.QUEUED;
        break;
      case ERROR:
        taskServiceState.state = TaskService.State.TaskState.ERROR;
        break;
      default:
        String errorMessage = String.format(
            "Unknown task state found in taskEntity {%s}",
            taskEntity);
        throw new IllegalArgumentException(errorMessage);
    }

    if (taskEntity.getSteps() != null && !taskEntity.getSteps().isEmpty()) {
      taskServiceState.steps = new ArrayList<>();
      taskEntity.getSteps().forEach(step -> {
        taskServiceState.steps.add(StepUtils.convertMiddleEndToBackEnd(step));
      });
    }

    return taskServiceState;
  }
}
