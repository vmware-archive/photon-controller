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

package com.vmware.photon.controller.dhcpagent;

import com.vmware.photon.controller.common.logging.LoggingConfiguration;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * This class implements configuration state for the dhcp-agent service.
 */
public class DHCPAgentConfig {

  @Valid
  @NotNull
  @JsonProperty("xenon")
  private XenonConfig xenonConfig;

  @Valid
  @NotNull
  private LoggingConfiguration logging = new LoggingConfiguration();

  public XenonConfig getXenonConfig() {
    return this.xenonConfig;
  }

  public LoggingConfiguration getLogging() {
    return checkNotNull(logging);
  }
}
