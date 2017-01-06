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

package com.vmware.photon.controller.scheduler;

import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Root scheduler configuration.
 */
@SuppressWarnings("UnusedDeclaration")
public class SchedulerConfig {
  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  @Valid
  @NotNull
  private SchedulingConfig root = new SchedulingConfig();

  public XenonConfig getXenonConfig() {
    return this.xenonConfig;
  }

  public ZookeeperConfig getZookeeper() {
    return zookeeper;
  }

  public SchedulingConfig getRoot() {
    return root;
  }
}
