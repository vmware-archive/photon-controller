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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Service;

/**
 * Default implementation of {@link HealthCheckHelperFactory} interface.
 */
public class HealthCheckHelper {

  private final HealthChecker healthChecker;

  public HealthCheckHelper(
      final Service service,
      final ContainersConfig.ContainerType containerType,
      final String ipAddress,
      final boolean authEnabled) {

    switch (containerType) {
      case LoadBalancer:
        this.healthChecker = new HttpBasedHealthChecker(HostUtils.getApiClient(service));
        break;

      case PhotonControllerCore:
        String protocol = "http";
        if (authEnabled) {
          protocol = "https";
        }
        this.healthChecker = new HttpBasedHealthChecker
          (HostUtils.getApiClient(service, protocol + "://" + ipAddress + ":" + Constants.MANAGEMENT_API_PORT));
        break;

      case Lightwave:
        this.healthChecker = new LightwaveHealthChecker(ipAddress, Constants.LIGHTWAVE_PORT);
        break;

      default:
        this.healthChecker = () -> {
          ServiceUtils.logInfo(service, "Default HealthChecker for %s - will always return true", containerType);
          return true;
        };
        break;
    }
  }

  public HealthChecker getHealthChecker() {
    return healthChecker;
  }


}
