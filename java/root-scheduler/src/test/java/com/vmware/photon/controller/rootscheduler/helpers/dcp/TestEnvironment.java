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

package com.vmware.photon.controller.rootscheduler.helpers.dcp;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.dcp.SchedulerDcpHost;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.testng.Assert.assertTrue;

import java.io.File;

/**
 * This class implements a test host for Xenon micro-services.
 */
public class TestEnvironment extends MultiHostEnvironment<SchedulerDcpHost> {
  private static final Logger logger = LoggerFactory.getLogger(TestEnvironment.class);

  /**
   * Constructs a test environment object.
   *
   * @throws Throwable Throws an exception if any error is encountered.
   */
  private TestEnvironment(HostClientFactory hostClientFactory, Config config,
                          ConstraintChecker checker, XenonRestClient xenonRestClient,
                          int hostCount) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new SchedulerDcpHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new SchedulerDcpHost(xenonConfig, hostClientFactory, config, checker, xenonRestClient);

    }
  }

  /**
   * Create instance of TestEnvironment with specified count of hosts and starts all hosts.
   *
   * @return
   * @throws Throwable
   */
  public static TestEnvironment create(HostClientFactory hostClientFactory, Config config,
                                       ConstraintChecker checker, XenonRestClient xenonRestClient,
                                       int hostCount) throws Throwable {
    TestEnvironment testEnvironment = new TestEnvironment(
        hostClientFactory, config, checker, xenonRestClient, hostCount);
    testEnvironment.start();
    return testEnvironment;
  }
}
