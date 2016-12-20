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

import static com.vmware.photon.controller.common.Constants.VOID;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Node that only joins the service after winning a leader election.
 */
public class LeaderElectedServiceNode extends AbstractServiceNode {

  private static final Logger logger = LoggerFactory.getLogger(LeaderElectedServiceNode.class);
  private final ExecutorService executorService;
  private final CuratorFramework zkClient;
  private final String serviceName;
  private final InetSocketAddress address;
  private LeaderLatch leaderLatch;
  private Future<?> leaderWait;
  private CountDownLatch waitDone;

  @Inject
  public LeaderElectedServiceNode(CuratorFramework zkClient,
                                  @Assisted String serviceName,
                                  @Assisted InetSocketAddress address) {
    this(zkClient, serviceName, address, Executors.newSingleThreadExecutor());
  }

  /**
   * Zookeeper-backed cluster member that only joins the cluster once it gets leader-elected.
   *
   * @param zkClient        Zookeeper client
   * @param serviceName     znode name used for cluster membership
   * @param address         Our address
   * @param executorService Executor service to use for waiting on leader latch
   */
  public LeaderElectedServiceNode(CuratorFramework zkClient,
                                  String serviceName,
                                  InetSocketAddress address,
                                  ExecutorService executorService) {
    super(zkClient, serviceName, address);

    this.zkClient = zkClient;
    this.serviceName = serviceName;
    this.address = address;
    this.executorService = executorService;

    // TODO(olegs): current Curator LeaderLatch recipe has a race which I attempted to fix
    // in https://github.com/olegshaldybin/curator/commit/790b04079af181a6e59cce2f1ce67219beeabb80,
    // we should update curator if/when they accept the change or release their own change resolving this.
    // Without the fix leader election can get stuck if ZK goes down before we attempt to join the cluster and
    // comes back up in process of joining.
    leaderLatch = null;
  }

  @Override
  protected synchronized ListenableFuture<Void> joinCondition() {
    logger.debug("Creating the leader latch for {}", serviceName);
    leaderLatch = new LeaderLatch(zkClient, "/elections/" + serviceName, address.toString());

    try {
      leaderLatch.start();
    } catch (Exception e) {
      return Futures.immediateFailedFuture(e);
    }

    final SettableFuture<Void> promise = SettableFuture.create();
    waitDone = new CountDownLatch(1);

    leaderWait = executorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          leaderLatch.await();
          logger.debug("{} is a leader now", serviceName);
          promise.set(VOID);
        } catch (InterruptedException e) {
          logger.debug("Leader latch waiting for {} interrupted", serviceName);

          waitDone.countDown(); // See comment below to understand why we do that both in 'finally' and here

          promise.setException(new InterruptedException("Interrupted while waiting for leader election"));
          Thread.currentThread().interrupt();

        } catch (Exception e) {
          promise.setException(e);
        } finally {
          // There is an interesting race condition here:
          // 1. Leader elected service starts up and it's not a leader, so it blocks on leader latch acquisition;
          // 2. ZK goes away, acquisition gets interrupted;
          // 3. failure callback for the acquisition gets fired and (currently) runs System.exit(0);
          // 4. According to the Java language spec 'finally' block is NOT guaranteed to run when JVM is exiting BUT
          //    we have a shutdown hook that attempts to leave the service and triggers 'cleanup' method below, where
          //    it blocks indefinitely on waiting for 'waitDone' countdown latch IF 'finally' doesn't execute.
          //
          // TODO(olegs): maybe find a cleaner way orchestrate the shutdown?
          waitDone.countDown();
        }
      }
    });

    return promise;
  }

  @Override
  protected synchronized void cleanup() {
    logger.debug("Trying to cleanup {}", serviceName);
    leaderWait.cancel(true);

    try {
      waitDone.await();
      logger.debug("Closing the leader latch for {}", serviceName);
      leaderLatch.close();
    } catch (Exception e) {
      logger.warn("Error closing the leader latch for " + serviceName, e);
    }

    logger.debug("Finished cleanup");
  }

}
