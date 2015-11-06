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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests leader election process for service cluster members.
 */
public class LeaderElectedServiceNodeTest extends BaseTestWithRealZookeeper {

  @Test
  public void testLeaderElection() throws Throwable {
    zkClient.start();
    zkClient2.start();

    try {
      InetSocketAddress server1 = InetSocketAddress.createUnresolved("192.168.1.1", 256);
      InetSocketAddress server2 = InetSocketAddress.createUnresolved("192.168.1.2", 512);

      final CountDownLatch finished = new CountDownLatch(2);
      final AtomicInteger nodeCounter = new AtomicInteger(0);

      ExecutorService executor = Executors.newCachedThreadPool();

      final ServiceNode node1 = new LeaderElectedServiceNode(zkClient, "baz", server1);
      final List<String> errors1 = new ArrayList<>();
      final ServiceNode node2 = new LeaderElectedServiceNode(zkClient2, "baz", server2);
      final List<String> errors2 = new ArrayList<>();

      executor.submit(new NodeRunner(node1, nodeCounter, errors1, finished));
      executor.submit(new NodeRunner(node2, nodeCounter, errors2, finished));

      assertTrue(finished.await(10, TimeUnit.SECONDS), "Timed out while finishing work");

      assertTrue(errors1.isEmpty(), "Errors for the first node: " + errors1);
      assertTrue(errors2.isEmpty(), "Errors for the second node: " + errors2);

    } finally {
      zkClient.close();
      zkClient2.close();
    }
  }

  private static class NodeRunner implements Runnable {
    private final ServiceNode node;
    private final AtomicInteger nodeCounter;
    private final List<String> errors;
    private final CountDownLatch finished;

    public NodeRunner(ServiceNode node, AtomicInteger nodeCounter,
                      List<String> errors, CountDownLatch finished) {
      this.node = node;
      this.nodeCounter = nodeCounter;
      this.errors = errors;
      this.finished = finished;
    }

    @Override
    public void run() {
      join();
      leave();
      finished.countDown();
    }

    private void join() {
      final CountDownLatch workDone = new CountDownLatch(1);
      ListenableFuture<ServiceNode.Lease> joined = node.join();

      Futures.addCallback(joined, new FutureCallback<ServiceNode.Lease>() {
        @Override
        public void onSuccess(ServiceNode.Lease lease) {
          if (nodeCounter.getAndIncrement() != 0) {
            errors.add("Not the only node when joining");
          }
          try {
            TimeUnit.MILLISECONDS.sleep(100); // "working"...
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          if (nodeCounter.decrementAndGet() != 0) {
            errors.add("Not the only node when leaving");
          }
          workDone.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          errors.add("Failed to join");
          workDone.countDown();
        }
      }, Executors.newSingleThreadExecutor());

      try {
        if (!workDone.await(5, TimeUnit.SECONDS)) {
          errors.add("Timed out waiting for node work to finish");
        }
      } catch (InterruptedException e) {
        errors.add("Interrupted while waiting for node work to finish");
      }
    }

    private void leave() {
      node.leave();
    }


  }
}
