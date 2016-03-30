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

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
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
    bind(ProvisionerConfig.class).toInstance(provisionerConfig);
    bind(XenonConfig.class).toInstance(provisionerConfig.getXenonConfig());
    bindConstant().annotatedWith(ProvisionerConfig.UsePhotonDHCP.class).to(provisionerConfig.getUsePhotonDHCP());
    bind(BuildInfo.class).toInstance(BuildInfo.get(ProvisionerConfig.class));

    install(new FactoryModuleBuilder()
        .implement(ServiceConfig.class, ServiceConfig.class)
        .build(ServiceConfigFactory.class));
  }
}
