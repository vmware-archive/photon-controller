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
import org.apache.curator.framework.recipes.cache.PathChildrenCache;

import java.util.concurrent.ExecutorService;

/**
 * Creates Thrift path children cache for a given service.
 */
public class PathChildrenCacheFactory {

  private final CuratorFramework zkClient;
  private final ZookeeperServerReader reader;

  @Inject
  public PathChildrenCacheFactory(CuratorFramework zkClient,
                                  ZookeeperServerReader reader) {
    this.zkClient = zkClient;
    this.reader = reader;
  }

  public PathChildrenCache create(String serviceName, ExecutorService executor) throws Exception {
    PathChildrenCache cache = new PathChildrenCache(zkClient, reader.basePath(serviceName), true, false, executor);
    cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
    return cache;
  }

  public PathChildrenCache createPathCache(String path, ExecutorService executor) throws Exception {
    PathChildrenCache cache = new PathChildrenCache(zkClient, path, true, false, executor);
    return cache;
  }

}
