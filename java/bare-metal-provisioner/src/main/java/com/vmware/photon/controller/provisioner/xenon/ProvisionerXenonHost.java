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

package com.vmware.photon.controller.provisioner.xenon;

import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.provisioner.ProvisionerConfig;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpLeaseServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.task.StartSlingshotFactoryService;
import com.vmware.photon.controller.provisioner.xenon.task.StartSlingshotService;
import com.vmware.xenon.common.ServiceHost;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

import java.nio.file.Paths;

/**
 * Class to initialize a Xenon host for provisioner.
 */
@Singleton
public class ProvisionerXenonHost extends ServiceHost implements XenonHostInfoProvider {
  private static final Logger logger = LoggerFactory.getLogger(ProvisionerXenonHost.class);
  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

  public static final Class[] FACTORY_SERVICES = {
      DhcpConfigurationServiceFactory.class,
      DhcpLeaseServiceFactory.class,
      DhcpSubnetServiceFactory.class,
      StartSlingshotFactoryService.class,
  };

  @Inject
  public ProvisionerXenonHost(
      @ProvisionerConfig.Bind String bindAddress,
      @ProvisionerConfig.Port int port,
      @ProvisionerConfig.StoragePath String storagePath
      ) throws Throwable {

    logger.info("Initializing XenonServer on port: {} path: {}", port, storagePath);
    ServiceHost.Arguments arguments = new ServiceHost.Arguments();
    arguments.port = port;
    arguments.bindAddress = bindAddress;
    arguments.sandbox = Paths.get(storagePath);
    this.initialize(arguments);
  }


  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    this.addPrivilegedService(StartSlingshotService.class);

    return this;
  }

  @Override
  public boolean isReady() {
    return
        checkServiceAvailable(DhcpConfigurationServiceFactory.SELF_LINK)
        && checkServiceAvailable(DhcpLeaseServiceFactory.SELF_LINK)
        && checkServiceAvailable(DhcpSubnetServiceFactory.SELF_LINK)

        && checkServiceAvailable(StartSlingshotFactoryService.SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

}
