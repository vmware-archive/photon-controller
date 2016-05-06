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

import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.xenon.common.Service;

import com.google.inject.assistedinject.Assisted;

/**
 * Factory to create a {@link HealthChecker} for specified component.
 */
public interface HealthCheckHelperFactory {

  public HealthCheckHelper create(
      @Assisted final Service service,
      @Assisted final ContainersConfig.ContainerType containerType,
      @Assisted final String ipAddress);

  public HealthCheckHelper create(
      @Assisted final Service service,
      @Assisted final Integer port,
      @Assisted final String ipAddress);

}
