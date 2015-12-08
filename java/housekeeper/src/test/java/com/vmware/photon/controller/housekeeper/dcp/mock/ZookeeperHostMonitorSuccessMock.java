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

import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;

import java.util.HashSet;
import java.util.Set;

/**
 * ZookeeperHostMonitor mock class.
 */
public class ZookeeperHostMonitorSuccessMock extends ZookeeperHostMonitor {
  public static final int DATASTORE_COUNT_DEFAULT = 10;
  public static final int HOST_COUNT_DEFAULT = 5;
  public static final int IMAGE_DATASTORE_COUNT_DEFAULT = 1;
  private int dataStoreCount;
  private int hostCount;
  private int imageDataStoreCount;
  private Set<Datastore> allDatastores;
  private Set<Datastore> imageDatastores;
  private Set<HostConfig> hosts;

  public ZookeeperHostMonitorSuccessMock() throws Exception {
    this(IMAGE_DATASTORE_COUNT_DEFAULT);
  }

  public ZookeeperHostMonitorSuccessMock(int imageDataStoreCount) throws Exception {

    this(imageDataStoreCount, HOST_COUNT_DEFAULT);
  }
  public ZookeeperHostMonitorSuccessMock(int imageDataStoreCount, int hostCount) throws Exception {
    this(imageDataStoreCount, hostCount, DATASTORE_COUNT_DEFAULT);
  }


  public ZookeeperHostMonitorSuccessMock(int imageDataStoreCount, int hostCount, int dataStoreCount) throws Exception {
    super(ZookeeperHostMonitorMockHelper.instance.zkClient, ZookeeperHostMonitorMockHelper.instance.pathCache,
        ZookeeperHostMonitorMockHelper.instance.executer);
    this.imageDataStoreCount = imageDataStoreCount;
    this.hostCount = hostCount;
    this.dataStoreCount = dataStoreCount;
    init();
  }

  @Override
  public synchronized Set<Datastore> getImageDatastores() {
    return imageDatastores;
  }

  @Override
  public synchronized Set<HostConfig> getHostsForDatastore(String datastoreId) {
    return hosts;
  }

  @Override
  public synchronized Set<Datastore> getAllDatastores() {
    return allDatastores;
  }

  private void init() {
    allDatastores = new HashSet<Datastore>();
    imageDatastores = new HashSet<Datastore>();
    for (int i = 0; i < dataStoreCount; i++) {
      Datastore datastore = new Datastore();
      if (i < imageDataStoreCount) {
        datastore.setId("image-datastore-id-" + i);
        datastore.setName("image-datastore-name-" + i);
        imageDatastores.add(datastore);
      } else {
        datastore.setName("vm-datastore-name-" + i);
        datastore.setId("vm-datastore-id-" + i);
      }

      allDatastores.add(datastore);
    }

    hosts = new HashSet<HostConfig>();
    for (int i = 0; i < hostCount; i++) {
      HostConfig host = new HostConfig();
      host.setAddress(new ServerAddress("192.168.0.1", 11111));
      hosts.add(host);
    }
  }
}
