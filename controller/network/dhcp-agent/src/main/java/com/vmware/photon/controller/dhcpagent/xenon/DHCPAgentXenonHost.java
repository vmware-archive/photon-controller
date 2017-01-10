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

package com.vmware.photon.controller.dhcpagent.xenon;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.provider.ListeningExecutorServiceProvider;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.photon.controller.dhcpagent.xenon.service.StatusService;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetConfigurationService;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Class to initialize a Xenon host for dhcp-agent.
 */
@Singleton
public class DHCPAgentXenonHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider, ListeningExecutorServiceProvider {


  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

  public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

  @SuppressWarnings("rawtypes")
  public static final Class[] FACTORY_SERVICES = {
      // Discovery
      RootNamespaceService.class,
  };

  private BuildInfo buildInfo;
  private final ListeningExecutorService listeningExecutorService;
  private DHCPDriver dhcpDriver;

  @Inject
  public DHCPAgentXenonHost(
      XenonConfig xenonConfig,
      BuildInfo buildInfo,
      ListeningExecutorService listeningExecutorService,
      DHCPDriver dhcpDriver) throws Throwable {
    super(xenonConfig);
    this.buildInfo = buildInfo;
    this.listeningExecutorService = listeningExecutorService;
    this.dhcpDriver = dhcpDriver;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
    super.startFactory(SubnetIPLeaseService.class, SubnetIPLeaseService::createFactory);
    super.startFactory(SubnetConfigurationService.class, SubnetConfigurationService::createFactory);
    ServiceHostUtils.startServices(this, FACTORY_SERVICES);

    // Start all special services
    ServiceHostUtils.startService(this, StatusService.class);

    return this;
  }

  @Override
  public boolean isReady() {
    return
        checkServiceAvailable(RootNamespaceService.SELF_LINK);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  @Override
  public BuildInfo getBuildInfo() {
    return this.buildInfo;
  }

  public DHCPDriver getDHCPDriver() {
    return this.dhcpDriver;
  }

  /**
   * This method gets the host-wide listening executor service instance.
   *
   * @return
   */
  @Override
  public ListeningExecutorService getListeningExecutorService() {
    return listeningExecutorService;
  }
}
