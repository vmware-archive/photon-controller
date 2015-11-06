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

  @Min(1000)
  @JsonProperty("find_timeout_ms")
  private long findTimeoutMs = 60000;

  @Max(1)
  @JsonProperty("fan_out_ratio")
  private double fanoutRatio = 0.15;

  @Min(1)
  @Max(32)
  @JsonProperty("max_fan_out_count")
  private int maxFanoutCount = 4;

  @Min(1)
  @Max(32)
  @JsonProperty("min_fan_out_count")
  private int minFanoutCount = 2;

  @Max(1)
  @JsonProperty("fast_place_response_timeout_ratio")
  private double fastPlaceResponseTimeoutRatio = 0.25;

  @Max(1)
  @JsonProperty("fast_place_response_ratio")
  private double fastPlaceResponseRatio = 0.5;

  @Min(1)
  @Max(32)
  @JsonProperty("fast_place_response_min_count")
  private int fastPlaceResponseMinCount = 2;

  @Min(0)
  @JsonProperty("utilization_transfer_ratio")
  private double utilizationTransferRatio = 9.0;

  public long getPlaceTimeoutMs() {
    return placeTimeoutMs;
  }

  public void setPlaceTimeoutMs(long placeTimeoutMs) {
    this.placeTimeoutMs = placeTimeoutMs;
  }

  public long getFindTimeoutMs() {
    return findTimeoutMs;
  }

  public void setFindTimeoutMs(long findTimeoutMs) {
    this.findTimeoutMs = findTimeoutMs;
  }

  public double getFanoutRatio() {
    return fanoutRatio;
  }

  public void setFanoutRatio(double fanoutRatio) {
    this.fanoutRatio = fanoutRatio;
  }

  public int getMaxFanoutCount() {
    return maxFanoutCount;
  }

  public void setMaxFanoutCount(int maxFanoutCount) {
    this.maxFanoutCount = maxFanoutCount;
  }

  public int getMinFanoutCount() {
    return minFanoutCount;
  }

  public void setMinFanoutCount(int minFanoutCount) {
    this.minFanoutCount = minFanoutCount;
  }

  public double getFastPlaceResponseTimeoutRatio() {
    return fastPlaceResponseTimeoutRatio;
  }

  public void setFastPlaceResponseTimeoutRatio(double fastPlaceResponseTimeoutRatio) {
    this.fastPlaceResponseTimeoutRatio = fastPlaceResponseTimeoutRatio;
  }

  public double getFastPlaceResponseRatio() {
    return fastPlaceResponseRatio;
  }

  public void setFastPlaceResponseRatio(double fastPlaceResponseRatio) {
    this.fastPlaceResponseRatio = fastPlaceResponseRatio;
  }

  public int getFastPlaceResponseMinCount() {
    return fastPlaceResponseMinCount;
  }

  public void setFastPlaceResponseMinCount(int fastPlaceResponseMinCount) {
    this.fastPlaceResponseMinCount = fastPlaceResponseMinCount;
  }

  public double getUtilizationTransferRatio() {
    return utilizationTransferRatio;
  }

  public void setUtilizationTransferRatio(double utilizationTransferRatio) {
    this.utilizationTransferRatio = utilizationTransferRatio;
  }
}
