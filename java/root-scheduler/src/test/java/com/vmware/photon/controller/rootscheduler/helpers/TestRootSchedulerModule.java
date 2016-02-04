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

import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.SchedulerDcpHost;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

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
    bindConstant().annotatedWith(Config.Bind.class).to(config.getBind());
    bindConstant().annotatedWith(Config.Port.class).to(config.getPort());
    bindConstant().annotatedWith(Config.StoragePath.class).to(config.getStoragePath());
  }

  @Provides
  @Singleton
  public SchedulerDcpHost createServer(
      @Config.Bind String bind,
      @Config.Port int port,
      @Config.StoragePath String storagePath) throws Throwable {
    return new SchedulerDcpHost(bind, port, storagePath);
  }
}
