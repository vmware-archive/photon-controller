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

import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Service;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * Default implementation of {@link HealthCheckHelperFactory} interface.
 */
public class HealthCheckHelper {

  private final HealthChecker healthChecker;

  @Inject
  public HealthCheckHelper(
      @Assisted final Service service,
      @Assisted final ContainersConfig.ContainerType containerType,
      @Assisted final String ipAddress) {

    switch (containerType) {
      case Zookeeper:
        this.healthChecker = new ZookeeperHealthChecker(ipAddress,
            ServicePortConstants.ZOOKEEPER_PORT);
        break;

      case Deployer:
        this.healthChecker = new ThriftBasedHealthChecker(containerType, ipAddress,
            ServicePortConstants.DEPLOYER_PORT);
        break;

      case Housekeeper:
        this.healthChecker = new ThriftBasedHealthChecker(containerType, ipAddress,
            ServicePortConstants.HOUSEKEEPER_PORT);
        break;

      case RootScheduler:
        this.healthChecker = new XenonBasedHealthChecker(service, ipAddress,
            ServicePortConstants.ROOT_SCHEDULER_PORT);
        break;

      case ManagementApi:
        this.healthChecker = new HttpBasedHealthChecker(HostUtils.getApiClient(service));
        break;

      case LoadBalancer:
        this.healthChecker = new HttpBasedHealthChecker(HostUtils.getApiClient(service));
        break;

      case CloudStore:
        this.healthChecker = new XenonBasedHealthChecker(service, ipAddress, ServicePortConstants.CLOUD_STORE_PORT);
        break;

      case Lightwave:
        this.healthChecker = new LightwaveHealthChecker(ipAddress, ServicePortConstants.LIGHTWAVE_PORT);
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
