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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Scheduler info for introspection API.
 */
// TODO(vspivak): remove demo ware
// https://www.pivotaltracker.com/s/projects/715511/stories/55520834
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchedulerInfo {

  @JsonProperty
  private String id;

  @JsonProperty
  private String parent;

  @JsonProperty
  private List<String> schedulers;

  @JsonProperty
  private List<String> hosts;

  public List<String> getHosts() {
    return hosts;
  }

  public void setHosts(List<String> hosts) {
    this.hosts = hosts;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public List<String> getSchedulers() {
    return schedulers;
  }

  public void setSchedulers(List<String> schedulers) {
    this.schedulers = schedulers;
  }
}
