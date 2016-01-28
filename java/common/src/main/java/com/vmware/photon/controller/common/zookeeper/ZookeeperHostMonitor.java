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
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;

import com.google.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.utils.ZKPaths;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * The ZookeeperHostMonitor watches hosts in /hosts and triggers create, update and delete events
 * for registered listeners.The event monitor maintains a current view of the child
 * nodes in memory. Thus, some events that happen in the past will be lost for future registrations.
 * For example, if a a child node is updated, then a callback is registered, then
 * during the registration of the callback, addEvent will be triggered for every node that is
 * being tracked. No update or delete events will be triggered unless they happen after the
 * listener registration.
 */
public class ZookeeperHostMonitor extends ZookeeperMonitor implements HostMonitor {
  static final String ZK_HOST_PATH = "hosts";
  private static final Logger logger = LoggerFactory.getLogger(ZookeeperHostMonitor.class);
  private final Set<HostChangeListener> listeners;
  private TDeserializer deserializer = new TDeserializer();

  @Inject
  public ZookeeperHostMonitor(CuratorFramework zkClient,
                              @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory,
                              ExecutorService executor) throws Exception {
    super(zkClient, ZK_HOST_PATH, childrenCacheFactory, executor);
    this.listeners = new HashSet<>();
  }


  private HostConfig deserialize(String id, byte[] data) {
    HostConfig config = new HostConfig();
    try {
      deserializer.deserialize(config, data);
    } catch (TException e) {
      logger.error("Couldn't deserialize for node {}", id, e);
    }

    return config;
  }

  protected void onAdd(String hostId, byte[] data) {
    for (HostChangeListener listener : listeners) {
      listener.onHostAdded(hostId, deserialize(hostId, data));
    }
  }

  protected void onUpdate(String hostId, byte[] data) {
    for (HostChangeListener listener : listeners) {
      listener.onHostUpdated(hostId, deserialize(hostId, data));
    }
  }

  protected void onRemove(String hostId, byte[] data) {
    for (HostChangeListener listener : listeners) {
      listener.onHostRemoved(hostId, deserialize(hostId, data));
    }
  }

  /**
   * Trigger onHostAdded on the listener for all the current nodes
   * that are being tracked in currentNodes. To be called when a listener
   * is just added.
   */
  private void propagateCurrentNodes(HostChangeListener listener) {
    for (ChildData child : childrenCache.getCurrentData()) {
      String hostId = ZKPaths.getNodeFromPath(child.getPath());
      HostConfig config = deserialize(hostId, child.getData());
      listener.onHostAdded(hostId, config);
    }
  }

  public synchronized ServerSet createStaticServerSet(String hostId) throws HostNotFoundException {
    String hostPath = ZKPaths.makePath(ZK_HOST_PATH, hostId);

    ChildData data = childrenCache.getCurrentData(hostPath);

    if (data == null) {
      try {
        // Try to build the node explicitly after a cache miss
        childrenCache.rebuildNode(hostPath);
        data = childrenCache.getCurrentData(hostPath);

        if (data == null) {
          // Host still doesn't exist after the path rebuild
          throw new Exception();
        }
      } catch (Exception e) {
        logger.error("Couldn't find host id {}", hostId);
        throw new HostNotFoundException(
            String.format("Host id %s", hostId));
      }
    }

    HostConfig host = deserialize(hostId, data.getData());
    InetSocketAddress addr = new InetSocketAddress(host.getAddress().getHost(),
        host.getAddress().getPort());
    return new StaticServerSet(addr);
  }

  @Override
  public synchronized void addChangeListener(HostChangeListener listener) {
    propagateCurrentNodes(listener);
    this.listeners.add(listener);
  }

  @Override
  public synchronized void removeChangeListener(HostChangeListener listener) {
    this.listeners.remove(listener);
  }

  /**
   * This method will traverse all the registered hosts and return a list of all
   * the image datastores.
   *
   * @return a set of Datastore objects
   */
  public synchronized Set<Datastore> getImageDatastores() {
    Set<Datastore> imageDatastores = new HashSet<>();

    for (ChildData child : childrenCache.getCurrentData()) {
      String hostId = ZKPaths.getNodeFromPath(child.getPath());
      HostConfig config = deserialize(hostId, child.getData());

      for (String imageDs : config.getImage_datastore_ids()) {
        Datastore tmpDs = null;
        for (Datastore ds : config.getDatastores()) {
          if (ds.getId().equals(imageDs)) {
            tmpDs = ds;
            break;
          }
        }

        if (tmpDs == null) {
          logger.warn("Image datastore id {} is specified, but doesn't exist in datastores", imageDs);
        } else {
          imageDatastores.add(tmpDs);
        }
      }
    }

    return imageDatastores;
  }

  /**
   * Given a datastore uuid, this method returns all the registered hosts that have access to
   * that datastore.
   *
   * @return a set of HostConfig objects
   */
  public synchronized Set<HostConfig> getHostsForDatastore(String datastoreId) {
    Set<HostConfig> hostConfigs = new HashSet<>();

    for (ChildData child : childrenCache.getCurrentData()) {
      String hostId = ZKPaths.getNodeFromPath(child.getPath());
      HostConfig config = deserialize(hostId, child.getData());

      for (Datastore ds : config.getDatastores()) {
        if (ds.getId().equals(datastoreId)) {
          hostConfigs.add(config);
          break;
        }
      }
    }

    return hostConfigs;
  }

  /**
   * Given a host uuid, this method returns all the registered datastores that have access to
   * that host.
   *
   * @return a set of Datastore objects
   */
  public synchronized Set<Datastore> getDatastoresForHost(String hostId) {
    Set<Datastore> datastores = new HashSet<>();

    for (ChildData child : childrenCache.getCurrentData()) {
      if (ZKPaths.getNodeFromPath(child.getPath()).equals(hostId)) {
        HostConfig config = deserialize(hostId, child.getData());
        for (Datastore ds : config.getDatastores()) {
          datastores.add(ds);
        }
      }
    }

    return datastores;
  }


  /**
   * Return all datastores of all the registered hosts.
   *
   * @return a list of Datastores
   */
  public synchronized Set<Datastore> getAllDatastores() {
    Set<Datastore> datastores = new HashSet<>();

    for (ChildData child : childrenCache.getCurrentData()) {
      String hostId = ZKPaths.getNodeFromPath(child.getPath());
      HostConfig config = deserialize(hostId, child.getData());

      for (Datastore ds : config.getDatastores()) {
        datastores.add(ds);
      }
    }
    return datastores;
  }
}
