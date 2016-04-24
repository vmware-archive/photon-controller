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

package com.vmware.photon.controller.rootscheduler;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Range;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Root scheduler configuration.
 */
@SuppressWarnings("UnusedDeclaration")
public class Config {
  private String mode;

  private String constraintChecker;

  // Refresh interval for in-memory constraint checker cache in seconds.
  @NotNull
  @Range(min = 1, max = 600)
  private Integer refreshIntervalSec = 30;

  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  @JsonProperty("thrift")
  private ThriftConfig thriftConfig;

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper = new ZookeeperConfig();

  @Valid
  @NotNull
  private SchedulerConfig root = new SchedulerConfig();

  private PlaceParams rootPlaceParams;

  public String getMode() {
    return mode;
  }

  public String getConstraintChecker() {
    return constraintChecker;
  }

  public Integer getRefreshIntervalSec() {
    return refreshIntervalSec;
  }

  public XenonConfig getXenonConfig() {
    return this.xenonConfig;
  }

  public ThriftConfig getThriftConfig() {
    return this.thriftConfig;
  }

  public LoggingConfiguration getLogging() {
    return logging;
  }

  public ZookeeperConfig getZookeeper() {
    return zookeeper;
  }

  public SchedulerConfig getRoot() {
    return root;
  }

  public void initRootPlaceParams() {
    rootPlaceParams = new PlaceParams();
    rootPlaceParams.setTimeout(root.getPlaceTimeoutMs());
    rootPlaceParams.setMaxFanoutCount(root.getMaxFanoutCount());
  }

  public PlaceParams getRootPlaceParams() {
    return rootPlaceParams;
  }
}
