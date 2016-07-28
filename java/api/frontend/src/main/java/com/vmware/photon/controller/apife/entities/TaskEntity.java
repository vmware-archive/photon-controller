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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.model.Operation;
import com.vmware.photon.controller.apife.entities.base.BaseEntity;
import com.vmware.photon.controller.apife.exceptions.external.StepNotFoundException;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Task entity.
 */
public class TaskEntity extends BaseEntity {

  public static final String KIND = "task";
  //Transient
  private List<BaseEntity> toBeLockedEntities = new ArrayList<>();
  //Transient
  private List<BaseEntity> lockedEntities = new ArrayList<>();
  //Transient
  AtomicInteger nextStepSequence = new AtomicInteger();
  private String entityId;
  private String entityKind;
  private State state;
  private Operation operation;
  private Date startedTime;
  private Date queuedTime;
  private Date endTime;

  private String resourceProperties;

  private String projectId;
  private List<StepEntity> steps = new ArrayList<>();

  public List<BaseEntity> getToBeLockedEntities() {
    return this.toBeLockedEntities;
  }

  public List<BaseEntity> getLockedEntityIds() {
    return this.lockedEntities;
  }

  public void setLockedEntityIds(List<BaseEntity> lockedEntities) {
    this.lockedEntities = lockedEntities;
  }

  @Override
  public String getKind() {
    return KIND;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getEntityKind() {
    return entityKind;
  }

  public void setEntityKind(String entityKind) {
    this.entityKind = entityKind;
  }

  public void setEntity(BaseEntity entity) {
    if (entity != null) {
      this.entityKind = entity.getKind();
      this.entityId = entity.getId();
    }
  }

  public Operation getOperation() {
    return operation;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
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

  public String getResourceProperties() {
    return resourceProperties;
  }

  public void setResourceProperties(String resourceProperties) {
    this.resourceProperties = resourceProperties;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public List<StepEntity> getSteps() {
    return steps;
  }

  public void setSteps(List<StepEntity> steps) {
    this.steps = steps;
    for (StepEntity step : this.steps) {
      step.setTask(this);
    }
  }

  public void addStep(StepEntity step) {
    this.steps.add(step);
    step.setTask(this);
  }

  public int getNextStepSequence() {
    return nextStepSequence.getAndIncrement();
  }

  public StepEntity findStep(Operation operation) throws StepNotFoundException {
    for (StepEntity step : getSteps()) {
      if (step.getOperation().equals(operation)) {
        return step;
      }
    }

    throw new StepNotFoundException(this, operation);
  }

  public boolean containsStep(Operation operation) {
    for (StepEntity step : getSteps()) {
      if (step.getOperation().equals(operation)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected Objects.ToStringHelper toStringHelper() {
    return super.toStringHelper()
        .add("entityId", entityId)
        .add("entityKind", entityKind)
        .add("state", state)
        .add("operation", operation)
        .add("startedTime", startedTime)
        .add("queuedTime", queuedTime)
        .add("endTime", endTime);
  }

  /**
   * Task state.
   */
  public enum State {
    QUEUED,
    STARTED,
    ERROR,
    COMPLETED
  }
}
