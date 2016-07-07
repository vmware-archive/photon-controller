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

package com.vmware.photon.controller.housekeeper.helpers.xenon;

import com.vmware.photon.controller.cloudstore.SystemConfig;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.housekeeper.xenon.HousekeeperServiceGroup;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import org.apache.commons.io.FileUtils;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * TestMachine class hosting a Xenon host.
 */
public class TestEnvironment extends MultiHostEnvironment<PhotonControllerXenonHost> {

  public TestEnvironment(CloudStoreHelper cloudStoreHelper,
                         HostClientFactory hostClientFactory,
                         NsxClientFactory nsxClientFactory,
                         int hostCount, boolean isBackgroudPaused) throws Throwable {

    assertTrue(hostCount > 0);
    hosts = new PhotonControllerXenonHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new PhotonControllerXenonHost(
          xenonConfig,
          hostClientFactory,
          null,
          nsxClientFactory,
          cloudStoreHelper);
      HousekeeperServiceGroup housekeeperServiceGroup = new HousekeeperServiceGroup();
      hosts[i].registerHousekeeper(housekeeperServiceGroup);
      SystemConfig.createInstance(hosts[i]);
    }

    TaskSchedulerServiceStateBuilder.triggerInterval = TimeUnit.MILLISECONDS.toMicros(500);
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {
    CloudStoreHelper cloudStoreHelper;
    HostClientFactory hostClientFactory;
    NsxClientFactory nsxClientFactory;
    Integer hostCount;
    boolean isBackgroundPaused;

    public Builder cloudStoreHelper(CloudStoreHelper helper) {
      this.cloudStoreHelper = helper;
      return this;
    }

    public Builder hostClientFactory(HostClientFactory factory) {
      this.hostClientFactory = factory;
      return this;
    }

    public Builder nsxClientFactory(NsxClientFactory factory) {
      this.nsxClientFactory = factory;
      return this;
    }

    public Builder hostCount(int hostCount) {
      this.hostCount = hostCount;
      return this;
    }

    public Builder isBackgroundPaused(boolean isBackgroundPause) {
      this.isBackgroundPaused = isBackgroundPause;
      return this;
    }

    /**
     * Create instance of TestEnvironment with specified count of hosts and start all hosts.
     *
     * @return
     * @throws Throwable
     */
    public TestEnvironment build() throws Throwable {
      if (null == this.hostCount) {
        throw new IllegalArgumentException("Host count is required");
      }

      CloudStoreHelper cloudStoreHelper = this.cloudStoreHelper;
      if (cloudStoreHelper == null) {
        cloudStoreHelper = mock(CloudStoreHelper.class);
      }

      HostClientFactory hostClientFactory = this.hostClientFactory;
      if (hostClientFactory == null) {
        hostClientFactory = mock(HostClientFactory.class);
      }

      NsxClientFactory nsxClientFactory = this.nsxClientFactory;
      if (nsxClientFactory == null) {
        nsxClientFactory = mock(NsxClientFactory.class);
      }

      TestEnvironment testEnvironment = new TestEnvironment(
          cloudStoreHelper,
          hostClientFactory,
          nsxClientFactory,
          this.hostCount,
          this.isBackgroundPaused);
      testEnvironment.start();

      return testEnvironment;
    }
  }
}
