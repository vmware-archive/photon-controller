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

package com.vmware.photon.controller.housekeeper;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Housekeeper Guice module.
 */
public class HousekeeperModule extends AbstractModule {

  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";

  private final Config config;

  public HousekeeperModule(Config config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(BuildInfo.class).toInstance(BuildInfo.get(HousekeeperModule.class));
    bind(ThriftConfig.class).toInstance(config.getThriftConfig());
    bind(XenonConfig.class).toInstance(config.getXenonConfig());

    bind(ScheduledExecutorService.class)
        .toInstance(Executors.newScheduledThreadPool(4));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(ServiceConfig.class, ServiceConfig.class)
        .build(ServiceConfigFactory.class));
  }

  @Provides
  @Singleton
  @HousekeeperServerSet
  public ServerSet getHousekeeperServerSet(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet("housekeeper", true);
    return serverSet;
  }

  @Provides
  @Singleton
  public HousekeeperService getHousekeeperService(
      @HousekeeperServerSet ServerSet serverSet,
      HousekeeperXenonServiceHost host,
      Config config,
      BuildInfo buildInfo) {
    return new HousekeeperService(serverSet, host, config, buildInfo);
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(ZookeeperServerSetFactory serverSetFactory) {
    return serverSetFactory.createServiceServerSet(CLOUDSTORE_SERVICE_NAME, true);
  }

  @Provides
  @Singleton
  public CloudStoreHelper getCloudStoreHelper(@CloudStoreServerSet ServerSet cloudStoreServerSet) {
    return new CloudStoreHelper(cloudStoreServerSet);
  }

  @Provides
  @Singleton
  public NsxClientFactory getNsxClientFactory() {
    return new NsxClientFactory();
  }
}
