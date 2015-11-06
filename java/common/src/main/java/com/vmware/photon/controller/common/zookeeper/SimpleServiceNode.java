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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.curator.framework.CuratorFramework;

import java.net.InetSocketAddress;

/**
 * Service node that lets multiple services to join at once (for clustered services).
 */
public class SimpleServiceNode extends AbstractServiceNode {

  /**
   * Zookeeper-backed cluster member that only joins the cluster once it gets leader-elected.
   *
   * @param zkClient    Zookeeper client
   * @param serviceName znode name used for cluster membership
   * @param address     Our address
   */
  @Inject
  public SimpleServiceNode(CuratorFramework zkClient,
                           @Assisted String serviceName,
                           @Assisted InetSocketAddress address) {
    super(zkClient, serviceName, address);
  }

  @Override
  /**
   * @return This is a simple one that allows anyone to join anytime
   */
  protected ListenableFuture<Void> joinCondition() {
    return Futures.immediateFuture(null);
  }

  @Override
  protected void cleanup() {
  }
}
