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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.base.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Task API representation.
 */
@ApiModel(
    value = "A class to track the status of asynchronous API operations.",
    description = "When a long-running task is triggered via the API, a Task will be returned. " +
        "This is how the status of the operation can be observed. " +
        "An operation is creating a VM, attaching a disk to a VM, etc. Because a task is associated with doing " +
        "something on a specific entity, it contains the type and id of that entity so that the entity that is being " +
        "worked on can be identified and queried.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Task extends Model {

  public static final String KIND = "task";

  @JsonProperty
  @ApiModelProperty(value = "Associated entity")
  private Entity entity;

  @JsonProperty
  @ApiModelProperty(value = "Task state", required = true, allowableValues = "QUEUED,STARTED,ERROR,COMPLETED")
  private String state;

  @JsonProperty
  @ApiModelProperty(value = "Steps associated with the task")
  private List<Step> steps = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty(value = "Operation performed by task")
  private String operation;

  @JsonProperty
  @ApiModelProperty(value = "Task start time", required = true)
  private Date startedTime;

  @JsonProperty
  @ApiModelProperty(value = "Task queueing time", required = true)
  private Date queuedTime;

  @JsonProperty
  @ApiModelProperty(value = "Task finish time")
  private Date endTime;

  @JsonProperty
  @ApiModelProperty(value = "This property may contain vm subnets' information and other task properties",
      required = false)
  private Object resourceProperties;

  @JsonIgnore
  public String getKind() {
    return KIND;
  }

  public Entity getEntity() {
    return entity;
  }

  public void setEntity(Entity entity) {
    this.entity = entity;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public List<Step> getSteps() {
    return steps;
  }

  public void setSteps(List<Step> steps) {
    this.steps = steps;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
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

  public Object getResourceProperties() {
    return resourceProperties;
  }

  public void setResourceProperties(Object resourceProperties) {
    this.resourceProperties = resourceProperties;
  }

  /**
   * Entity associated with task.
   */
  public static class Entity {

    @JsonProperty
    @ApiModelProperty(value = "Entity kind", required = true)
    private String kind;

    @JsonProperty
    @ApiModelProperty(value = "Entity id", required = true)
    private String id;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getKind() {
      return kind;
    }

    public void setKind(String kind) {
      this.kind = kind;
    }
  }
}
