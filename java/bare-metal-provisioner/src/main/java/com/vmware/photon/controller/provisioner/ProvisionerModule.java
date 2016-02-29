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

package com.vmware.photon.controller.provisioner;

import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

/**
 * This class implements a Guice module for the deployer service.
 */
public class ProvisionerModule extends AbstractModule {

  private final ProvisionerConfig provisionerConfig;

  public ProvisionerModule(ProvisionerConfig provisionerConfig) {
    this.provisionerConfig = provisionerConfig;
  }

  @Override
  protected void configure() {
    bindConstant().annotatedWith(ProvisionerConfig.Bind.class).to(provisionerConfig.getBind());
    bindConstant().annotatedWith(ProvisionerConfig.RegistrationAddress.class)
        .to(provisionerConfig.getRegistrationAddress());
    bindConstant().annotatedWith(ProvisionerConfig.Port.class).to(provisionerConfig.getPort());
    bindConstant().annotatedWith(ProvisionerConfig.StoragePath.class).to(provisionerConfig.getStoragePath());
    bind(BuildInfo.class).toInstance(BuildInfo.get(ProvisionerConfig.class));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(AgentControlClient.class, AgentControlClient.class)
        .build(AgentControlClientFactory.class));

    install(new FactoryModuleBuilder()
        .implement(ServiceConfig.class, ServiceConfig.class)
        .build(ServiceConfigFactory.class));

  }
}
