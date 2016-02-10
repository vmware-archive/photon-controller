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

package com.vmware.photon.controller.cloudstore;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/**
 * This class implements a Guice module for the deployer service.
 */
public class CloudStoreModule extends AbstractModule {

  private final CloudStoreConfig cloudStoreConfig;

  public CloudStoreModule(CloudStoreConfig cloudStoreConfig) {
    this.cloudStoreConfig = cloudStoreConfig;
  }

  @Override
  protected void configure() {
    bindConstant().annotatedWith(CloudStoreConfig.Bind.class).to(cloudStoreConfig.getBind());
    bindConstant().annotatedWith(CloudStoreConfig.RegistrationAddress.class)
        .to(cloudStoreConfig.getRegistrationAddress());
    bindConstant().annotatedWith(CloudStoreConfig.Port.class).to(cloudStoreConfig.getPort());
    bindConstant().annotatedWith(CloudStoreConfig.StoragePath.class).to(cloudStoreConfig.getStoragePath());
    bind(BuildInfo.class).toInstance(BuildInfo.get(CloudStoreModule.class));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(AgentControlClient.class, AgentControlClient.class)
        .build(AgentControlClientFactory.class));
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(
      ZookeeperServerSetFactory serverSetFactory,
      CloudStoreServerSetChangeListener cloudStoreServerSetChangeListener) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet("cloudstore", true);
    serverSet.addChangeListener(cloudStoreServerSetChangeListener);
    return serverSet;
  }
}
