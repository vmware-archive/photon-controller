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
//import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestId;
import com.vmware.photon.controller.rootscheduler.interceptors.RequestIdInterceptor;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.matcher.Matchers;

/**
 * Root scheduler Guice module.
 */
public class RootSchedulerModule extends AbstractModule {

  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RequestId.class), new RequestIdInterceptor());
//    bind(BuildInfo.class).toInstance(BuildInfo.get(this.getClass()));

    install(new FactoryModuleBuilder()
        .implement(HostClient.class, HostClient.class)
        .build(HostClientFactory.class));
  }
}
