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

package com.vmware.photon.controller.api.frontend.entities;

import com.vmware.photon.controller.api.frontend.entities.base.BaseEntity;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Operation;

import com.google.common.base.Objects;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step entity.
 */
public class StepEntity extends BaseEntity {

  public static final String KIND = "step";
  private int sequence;

  private State state;
  private Operation operation;
  private String options;
  private Date startedTime;
  private Date queuedTime;
  private Date endTime;
  private TaskEntity task;
  private List<StepErrorEntity> errors = new ArrayList<>();
  private List<StepWarningEntity> warnings = new ArrayList<>();
  private List<StepResourceEntity> resources = new ArrayList<>();
  private List<BaseEntity> transientResourceEntities = new ArrayList<>();
  private Map<String, Object> transientResources = new HashMap<>();
  //Transient
  private boolean disabled = false;

  @Override
  public String getKind() {
    return KIND;
  }

  public int getSequence() {
    return sequence;
  }

  public void setSequence(int sequence) {
    this.sequence = sequence;
  }

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }

  public String getOptions() {
    return options;
  }

  public void setOptions(String options) {
    this.options = options;
  }

  public List<StepErrorEntity> getErrors() {
    return errors;
  }

  public void addException(Throwable t) {
    StepErrorEntity stepError = new StepErrorEntity();
    fillStepErrorEntity(stepError, t);
    errors.add(stepError);
  }

  public List<StepWarningEntity> getWarnings() {
    return warnings;
  }

  public void addWarning(Throwable t) {
    StepWarningEntity stepWarning = new StepWarningEntity();
    fillStepErrorEntity(stepWarning, t);
    warnings.add(stepWarning);
  }

  private void fillStepErrorEntity(StepErrorBaseEntity entity, Throwable t) {
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    ExternalException exception = ExternalException.launder(t);

    entity.setCode(exception.getErrorCode());
    entity.setMessage(exception.getMessage());
    entity.setData(exception.getData());

    entity.setStep(this);
  }

  public List<StepResourceEntity> getResources() {
    return resources;
  }

  public void addResource(BaseEntity entity) {
    addTransientResourceEntity(entity);
    StepResourceEntity stepResource = new StepResourceEntity();
    stepResource.setEntityId(entity.getId());
    stepResource.setEntityKind(entity.getKind());

    resources.add(stepResource);
    stepResource.setStep(this);
  }

  public List<BaseEntity> getTransientResourceEntities() {
    return transientResourceEntities;
  }

  public void setTransientResourceEntities(List<BaseEntity> transientResourceEntities) {
    this.transientResourceEntities = transientResourceEntities;
  }

  /**
   * Returns a list of entities referenced by step.
   *
   * @param kind The kind of resource to search for. It assumes all kinds if null is specified.
   * @return List of entities referenced by step.
   */
  public <T extends BaseEntity> List<T> getTransientResourceEntities(String kind) {
    List<T> entities = new ArrayList<>();
    for (BaseEntity resource : getTransientResourceEntities()) {
      if (kind == null || kind.equals(resource.getKind())) {
        entities.add((T) resource);
      }
    }

    return entities;
  }

  public void addTransientResourceEntity(BaseEntity entity) {
    transientResourceEntities.add(entity);
  }

  public void addResources(List<BaseEntity> entities) {
    for (BaseEntity entity : entities) {
      addResource(entity);
    }
  }

  public Object getTransientResource(String key) {
    return transientResources.get(key);
  }

  public void createOrUpdateTransientResource(String key, Object value) {
    transientResources.put(key, value);
  }

  public Date getStartedTime() {
    return startedTime;
  }

  public void setStartedTime(Date startedTime) {
    this.startedTime = startedTime;
  }

  public Date getQueuedTime() {
    return queuedTime;
  }

  public void setQueuedTime(Date queuedTime) {
    this.queuedTime = queuedTime;
  }

  public Date getEndTime() {
    return endTime;
  }

  public void setEndTime(Date endTime) {
    this.endTime = endTime;
  }

  public State getState() {
    return state;
  }

  public void setState(State state) {
    this.state = state;
  }

  public TaskEntity getTask() {
    return task;
  }

  public void setTask(TaskEntity task) {
    this.task = task;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
  }

  @Override
  protected Objects.ToStringHelper toStringHelper() {
    Objects.ToStringHelper result = super.toStringHelper()
        .add("state", state)
        .add("operation", operation)
        .add("startedTime", startedTime)
        .add("queuedTime", queuedTime)
        .add("endTime", endTime)
        .add("sequence", sequence);

    if (!getErrors().isEmpty()) {
      result.add("error", StringUtils.join(getErrors(), ';'));
    }

    return result;
  }

  /**
   * Step state.
   */
  public enum State {
    QUEUED,
    STARTED,
    ERROR,
    COMPLETED
  }
}
