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
import com.vmware.photon.controller.api.Step;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.db.dao.TaskDao;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.base.InfrastructureEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidQueryParamsException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
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
import java.util.List;

/**
 * Common task operations using SQL data store.
 */
@Singleton
public class TaskSqlBackend implements TaskBackend {

  private static final Logger logger = LoggerFactory.getLogger(TaskSqlBackend.class);

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final TaskDao taskDao;

  private final StepBackend stepBackend;

  private final EntityLockBackend entityLockBackend;

  @Inject
  public TaskSqlBackend(
      TaskDao taskDao, EntityLockBackend entityLockBackend,
      StepBackend stepBackend) {
    this.taskDao = taskDao;
    this.entityLockBackend = entityLockBackend;
    this.stepBackend = stepBackend;
  }

  public StepBackend getStepBackend() {
    return stepBackend;
  }

  @Transactional
  public Task getApiRepresentation(String id) throws TaskNotFoundException {
    return toApiRepresentation(findById(id));
  }

  @Transactional
  public Task getApiRepresentation(TaskEntity task) throws TaskNotFoundException {
    taskDao.merge(task);
    return toApiRepresentation(task);
  }

  @Transactional
  public List<Task> filter(String entityId, String entityKind, Optional<String> state)
      throws ExternalException {
    return this.filter(Optional.of(entityId), Optional.of(entityKind), state);
  }

  @Transactional
  public List<Task> filter(Optional<String> entityId, Optional<String> entityKind, Optional<String> state)
      throws ExternalException {
    List<TaskEntity> tasks = getEntityTasks(entityId, entityKind, state);

    List<Task> result = new ArrayList<>();

    for (TaskEntity task : tasks) {
      result.add(toApiRepresentation(task));
    }

    return result;
  }

  @Transactional
  public List<Task> filterInProject(String projectId, Optional<String> state, Optional<String> kind) {
    List<TaskEntity> tasks = taskDao.findInProject(projectId, state, kind);

    List<Task> result = new ArrayList<>();

    for (TaskEntity task : tasks) {
      result.add(toApiRepresentation(task));
    }

    return result;
  }

  @Transactional
  public TaskEntity createQueuedTask(BaseEntity entity, Operation operation) {
    TaskEntity task = new TaskEntity();
    task.setState(TaskEntity.State.QUEUED);
    task.setEntity(entity);
    task.setOperation(operation);
    task.setQueuedTime(DateTime.now().toDate());

    // auto-link infrastructure tasks to their project
    if (entity instanceof InfrastructureEntity) {
      InfrastructureEntity infrastructureEntity = (InfrastructureEntity) entity;
      String projectId = infrastructureEntity.getProjectId();
      task.setProjectId(projectId);
    }

    taskDao.create(task);
    logger.info("created task: {}", task);
    return task;
  }

  @Transactional
  public TaskEntity createCompletedTask(BaseEntity entity, Operation operation) {
    TaskEntity task = new TaskEntity();
    task.setState(TaskEntity.State.COMPLETED);
    task.setEntity(entity);
    task.setOperation(operation);
    task.setStartedTime(DateTime.now().toDate());
    task.setEndTime(task.getStartedTime());
    task.setQueuedTime(task.getStartedTime());

    // auto-link infrastructure tasks to their project
    if (entity instanceof InfrastructureEntity) {
      InfrastructureEntity infrastructureEntity = (InfrastructureEntity) entity;
      String projectId = infrastructureEntity.getProjectId();
      task.setProjectId(projectId);
    }

    taskDao.create(task);
    logger.info("created task: {}", task);
    return task;
  }

  /**
   * Marks task as started.
   *
   * @param task Task entity
   */
  @Transactional
  public void markTaskAsStarted(TaskEntity task) {
    logger.info("Task {} has been marked as STARTED", task.getId());
    task.setState(TaskEntity.State.STARTED);
    task.setStartedTime(DateTime.now().toDate());
    taskDao.update(task);
  }

  /**
   * Marks task as done.
   *
   * @param task Task entity
   */
  @Transactional
  public void markTaskAsDone(TaskEntity task) {
    clearTaskLocks(task);

    task.setState(TaskEntity.State.COMPLETED);
    task.setEndTime(DateTime.now().toDate());
    taskDao.update(task);
    logger.info("Task {} has been marked as COMPLETED", task.getId());
  }

  /**
   * Marks tasks as failed.
   *
   * @param task Task entity
   */
  @Transactional
  public void markTaskAsFailed(TaskEntity task) {
    clearTaskLocks(task);

    task.setState(TaskEntity.State.ERROR);
    task.setEndTime(DateTime.now().toDate());
    taskDao.update(task);
    logger.error("Task {} has been marked as ERROR", task.getId());
  }

  /**
   * Marks tasks as failed as well as all its steps.
   *
   * @param task Task entity.
   * @param t    The throwable exception for why this task failed.
   */
  @Transactional
  public void markAllStepsAsFailed(TaskEntity task, Throwable t)
      throws TaskNotFoundException {
    for (StepEntity step : task.getSteps()) {
      stepBackend.markStepAsFailed(step, t);
    }
    markTaskAsFailed(task);
  }

  @Transactional
  public void update(TaskEntity task) {
    taskDao.update(task);
  }

  @Transactional
  public List<TaskEntity> getEntityTasks(
      Optional<String> entityId, Optional<String> entityKind, Optional<String> state)
      throws InvalidQueryParamsException {
    List<TaskEntity> tasks;

    if (entityId.isPresent() && entityKind.isPresent() && state.isPresent()) {
      tasks = taskDao.findByEntityAndState(entityId.get(), entityKind.get().toLowerCase(), state.get().toUpperCase());
    } else if (entityId.isPresent() && entityKind.isPresent() && !state.isPresent()) {
      tasks = taskDao.findByEntity(entityId.get(), entityKind.get().toLowerCase());
    } else if (!entityId.isPresent() && !entityKind.isPresent() && state.isPresent()) {
      tasks = taskDao.findByState(state.get());
    } else if (!entityId.isPresent() && !entityKind.isPresent() && !state.isPresent()) {
      tasks = taskDao.findAll();
    } else {
      throw new InvalidQueryParamsException("Both entityId and entityKind params need to be specified.");
    }

    return tasks;
  }

  @Transactional
  public void delete(TaskEntity task) {
    logger.debug("Deleting task {}", task);
    taskDao.delete(task);
    logger.debug("Task {} is deleted", task);
  }

  @Transactional
  public void setTaskResourceProperties(TaskEntity task, String properties) {
    task.setResourceProperties(properties);
    update(task);
  }

  @Transactional
  public TaskEntity findById(String id) throws TaskNotFoundException {
    TaskEntity task = getById(id);

    if (task == null) {
      throw new TaskNotFoundException(id);
    }

    return task;
  }

  @Transactional
  public TaskEntity getById(String id) {
    return taskDao.findById(id).orNull();
  }

  private void clearTaskLocks(TaskEntity task) {
    for (StepEntity step : task.getSteps()) {
      entityLockBackend.clearLocks(step);
    }
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
      steps.add(stepBackend.toApiRepresentation(stepEntity));
    }
    task.setSteps(steps);

    return task;
  }
}
