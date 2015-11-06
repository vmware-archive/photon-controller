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

import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import static com.vmware.photon.controller.common.Constants.VOID;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Represents the membership of a service node: underlying znode along with its creation/deletion callbacks.
 */
public class ServiceNodeMembership {

  private static final Logger logger = LoggerFactory.getLogger(ServiceNodeMembership.class);

  private final CuratorFramework zkClient;
  private final InetSocketAddress address;

  private final String membershipNode;
  private final SettableFuture<Void> expirationPromise;

  public ServiceNodeMembership(CuratorFramework zkClient,
                               String serviceName,
                               InetSocketAddress address) {
    this.zkClient = zkClient;
    this.address = address;

    membershipNode = "/services/" + serviceName + "/node-" + UUID.randomUUID().toString();
    expirationPromise = SettableFuture.create();
  }

  /**
   * Creates a new membership node in Zookeeper and sets the provided future when creation is complete.
   *
   * @param leasePromise promise that gets fulfilled when node is created
   * @return a listenable future to get notified when the znode gets created
   * @throws Exception
   */
  public synchronized ListenableFuture<Void> create(final SettableFuture<ServiceNode.Lease> leasePromise)
      throws Exception {
    logger.debug("Creating membership node for {}", membershipNode);

    final SettableFuture<Void> future = SettableFuture.create();
    BackgroundCallback callback = new BackgroundCallback() {
      @Override
      public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
        if (event.getResultCode() == KeeperException.Code.OK.intValue()) {
          leasePromise.set(new ServiceNode.Lease() {
            @Override
            public ListenableFuture<Void> getExpirationFuture() {
              return expirationPromise;
            }
          });
          future.set(null);
        } else {
          logger.error("Failed to create node {}: {}", membershipNode, event.getResultCode());
          future.setException(KeeperException.create(KeeperException.Code.get(event.getResultCode())));
          leasePromise.setException(KeeperException.create(KeeperException.Code.get(event.getResultCode())));
        }
      }
    };

    zkClient
        .create()
        .creatingParentsIfNeeded()
        .withMode(CreateMode.EPHEMERAL)
        .inBackground(callback)
        .forPath(membershipNode, getSerializedAddress());
    return future;
  }

  public synchronized void delete(final SettableFuture<Void> donePromise) throws Exception {
    logger.debug("Deleting membership node for {}", membershipNode);

    BackgroundCallback callback = new BackgroundCallback() {
      @Override
      public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
        if (event.getResultCode() == KeeperException.Code.OK.intValue()) {
          donePromise.set(VOID);
          logger.debug("Membership node deleted");
        } else {
          logger.error("Error deleting the node {}: {}", membershipNode, event.getResultCode());
          donePromise.setException(KeeperException.create(KeeperException.Code.get(event.getResultCode())));
        }
      }
    };

    zkClient.delete().guaranteed().inBackground(callback).forPath(membershipNode);
  }

  public synchronized void expire() throws Exception {
    expirationPromise.set(VOID);
    zkClient.delete().guaranteed().inBackground().forPath(membershipNode);
  }

  private byte[] getSerializedAddress() {
    ServerAddress serverAddress = new ServerAddress(address.getHostString(), address.getPort());
    try {
      return new TSerializer().serialize(serverAddress);
    } catch (TException e) {
      throw new RuntimeException(e);
    }
  }
}
