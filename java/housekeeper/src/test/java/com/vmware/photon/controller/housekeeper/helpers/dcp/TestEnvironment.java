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

import com.vmware.dcp.common.ServiceHost;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.helpers.dcp.MultiHostEnvironment;
import com.vmware.photon.controller.common.dcp.scheduler.TaskSchedulerServiceStateBuilder;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.dcp.HousekeeperDcpServiceHost;

import org.apache.commons.io.FileUtils;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * TestMachine class hosting a DCP host.
 */
public class TestEnvironment extends MultiHostEnvironment<HousekeeperDcpServiceHost> {

  public TestEnvironment(CloudStoreHelper cloudStoreHelper, HostClientFactory hostClientFactory,
                         ZookeeperHostMonitor zookeeperHostMonitor, int hostCount) throws Throwable {
    assertTrue(hostCount > 0);
    hosts = new HousekeeperDcpServiceHost[hostCount];
    for (int i = 0; i < hosts.length; i++) {

      String sandbox = generateStorageSandboxPath();
      FileUtils.forceMkdir(new File(sandbox));

      hosts[i] = new HousekeeperDcpServiceHost(cloudStoreHelper, BIND_ADDRESS, -1,
          sandbox, hostClientFactory, zookeeperHostMonitor);
    }
  }

  /**
   * Get CloudStoreHelper created using one of the hosts in TestEnvironment.
   */
  public CloudStoreHelper getCloudStoreHelper() {
    ServiceHost host = this.getHosts()[0];
    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    return new CloudStoreHelper(serverSet);
  }

  /**
   * Create instance of TestEnvironment with specified count of hosts and start all hosts.
   *
   * @param hostClientFactory
   * @param hostCount
   * @return
   * @throws Throwable
   */
  public static TestEnvironment create(CloudStoreHelper cloudStoreHelper,
                                       HostClientFactory hostClientFactory,
                                       ZookeeperHostMonitor zookeeperHostMonitor,
                                       int hostCount) throws Throwable {
    TestEnvironment testEnvironment = new TestEnvironment(cloudStoreHelper, hostClientFactory,
        zookeeperHostMonitor, hostCount);
    testEnvironment.start();
    return testEnvironment;
  }

  /**
   * Start the DCP host.
   *
   * @throws Throwable
   */
  @Override
  public void start() throws Throwable {
    TaskSchedulerServiceStateBuilder.triggerInterval = TimeUnit.MILLISECONDS.toMicros(500);

    super.start();
  }

  /**
   * Get cleaner trigger service uri.
   */
  public String getTriggerCleanerServiceUri() {
    return hosts[0].getTriggerCleanerServiceUri();
  }

}
