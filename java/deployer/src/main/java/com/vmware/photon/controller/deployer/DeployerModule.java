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

package com.vmware.photon.controller.deployer;

import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperServerSetFactory;
import com.vmware.photon.controller.deployer.service.client.AddHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ChangeHostModeTaskServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeploymentWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.DeprovisionHostWorkflowServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.HostServiceClientFactory;
import com.vmware.photon.controller.deployer.service.client.ValidateHostTaskServiceClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This class implements a Guice module for the deployer service.
 */
public class DeployerModule extends AbstractModule {
  private final DeployerConfig deployerConfig;

  public DeployerModule(DeployerConfig deployerConfig) {
    this.deployerConfig = deployerConfig;
  }

  @Override
  protected void configure() {
    bind(ScheduledExecutorService.class)
        .toInstance(Executors.newScheduledThreadPool(4));
  }

  @Provides
  @Singleton
  @CloudStoreServerSet
  public ServerSet getCloudStoreServerSet(ZookeeperServerSetFactory serverSetFactory) {
    ServerSet serverSet = serverSetFactory.createServiceServerSet(Constants.CLOUDSTORE_SERVICE_NAME, true);
    return serverSet;
  }

  @Provides
  @Singleton
  HostServiceClientFactory getHostServiceClientFactory(@CloudStoreServerSet ServerSet serverSet) {
    return new HostServiceClientFactory(serverSet);
  }

  @Provides
  @Singleton
  ValidateHostTaskServiceClientFactory getValidateHostTaskServiceClientFactory() {
    return new ValidateHostTaskServiceClientFactory();
  }

  @Provides
  @Singleton
  DeploymentWorkflowServiceClientFactory getDeplomentWorkflowServiceClientFactory() {
    return new DeploymentWorkflowServiceClientFactory(deployerConfig);
  }

  @Provides
  @Singleton
  AddHostWorkflowServiceClientFactory getAddCloudHostWorkflowServiceClientFactory() {
    return new AddHostWorkflowServiceClientFactory();
  }

  @Provides
  @Singleton
  DeprovisionHostWorkflowServiceClientFactory getDeprovisionHostWorkflowServiceClientFactory() {
    return new DeprovisionHostWorkflowServiceClientFactory();
  }

  @Provides
  @Singleton
  ChangeHostModeTaskServiceClientFactory getChangeHostModeTaskServiceClientFactory() {
    return new ChangeHostModeTaskServiceClientFactory();
  }
}
