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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.Operation;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.utils.StepUtils;
import com.vmware.photon.controller.apife.backends.utils.TaskUtils;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Common task operations using DCP cloud store.
 */
@Singleton
public class TaskDcpBackend implements TaskBackend, StepBackend {

  private static final Logger logger = LoggerFactory.getLogger(TaskDcpBackend.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final ApiFeXenonRestClient dcpClient;

  private final EntityLockBackend entityLockBackend;

  @Inject
  public TaskDcpBackend(
      ApiFeXenonRestClient dcpClient,
      EntityLockBackend entityLockBackend) {
    this.dcpClient = dcpClient;
    this.entityLockBackend = entityLockBackend;

    dcpClient.start();
  }

  @Override
  public StepBackend getStepBackend() {
    return this;
  }

  @Override
  public Task getApiRepresentation(String id) throws TaskNotFoundException {
    return TaskUtils.convertMiddleEndToFrontEnd(findById(id));
  }

  @Override
  public Task getApiRepresentation(TaskEntity task) throws TaskNotFoundException {
    return TaskUtils.convertMiddleEndToFrontEnd(task);
  }

  @Override
  public ResourceList<Task> filter(String entityId, String entityKind, Optional<String> state,
                                   Optional<Integer> pageSize) throws ExternalException {

    return this.filter(Optional.of(entityId), Optional.of(entityKind), state, pageSize);
  }

  @Override
  public ResourceList<Task> filter(Optional<String> entityId, Optional<String> entityKind, Optional<String> state,
                                   Optional<Integer> pageSize) throws ExternalException {
    ResourceList<TaskEntity> taskEntities = getEntityTasks(entityId, entityKind, state, pageSize);
    return TaskUtils.convertMiddleEndToFrontEnd(taskEntities);
  }

  @Override
  public ResourceList<Task> getTasksPage(String pageLink) throws PageExpiredException {
    ResourceList<TaskEntity> taskEntities = getEntityTasksPage(pageLink);
    return TaskUtils.convertMiddleEndToFrontEnd(taskEntities);
  }

  @Override
  public TaskEntity createQueuedTask(BaseEntity entity, Operation operation) {

    TaskService.State taskServiceState = new TaskService.State();

    //currently creation of kubernetes and mesos cluster, their resize and delete pass null entity
    //putting this null check temporarily to allow the switch to dcp backend to work
    //
    if (entity != null) {
      taskServiceState.entityId = entity.getId();
      taskServiceState.entityKind = entity.getKind();

      // auto-link infrastructure tasks to their project
      if (entity instanceof InfrastructureEntity) {
        InfrastructureEntity infrastructureEntity = (InfrastructureEntity) entity;
        String projectId = infrastructureEntity.getProjectId();
        taskServiceState.projectId = projectId;
      }
    }

    taskServiceState.state = TaskService.State.TaskState.QUEUED;
    taskServiceState.operation = operation.getOperation();
    taskServiceState.queuedTime = DateTime.now().toDate();

    com.vmware.xenon.common.Operation result = dcpClient.post(TaskServiceFactory.SELF_LINK, taskServiceState);
    TaskService.State createdState = result.getBody(TaskService.State.class);
    TaskEntity task = TaskUtils.convertBackEndToMiddleEnd(createdState);
    logger.info("created task: {}", task);
    return task;
  }

  @Override
  public TaskEntity createCompletedTask(BaseEntity entity, Operation operation) {
    TaskService.State taskServiceState = new TaskService.State();
    if (entity != null) {
      taskServiceState.entityId = entity.getId();
      taskServiceState.entityKind = entity.getKind();

      // auto-link infrastructure tasks to their project
      if (entity instanceof InfrastructureEntity) {
        InfrastructureEntity infrastructureEntity = (InfrastructureEntity) entity;
        String projectId = infrastructureEntity.getProjectId();
        taskServiceState.projectId = projectId;
      }
    }

    taskServiceState.state = TaskService.State.TaskState.COMPLETED;
    taskServiceState.operation = operation.getOperation();
    taskServiceState.startedTime = DateTime.now().toDate();
    taskServiceState.endTime = taskServiceState.startedTime;
    taskServiceState.queuedTime = taskServiceState.startedTime;

    com.vmware.xenon.common.Operation result = dcpClient.post(TaskServiceFactory.SELF_LINK, taskServiceState);
    TaskService.State createdState = result.getBody(TaskService.State.class);
    TaskEntity task = TaskUtils.convertBackEndToMiddleEnd(createdState);
    logger.info("created task: {}", task);
    return task;
  }

  @Override
  public TaskEntity createTaskWithSteps(BaseEntity entity,
                                        Operation operation,
                                        Boolean isCompleted,
                                        List<StepEntity> stepEntities) {
    Date currentTime = DateTime.now().toDate();
    TaskService.State taskServiceState = new TaskService.State();

    //currently creation of kubernetes and mesos cluster, their resize and delete pass null entity
    //putting this null check temporarily to allow the switch to dcp backend to work
    //
    if (entity != null) {
      taskServiceState.entityId = entity.getId();
      taskServiceState.entityKind = entity.getKind();

      // auto-link infrastructure tasks to their project
      if (entity instanceof InfrastructureEntity) {
        InfrastructureEntity infrastructureEntity = (InfrastructureEntity) entity;
        String projectId = infrastructureEntity.getProjectId();
        taskServiceState.projectId = projectId;
      }
    }

    if (isCompleted) {
      taskServiceState.state = TaskService.State.TaskState.COMPLETED;
      taskServiceState.startedTime = currentTime;
      taskServiceState.endTime = currentTime;
      taskServiceState.queuedTime = currentTime;
    } else {
      taskServiceState.state = TaskService.State.TaskState.QUEUED;
      taskServiceState.queuedTime = currentTime;
    }

    taskServiceState.operation = operation.getOperation();

    if (stepEntities != null) {
      taskServiceState.steps = new ArrayList<>();
      Integer nextStepSequence = 0;
      for (StepEntity stepEntity : stepEntities) {
        stepEntity.setQueuedTime(currentTime);
        if (isCompleted) {
          stepEntity.setState(StepEntity.State.COMPLETED);
          stepEntity.setStartedTime(currentTime);
          stepEntity.setEndTime(currentTime);
        } else {
          stepEntity.setState(StepEntity.State.QUEUED);
        }
        stepEntity.setSequence(nextStepSequence);
        nextStepSequence++;
        TaskService.State.Step step = StepUtils.convertMiddleEndToBackEnd(stepEntity);
        taskServiceState.steps.add(step);
      }
    }

    com.vmware.xenon.common.Operation result = dcpClient.post(TaskServiceFactory.SELF_LINK, taskServiceState);
    TaskService.State createdState = result.getBody(TaskService.State.class);
    TaskEntity task = TaskUtils.convertBackEndToMiddleEnd(createdState);
    task.setSteps(stepEntities); // replacing steps to retain the transient properties
    logger.info("created task: {}", task);
    return task;
  }

  @Override
  public void markTaskAsStarted(TaskEntity task) throws TaskNotFoundException {
    logger.info("Task {} has been marked as STARTED", task.getId());

    TaskService.State taskServiceState = new TaskService.State();
    taskServiceState.state = TaskService.State.TaskState.STARTED;
    taskServiceState.startedTime = DateTime.now().toDate();

    patchTaskService(task.getId(), taskServiceState);
  }

  @Override
  public void markTaskAsDone(TaskEntity task) throws TaskNotFoundException {
    TaskService.State taskServiceState = new TaskService.State();
    taskServiceState.state = TaskService.State.TaskState.COMPLETED;
    taskServiceState.endTime = DateTime.now().toDate();
    patchTaskService(task.getId(), taskServiceState);
    logger.info("Task {} has been marked as COMPLETED", task.getId());
  }

  @Override
  public void markTaskAsFailed(TaskEntity task) throws TaskNotFoundException {
    TaskService.State taskServiceState = new TaskService.State();
    taskServiceState.state = TaskService.State.TaskState.ERROR;
    taskServiceState.endTime = DateTime.now().toDate();
    patchTaskService(task.getId(), taskServiceState);
    logger.info("Task {} has been marked as ERROR", task);
  }

  @Override
  public void markAllStepsAsFailed(TaskEntity taskEntity, Throwable t) throws TaskNotFoundException {
    taskEntity.setState(TaskEntity.State.ERROR);
    taskEntity.setEndTime(DateTime.now().toDate());

    if (taskEntity.getSteps() != null) {
      for (StepEntity stepEntity : taskEntity.getSteps()) {
        stepEntity.setState(StepEntity.State.ERROR);
        stepEntity.setEndTime(taskEntity.getEndTime());
        stepEntity.addException(t);
      }
    }

    TaskService.State task = TaskUtils.convertMiddleEndToBackEnd(taskEntity);
    patchTaskService(taskEntity.getId(), task);
  }

  @Override
  public void update(TaskEntity task) throws TaskNotFoundException {
    TaskService.State taskState = TaskUtils.convertMiddleEndToBackEnd(task);
    patchTaskService(task.getId(), taskState);
  }

  @Override
  public ResourceList<TaskEntity> getEntityTasks(Optional<String> entityId, Optional<String> entityKind,
                                                 Optional<String> state, Optional<Integer> pageSize)
      throws InvalidQueryParamsException {

    ResourceList<TaskService.State> tasksDocuments = getEntityDocuments(entityId, entityKind, state, pageSize);
    return TaskUtils.convertBackEndToMiddleEnd(tasksDocuments);
  }

  @Override
  public ResourceList<TaskEntity> getEntityTasksPage(String pageLink) throws PageExpiredException {
    ServiceDocumentQueryResult queryResult = null;
    try {
      queryResult = dcpClient.queryDocumentPage(pageLink);
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(pageLink);
    }

    ResourceList<TaskService.State> taskStates = PaginationUtils.xenonQueryResultToResourceList(
        TaskService.State.class, queryResult);

    return TaskUtils.convertBackEndToMiddleEnd(taskStates);
  }

  private void patchTaskService(String taskId, TaskService.State taskServiceState) throws TaskNotFoundException {
    try {
      dcpClient.patch(TaskServiceFactory.SELF_LINK + "/" + taskId, taskServiceState);
    } catch (DocumentNotFoundException e) {
      throw new TaskNotFoundException(taskId);
    }
  }

  private void patchTaskServiceWithStepUpdate(String taskId, TaskService.StepUpdate stepUpdate) throws
      TaskNotFoundException {
    try {
      dcpClient.patch(TaskServiceFactory.SELF_LINK + "/" + taskId, stepUpdate);
    } catch (DocumentNotFoundException e) {
      throw new TaskNotFoundException(taskId);
    }
  }

  private ResourceList<TaskService.State> getEntityDocuments(Optional<String> entityId, Optional<String> entityKind,
                                                             Optional<String> state, Optional<Integer> pageSize)
      throws InvalidQueryParamsException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();

    if (entityId.isPresent() && !entityKind.isPresent()) {
      throw new InvalidQueryParamsException("Both entityId and entityKind params need to be specified.");
    }

    if (!entityId.isPresent() && entityKind.isPresent()) {
      throw new InvalidQueryParamsException("Both entityId and entityKind params need to be specified.");
    }

    if (entityId.isPresent()) {
      termsBuilder.put("entityId", entityId.get());
    }

    if (entityKind.isPresent()) {
      termsBuilder.put("entityKind", entityKind.get().toLowerCase());
    }

    if (state.isPresent()) {
      termsBuilder.put("state", state.get().toUpperCase());
    }

    ServiceDocumentQueryResult queryResult = dcpClient.queryDocuments(TaskService.State.class, termsBuilder.build(),
        pageSize, true);

    return PaginationUtils.xenonQueryResultToResourceList(TaskService.State.class, queryResult);
  }

  @Override
  public void delete(TaskEntity task) {
    dcpClient.delete(TaskServiceFactory.SELF_LINK + "/" + task.getId(), new TaskService.State());
  }

  @Override
  public TaskEntity findById(String id) throws TaskNotFoundException {
    TaskService.State taskState = getTaskStateById(id);

    if (taskState == null) {
      throw new TaskNotFoundException(id);
    }

    return TaskUtils.convertBackEndToMiddleEnd(taskState);
  }

  @Override
  public void update(StepEntity stepEntity) throws TaskNotFoundException {
    TaskService.State.Step step = StepUtils.convertMiddleEndToBackEnd(stepEntity);
    TaskService.StepUpdate stepUpdate = new TaskService.StepUpdate(step);
    patchTaskServiceWithStepUpdate(stepEntity.getTask().getId(), stepUpdate);
  }

  @Override
  public StepEntity createQueuedStep(TaskEntity task, Operation operation)
      throws TaskNotFoundException {
    return createQueuedStep(task, new ArrayList<BaseEntity>(), operation, null);
  }

  @Override
  public StepEntity createQueuedStep(TaskEntity task, BaseEntity entity, Operation operation)
      throws TaskNotFoundException {
    return createQueuedStep(task, entity, operation, null);
  }

  @Override
  public StepEntity createQueuedStep(TaskEntity task, BaseEntity entity,
                                     Operation operation, Map<String, String> stepOptions)
      throws TaskNotFoundException {
    List<BaseEntity> entities = new ArrayList<>();
    if (entity != null) {
      entities.add(entity);
    }
    return createQueuedStep(task, entities, operation, stepOptions);
  }

  @Override
  public StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities, Operation operation)
      throws TaskNotFoundException {
    return createQueuedStep(task, entities, operation, null);
  }

  @Override
  public StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities,
                                     Operation operation, Map<String, String> stepOptions)
      throws TaskNotFoundException {
    return createStep(task, StepEntity.State.QUEUED, entities, operation, stepOptions);
  }

  @Override
  public StepEntity createCompletedStep(TaskEntity task, BaseEntity entity, Operation operation)
      throws TaskNotFoundException {
    List<BaseEntity> entities = new ArrayList<>();
    if (entity != null) {
      entities.add(entity);
    }
    return createStep(task, StepEntity.State.COMPLETED, entities, operation, null);
  }

  @Override
  public void markStepAsStarted(StepEntity stepEntity) throws TaskNotFoundException {
    stepEntity.setState(StepEntity.State.STARTED);
    stepEntity.setStartedTime(DateTime.now().toDate());
    update(stepEntity);
  }

  @Override
  public void markStepAsDone(StepEntity stepEntity) throws TaskNotFoundException {
    stepEntity.setState(StepEntity.State.COMPLETED);
    stepEntity.setEndTime(DateTime.now().toDate());
    update(stepEntity);
  }

  @Override
  public void markStepAsFailed(StepEntity stepEntity, Throwable t) throws TaskNotFoundException {
    stepEntity.setState(StepEntity.State.ERROR);
    stepEntity.setEndTime(DateTime.now().toDate());
    stepEntity.addException(t);
    update(stepEntity);
  }

  @Override
  public void addWarning(StepEntity stepEntity, Throwable t) throws TaskNotFoundException {
    logger.warn("Step {} has warning", stepEntity, t);

    stepEntity.addWarning(t);
    stepEntity.setEndTime(DateTime.now().toDate());
    update(stepEntity);
  }

  @Override
  public void addWarnings(StepEntity stepEntity, List<Throwable> warningList) throws TaskNotFoundException {
    for (Throwable t : warningList) {
      logger.warn("Step {} has warning", stepEntity, t);
      stepEntity.addWarning(t);
    }

    stepEntity.setEndTime(DateTime.now().toDate());
    update(stepEntity);
  }

  @Override
  public StepEntity getStepByTaskIdAndOperation(String taskId, Operation operation) throws TaskNotFoundException {
    TaskEntity taskEntity = findById(taskId);

    for (StepEntity stepEntity : taskEntity.getSteps()) {
      if (stepEntity.getOperation() == operation) {
        return stepEntity;
      }
    }
    return null;
  }

  @Override
  public void setTaskResourceProperties(TaskEntity task, String properties) throws TaskNotFoundException {
    TaskService.State taskServiceState = new TaskService.State();
    taskServiceState.resourceProperties = properties;

    patchTaskService(task.getId(), taskServiceState);
  }

  private TaskService.State getTaskStateById(String taskId) {
    com.vmware.xenon.common.Operation result;
    try {
      result = dcpClient.get(TaskServiceFactory.SELF_LINK + "/" + taskId);
    } catch (DocumentNotFoundException documentNotFoundException) {
      return null;
    }

    if (result == null) {
      return null;
    }

    return result.getBody(TaskService.State.class);
  }

  private String convertStepOptionsToString(Map<String, String> stepOptions, TaskEntity taskEntity) {
    if (stepOptions != null && !stepOptions.isEmpty()) {
      try {
        return objectMapper.writeValueAsString(stepOptions);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            String.format("Error serializing step options: {%s} for task id {%s}",
                e.getMessage(), taskEntity.getId()));
      }
    }

    return null;
  }

  private StepEntity createStep(TaskEntity taskEntity, StepEntity.State state, List<BaseEntity> entities,
                                Operation operation, Map<String, String> stepOptions) throws TaskNotFoundException {
    TaskService.State task = getTaskStateById(taskEntity.getId());
    if (task.steps == null) {
      task.steps = new ArrayList<>();
    }

    TaskService.State.Step step = new TaskService.State.Step();

    step.sequence = taskEntity.getNextStepSequence();

    switch (state) {
      case QUEUED:
        step.state = TaskService.State.StepState.QUEUED;
        break;
      case STARTED:
        step.state = TaskService.State.StepState.STARTED;
        break;
      case ERROR:
        step.state = TaskService.State.StepState.ERROR;
        break;
      case COMPLETED:
        step.state = TaskService.State.StepState.COMPLETED;
        break;
      default:
        String errorMessage = String.format(
            "Unknown step state {%s} passed in createStep for task id {%s}",
            state, taskEntity.getId());
        logger.error(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    step.operation = operation.getOperation();
    Date currentTime = DateTime.now().toDate();
    step.queuedTime = currentTime;
    step.options = convertStepOptionsToString(stepOptions, taskEntity);

    if (StepEntity.State.COMPLETED.equals(state)) {
      step.startedTime = currentTime;
      step.endTime = currentTime;
    }

    if (entities != null) {
      step.resources = new ArrayList<>();
      for (BaseEntity entity : entities) {
        TaskService.State.StepResource stepResource = new TaskService.State.StepResource();
        stepResource.resourceId = entity.getId();
        stepResource.resourceKind = entity.getKind();
        step.resources.add(stepResource);
      }
    }

    task.steps.add(step);

    patchTaskService(taskEntity.getId(), task);

    StepEntity stepEntity = StepUtils.convertBackEndToMiddleEnd(taskEntity, step);
    stepEntity.setTask(taskEntity);
    taskEntity.addStep(stepEntity);

    //populate entity with transient values that are not persisted in dcp document.

    if (entities != null) {
      for (BaseEntity entity : entities) {
        stepEntity.addTransientResourceEntity(entity);
      }
    }

    logger.info("created step: {}", stepEntity);
    return stepEntity;
  }
}
