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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Skeleton implementation of service cluster members backed by the children of ZooKeeper znode.
 */
public abstract class AbstractServiceNode implements ServiceNode {

  private static final Logger logger = LoggerFactory.getLogger(AbstractServiceNode.class);
  private final CuratorFramework zkClient;
  private final String serviceName;
  private final InetSocketAddress address;
  private volatile State state;
  private ServiceNodeMembership membership;

  /**
   * Service node backed by a particular znode children in Zookeeper. The convention is that the process
   * using AbstractServiceNode is the one trying to join/leave that cluster, as membership nodes are
   * ephemeral and once ZK connection goes away the node will get deleted by ZK.
   *
   * @param zkClient    Zookeeper client, supposed to be in 'started' state
   * @param serviceName Cluster membership is effectively represented by the children
   *                    of a "/services/{serviceName} znode.
   */
  protected AbstractServiceNode(CuratorFramework zkClient, String serviceName, InetSocketAddress address) {
    Preconditions.checkNotNull(zkClient, "Zookeeper client cannot be null");
    Preconditions.checkNotNull(serviceName, "Service name cannot be null");
    Preconditions.checkNotNull(address, "Address cannot be null");

    if (zkClient.getState() != CuratorFrameworkState.STARTED) {
      throw new IllegalArgumentException("Zookeeper client is not in 'started' state");
    }

    this.zkClient = zkClient;
    this.serviceName = serviceName;
    this.address = address;

    state = State.NOT_JOINED;

    ConnectionStateListener listener = new ConnectionStateListener() {
      @Override
      public void stateChanged(CuratorFramework client, ConnectionState newState) {
        handleConnectionStateChange(newState);
      }
    };

    zkClient.getConnectionStateListenable().addListener(listener);
  }

  /**
   * @return Join condition future. Node can only join a cluster once it's join condition future is successfully
   * fulfilled.
   */
  protected abstract ListenableFuture<Void> joinCondition();

  /**
   * Hook for any actions that need to be done after leaving the cluster.
   */
  protected abstract void cleanup();

  /**
   * Joins a cluster defined by child nodes of a Zookeeper znode. Returns a promise that gets fulfilled
   * when this node has finished joining the cluster. The promise returns a Lease object that client code
   * can use to get expiration promise (the one that gets fulfilled when this particular membership gets
   * recalled b/c of ZK connection loss).
   */
  @Override
  public synchronized ListenableFuture<Lease> join() {
    logger.debug("Attempting to join cluster: {}", this);

    if (state == State.JOINED) {
      return Futures.immediateFailedFuture(new IllegalStateException("Node has already joined"));
    }

    final SettableFuture<Lease> leasePromise = SettableFuture.create();

    try {
      Futures.addCallback(joinCondition(), new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
          state = State.JOINED;
          handleSuccessfulJoinCondition(leasePromise);
        }

        @Override
        public void onFailure(Throwable t) {
          leasePromise.setException(t);
        }
      });
    } catch (Exception e) {
      leasePromise.setException(e);
    }

    return leasePromise;
  }

  @Override
  public String toString() {
    return "AbstractServiceNode{" +
        "serviceName='" + serviceName + '\'' +
        ", address=" + address +
        ", state=" + state +
        '}';
  }

  @Override
  public synchronized void leave() {
    logger.debug("Attempting to leave cluster: {}", this);

    if (state == State.NOT_JOINED) {
      throw new IllegalStateException("Node is not currently joined");
    }
    state = State.NOT_JOINED;
    cleanup();
    zkClient.close();
    membership = null;
  }

  private synchronized void handleSuccessfulJoinCondition(SettableFuture<Lease> leasePromise) {
    try {
      // This is called asynchronously when join condition has been met,
      // so we need to re-check the intended state
      if (state == State.JOINED) {
        membership = new ServiceNodeMembership(zkClient, serviceName, address);
        Futures.addCallback(membership.create(leasePromise),
                            new FutureCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            logger.info("Created a znode for {} with value {}", serviceName, address);
          }

          @Override
          public void onFailure(Throwable t) {
            logger.info("Resetting the state to NOT_JOINED for {}", serviceName);
            cleanup();
            state = State.NOT_JOINED;
          }
        });
      } else {
        cleanup();
      }
    } catch (Exception e) {
      if (state == State.JOINED) {
        // Failed to create the znode. Reset the state to NOT_JOINED so that we
        // can retry calling join() again.
        state = State.NOT_JOINED;
      }
      leasePromise.setException(e);
    }
  }

  private synchronized void handleConnectionStateChange(ConnectionState newState) {
    switch (newState) {
      case SUSPENDED:
      case LOST:
        logger.warn("{} got disconnected from Zookeeper", this);

        if (state == State.JOINED) {
          state = State.NOT_JOINED;
          cleanup();

          if (membership != null) {
            try {
              membership.expire();
              membership = null;
            } catch (Exception e) {
              logger.error("Error expiring service node membership", e);
            }
          }
        }

        break;
    }
  }

  enum State {
    NOT_JOINED,
    JOINED,
  }

}
