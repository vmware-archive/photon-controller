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

import com.google.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * The ZookeeperMissingHostMonitor watches hosts in /missing and triggers create, update and delete events
 * for registered listeners.The event monitor maintains a current view of the child
 * nodes in memory. Thus, some events that happen in the past will be lost for future registrations.
 * For example, if a a child node is updated, then a callback is registered, then
 * during the registration of the callback, addEvent will be triggered for every node that is
 * being tracked. No update or delete events will be triggered unless they happen after the
 * listener registration.
 */
public class ZookeeperMissingHostMonitor extends ZookeeperMonitor implements MissingHostMonitor {
  static final String ZK_MISSING_HOST_PATH = "missing";
  private static final Logger logger = LoggerFactory.getLogger(ZookeeperMissingHostMonitor.class);
  private final Set<ChangeListener> listeners;

  @Inject
  public ZookeeperMissingHostMonitor(CuratorFramework zkClient,
                                     @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory,
                                     ExecutorService executor) throws Exception {
    super(zkClient, ZK_MISSING_HOST_PATH, childrenCacheFactory, executor);
    this.listeners = new HashSet<>();
  }


  protected void onAdd(String hostId, byte[] data) {
    for (ChangeListener listener : listeners) {
      listener.onHostAdded(hostId);
    }
  }

  protected void onUpdate(String hostId, byte[] data) {
    //no-op
  }

  protected void onRemove(String hostId, byte[] data) {
    for (ChangeListener listener : listeners) {
      listener.onHostRemoved(hostId);
    }
  }

  /**
   * Trigger onHostAdded on the listener for all the current nodes
   * that are being tracked in currentNodes. To be called when a listener
   * is just added.
   */
  private void propagateCurrentNodes(ChangeListener listener) {
    for (ChildData child : childrenCache.getCurrentData()) {
      String hostId = ZKPaths.getNodeFromPath(child.getPath());
      listener.onHostAdded(hostId);
    }
  }

  @Override
  public synchronized void addChangeListener(ChangeListener listener) {
    propagateCurrentNodes(listener);
    this.listeners.add(listener);
  }

  @Override
  public synchronized void removeChangeListener(ChangeListener listener) {
    this.listeners.remove(listener);
  }

}
