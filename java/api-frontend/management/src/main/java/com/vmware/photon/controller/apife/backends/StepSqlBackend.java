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
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.entities.base.BaseEntity;
import com.vmware.photon.controller.api.common.exceptions.external.TaskNotFoundException;
import com.vmware.photon.controller.apife.db.dao.StepDao;
import com.vmware.photon.controller.apife.entities.StepEntity;
import com.vmware.photon.controller.apife.entities.StepErrorEntity;
import com.vmware.photon.controller.apife.entities.StepWarningEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StepBackend is performing common step operations.
 */
@Singleton
public class StepSqlBackend implements StepBackend {

  private static final Logger logger = LoggerFactory.getLogger(StepSqlBackend.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final StepDao stepDao;

  @Inject
  public StepSqlBackend(StepDao stepDao) {
    this.stepDao = stepDao;
  }

  @Transactional
  public String getStepOption(StepEntity stepEntity, String key) {
    return getStepOptions(stepEntity).get(key);
  }

  @Transactional
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

  @Transactional
  public void update(StepEntity step) {
    stepDao.update(step);
  }

  @Transactional
  public StepEntity createQueuedStep(TaskEntity task, Operation operation) {
    return createStep(task, StepEntity.State.QUEUED, new ArrayList<BaseEntity>(), operation, null);
  }

  @Transactional
  public StepEntity createQueuedStep(TaskEntity task, BaseEntity entity, Operation operation) {
    return createStep(task, StepEntity.State.QUEUED, entity, operation, null);
  }

  @Transactional
  public StepEntity createQueuedStep(TaskEntity task, BaseEntity entity, Operation operation,
                                     Map<String, String> stepOptions) {
    return createStep(task, StepEntity.State.QUEUED, entity, operation, stepOptions);
  }

  @Transactional
  public StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities, Operation operation) {
    return createStep(task, StepEntity.State.QUEUED, entities, operation, null);
  }

  @Transactional
  public StepEntity createQueuedStep(TaskEntity task, List<BaseEntity> entities, Operation operation,
                                     Map<String, String> stepOptions) {
    return createStep(task, StepEntity.State.QUEUED, entities, operation, stepOptions);
  }

  @Transactional
  public StepEntity createCompletedStep(TaskEntity task, BaseEntity entity, Operation operation) {
    return createStep(task, StepEntity.State.COMPLETED, entity, operation, null);
  }

  /**
   * Marks step as started.
   *
   * @param step Step entity
   */
  @Transactional
  public void markStepAsStarted(StepEntity step) {
    logger.info("Step Id {} and Operation {} has been marked as STARTED", step.getId(), step.getOperation());
    step.setState(StepEntity.State.STARTED);
    step.setStartedTime(DateTime.now().toDate());
    stepDao.update(step);
  }

  /**
   * Marks step as done.
   *
   * @param step Step entity
   */
  @Transactional
  public void markStepAsDone(StepEntity step) {
    logger.info("Step Id {} and Operation {} has been marked as COMPLETED", step.getId(), step.getOperation());
    step.setState(StepEntity.State.COMPLETED);
    step.setEndTime(DateTime.now().toDate());
    stepDao.update(step);
  }

  /**
   * Marks step as failed.
   *
   * @param step Step entity
   * @param t    The throwable exception for why this task failed.
   */
  @Transactional
  public void markStepAsFailed(StepEntity step, Throwable t) {
    logger.error("Step {} has been marked as ERROR", step, t);

    step.setState(StepEntity.State.ERROR);
    step.addException(t);
    step.setEndTime(DateTime.now().toDate());
    stepDao.update(step);
  }

  /**
   * Add a warning into step.
   *
   * @param step Step entity
   * @param t    The throwable exception for warning.
   */
  @Transactional
  public void addWarning(StepEntity step, Throwable t) {
    logger.warn("Step {} has warning", step, t);

    step.addWarning(t);
    step.setEndTime(DateTime.now().toDate());
    stepDao.update(step);
  }

  /**
   * Add a warning into step.
   *
   * @param step        Step entity
   * @param warningList The list of throwable exceptions for warning.
   */
  @Transactional
  public void addWarnings(StepEntity step, List<Throwable> warningList) {
    for (Throwable t : warningList) {
      logger.warn("Step {} has warning", step, t);
      step.addWarning(t);
    }

    step.setEndTime(DateTime.now().toDate());
    stepDao.update(step);
  }

  @Transactional
  public StepEntity getStepByTaskIdAndOperation(String taskId, Operation operation) throws TaskNotFoundException {
    List<StepEntity> steps = stepDao.findByTaskIdAndOperation(taskId, operation);

    if (steps == null || steps.size() <= 0) {
      return null;
    }

    if (steps.size() > 1) {
      String errorMessage =
          String.format("More than one step with same operation found. taskId={%s}, operation= {%s}",
              taskId, operation.getOperation());
      throw new IllegalStateException(errorMessage);
    }

    return steps.get(0);
  }

  private StepEntity createStep(TaskEntity task, StepEntity.State state, BaseEntity entity, Operation operation,
                                Map<String, String> stepOptions) {
    StepEntity step = getStepEntity(task, state, operation, stepOptions);
    if (entity != null) {
      step.addResource(entity);
    }

    stepDao.create(step);
    logger.info("created step: {}", step);
    return step;
  }

  private StepEntity createStep(TaskEntity task, StepEntity.State state, List<BaseEntity> entities,
                                Operation operation, Map<String, String> stepOptions) {
    StepEntity step = getStepEntity(task, state, operation, stepOptions);
    if (entities != null && !entities.isEmpty()) {
      step.addResources(entities);
    }

    stepDao.create(step);
    logger.info("created step: {}", step);
    return step;
  }

  private StepEntity getStepEntity(TaskEntity task, StepEntity.State state, Operation operation,
                                   Map<String, String> stepOptions) {
    StepEntity step = new StepEntity();
    step.setSequence(task.getNextStepSequence());
    step.setState(state);
    step.setOperation(operation);
    step.setQueuedTime(DateTime.now().toDate());
    // auto-link step to their task
    step.setTask(task);
    task.addStep(step);

    if (stepOptions != null && !stepOptions.isEmpty()) {
      try {
        step.setOptions(objectMapper.writeValueAsString(stepOptions));
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(String.format("Error serializing step %s options: %s",
            step.getId(), e.getMessage()));
      }
    }

    if (StepEntity.State.COMPLETED.equals(state)) {
      step.setStartedTime(DateTime.now().toDate());
      step.setEndTime(DateTime.now().toDate());
    }
    return step;
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
}
