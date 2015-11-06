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

package com.vmware.photon.controller.housekeeper.dcp.mock;

import com.vmware.photon.controller.host.gen.HostConfig;

import java.util.Set;

/**
 * ZookeeperHostMonitor mock class that raises an exception for getHostsForDatastore method calls.
 */
public class ZookeeperHostMonitorGetHostsForDatastoreErrorMock extends ZookeeperHostMonitorSuccessMock {
  public ZookeeperHostMonitorGetHostsForDatastoreErrorMock() throws Exception {
  }

  public ZookeeperHostMonitorGetHostsForDatastoreErrorMock(int dataStoreCount) throws Exception {
    super(ZookeeperHostMonitorSuccessMock.IMAGE_DATASTORE_COUNT_DEFAULT,
        ZookeeperHostMonitorSuccessMock.HOST_COUNT_DEFAULT,
        dataStoreCount);
  }

  @Override
  public synchronized Set<HostConfig> getHostsForDatastore(String datastoreId) {
    throw new RuntimeException("GetHostsForDatastore error");
  }
}
