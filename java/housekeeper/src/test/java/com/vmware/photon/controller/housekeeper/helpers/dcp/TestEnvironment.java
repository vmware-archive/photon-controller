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

package com.vmware.photon.controller.housekeeper.helpers.dcp;

import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.MultiHostEnvironment;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperXenonServiceHost;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import org.apache.commons.io.FileUtils;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * TestMachine class hosting a DCP host.
 */
public class TestEnvironment extends MultiHostEnvironment<HousekeeperXenonServiceHost> {

  public TestEnvironment(CloudStoreHelper cloudStoreHelper,
                         HostClientFactory hostClientFactory,
                         ServiceConfigFactory serviceConfigFactory,
                         NsxClientFactory nsxClientFactory,
                         int hostCount) throws Throwable {

    assertTrue(hostCount > 0);
    hosts = new HousekeeperXenonServiceHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {
      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      XenonConfig xenonConfig = new XenonConfig();
      xenonConfig.setBindAddress(BIND_ADDRESS);
      xenonConfig.setPort(0);
      xenonConfig.setStoragePath(sandbox);

      hosts[i] = new HousekeeperXenonServiceHost(
          xenonConfig,
          cloudStoreHelper,
          hostClientFactory,
          serviceConfigFactory,
          nsxClientFactory);
    }

    TaskSchedulerServiceStateBuilder.triggerInterval = TimeUnit.MILLISECONDS.toMicros(500);
  }

  /**
   * Get cleaner trigger service uri.
   */
  public String getTriggerCleanerServiceUri() {
    return hosts[0].getTriggerCleanerServiceUri();
  }

  /**
   * Get ImageSeederService Sync trigger service uri.
   */
  public String getImageSeederSyncServiceUri() {
    return hosts[0].getImageSeederSyncServiceUri();
  }

  /**
   * This class implements a builder for {@link TestEnvironment} objects.
   */
  public static class Builder {
    CloudStoreHelper cloudStoreHelper;
    HostClientFactory hostClientFactory;
    ServiceConfigFactory serviceConfigFactory;
    NsxClientFactory nsxClientFactory;
    Integer hostCount;

    public Builder cloudStoreHelper(CloudStoreHelper helper) {
      this.cloudStoreHelper = helper;
      return this;
    }

    public Builder hostClientFactory(HostClientFactory factory) {
      this.hostClientFactory = factory;
      return this;
    }

    public Builder serviceConfigFactory(ServiceConfigFactory factory) {
      this.serviceConfigFactory = factory;
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

      ServiceConfigFactory serviceConfigFactory = this.serviceConfigFactory;
      if (serviceConfigFactory == null) {
        serviceConfigFactory = mock(ServiceConfigFactory.class);
      }

      NsxClientFactory nsxClientFactory = this.nsxClientFactory;
      if (nsxClientFactory == null) {
        nsxClientFactory = mock(NsxClientFactory.class);
      }

      TestEnvironment testEnvironment = new TestEnvironment(
          cloudStoreHelper,
          hostClientFactory,
          serviceConfigFactory,
          nsxClientFactory,
          this.hostCount);
      testEnvironment.start();

      return testEnvironment;
    }
  }
}
