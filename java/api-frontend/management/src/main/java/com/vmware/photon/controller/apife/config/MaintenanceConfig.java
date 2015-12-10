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

package com.vmware.photon.controller.apife.config;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.util.Duration;

/**
 * Configuration for maintenance such as task expiration.
 */
public class MaintenanceConfig {

  @VisibleForTesting
  protected static final Duration DEFAULT_TASK_EXPIRATION_THRESHOLD = Duration.hours(5);
  private Duration taskExpirationThreshold = DEFAULT_TASK_EXPIRATION_THRESHOLD;
  @VisibleForTesting
  protected static final Duration DEFAULT_TASK_EXPIRATION_SCAN_INTERVAL = Duration.hours(3);
  private Duration taskExpirationScanInterval = DEFAULT_TASK_EXPIRATION_SCAN_INTERVAL;

  public Duration getTaskExpirationThreshold() {
    return taskExpirationThreshold;
  }

  public Duration getTaskExpirationScanInterval() {
    return taskExpirationScanInterval;
  }

}
