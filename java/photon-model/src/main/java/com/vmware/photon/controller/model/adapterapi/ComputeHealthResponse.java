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

package com.vmware.photon.controller.model.adapterapi;

/**
 * Defines the response body for getting health status of a Compute instance.
 */
public class ComputeHealthResponse {

  /**
   * Compute Health State.
   */
  public enum ComputeHealthState {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY
  }

  /**
   * Health state.
   */
  public ComputeHealthState healthState;

  /**
   * CPU count.
   */
  public long cpuCount;

  /**
   * CPU utilization percent.
   */
  public double cpuUtilizationPct;

  /**
   * CPU utilized Mhz.
   */
  public long cpuUtilizationMhz;

  /**
   * CPU total Mhz.
   */
  public long cpuTotalMhz;

  /**
   * Total memory.
   */
  public long totalMemoryBytes;

  /**
   * Used memory.
   */
  public long usedMemoryBytes;

  /**
   * Memory utilization percent.
   */
  public double memoryUtilizationPct;
}
