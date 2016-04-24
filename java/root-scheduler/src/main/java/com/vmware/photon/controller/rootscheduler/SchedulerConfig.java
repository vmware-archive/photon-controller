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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Root scheduler configuration.
 */
@SuppressWarnings("UnusedDeclaration")
public class SchedulerConfig {
  @Min(1000)
  @JsonProperty("place_timeout_ms")
  private long placeTimeoutMs = 60000;

  @Min(1)
  @Max(32)
  @JsonProperty("max_fan_out_count")
  private int maxFanoutCount = 4;

  @Min(0)
  @JsonProperty("utilization_transfer_ratio")
  private double utilizationTransferRatio = 9.0;

  public long getPlaceTimeoutMs() {
    return placeTimeoutMs;
  }

  public void setPlaceTimeoutMs(long placeTimeoutMs) {
    this.placeTimeoutMs = placeTimeoutMs;
  }

  public int getMaxFanoutCount() {
    return maxFanoutCount;
  }

  public void setMaxFanoutCount(int maxFanoutCount) {
    this.maxFanoutCount = maxFanoutCount;
  }

  public double getUtilizationTransferRatio() {
    return utilizationTransferRatio;
  }

  public void setUtilizationTransferRatio(double utilizationTransferRatio) {
    this.utilizationTransferRatio = utilizationTransferRatio;
  }
}
