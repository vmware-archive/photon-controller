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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * The ZookeeperMonitor implements PathChildrenCache. Essentially, it is a view of all the zkPath
 * children. On add, edit and delete events the ZookeeperMonitor will propagate the event to either
 * onAdd, onUpdate or onRemove methods. Classes that extend this class should implement the onAdd, onUpdate
 * and onRemove methods to implement more specialized monitors, for example ZookeeperHostMonitor.
 */
public abstract class ZookeeperMonitor implements PathChildrenCacheListener {
  private static final Logger logger = LoggerFactory.getLogger(ZookeeperMonitor.class);

  protected final PathChildrenCache childrenCache;
  private final CuratorFramework zkClient;

  public ZookeeperMonitor(CuratorFramework zkClient,
                          String zkPath,
                          PathChildrenCacheFactory childrenCacheFactory,
                          ExecutorService executor) throws Exception {
    this.zkClient = zkClient;
    childrenCache = childrenCacheFactory.createPathCache(ZKPaths.makePath(zkPath, ""), executor);
    childrenCache.getListenable().addListener(this);
    childrenCache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
  }

  @Override
  public synchronized void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
    logger.debug("Child event: {}", event);

    if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
        event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED ||
        event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {

      String hostId = ZKPaths.getNodeFromPath(event.getData().getPath());
      byte[] data = event.getData().getData();

      if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {
        onAdd(hostId, data);
      } else if (event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
        onUpdate(hostId, data);
      } else if (event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
        onRemove(hostId, data);
      }
    }
  }

  /*
   * This method is called when a child node is created under zkPath
   */
  protected abstract void onAdd(String id, byte[] data);

  /*
   * This method is called when a child node is updated
   */
  protected abstract void onUpdate(String id, byte[] data);

  /*
   * This method is called when a child node is deleted.
   */
  protected abstract void onRemove(String id, byte[] data);
}
