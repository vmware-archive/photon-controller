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

import com.vmware.photon.controller.common.dcp.DcpHostInfoProvider;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * This class implements the Xenon service host object
 * for the Root-Scheduler service.
 */
@Singleton
public class SchedulerDcpHost
    extends ServiceHost implements DcpHostInfoProvider {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerDcpHost.class);
  public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

  public static final Class[] FACTORY_SERVICES = {
      RootNamespaceService.class

      // Add more Factory Services here.
  };

  @Inject
  public SchedulerDcpHost(
      @Config.Bind String bindAddress,
      @Config.Port int port,
      @Config.StoragePath String storagePath)
      throws Throwable {

    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port + 1;
    arguments.bindAddress = bindAddress;
    arguments.sandbox = Paths.get(storagePath);

    logger.info("Initializing SchedulerDcpHost on port: {} path: {}", arguments.port, storagePath);

    this.initialize(arguments);
  }

  /**
   * This method starts the default Xenon core services and the scheduler-specific factory service
   * factories.
   *
   * @return
   * @throws Throwable
   */
  @Override
  public ServiceHost start() throws Throwable {
    super.start();
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, getFactoryServices());
    return this;
  }

  /**
   * This method returns whether the services started above have come up.
   *
   * @return
   */
  @Override
  public boolean isReady() {
    try {
      return ServiceHostUtils.areServicesReady(
          this, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, getFactoryServices());
    } catch (Throwable t) {
      logger.debug("IsReady failed: {}", t);
      return false;
    }
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }
}
