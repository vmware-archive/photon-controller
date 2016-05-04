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

package com.vmware.photon.controller.rootscheduler.helpers;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.ThriftConfig;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.service.CloudStoreConstraintChecker;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Provides common test dependencies.
 */
public class TestRootSchedulerModule extends AbstractModule {

  private final Config config;

  public TestRootSchedulerModule(Config config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(ThriftConfig.class).toInstance(config.getThriftConfig());
    bind(XenonConfig.class).toInstance(config.getXenonConfig());

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));
    bind(ConstraintChecker.class).to(CloudStoreConstraintChecker.class);
  }

  @Provides
  @Singleton
  public XenonRestClient getDcpRestClient(@CloudStoreServerSet ServerSet serverSet) {
    XenonRestClient client = new XenonRestClient(serverSet, Executors.newFixedThreadPool(4));
    client.start();
    return client;
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
}
