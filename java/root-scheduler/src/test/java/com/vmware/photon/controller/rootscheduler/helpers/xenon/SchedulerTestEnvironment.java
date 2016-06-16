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
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.service.ConstraintChecker;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerServiceGroup;

import org.apache.commons.io.FileUtils;
import static org.testng.Assert.assertTrue;

import java.io.File;

/**
 * This class implements a test host for Xenon micro-services.
 */
public class SchedulerTestEnvironment extends MultiHostEnvironment<PhotonControllerXenonHost> {
  /**
   * Constructs a test environment object.
   *
   * @throws Throwable Throws an exception if any error is encountered.
   */
  private SchedulerTestEnvironment(HostClientFactory hostClientFactory, RootSchedulerConfig config,
                                   ConstraintChecker constraintChecker, CloudStoreHelper cloudStoreHelper,
                                   int hostCount) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new PhotonControllerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new PhotonControllerXenonHost(xenonConfig, hostClientFactory, null, null, cloudStoreHelper);
      SchedulerServiceGroup schedulerServiceGroup = new SchedulerServiceGroup(config.getRoot(), constraintChecker);
      hosts[i].registerScheduler(schedulerServiceGroup);
    }
  }

  /**
   * Create instance of SchedulerTestEnvironment with specified count of hosts and starts all hosts.
   *
   * @return
   * @throws Throwable
   */
  public static SchedulerTestEnvironment create(HostClientFactory hostClientFactory, RootSchedulerConfig config,
                                                ConstraintChecker checker,
                                                CloudStoreHelper cloudStoreHelper, int hostCount) throws Throwable {
    SchedulerTestEnvironment schedulerTestEnvironment = new SchedulerTestEnvironment(hostClientFactory,
        config, checker, cloudStoreHelper, hostCount);
    schedulerTestEnvironment.start();
    return schedulerTestEnvironment;
  }
}
