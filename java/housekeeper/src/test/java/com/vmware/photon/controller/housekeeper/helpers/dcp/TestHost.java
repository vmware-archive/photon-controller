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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.housekeeper.zookeeper.ZookeeperHostMonitorProvider;

/**
 * Test helper used to test MicroServices in isolation.
 */
public class TestHost
    extends BasicServiceHost
    implements HostClientProvider, ZookeeperHostMonitorProvider, CloudStoreHelperProvider {

  private final HostClient hostClient;
  private final ZookeeperHostMonitor zookeeperHostMonitor;
  private final CloudStoreHelper cloudStoreHelper;

  private TestHost(HostClient hostClient, ZookeeperHostMonitor zookeeperHostMonitor,
                   CloudStoreHelper cloudStoreHelper) {
    super();
    this.hostClient = hostClient;
    this.zookeeperHostMonitor = zookeeperHostMonitor;
    this.cloudStoreHelper = cloudStoreHelper;
  }

  public static TestHost create(HostClient hostClient) throws Throwable {
    return create(hostClient, null);
  }

  public static TestHost create(HostClient hostClient,
                                ZookeeperHostMonitor zookeeperHostMonitor) throws Throwable {
    return create(hostClient, zookeeperHostMonitor, null);
  }

  public static TestHost create(HostClient hostClient,
                                ZookeeperHostMonitor zookeeperHostMonitor, CloudStoreHelper cloudStoreHelper)
      throws Throwable {
    TestHost host = new TestHost(hostClient, zookeeperHostMonitor, cloudStoreHelper);
    host.initialize();
    host.startWithCoreServices();
    return host;
  }

  @Override
  public HostClient getHostClient() {
    return hostClient;
  }

  @Override
  public ZookeeperHostMonitor getZookeeperHostMonitor() {
    return zookeeperHostMonitor;
  }

  @Override
  public CloudStoreHelper getCloudStoreHelper() {
    return cloudStoreHelper;
  }

}
