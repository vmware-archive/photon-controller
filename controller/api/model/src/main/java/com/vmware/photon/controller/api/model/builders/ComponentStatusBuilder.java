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

package com.vmware.photon.controller.api.model.builders;

import com.vmware.photon.controller.api.model.Component;
import com.vmware.photon.controller.api.model.ComponentInstance;
import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.status.gen.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class implements a builder for {@link ComponentStatus} object.
 */
public class ComponentStatusBuilder {

  private Component component;

  private StatusType status;

  private String message;

  private Map<String, String> stats;

  private String buildInfo;

  private List<ComponentInstance> instances = new ArrayList<>();

  public ComponentStatusBuilder component(Component component) {
    this.component = component;
    return this;
  }

  public ComponentStatusBuilder status(StatusType status) {
    this.status = status;
    return this;
  }

  public ComponentStatusBuilder message(String message) {
    this.message = message;
    return this;
  }

  public ComponentStatusBuilder stats(Map<String, String> stats) {
    this.stats = stats;
    return this;
  }

  public ComponentStatusBuilder instances(List<ComponentInstance> instances) {
    this.instances = instances;
    return this;
  }

  public ComponentStatusBuilder buildInfo(String buildInfo) {
    this.buildInfo = buildInfo;
    return this;
  }

  public ComponentStatus build() {
    ComponentStatus status = new ComponentStatus();
    status.setComponent(this.component);
    status.setMessage(this.message);
    status.setStats(this.stats);
    status.setStatus(this.status);
    status.setInstances(this.instances);
    status.setBuildInfo(this.buildInfo);
    return status;
  }
}
