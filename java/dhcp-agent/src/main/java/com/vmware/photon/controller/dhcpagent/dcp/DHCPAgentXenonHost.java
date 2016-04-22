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

package com.vmware.photon.controller.dhcpagent.dcp;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.host.AbstractServiceHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to initialize a Xenon host for dhcp-agent.
 */
@Singleton
public class DHCPAgentXenonHost
    extends AbstractServiceHost
    implements XenonHostInfoProvider{

  private static final Logger logger = LoggerFactory.getLogger(DHCPAgentXenonHost.class);

  public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

  public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

  public static final Class[] FACTORY_SERVICES = {
      // Discovery
      RootNamespaceService.class,
  };

  private BuildInfo buildInfo;

  @Inject
  public DHCPAgentXenonHost(
      XenonConfig xenonConfig,
      BuildInfo buildInfo) throws Throwable {

    super(xenonConfig);
    this.buildInfo = buildInfo;
  }

  @Override
  public ServiceHost start() throws Throwable {
    super.start();

    /**
     * Xenon currently uses a garbage collection algorithm for its Lucene index searchers which
     * results in index searchers being closed while still in use by paginated queries. As a
     * temporary workaround until the issue is fixed on the framework side (v0.7.6), raise the
     * threshold at which index searcher garbage collection is triggered to limit the impact of
     * this issue.
     */
    LuceneDocumentIndexService.setSearcherCountThreshold(INDEX_SEARCHER_COUNT_THRESHOLD);

    this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
    startDefaultCoreServicesSynchronously();

    // Start all the factories
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

  @Override
  public Class[] getFactoryServices() {
    return FACTORY_SERVICES;
  }

  public BuildInfo getBuildInfo() {
    return this.buildInfo;
  }
}
