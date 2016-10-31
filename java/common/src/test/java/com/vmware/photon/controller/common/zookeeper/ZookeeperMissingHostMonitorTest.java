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
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.data.Stat;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Tests {@link ZookeeperMissingHostMonitorTest}.
 */
public class ZookeeperMissingHostMonitorTest {

  @Mock
  private PathChildrenCacheFactory pathCache;

  @Mock
  private PathChildrenCache mockCache;

  @Mock
  private ListenerContainer<PathChildrenCacheListener> mockListener;

  @Mock
  private CuratorFramework zkClient;

  @Mock
  private ExecutorService executer;

  @Mock
  private ExistsBuilder existBuilder;

  @Mock
  private Stat stat;

  @BeforeTest
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(pathCache.createPathCache(eq("/missing"), any(ExecutorService.class))).thenReturn(mockCache);
    when(mockCache.getListenable()).thenReturn(mockListener);
  }

  @Test
  public void testListenersInteractions() throws Exception {

    PathChildrenCacheEvent event1 = getMockedEvent("host1", Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", Type.CHILD_ADDED);
    PathChildrenCacheEvent event4 = getMockedEvent("host3", Type.CHILD_REMOVED);
    PathChildrenCacheEvent event5 = getMockedEvent("host2", Type.CHILD_REMOVED);
    PathChildrenCacheEvent event6 = getMockedEvent("host1", Type.CHILD_REMOVED);


    MissingHostMonitor.ChangeListener listener1 = mock(MissingHostMonitor.ChangeListener.class);
    MissingHostMonitor.ChangeListener listener2 = mock(MissingHostMonitor.ChangeListener.class);

    when(zkClient.checkExists()).thenReturn(existBuilder);
    when(existBuilder.forPath("/missing")).thenReturn(stat);
    ZookeeperMissingHostMonitor missingHostMonitor = new ZookeeperMissingHostMonitor(zkClient, pathCache, executer);

    // Interleave add events and add change listener
    missingHostMonitor.addChangeListener(listener1);
    missingHostMonitor.childEvent(zkClient, event1);
    missingHostMonitor.childEvent(zkClient, event2);
    verify(listener1).onHostAdded("host1");
    verify(listener1).onHostAdded("host2");
    missingHostMonitor.childEvent(zkClient, event3);
    verify(listener1).onHostAdded("host3");

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3);
    when(mockCache.getCurrentData()).thenReturn(currEvents);
    // Add change listener after events are processed
    missingHostMonitor.addChangeListener(listener2);
    verify(listener2).onHostAdded("host1");
    verify(listener2).onHostAdded("host2");
    verify(listener2).onHostAdded("host3");

    // Remove a host3
    missingHostMonitor.childEvent(zkClient, event4);
    verify(listener1).onHostRemoved("host3");
    verify(listener2).onHostRemoved("host3");

    missingHostMonitor.removeChangeListener(listener2);

    // Remove all the missing hosts after removing listener2
    missingHostMonitor.childEvent(zkClient, event5);
    missingHostMonitor.childEvent(zkClient, event6);

    verify(listener1).onHostRemoved("host1");
    verify(listener1).onHostRemoved("host2");

    missingHostMonitor.removeChangeListener(listener1);

    verifyNoMoreInteractions(listener1, listener2);

  }

  private List<ChildData> getChildDataFromEvents(PathChildrenCacheEvent... events) {
    List<ChildData> currEvents = new ArrayList<>();
    for (PathChildrenCacheEvent event : events) {
      currEvents.add(new ChildData(event.getData().getPath(), null, event.getData().getData()));
    }
    return currEvents;
  }

  private PathChildrenCacheEvent getMockedEvent(String host, Type eventType) throws Exception {
    PathChildrenCacheEvent event = mock(PathChildrenCacheEvent.class);
    ChildData eventData = mock(ChildData.class);
    when(event.getType()).thenReturn(eventType);
    when(event.getData()).thenReturn(eventData);
    when(eventData.getData()).thenReturn(null);
    when(eventData.getPath()).thenReturn("/missing/" + host);
    return event;
  }
}
