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

package com.vmware.photon.controller.housekeeper.dcp.mock;

import com.vmware.photon.controller.common.zookeeper.PathChildrenCacheFactory;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.concurrent.ExecutorService;

/**
 * Helper used to instantiate the ZookeeperHostMonitor mock class.
 */
public class ZookeeperHostMonitorMockHelper {
  static ZookeeperHostMonitorMockHelper instance;

  static {
    try {
      instance = new ZookeeperHostMonitorMockHelper();
    } catch (Exception e) {
      instance = null;
    }
  }

  @Mock
  public PathChildrenCacheFactory pathCache;
  @Mock
  public CuratorFramework zkClient;
  @Mock
  public ExecutorService executer;
  private PathChildrenCache mockCache;
  @Mock
  private ListenerContainer<PathChildrenCacheListener> mockListener;

  public ZookeeperHostMonitorMockHelper() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockCache = mock(PathChildrenCache.class);
    when(pathCache.createPathCache(eq("/hosts"), any(ExecutorService.class))).thenReturn(mockCache);
    when(mockCache.getListenable()).thenReturn(mockListener);
  }
}
