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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.Range;

/**
 * Health check configuration (timeouts, period).
 */
@SuppressWarnings("UnusedDeclaration")
public class HealthCheckConfig {
  @Range(min = 1000, max = 120000)
  @JsonProperty("period_ms")
  private int periodMs = 5000;

  @Range(min = 1000, max = 120000)
  @JsonProperty("timeout_ms")
  private int timeoutMs = 10000;

  public int getPeriodMs() {
    return periodMs;
  }

  public void setPeriodMs(int periodMs) {
    this.periodMs = periodMs;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }
}
