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

package com.vmware.photon.controller.apife.commands;

import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.apife.DeployerServerSet;
import com.vmware.photon.controller.apife.config.ApiFeConfiguration;
import com.vmware.photon.controller.apife.config.ConfigurationUtils;
import com.vmware.photon.controller.apife.lib.ImageStoreFactory;
import com.vmware.photon.controller.apife.lib.VsphereIsoStore;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServicePathCacheFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import org.apache.curator.framework.CuratorFramework;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.concurrent.ExecutorService;

/**
 * The test module for Commands tests.
 */
public class CommandTestModule extends AbstractModule {
  @Override
  protected void configure() {
    try {
      ApiFeConfiguration config = ConfigurationUtils.parseConfiguration(CommandTestModule.class.getResource("/config" +
          ".yml").getPath());
      install(new ZookeeperModule(config.getZookeeper()));
      bindConstant().annotatedWith(Names.named("useVirtualNetwork")).to(config.useVirtualNetwork());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Provides
  @Singleton
  public ServiceConfig getServiceConfig(
      CuratorFramework zkClient,
      @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory)
      throws Exception {
    return new ServiceConfig(zkClient, childrenCacheFactory, "apife");
  }

  @Provides
  @Singleton
  ImageStoreFactory getImageStoreFactory() {
    return mock(ImageStoreFactory.class);
  }

  @Provides
  @Singleton
  VsphereIsoStore getIsoStore() {
    return mock(VsphereIsoStore.class);
  }

  @Provides
  @Singleton
  @BackendTaskExecutor
  public ExecutorService getBackendTaskExecutor() {
    return mock(ExecutorService.class);
  }

  @Provides
  @Singleton
  @DeployerServerSet
  public ServerSet getDeployerServerSet() {
    return mock(ServerSet.class);
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet() {
    return mock(ServerSet.class);
  }
}
