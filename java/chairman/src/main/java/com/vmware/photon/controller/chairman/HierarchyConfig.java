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

package com.vmware.photon.controller.chairman;

import javax.validation.constraints.Min;

/**
 * Chairman hierarchy config (how often to scan hierarchy, how many schedulers per tier).
 */
@SuppressWarnings("UnusedDeclaration")
public class HierarchyConfig {
  @Min(1)
  private int maxTopTierSchedulers = 1024;

  @Min(1)
  private int maxMidTierSchedulers = 32;

  @Min(1)
  private int initialScanDelayMs = 120000;

  @Min(1)
  private int scanPeriodMs = 30000;

  @Min(1)
  private int hostConfigPoolSize = 10;

  private boolean enableScan = true;

  public int getMaxTopTierSchedulers() {
    return maxTopTierSchedulers;
  }

  public void setMaxTopTierSchedulers(int maxTopTierSchedulers) {
    this.maxTopTierSchedulers = maxTopTierSchedulers;
  }

  public int getMaxMidTierSchedulers() {
    return maxMidTierSchedulers;
  }

  public void setMaxMidTierSchedulers(int maxMidTierSchedulers) {
    this.maxMidTierSchedulers = maxMidTierSchedulers;
  }

  public int getInitialScanDelayMs() {
    return initialScanDelayMs;
  }

  public void setInitialScanDelayMs(int initialScanDelayMs) {
    this.initialScanDelayMs = initialScanDelayMs;
  }

  public int getScanPeriodMs() {
    return scanPeriodMs;
  }

  public void setScanPeriodMs(int scanPeriodMs) {
    this.scanPeriodMs = scanPeriodMs;
  }

  public int gethostConfigPoolSize() {
    return hostConfigPoolSize;
  }

  public void sethostConfigPoolSize(int poolSize) {
    this.hostConfigPoolSize = poolSize;
  }

  public boolean getEnableScan() {
    return enableScan;
  }

  public void setEnableScan(boolean enableScan) {
    this.enableScan = enableScan;
  }
}
