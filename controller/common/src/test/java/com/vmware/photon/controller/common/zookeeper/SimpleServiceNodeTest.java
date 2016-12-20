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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.curator.test.KillSession;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertEqualsNoOrder;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests typical join/leave/expire flows for the simple service node.
 */
public class SimpleServiceNodeTest extends BaseTestWithRealZookeeper {

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testZookeeperShouldBeStarted() {
    new SimpleServiceNode(zkClient, "foo", new InetSocketAddress("192.168.1.1", 256));
  }

  @Test
  public void testJoin() throws Throwable {
    zkClient.start();
    ZookeeperServerReader reader = new ZookeeperServiceReader();

    try {
      InetSocketAddress server1 = new InetSocketAddress("192.168.1.1", 256);
      InetSocketAddress server2 = new InetSocketAddress("192.168.1.2", 512);

      final CountDownLatch done = new CountDownLatch(2);

      ServerSet serverSet = new ZookeeperServerSet(new PathChildrenCacheFactory(zkClient, reader), zkClient,
          reader, "baz", true);
      serverSet.addChangeListener(new ServerSet.ChangeListener() {
        @Override
        public void onServerAdded(InetSocketAddress address) {
          done.countDown();
        }

        @Override
        public void onServerRemoved(InetSocketAddress address) {
          done.countDown();
        }
      });

      ServiceNode node1 = new SimpleServiceNode(zkClient, "baz", server1);
      ServiceNode node2 = new SimpleServiceNode(zkClient, "baz", server2);

      joinNode(node1);
      joinNode(node2);

      assertTrue(done.await(2, TimeUnit.SECONDS), "Timed out waiting for server set callback");
      assertEquals(serverSet.getServers().size(), 2);

      assertEqualsNoOrder(serverSet.getServers().toArray(),
          new InetSocketAddress[]{server1, server2});
      serverSet.close();

    } finally {
      zkClient.close();
    }
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void testNoDoubleJoin() throws Throwable {
    zkClient.start();

    try {
      InetSocketAddress server = new InetSocketAddress("192.168.1.1", 256);
      ServiceNode node = new SimpleServiceNode(zkClient, "bar", server);

      joinNode(node);
      joinNode(node);
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testLeave() throws Throwable {
    zkClient.start();
    ZookeeperServerReader reader = new ZookeeperServiceReader();

    try {
      InetSocketAddress server = new InetSocketAddress("192.168.1.1", 256);
      ServiceNode node = new SimpleServiceNode(zkClient, "baz", server);

      final CountDownLatch added = new CountDownLatch(1);
      final CountDownLatch removed = new CountDownLatch(1);

      final List<InetSocketAddress> servers = new ArrayList<>();

      ServerSet serverSet = new ZookeeperServerSet(new PathChildrenCacheFactory(zkClient, reader), zkClient,
          reader, "baz", true);
      serverSet.addChangeListener(new ServerSet.ChangeListener() {
        @Override
        public void onServerAdded(InetSocketAddress address) {
          servers.add(address);
          added.countDown();
        }

        @Override
        public void onServerRemoved(InetSocketAddress address) {
          servers.remove(address);
          removed.countDown();
        }
      });

      joinNode(node);
      assertTrue(added.await(5, TimeUnit.SECONDS),
          "Timed out waiting for the node to be added to the server set");
      leaveNode(node);
      assertTrue(removed.await(5, TimeUnit.SECONDS),
          "Timed out waiting for the node to be removed from the server set");

      assertTrue(servers.isEmpty());
      serverSet.close();
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testNodeExpirationWhenZookeeperGoesDown() throws Throwable {
    zkClient.start();


    try {
      InetSocketAddress server = new InetSocketAddress("192.168.1.1", 256);
      ServiceNode node = new SimpleServiceNode(zkClient, "bar", server);

      final CountDownLatch joined = new CountDownLatch(1);
      final CountDownLatch expired = new CountDownLatch(1);

      ListenableFuture<ServiceNode.Lease> joining = node.join();

      Futures.addCallback(joining, new FutureCallback<ServiceNode.Lease>() {
        @Override
        public void onSuccess(ServiceNode.Lease lease) {
          Futures.addCallback(lease.getExpirationFuture(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              expired.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
            }
          });

          joined.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
        }
      });

      assertTrue(joined.await(5, TimeUnit.SECONDS), "Timed out while waiting for the node to join the cluster");
      KillSession.kill(zkClient.getZookeeperClient().getZooKeeper(), zookeeper.getConnectString());
      assertTrue(expired.await(5, TimeUnit.SECONDS), "Timed out while waiting for the lease to expire");

    } finally {
      zkClient.close();
    }
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void testNoDoubleLeave() throws Throwable {
    zkClient.start();

    try {
      InetSocketAddress server = new InetSocketAddress("192.168.1.1", 256);
      ServiceNode node = new SimpleServiceNode(zkClient, "bar", server);

      joinNode(node);
      leaveNode(node);
      leaveNode(node);
    } finally {
      zkClient.close();
    }
  }

  protected ServiceNode.Lease joinNode(ServiceNode node) throws Throwable {
    ListenableFuture<ServiceNode.Lease> joined = node.join();

    final CountDownLatch done = new CountDownLatch(1);
    final ServiceNode.Lease[] result = {null};
    final Throwable[] error = {null};

    Futures.addCallback(joined, new FutureCallback<ServiceNode.Lease>() {
      @Override
      public void onSuccess(ServiceNode.Lease lease) {
        result[0] = lease;
        done.countDown();
      }

      @Override
      public void onFailure(Throwable t) {
        error[0] = t;
        done.countDown();
      }
    });

    if (!done.await(5, TimeUnit.SECONDS)) {
      error[0] = new TimeoutException("Timed out waiting for the node to join");
    }
    if (error[0] != null) {
      throw error[0];
    }

    return result[0];
  }

  protected void leaveNode(ServiceNode node) throws Throwable {
    node.leave();
  }

}
