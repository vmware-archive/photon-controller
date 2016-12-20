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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.status.gen.StatusType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Component Status Structure.
 */
@ApiModel(value = "This class represents the status of a component")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentStatus {

  @JsonProperty
  @ApiModelProperty(value = "Name of component", required = true)
  @NotNull
  private Component component;

  @JsonProperty
  @ApiModelProperty(value = "Status of component", required = true)
  @NotNull
  private StatusType status;

  @JsonProperty
  @ApiModelProperty(value = "Instances of component", required = true)
  @Valid
  @NotNull
  private List<ComponentInstance> instances = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty(value = "Detailed message regarding a component", required = false)
  private String message;

  @JsonProperty
  @ApiModelProperty(value = "Detailed stats of a component", required = false)
  private Map<String, String> stats;

  @JsonProperty
  @ApiModelProperty(value = "Detailed build information of a component instance", required = false)
  private String buildInfo;

  public ComponentStatus() {
  }

  public Component getComponent() {
    return component;
  }

  public void setComponent(Component component) {
    this.component = component;
  }

  public StatusType getStatus() {
    return status;
  }

  public void setStatus(StatusType status) {
    this.status = status;
  }

  public String getBuildInfo() {
    return buildInfo;
  }

  public void setBuildInfo(String buildInfo) {
    this.buildInfo = buildInfo;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, String> getStats() {
    return stats;
  }

  public void setStats(Map<String, String> stats) {
    this.stats = stats;
  }

  public List<ComponentInstance> getInstances() {
    return instances;
  }

  public void setInstances(List<ComponentInstance> instances) {
    this.instances = instances;
  }

  public synchronized void addInstance(ComponentInstance instance) {
    this.instances.add(instance);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ComponentStatus other = (ComponentStatus) o;

    return Objects.equals(status, other.status) &&
        Objects.equals(message, other.message) &&
        Objects.equals(stats, other.stats) &&
        Objects.equals(instances, other.instances) &&
        Objects.equals(component, other.component) &&
        Objects.equals(buildInfo, other.buildInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, message, stats, instances, component);
  }

  @Override
  public String toString() {
    return com.google.common.base.Objects.toStringHelper(this)
        .add("status", status)
        .add("message", message)
        .add("stats", stats)
        .add("component", component)
        .add("instances", instances)
        .add("buildInfo", buildInfo)
        .toString();
  }
}
