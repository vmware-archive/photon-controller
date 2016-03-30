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
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.provisioner.xenon.entity.ComputeServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpLeaseServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DiskServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.task.StartSlingshotFactoryService;
import com.vmware.photon.controller.provisioner.xenon.task.StartSlingshotService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/**
 * Class to initialize a Xenon host for provisioner.
 */
@Singleton
public class ProvisionerXenonHost extends AbstractServiceHost implements XenonHostInfoProvider {
  private static final Logger logger = LoggerFactory.getLogger(ProvisionerXenonHost.class);
  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;
  private static final String REFERRER_PATH = "/slingshot-service-control";

  public static final Class[] FACTORY_SERVICES = {
      DhcpConfigurationServiceFactory.class,
      DhcpLeaseServiceFactory.class,
      DhcpSubnetServiceFactory.class,

      ComputeServiceFactory.class,
      DiskServiceFactory.class,

      StartSlingshotFactoryService.class,
      // Discovery
      RootNamespaceService.class,
  };

  @Inject
  public ProvisionerXenonHost(XenonConfig xenonConfig) throws Throwable {
    super(xenonConfig);
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);
    logger.info("Started factory services");

    this.addPrivilegedService(StartSlingshotService.class);

    return this;
  }

  @Override
  public boolean isReady() {
    return
        checkServiceAvailable(RootNamespaceService.SELF_LINK)
        && checkServiceAvailable(DhcpConfigurationServiceFactory.SELF_LINK)
        && checkServiceAvailable(DhcpLeaseServiceFactory.SELF_LINK)
        && checkServiceAvailable(DhcpSubnetServiceFactory.SELF_LINK)

        && checkServiceAvailable(ComputeServiceFactory.SELF_LINK)
        && checkServiceAvailable(DiskServiceFactory.SELF_LINK)
        && checkServiceAvailable(StartSlingshotFactoryService.SELF_LINK);
  }

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  public void startSlingshotService(Integer slingshotLogVerbosity, String logDirectory) throws Throwable {
    logger.info("Running startSlingshotService");
    try {
      StartSlingshotService.State state = new StartSlingshotService.State();
      state.httpPort = this.getPort() + 2;
      state.logDirectory = logDirectory;
      if (slingshotLogVerbosity != null && slingshotLogVerbosity > 0) {
        state.slingshotLogVLevel = slingshotLogVerbosity;
      }
      Operation post = Operation
          .createPost(UriUtils.buildUri(this, StartSlingshotFactoryService.SELF_LINK, null))
          .setBody(state);

      Operation operation = ServiceHostUtils.sendRequestAndWait(this, post, REFERRER_PATH);
      if (operation.getStatusCode() != Operation.STATUS_CODE_OK) {
        logger.info("Slingshot service start failed");
      }
      logger.info("Started Slingshot service {} ",
          operation.getBody(StartSlingshotService.State.class).documentSelfLink);
    } catch (Throwable t) {
      logger.error("Start Slingshot failed.", t);
    }
  }
}
