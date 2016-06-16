/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
package com.vmware.photon.controller.deployer.healthcheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Health checker to support multiple health check types for a single container.
 */
public class MultiTypeHealthChecker implements HealthChecker {
  private List<HealthChecker> healthCheckers = new ArrayList<>();

  public void addHealthChecker(HealthChecker healthChecker) {
    this.healthCheckers.add(healthChecker);
  }

  @Override
  public boolean isReady() {
    for (HealthChecker healthChecker : healthCheckers) {
      if (healthChecker.isReady() == false) {
        return false;
      }
    }

    return true;
  }
}
