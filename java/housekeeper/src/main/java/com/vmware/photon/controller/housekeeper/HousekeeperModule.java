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
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZkHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.housekeeper.dcp.DcpConfig;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;
import com.vmware.photon.controller.housekeeper.service.HousekeeperService;

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
    bindConstant().annotatedWith(Config.Bind.class).to(config.getBind());
    bindConstant().annotatedWith(Config.RegistrationAddress.class).to(config.getRegistrationAddress());
    bindConstant().annotatedWith(Config.Port.class).to(config.getPort());
    bind(DcpConfig.class).toInstance(config.getDcp());
    bindConstant().annotatedWith(DcpConfig.StoragePath.class).to(config.getDcp().getStoragePath());
    bind(BuildInfo.class).toInstance(BuildInfo.get(HousekeeperModule.class));

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
      HousekeeperDcpServiceHost host,
      DcpConfig dcpConfig,
      BuildInfo buildInfo) {
    return new HousekeeperService(serverSet, host, dcpConfig, buildInfo);
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet(CLOUDSTORE_SERVICE_NAME, true);
    return serverSet;
  }

  @Provides
  @Singleton
  public CloudStoreHelper getCloudStoreHelper(@CloudStoreServerSet ServerSet cloudStoreServerSet) {
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    return cloudStoreHelper;
  }

  @Provides
  @Singleton
  public HousekeeperDcpServiceHost getHousekeeperDcpServiceHost(
      CloudStoreHelper cloudStoreHelper,
      @Config.Bind String bind,
      @Config.Port int port,
      @DcpConfig.StoragePath String storagePath,
      HostClientFactory hostClientFactory,
      @ZkHostMonitor ZookeeperHostMonitor zkHostMonitor,
      ServiceConfigFactory serviceConfigFactory) throws Throwable {
    return new HousekeeperDcpServiceHost(cloudStoreHelper, bind, port, storagePath, hostClientFactory, zkHostMonitor,
        serviceConfigFactory);
  }
}
