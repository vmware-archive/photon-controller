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

package com.vmware.photon.controller.housekeeper.helpers;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.Config;
import com.vmware.photon.controller.housekeeper.HousekeeperServerSet;
import com.vmware.photon.controller.housekeeper.dcp.DcpConfig;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provides common test dependencies.
 */
public class TestHousekeeperModule extends AbstractModule {
  private final Config config;

  public TestHousekeeperModule(Config config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bindConstant().annotatedWith(Config.Bind.class).to(config.getBind());
    bindConstant().annotatedWith(Config.RegistrationAddress.class).to(config.getRegistrationAddress());
    bindConstant().annotatedWith(Config.Port.class).to(config.getPort());
    bind(DcpConfig.class).toInstance(config.getDcp());
    bindConstant().annotatedWith(DcpConfig.StoragePath.class).to(config.getDcp().getStoragePath());
    bind(BuildInfo.class).toInstance(BuildInfo.get(TestHousekeeperModule.class));

    bind(ScheduledExecutorService.class)
        .toInstance(Executors.newScheduledThreadPool(4));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));
  }

  @Provides
  @Singleton
  public HousekeeperDcpServiceHost getHousekeepDcpServiceHost(
      CloudStoreHelper cloudStoreHelper,
      @Config.Bind String bind,
      @Config.Port int port,
      @DcpConfig.StoragePath String storagePath,
      HostClientFactory hostClientFactory,
      ZookeeperHostMonitor zookeeperHostMonitor) throws Throwable {
    return spy(new HousekeeperDcpServiceHost(cloudStoreHelper, bind, port, storagePath, hostClientFactory,
        zookeeperHostMonitor));
  }

  @Provides
  @Singleton
  @HousekeeperServerSet
  public ServerSet getHousekeeperServerSet() {
    return spy(new ServerSet() {
      @Override
      public void addChangeListener(ChangeListener listener) {

      }

      @Override
      public void removeChangeListener(ChangeListener listener) {

      }

      @Override
      public void close() throws IOException {

      }

      @Override
      public Set<InetSocketAddress> getServers() {
        return new HashSet<>();
      }
    });
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet() {
    return spy(new ServerSet() {
      @Override
      public void addChangeListener(ChangeListener listener) {

      }

      @Override
      public void removeChangeListener(ChangeListener listener) {

      }

      @Override
      public void close() throws IOException {

      }

      @Override
      public Set<InetSocketAddress> getServers() {
        return new HashSet<>();
      }
    });
  }

  @Provides
  @Singleton
  public CloudStoreHelper getCloudStoreHelper(@CloudStoreServerSet ServerSet cloudStoreServerSet) {
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);
    return cloudStoreHelper;
  }

  @Provides
  ExecutorService provideExecutor() {
    return Executors.newFixedThreadPool(10);
  }
}
