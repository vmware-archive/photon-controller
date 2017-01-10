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

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Dynamic server set persisted in ZooKeeper. Every time server set changes it triggers
 * onServerAdded/onServerRemoved callback on subscribed clients, so clients can update
 * their view of available servers.
 */
public class ZookeeperServerSet implements ServerSet, PathChildrenCacheListener {

  private static final Logger logger = LoggerFactory.getLogger(ZookeeperServerSet.class);

  private final SetMultimap<InetSocketAddress, String> servers;
  private final Set<ChangeListener> listeners;
  private final PathChildrenCache childrenCache;
  private final ExecutorService executor;
  private final String serviceName;
  private final CuratorFramework zkClient;
  private final ZookeeperServerReader reader;

  private boolean needToPopulateServers;

  /**
   * Creates new ZK server backed by the children of a particular zNode for services.
   */
  @AssistedInject
  public ZookeeperServerSet(@ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory,
                            CuratorFramework zkClient,
                            @ServiceReader ZookeeperServerReader reader,
                            @Assisted("serviceName") String serviceName,
                            @Assisted boolean subscribeToUpdates) throws Exception {
    this.zkClient = zkClient;
    this.reader = reader;
    this.serviceName = serviceName;

    this.listeners = new HashSet<>();
    this.servers = HashMultimap.create();

    if (subscribeToUpdates) {
      needToPopulateServers = false;

      ThreadFactory threadFactory = new ThreadFactoryBuilder()
          .setNameFormat("ZkServerSetPathChildrenCache" + "-%d")
          .setDaemon(true)
          .build();
      executor = Executors.newSingleThreadExecutor(threadFactory);

      childrenCache = childrenCacheFactory.create(serviceName, executor);
      childrenCache.getListenable().addListener(this);
    } else {
      needToPopulateServers = true;
      executor = null;
      childrenCache = null;
    }
  }

  @Override
  public synchronized void addChangeListener(ChangeListener listener) {
    if (needToPopulateServers) {
      try {
        populateServers();
        needToPopulateServers = false;
      } catch (Exception e) {
        logger.warn("Error populating server list for service '" + serviceName + "'");
        throw new RuntimeException(e);
      }
    }

    for (InetSocketAddress address : servers.keySet()) {
      listener.onServerAdded(address);
    }

    listeners.add(listener);
  }

  @Override
  public synchronized void removeChangeListener(ChangeListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void close() throws IOException {
    if (childrenCache != null) {
      childrenCache.close();
    }
    if (executor != null) {
      executor.shutdown();
    }
  }

  @Override
  public synchronized void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
    logger.debug("Child event: {}", event);

    // We ignore CHILD_UPDATED events as server data is immutable
    if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {
      addServer(event.getData().getData(), event.getData().getPath());
    } else if (event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
      removeServer(event.getData().getData(), event.getData().getPath());
    }
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("serviceName", serviceName)
        .add("servers", servers)
        .toString();
  }

  @Override
  public Set<InetSocketAddress> getServers() {
    Set<InetSocketAddress> serverList = new HashSet<InetSocketAddress>();
    for (InetSocketAddress address : servers.keySet()) {
      serverList.add(address);
    }

    return serverList;
  }

  private void populateServers() throws Exception {
    String basePath = reader.basePath(serviceName);
    List<String> nodes = reader.nodePaths(zkClient, serviceName);
    for (String nodePath : nodes) {
      try {
        byte[] nodeData = zkClient.getData().forPath(nodePath);
        addServer(nodeData, nodePath);
      } catch (KeeperException.NoNodeException e) {
        // node went away in between getting a list and getting data, which is OK
      }
    }
  }

  private void addServer(byte[] data, String path) {
    if (data == null) {
      // clustered service member went away, but path still exists
      logger.debug("{} data == null", path);
      return;
    }
    InetSocketAddress address = reader.deserialize(data);

    Set<String> nodes = servers.get(address);
    boolean added = nodes.add(path);

    if (added && nodes.size() == 1) {
      // It's a first time we see a node with this address
      for (ChangeListener listener : listeners) {
        listener.onServerAdded(address);
      }
    }
  }

  private void removeServer(byte[] data, String path) {
    InetSocketAddress address = reader.deserialize(data);

    Set<String> nodes = servers.get(address);
    boolean removed = nodes.remove(path);

    if (removed && nodes.isEmpty()) {
      for (ChangeListener listener : listeners) {
        listener.onServerRemoved(address);
      }
    }
  }
}
