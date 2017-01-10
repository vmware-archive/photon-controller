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

package com.vmware.photon.controller.common.zookeeper;

import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * A wrapper class that uses ZookeeperHostMonitor to back a static serverset.
 */
public class ZookeeperHostSet implements ServerSet {
  final ServerSet serverSet;

  @Inject
  ZookeeperHostSet(@ZkHostMonitor ZookeeperHostMonitor zkHostMonitor,
                   @Assisted("hostName") String hostName) throws Exception {
    this.serverSet = zkHostMonitor.createStaticServerSet(hostName);
  }

  public void addChangeListener(ChangeListener listener) {
    this.serverSet.addChangeListener(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    this.serverSet.removeChangeListener(listener);
  }

  public void close() throws IOException {
    this.serverSet.close();
  }

  @Override
  public Set<InetSocketAddress> getServers() {
    return this.serverSet.getServers();
  }
}
