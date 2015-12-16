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

import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.ConfigTest;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {
  public static Injector createInjector(String configFileResourcePath)
      throws BadConfigException {
    Config config = ConfigBuilder.build(Config.class,
        ConfigTest.class.getResource(configFileResourcePath).getPath());
    return Guice.createInjector(
        new ZookeeperModule(),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new TestRootSchedulerModule(config));
  }
}
