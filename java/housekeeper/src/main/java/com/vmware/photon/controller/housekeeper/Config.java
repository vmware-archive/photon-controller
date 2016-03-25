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

package com.vmware.photon.controller.housekeeper;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ZookeeperConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Range;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Housekeeper configuration.
 */
public class Config {

  @NotNull
  @Range(min = 0, max = 100)
  @JsonProperty("image_copy_batch_size")
  private int imageCopyBatchSize;

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
  private LoggingConfiguration logging;

  @Valid
  @NotNull
  private ZookeeperConfig zookeeper;

  public LoggingConfiguration getLogging() {
    return this.logging;
  }

  public ThriftConfig getThriftConfig() {
    return this.thriftConfig;
  }

  public XenonConfig getXenonConfig() {
    return this.xenonConfig;
  }

  public ZookeeperConfig getZookeeper() {
    return this.zookeeper;
  }
}
