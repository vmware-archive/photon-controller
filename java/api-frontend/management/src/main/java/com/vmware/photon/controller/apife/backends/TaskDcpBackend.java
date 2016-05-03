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
import com.vmware.photon.controller.api.Step;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepErrorBaseEntity;
import com.vmware.photon.controller.apife.entities.StepErrorEntity;
import com.vmware.photon.controller.apife.entities.StepResourceEntity;
import com.vmware.photon.controller.apife.entities.StepWarningEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskService;
import com.vmware.photon.controller.cloudstore.dcp.entity.TaskServiceFactory;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    return toApiRepresentation(findById(id));
  }

  @Override
  public Task getApiRepresentation(TaskEntity task) throws TaskNotFoundException {
    return toApiRepresentation(task);
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
    return toApiRepresentation(taskEntities);
  }

  @Override
  public ResourceList<Task> getTasksPage(String pageLink) throws PageExpiredException {
    ResourceList<TaskEntity> taskEntities = getEntityTasksPage(pageLink);
    return toApiRepresentation(taskEntities);
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
    TaskEntity task = convertToTaskEntity(createdState);
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
    TaskEntity task = convertToTaskEntity(createdState);
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
        TaskService.State.Step step = new TaskService.State.Step();
        fillStep(step, stepEntity);
        taskServiceState.steps.add(step);
      }
    }

    com.vmware.xenon.common.Operation result = dcpClient.post(TaskServiceFactory.SELF_LINK, taskServiceState);
    TaskService.State createdState = result.getBody(TaskService.State.class);
    TaskEntity task = convertToTaskEntity(createdState);
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

    TaskService.State task = convertToTask(taskEntity);
    patchTaskService(taskEntity.getId(), task);
  }

  @Override
  public void update(TaskEntity task) throws TaskNotFoundException {
    TaskService.State taskState = convertToTask(task);
    patchTaskService(task.getId(), taskState);
  }

  @Override
  public ResourceList<TaskEntity> getEntityTasks(Optional<String> entityId, Optional<String> entityKind,
                                                 Optional<String> state, Optional<Integer> pageSize)
      throws InvalidQueryParamsException {

    ResourceList<TaskService.State> tasksDocuments = getEntityDocuments(entityId, entityKind, state, pageSize);
    return getTaskEntitiesFromDocuments(tasksDocuments);
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

    return getTaskEntitiesFromDocuments(taskStates);
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

  private ResourceList<TaskEntity> getTaskEntitiesFromDocuments(ResourceList<TaskService.State> tasksDocuments) {

    ResourceList<TaskEntity> taskEntityList = new ResourceList<>();
    taskEntityList.setItems(tasksDocuments.getItems().stream()
        .map(d -> convertToTaskEntity(d))
        .collect(Collectors.toList())
    );
    taskEntityList.setNextPageLink(tasksDocuments.getNextPageLink());
    taskEntityList.setPreviousPageLink(tasksDocuments.getPreviousPageLink());

    return taskEntityList;
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
    TaskEntity task = getById(id);

    if (task == null) {
      throw new TaskNotFoundException(id);
    }

    return task;
  }

  @Override
  public Step toApiRepresentation(StepEntity stepEntity) {
    Step step = new Step();

    step.setSequence(stepEntity.getSequence());
    step.setQueuedTime(stepEntity.getQueuedTime());
    step.setStartedTime(stepEntity.getStartedTime());
    step.setEndTime(stepEntity.getEndTime());
    step.setOperation(stepEntity.getOperation().toString());
    step.setState(stepEntity.getState().toString());

    if (StringUtils.isNotBlank(stepEntity.getOptions())) {
      step.setOptions(getStepOptions(stepEntity));
    }

    for (StepErrorEntity error : stepEntity.getErrors()) {
      step.addError(error.toApiError());
    }

    for (StepWarningEntity warning : stepEntity.getWarnings()) {
      step.addWarning(warning.toApiError());
    }

    return step;
  }

  @Override
  public void update(StepEntity stepEntity) throws TaskNotFoundException {
    TaskService.State.Step step = new TaskService.State.Step();
    fillStep(step, stepEntity);
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
  public TaskEntity getById(String id) {
    TaskService.State taskState = getTaskStateById(id);
    if (taskState == null) {
      return null;
    }
    return convertToTaskEntity(taskState);
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

  private TaskEntity convertToTaskEntity(TaskService.State taskState) {
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
        logger.error(errorMessage);
        throw new IllegalStateException(errorMessage);
    }

    taskEntity.setSteps(new ArrayList<StepEntity>());

    if (taskState.steps != null) {
      for (TaskService.State.Step step : taskState.steps) {
        StepEntity stepEntity = convertToStepEntity(taskEntity, step);
        taskEntity.addStep(stepEntity);
      }
    }
    return taskEntity;
  }

  private StepEntity convertToStepEntity(TaskEntity taskEntity, TaskService.State.Step step) {
    StepEntity stepEntity = new StepEntity();
    stepEntity.setTask(taskEntity);
    stepEntity.setSequence(step.sequence);
    stepEntity.setOperation(Operation.parseOperation(step.operation));

    switch (step.state) {
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
            step.state,
            taskEntity.getId());
        logger.error(errorMessage);
        throw new IllegalStateException(errorMessage);
    }

    stepEntity.setStartedTime(step.startedTime);
    stepEntity.setQueuedTime(step.queuedTime);
    stepEntity.setEndTime(step.endTime);
    stepEntity.setOptions(step.options);

    if (step.errors != null) {
      for (TaskService.State.StepError error : step.errors) {
        StepErrorEntity stepErrorEntity = new StepErrorEntity();
        fillStepErrorBaseEntity(stepErrorEntity, error);
        stepEntity.getErrors().add(stepErrorEntity);
      }
    }

    if (step.warnings != null) {
      for (TaskService.State.StepError warning : step.warnings) {
        StepWarningEntity stepErrorWarning = new StepWarningEntity();
        fillStepErrorBaseEntity(stepErrorWarning, warning);
        stepEntity.getWarnings().add(stepErrorWarning);
      }
    }

    if (step.resources != null) {
      for (TaskService.State.StepResource stepResource : step.resources) {
        stepEntity.getResources().add(convertToStepResourceEntity(stepResource));
      }
    }

    return stepEntity;
  }

  private void fillStepErrorBaseEntity(
      StepErrorBaseEntity stepErrorBaseEntity, TaskService.State.StepError stepError) {
    stepErrorBaseEntity.setCode(stepError.code);
    stepErrorBaseEntity.setMessage(stepError.message);
    stepErrorBaseEntity.setData(stepError.data);
  }

  private StepResourceEntity convertToStepResourceEntity(TaskService.State.StepResource stepResource) {
    StepResourceEntity stepResourceEntity = new StepResourceEntity();
    stepResourceEntity.setEntityId(stepResource.resourceId);
    stepResourceEntity.setEntityKind(stepResource.resourceKind);
    return stepResourceEntity;
  }

  private TaskService.State convertToTask(TaskEntity taskEntity) {

    TaskService.State taskState = new TaskService.State();
    taskState.entityId = taskEntity.getEntityId();
    taskState.entityKind = taskEntity.getEntityKind();
    taskState.queuedTime = taskEntity.getQueuedTime();
    taskState.startedTime = taskEntity.getStartedTime();
    taskState.endTime = taskEntity.getEndTime();
    taskState.projectId = taskEntity.getProjectId();
    taskState.operation = taskEntity.getOperation().getOperation();

    switch (taskEntity.getState()) {
      case COMPLETED:
        taskState.state = TaskService.State.TaskState.COMPLETED;
        break;
      case STARTED:
        taskState.state = TaskService.State.TaskState.STARTED;
        break;
      case QUEUED:
        taskState.state = TaskService.State.TaskState.QUEUED;
        break;
      case ERROR:
        taskState.state = TaskService.State.TaskState.ERROR;
        break;
      default:
        String errorMessage = String.format(
            "Unknown task state found in taskEntity {%s}",
            taskEntity);
        logger.error(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    if (taskEntity.getSteps() == null || taskEntity.getSteps().size() <= 0) {
      return taskState;
    }

    taskState.steps = new ArrayList<>();

    for (StepEntity stepEntity : taskEntity.getSteps()) {
      TaskService.State.Step step = new TaskService.State.Step();
      fillStep(step, stepEntity);
      taskState.steps.add(step);
    }

    return taskState;
  }

  private void fillStep(TaskService.State.Step step, StepEntity stepEntity) {
    step.operation = stepEntity.getOperation().getOperation();
    step.sequence = stepEntity.getSequence();
    switch (stepEntity.getState()) {
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
            "Unknown step state {%s} found for task id {%s}",
            step.state,
            stepEntity.getTask().getId());
        logger.error(errorMessage);
        throw new IllegalStateException(errorMessage);
    }

    step.startedTime = stepEntity.getStartedTime();
    step.queuedTime = stepEntity.getQueuedTime();
    step.endTime = stepEntity.getEndTime();
    step.options = stepEntity.getOptions();

    if (stepEntity.getErrors() != null) {
      step.errors = new ArrayList<>();
      for (StepErrorBaseEntity stepErrorBaseEntity : stepEntity.getErrors()) {
        step.errors.add(convertToStepError(stepErrorBaseEntity));
      }
    }

    if (stepEntity.getWarnings() != null) {
      step.warnings = new ArrayList<>();
      for (StepErrorBaseEntity stepErrorBaseEntity : stepEntity.getWarnings()) {
        step.warnings.add(convertToStepError(stepErrorBaseEntity));
      }
    }

    if (stepEntity.getResources() != null) {
      step.resources = new ArrayList<>();
      for (StepResourceEntity stepResourceEntity : stepEntity.getResources()) {
        step.resources.add(convertToStepResource(stepResourceEntity));
      }
    }
  }

  private TaskService.State.StepError convertToStepError(StepErrorBaseEntity stepErrorBaseEntity) {
    TaskService.State.StepError stepError = new TaskService.State.StepError();
    stepError.code = stepErrorBaseEntity.getCode();
    stepError.message = stepErrorBaseEntity.getMessage();
    stepError.data = stepErrorBaseEntity.getData();
    return stepError;
  }

  private TaskService.State.StepResource convertToStepResource(StepResourceEntity stepResourceEntity) {
    TaskService.State.StepResource stepResource = new TaskService.State.StepResource();
    stepResource.resourceId = stepResourceEntity.getEntityId();
    stepResource.resourceKind = stepResourceEntity.getEntityKind();
    return stepResource;
  }

  private ResourceList<Task> toApiRepresentation(ResourceList<TaskEntity> taskEntities) {
    ResourceList<Task> result = new ResourceList<>();
    result.setItems(taskEntities.getItems().stream()
        .map(t -> toApiRepresentation(t))
        .collect(Collectors.toList())
    );
    result.setNextPageLink(taskEntities.getNextPageLink());
    result.setPreviousPageLink(taskEntities.getPreviousPageLink());

    return result;
  }

  private Task toApiRepresentation(TaskEntity taskEntity) {
    Task task = new Task();

    task.setId(taskEntity.getId());
    task.setQueuedTime(taskEntity.getQueuedTime());
    task.setStartedTime(taskEntity.getStartedTime());
    task.setEndTime(taskEntity.getEndTime());
    task.setOperation(taskEntity.getOperation().toString());
    task.setState(taskEntity.getState().toString());

    Task.Entity entity = new Task.Entity();
    entity.setId(taskEntity.getEntityId());
    entity.setKind(taskEntity.getEntityKind());
    task.setEntity(entity);

    if (StringUtils.isNotBlank(taskEntity.getResourceProperties())) {
      try {
        Object resourceProperties = objectMapper.readValue(
            taskEntity.getResourceProperties(),
            Object.class);
        task.setResourceProperties(resourceProperties);
      } catch (IOException e) {
        logger.error("Error deserializing taskEntity resourceProperties {}",
            taskEntity.getResourceProperties(), e);
        throw new IllegalArgumentException(
            String.format("Error deserializing taskEntity resourceProperties %s, error %s",
                taskEntity.getResourceProperties(), e.getMessage())
        );
      }
    }

    List<Step> steps = new ArrayList<>();
    Collections.sort(taskEntity.getSteps(), new Comparator<StepEntity>() {
      @Override
      public int compare(StepEntity s1, StepEntity s2) {
        return Integer.compare(s1.getSequence(), s2.getSequence());
      }
    });
    for (StepEntity stepEntity : taskEntity.getSteps()) {
      steps.add(toApiRepresentation(stepEntity));
    }
    task.setSteps(steps);

    return task;
  }

  private HashMap<String, String> getStepOptions(StepEntity stepEntity) {
    try {
      return objectMapper.readValue(stepEntity.getOptions(), HashMap.class);
    } catch (IOException e) {
      logger.error("Error deserializing step {} options", stepEntity.getId(), e);
      throw new IllegalArgumentException(String.format("Error deserializing step %s options: %s",
          stepEntity.getId(), e.getMessage()));
    }
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

    StepEntity stepEntity = convertToStepEntity(taskEntity, step);
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
