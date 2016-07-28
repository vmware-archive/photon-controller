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

package com.vmware.photon.controller.api.frontend.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.util.Duration;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Root Scheduler configuration.
 */
public class RootSchedulerConfig {

  @Min(1)
  @JsonProperty("max_clients")
  private int maxClients = 128;

  @Min(1)
  @JsonProperty("max_waiters")
  private int maxWaiters = 1024;

  @NotNull
  @JsonProperty
  private Duration timeout = Duration.seconds(120);

  public int getMaxClients() {
    return maxClients;
  }

  public int getMaxWaiters() {
    return maxWaiters;
  }

  public Duration getTimeout() {
    return timeout;
  }
}
