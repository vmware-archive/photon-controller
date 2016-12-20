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

import com.vmware.photon.controller.api.model.ComponentInstance;
import com.vmware.photon.controller.status.gen.StatusType;

import java.util.Map;

/**
 * This class implements a builder for {@link ComponentInstance} object.
 */
public class ComponentInstanceBuilder {

  private String address;

  private StatusType status;

  private String message;

  private Map<String, String> stats;

  private String buildInfo;

  public ComponentInstanceBuilder address(String address) {
    this.address = address;
    return this;
  }

  public ComponentInstanceBuilder status(StatusType status) {
    this.status = status;
    return this;
  }

  public ComponentInstanceBuilder message(String message) {
    this.message = message;
    return this;
  }

  public ComponentInstanceBuilder stats(Map<String, String> stats) {
    this.stats = stats;
    return this;
  }

  public ComponentInstanceBuilder buildInfo(String buildInfo) {
    this.buildInfo = buildInfo;
    return this;
  }

  public ComponentInstance build() {
    ComponentInstance instance = new ComponentInstance();
    instance.setAddress(this.address);
    instance.setMessage(this.message);
    instance.setStats(this.stats);
    instance.setStatus(this.status);
    instance.setBuildInfo(this.buildInfo);
    return instance;
  }
}
