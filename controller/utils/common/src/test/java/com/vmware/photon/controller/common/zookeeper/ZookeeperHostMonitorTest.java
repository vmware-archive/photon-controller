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
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.ExistsBuilder;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.thrift.TSerializer;
import org.apache.zookeeper.data.Stat;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Tests {@link ZookeeperHostMonitorTest}.
 */
public class ZookeeperHostMonitorTest {

  @Mock
  private PathChildrenCacheFactory pathCache;

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

  @BeforeMethod
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockCache = mock(PathChildrenCache.class);
    when(pathCache.createPathCache(eq("/hosts"), any(ExecutorService.class))).thenReturn(mockCache);
    when(mockCache.getListenable()).thenReturn(mockListener);
  }

  @Test
  public void testListenersInteractions() throws Exception {

    HostConfig host1 = getHostConfig("host1");
    HostConfig host2 = getHostConfig("host2");
    HostConfig host3 = getHostConfig("host3");


    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", host2, Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", host3, Type.CHILD_ADDED);
    PathChildrenCacheEvent event4 = getMockedEvent("host3", host3, Type.CHILD_REMOVED);
    PathChildrenCacheEvent event5 = getMockedEvent("host2", host2, Type.CHILD_UPDATED);


    HostChangeListener listener1 = mock(HostChangeListener.class);
    HostChangeListener listener2 = mock(HostChangeListener.class);

    when(zkClient.checkExists()).thenReturn(existBuilder);
    when(existBuilder.forPath("/hosts")).thenReturn(stat);
    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    // Interleave add events and add change listener
    hostMonitor.addChangeListener(listener1);
    hostMonitor.childEvent(zkClient, event1);
    hostMonitor.childEvent(zkClient, event2);
    verify(listener1).onHostAdded("host1", host1);
    verify(listener1).onHostAdded("host2", host2);
    hostMonitor.childEvent(zkClient, event3);
    verify(listener1).onHostAdded("host3", host3);

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3);
    when(mockCache.getCurrentData()).thenReturn(currEvents);
    // Add change listener after events are processed
    hostMonitor.addChangeListener(listener2);
    verify(listener2).onHostAdded("host1", host1);
    verify(listener2).onHostAdded("host2", host2);
    verify(listener2).onHostAdded("host3", host3);

    // Remove a host
    hostMonitor.childEvent(zkClient, event4);
    verify(listener1).onHostRemoved("host3", host3);
    verify(listener2).onHostRemoved("host3", host3);

    hostMonitor.removeChangeListener(listener2);

    // Update a host
    hostMonitor.childEvent(zkClient, event5);
    verify(listener1).onHostUpdated("host2", host2);

    hostMonitor.removeChangeListener(listener1);

    // Get a static server set for two hosts

    ChildData childData1 = getChildDataFromEvents(event1).get(0);
    ChildData childData2 = getChildDataFromEvents(event2).get(0);

    when(mockCache.getCurrentData(eq("/hosts/host1"))).thenReturn(childData1);
    when(mockCache.getCurrentData(eq("/hosts/host2"))).thenReturn(childData2);

    ServerSet serverSet1 = hostMonitor.createStaticServerSet("host1");
    ServerSet serverSet2 = hostMonitor.createStaticServerSet("host2");

    ServerSet.ChangeListener listener3 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener4 = mock(ServerSet.ChangeListener.class);

    serverSet1.addChangeListener(listener3);

    serverSet2.addChangeListener(listener4);

    verify(listener3).onServerAdded(getHostAddr(host1));

    verify(listener4).onServerAdded(getHostAddr(host2));

    // Test a cache miss
    String notCachedHost = "notCachedHost";
    HostConfig host4 = getHostConfig("notCachedHost");
    PathChildrenCacheEvent event6 = getMockedEvent("notCachedHost", host4, Type.CHILD_ADDED);
    ChildData childData3 = getChildDataFromEvents(event6).get(0);
    when(mockCache.getCurrentData(eq("/hosts/notCachedHost"))).thenReturn(null).thenReturn(childData3);
    ServerSet serverSet3 = hostMonitor.createStaticServerSet(notCachedHost);
    verify(mockCache).rebuildNode(eq("/hosts/notCachedHost"));
    ServerSet.ChangeListener listener5 = mock(ServerSet.ChangeListener.class);
    serverSet3.addChangeListener(listener5);
    verify(listener5).onServerAdded(getHostAddr(host4));

    // Try to create a static server set for an unknown host
    String unknownHost = "hostX";
    try {
      hostMonitor.createStaticServerSet(unknownHost);
      fail("Creating static server set for an unknown host should throw and exception");

    } catch (HostNotFoundException expected) {
      String errorMsg = "Host id " + unknownHost;
      assertEquals(expected.getMessage(), errorMsg);
    }

    verifyNoMoreInteractions(listener1, listener2, listener3, listener4);

  }

  @Test
  public void testGetImageDataStoresUnset() throws Exception {
    List<String> dsList1 = new ArrayList<>();
    dsList1.add("ds1");

    // The test case where image_datastore_id isn't set

    HostConfig host1 = getHostConfig("host1", dsList1, null);

    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);

    List<ChildData> currEvents = getChildDataFromEvents(event1);
    when(mockCache.getCurrentData()).thenReturn(currEvents);

    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    Set<Datastore> datastores = hostMonitor.getImageDatastores();

    // Verify that there isn't any image datastores
    assertThat(datastores.size(), is(0));
  }

  @Test
  public void testGetImageDatastores() throws Exception {

    List<String> dsList1 = new ArrayList<>();
    List<String> dsList2 = new ArrayList<>();
    List<String> dsList3 = new ArrayList<>();
    List<String> dsList4 = new ArrayList<>();

    //Mark ds1 and ds5 as an image ds
    String imageDs = "ds1";
    String imageDs2 = "ds5";

    dsList1.add(imageDs);
    dsList1.add("ds2");
    dsList1.add("ds3");

    dsList2.add(imageDs);
    dsList2.add("ds4");

    dsList3.add(imageDs);

    dsList4.add(imageDs2);

    HostConfig host1 = getHostConfig("host1", dsList1, imageDs);
    HostConfig host2 = getHostConfig("host2", dsList2, imageDs);
    HostConfig host3 = getHostConfig("host3", dsList3, imageDs);
    HostConfig host4 = getHostConfig("host4", dsList4, imageDs2);


    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", host2, Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", host3, Type.CHILD_ADDED);
    PathChildrenCacheEvent event4 = getMockedEvent("host4", host4, Type.CHILD_ADDED);

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3, event4);
    when(mockCache.getCurrentData()).thenReturn(currEvents);

    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    Set<Datastore> datastores = hostMonitor.getImageDatastores();

    // Verify that only the image datastores were returned
    assertThat(datastores.contains(new Datastore(imageDs)), is(true));
    assertThat(datastores.contains(new Datastore(imageDs2)), is(true));
    assertThat(datastores.size(), is(2));
  }

  @Test
  public void testGetHostsForDatastore() throws Exception {
    String sharedDs1 = "shared1";

    List<String> dsList1 = new ArrayList<>();
    List<String> dsList2 = new ArrayList<>();
    List<String> dsList3 = new ArrayList<>();

    dsList1.add(sharedDs1);

    dsList2.add(sharedDs1);
    dsList2.add("ds2");

    dsList3.add("ds3");

    HostConfig host1 = getHostConfig("host1", dsList1, "");
    HostConfig host2 = getHostConfig("host2", dsList2, "");
    HostConfig host3 = getHostConfig("host3", dsList3, "");

    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", host2, Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", host3, Type.CHILD_ADDED);

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3);
    when(mockCache.getCurrentData()).thenReturn(currEvents);

    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    Set<HostConfig> configs = hostMonitor.getHostsForDatastore(sharedDs1);

    Map<String, HostConfig> configMap = new HashMap<>();

    for (HostConfig config : configs) {
      configMap.put(config.getAgent_id(), config);
    }

    //Verify that only host1 and host2 are returned for sharedDs1
    assertThat(configMap.get(host1.getAgent_id()), is(host1));
    assertThat(configMap.get(host2.getAgent_id()), is(host2));
    assertThat(configMap.size(), is(2));

    // Verify that only host3 is returned for ds3
    configs = hostMonitor.getHostsForDatastore("ds3");

    assertThat(configs.size(), is(1));
    assertThat(configs.iterator().next(), is(host3));
  }

  @Test
  public void testGetDatastoreForHost() throws Exception {
    String sharedDs1 = "shared1";

    List<String> dsList1 = new ArrayList<>();
    List<String> dsList2 = new ArrayList<>();
    List<String> dsList3 = new ArrayList<>();

    dsList1.add(sharedDs1);

    dsList2.add(sharedDs1);
    dsList2.add("ds2");

    dsList3.add("ds3");

    HostConfig host1 = getHostConfig("host1", dsList1, "");
    HostConfig host2 = getHostConfig("host2", dsList2, "");
    HostConfig host3 = getHostConfig("host3", dsList3, "");

    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", host2, Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", host3, Type.CHILD_ADDED);

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3);
    when(mockCache.getCurrentData()).thenReturn(currEvents);

    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    Set<Datastore> datastores = hostMonitor.getDatastoresForHost("host1");
    assertThat(datastores.size(), is(1));
    assertThat(datastores.contains(new Datastore("shared1")), is(true));

    datastores = hostMonitor.getDatastoresForHost("host2");
    assertThat(datastores.size(), is(2));
    assertThat(datastores.contains(new Datastore("shared1")), is(true));
    assertThat(datastores.contains(new Datastore("ds2")), is(true));

    datastores = hostMonitor.getDatastoresForHost("host3");
    assertThat(datastores.size(), is(1));
    assertThat(datastores.contains(new Datastore("ds3")), is(true));
  }

  @Test
  public void testGetAllDatastores() throws Exception {
    List<String> dsList1 = new ArrayList<>();
    List<String> dsList2 = new ArrayList<>();
    List<String> dsList3 = new ArrayList<>();

    dsList1.add("ds1");

    dsList2.add("ds1");
    dsList2.add("ds2");

    dsList3.add("ds3");

    HostConfig host1 = getHostConfig("host1", dsList1, "");
    HostConfig host2 = getHostConfig("host2", dsList2, "");
    HostConfig host3 = getHostConfig("host3", dsList3, "");

    PathChildrenCacheEvent event1 = getMockedEvent("host1", host1, Type.CHILD_ADDED);
    PathChildrenCacheEvent event2 = getMockedEvent("host2", host2, Type.CHILD_ADDED);
    PathChildrenCacheEvent event3 = getMockedEvent("host3", host3, Type.CHILD_ADDED);

    List<ChildData> currEvents = getChildDataFromEvents(event1, event2, event3);
    when(mockCache.getCurrentData()).thenReturn(currEvents);

    ZookeeperHostMonitor hostMonitor = new ZookeeperHostMonitor(zkClient, pathCache, executer);

    Set<Datastore> datastores = hostMonitor.getAllDatastores();

    // Verify that all the datastores were returned
    assertThat(datastores.size(), is(3));
    assertThat(datastores.contains(new Datastore("ds1")), is(true));
    assertThat(datastores.contains(new Datastore("ds2")), is(true));
    assertThat(datastores.contains(new Datastore("ds3")), is(true));
  }

  private List<ChildData> getChildDataFromEvents(PathChildrenCacheEvent... events) {
    List<ChildData> currEvents = new ArrayList<>();
    for (PathChildrenCacheEvent event : events) {
      currEvents.add(new ChildData(event.getData().getPath(), null, event.getData().getData()));
    }
    return currEvents;
  }

  private PathChildrenCacheEvent getMockedEvent(String host, HostConfig config, Type eventType) throws Exception {
    PathChildrenCacheEvent event = mock(PathChildrenCacheEvent.class);
    ChildData eventData = mock(ChildData.class);
    when(event.getType()).thenReturn(eventType);
    when(event.getData()).thenReturn(eventData);
    when(eventData.getData()).thenReturn(serialize(config));
    when(eventData.getPath()).thenReturn("/hosts/" + host);
    return event;
  }

  private HostConfig getHostConfig(String name) {
    ServerAddress sAddr = new ServerAddress(name, 1234);
    return new HostConfig(name, new ArrayList<Datastore>(), sAddr);
  }

  private HostConfig getHostConfig(String name, List<String> datastoreIDs, String imageDatastore) {
    ServerAddress sAddr = new ServerAddress(name, 1234);
    List<Datastore> datastore = new ArrayList<>();

    for (String dsId : datastoreIDs) {
      datastore.add(new Datastore(dsId));
    }

    Set<String> imageDatastores = new HashSet<>();
    if (imageDatastore != null) {
      imageDatastores.add(imageDatastore);
    }
    HostConfig config = new HostConfig(name, datastore, sAddr);
    config.setImage_datastore_ids(imageDatastores);
    return config;
  }

  private byte[] serialize(HostConfig host) throws Exception {
    TSerializer serializer = new TSerializer();
    return serializer.serialize(host);
  }

  private InetSocketAddress getHostAddr(HostConfig config) {
    return new InetSocketAddress(config.getAddress().getHost(),
        config.getAddress().getPort());
  }
}
