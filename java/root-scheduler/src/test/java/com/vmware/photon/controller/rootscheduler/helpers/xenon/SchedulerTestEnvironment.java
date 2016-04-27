/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.rootscheduler.helpers.xenon;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;

import org.apache.commons.io.FileUtils;
import static org.testng.Assert.assertTrue;

import java.io.File;

/**
 * This class implements a test host for Xenon micro-services.
 */
public class SchedulerTestEnvironment extends MultiHostEnvironment<SchedulerXenonHost> {
  /**
   * Constructs a test environment object.
   *
   * @throws Throwable Throws an exception if any error is encountered.
   */
  private SchedulerTestEnvironment(HostClientFactory hostClientFactory, Config config,
                                   ConstraintChecker checker, XenonRestClient xenonRestClient,
                                   CloudStoreHelper cloudStoreHelper, int hostCount) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new SchedulerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new SchedulerXenonHost(xenonConfig, hostClientFactory, config, checker, xenonRestClient,
          cloudStoreHelper);

    }
  }

  /**
   * Create instance of SchedulerTestEnvironment with specified count of hosts and starts all hosts.
   *
   * @return
   * @throws Throwable
   */
  public static SchedulerTestEnvironment create(HostClientFactory hostClientFactory, Config config,
                                                ConstraintChecker checker, XenonRestClient xenonRestClient,
                                                CloudStoreHelper cloudStoreHelper, int hostCount) throws Throwable {
    SchedulerTestEnvironment schedulerTestEnvironment = new SchedulerTestEnvironment(
        hostClientFactory, config, checker, xenonRestClient, cloudStoreHelper, hostCount);
    schedulerTestEnvironment.start();
    return schedulerTestEnvironment;
  }
}
