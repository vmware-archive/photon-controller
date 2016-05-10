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

package com.vmware.photon.controller.dhcpagent;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.photon.controller.dhcpagent.xenon.constants.DHCPAgentDefaults;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.AbstractModule;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Guice module for the deployer service.
 */
public class DHCPAgentModule extends AbstractModule {

  public static final String DHCPAGENT_SERVICE_NAME = "dhcpagent";

  /**
   * The blocking queue associated with the thread pool executor service
   * controls the rejection policy for new work items: a bounded queue, such as
   * an ArrayBlockingQueue, will cause new work items to be rejected (and thus
   * failed) when the queue length is reached.
   */
  private final ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(DHCPAgentDefaults.CORE_POOL_SIZE);
  private final DHCPAgentConfig dhcpAgentConfig;
  private final DHCPDriver dhcpDriver;

  public DHCPAgentModule(DHCPAgentConfig dhcpAgentConfig, DHCPDriver dhcpDriver) {
    this.dhcpAgentConfig = dhcpAgentConfig;
    this.dhcpDriver = dhcpDriver;
  }

  @Override
  protected void configure() {
    bind(BuildInfo.class).toInstance(BuildInfo.get(this.getClass()));
    bind(DHCPAgentConfig.class).toInstance(dhcpAgentConfig);
    bind(XenonConfig.class).toInstance(dhcpAgentConfig.getXenonConfig());
    bind(DHCPDriver.class).toInstance(dhcpDriver);
    bind(ListeningExecutorService.class)
            .toInstance(MoreExecutors.listeningDecorator(
                    new ThreadPoolExecutor(
                            DHCPAgentDefaults.CORE_POOL_SIZE,
                            DHCPAgentDefaults.MAXIMUM_POOL_SIZE,
                            DHCPAgentDefaults.KEEP_ALIVE_TIME,
                            TimeUnit.SECONDS,
                            blockingQueue)));
  }
}
