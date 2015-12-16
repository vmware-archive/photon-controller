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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestIdInterceptor;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.InMemoryConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.SchedulerService;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Root scheduler Guice module.
 */
public class RootSchedulerModule extends AbstractModule {

  private final Config config;

  public RootSchedulerModule(Config config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RequestId.class), new RequestIdInterceptor());

    bindConstant().annotatedWith(Config.Bind.class).to(config.getBind());
    bindConstant().annotatedWith(Config.RegistrationAddress.class).to(config.getRegistrationAddress());
    bindConstant().annotatedWith(Config.Port.class).to(config.getPort());
    bindConstant().annotatedWith(Config.StoragePath.class).to(config.getStoragePath());
    bind(BuildInfo.class).toInstance(BuildInfo.get(RootSchedulerModule.class));
    bind(Config.class).toInstance(config);
    config.initRootPlaceParams();

    bind(ScheduledExecutorService.class)
        .toInstance(Executors.newScheduledThreadPool(4));

    // threadpool for HeartbeatServerSet
    bind(Integer.class)
        .annotatedWith(Names.named("heartbeat_pool_size"))
        .toInstance(32);

    install(new FactoryModuleBuilder()
        .implement(ServerSet.class, StaticServerSet.class)
        .build(StaticServerSetFactory.class));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    bind(RootScheduler.Iface.class).to(SchedulerService.class);
    if (config.getConstraintChecker().equals("dcp")) {
      bind(ConstraintChecker.class).to(CloudStoreConstraintChecker.class);
    } else {
      bind(ConstraintChecker.class).to(InMemoryConstraintChecker.class);
    }

  }

  @Provides
  @Singleton
  public DcpRestClient getDcpRestClient(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet("cloudstore", true);
    DcpRestClient client = new DcpRestClient(serverSet, Executors.newFixedThreadPool(4));
    client.start();
    return client;
  }
}
