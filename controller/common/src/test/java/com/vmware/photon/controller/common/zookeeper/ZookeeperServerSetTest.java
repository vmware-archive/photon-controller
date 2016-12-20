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
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Tests {@link ZookeeperServerSet}.
 */
public class ZookeeperServerSetTest {

  private TSerializer serializer = new TSerializer();

  @Mock
  private PathChildrenCacheFactory mockChildrenCacheFactory;

  @Mock
  private CuratorFramework zkClient;

  @Mock
  private PathChildrenCache mockChildrenCache;

  @Mock
  private ListenerContainer<PathChildrenCacheListener> childrenCacheListener;

  private ZookeeperServerReader serviceReader = new ZookeeperServiceReader();
  private ZookeeperHostReader hostReader = new ZookeeperHostReader();

  @BeforeTest
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockChildrenCacheFactory.create(eq("foo"), any(ExecutorService.class))).thenReturn(mockChildrenCache);
    when(mockChildrenCache.getListenable()).thenReturn(childrenCacheListener);
  }

  @Test
  public void testAddChangeListener() throws Exception {
    ServerSet.ChangeListener listener1 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener2 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener3 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener4 = mock(ServerSet.ChangeListener.class);

    InetSocketAddress server1 = new InetSocketAddress("192.168.1.1", 256);
    InetSocketAddress server2 = new InetSocketAddress("192.168.1.2", 512);
    HostConfig host1 = getHostConfig("node-1", server1);
    HostConfig host2 = getHostConfig("node-2", server2);

    ZookeeperServerSet set = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo", true);
    ZookeeperServerSet hostSet = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, hostReader, "foo",
        true);
    CuratorFramework mockZk = mock(CuratorFramework.class);

    set.addChangeListener(listener1);
    hostSet.addChangeListener(listener3);

    // Interleaving childEvent's with addChangeListener to make sure listeners added
    // after events still get information about all servers
    set.childEvent(mockZk, childAddedEvent("/services/foo/node-1", data(server1)));
    hostSet.childEvent(mockZk, childAddedEvent("/hosts/node-1", data(host1)));

    // Adding server twice should be OK and only generate one callback per listener
    // This part of the test doesn't make sense, why would there be two different znodes with the same ip address?
    // Maybe the contract should be that that chairman only allows one IP to register for a given host.
    set.childEvent(mockZk, childAddedEvent("/services/foo/node-2", data(server2)));
    set.childEvent(mockZk, childAddedEvent("/services/foo/node-3", data(server2)));
    hostSet.childEvent(mockZk, childAddedEvent("/hosts/node-2", data(host2)));
    hostSet.childEvent(mockZk, childAddedEvent("/hosts/node-3", data(host2)));

    set.addChangeListener(listener2);
    hostSet.addChangeListener(listener4);

    // Removing server twice should be OK
    set.childEvent(mockZk, childRemovedEvent("/services/foo/node-1", data(server1)));
    set.childEvent(mockZk, childRemovedEvent("/services/foo/node-1", data(server1)));
    hostSet.childEvent(mockZk, childRemovedEvent("/hosts/node-1", data(host1)));
    hostSet.childEvent(mockZk, childRemovedEvent("/hosts/node-1", data(host1)));

    verify(childrenCacheListener).addListener(set);

    verify(listener1).onServerAdded(server1);
    verify(listener1).onServerAdded(server2);
    verify(listener2).onServerAdded(server1);
    verify(listener2).onServerAdded(server2);

    verify(listener3).onServerAdded(server1);
    verify(listener3).onServerAdded(server2);
    verify(listener4).onServerAdded(server1);
    verify(listener4).onServerAdded(server2);

    verify(listener1).onServerRemoved(server1);
    verify(listener2).onServerRemoved(server1);
    verify(listener3).onServerRemoved(server1);
    verify(listener4).onServerRemoved(server1);

    verifyNoMoreInteractions(listener1, listener2, mockZk, listener3, listener4);
    hostSet.close();
  }

  @Test
  public void testRemoveChangeListener() throws Exception {
    ServerSet.ChangeListener listener1 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener2 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener3 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener4 = mock(ServerSet.ChangeListener.class);

    ZookeeperServerSet set = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo", true);
    ZookeeperServerSet hostSet = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, hostReader, "foo",
        true);
    CuratorFramework mockZk = mock(CuratorFramework.class);

    set.addChangeListener(listener1);
    set.addChangeListener(listener2);
    hostSet.addChangeListener(listener3);
    hostSet.addChangeListener(listener4);

    InetSocketAddress server = new InetSocketAddress("192.168.1.1", 256);

    set.childEvent(mockZk, childAddedEvent("/services/foo/node-1", data(server)));
    set.removeChangeListener(listener1);
    set.childEvent(mockZk, childRemovedEvent("/services/foo/node-1", data(server)));

    hostSet.childEvent(mockZk, childAddedEvent("/hosts/node-1", data(getHostConfig("node-1", server))));
    hostSet.removeChangeListener(listener3);
    hostSet.childEvent(mockZk, childRemovedEvent("/hosts/node-1", data(getHostConfig("node-1", server))));

    verify(listener1).onServerAdded(server);
    verify(listener3).onServerAdded(server);

    verify(listener2).onServerAdded(server);
    verify(listener4).onServerAdded(server);
    verify(listener2).onServerRemoved(server);
    verify(listener4).onServerRemoved(server);

    verifyNoMoreInteractions(listener1, listener2, listener3, listener4);
    set.close();
    hostSet.close();
  }

  @Test(expectedExceptions = RuntimeException.class)
  public void testDeserializeError() throws Exception {
    ZookeeperServerSet set = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo", true);
    set.childEvent(mock(CuratorFramework.class), badChildEvent());
    set.close();
  }

  @Test
  public void testNoUpdateSubscription() throws Exception {
    ZookeeperServerSet set = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo", false);
    ZookeeperServerSet hostSet = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo",
        false);

    List<String> nodes = new ArrayList<>();
    nodes.add("node-1");
    nodes.add("node-2");

    GetChildrenBuilder getChildrenBuilder = mock(GetChildrenBuilder.class);
    GetDataBuilder getDataBuilder = mock(GetDataBuilder.class);

    when(getChildrenBuilder.forPath("/services/foo")).thenReturn(nodes);
    when(getChildrenBuilder.forPath("/hosts/")).thenReturn(nodes);

    when(zkClient.getChildren()).thenReturn(getChildrenBuilder);
    when(zkClient.getData()).thenReturn(getDataBuilder);

    ServerAddress server1 = new ServerAddress("192.168.1.1", 256);
    ServerAddress server2 = new ServerAddress("192.168.1.2", 256);
    HostConfig host1 = getHostConfig("server1", server1);
    HostConfig host2 = getHostConfig("server2", server2);

    when(getDataBuilder.forPath("/services/foo/node-1")).thenReturn(serializer.serialize(server1));
    when(getDataBuilder.forPath("/services/foo/node-2")).thenReturn(serializer.serialize(server2));
    when(getDataBuilder.forPath("/hosts/node-1")).thenReturn(serializer.serialize(host1));
    when(getDataBuilder.forPath("/hosts/node-2")).thenReturn(serializer.serialize(host2));

    ServerSet.ChangeListener listener1 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener2 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener3 = mock(ServerSet.ChangeListener.class);
    ServerSet.ChangeListener listener4 = mock(ServerSet.ChangeListener.class);

    set.addChangeListener(listener1);
    set.addChangeListener(listener2);
    hostSet.addChangeListener(listener3);
    hostSet.addChangeListener(listener4);

    verify(listener1).onServerAdded(new InetSocketAddress("192.168.1.1", 256));
    verify(listener1).onServerAdded(new InetSocketAddress("192.168.1.2", 256));

    verify(listener2).onServerAdded(new InetSocketAddress("192.168.1.1", 256));
    verify(listener2).onServerAdded(new InetSocketAddress("192.168.1.2", 256));

    verify(listener3).onServerAdded(new InetSocketAddress("192.168.1.1", 256));
    verify(listener3).onServerAdded(new InetSocketAddress("192.168.1.2", 256));

    verify(listener4).onServerAdded(new InetSocketAddress("192.168.1.1", 256));
    verify(listener4).onServerAdded(new InetSocketAddress("192.168.1.2", 256));

    verifyNoMoreInteractions(listener1, listener2, listener3, listener4);
    set.close();
    hostSet.close();
  }

  @Test
  public void testGetServers() throws Exception {
    InetSocketAddress server1 = new InetSocketAddress("192.168.1.1", 256);
    InetSocketAddress server2 = new InetSocketAddress("192.168.1.2", 512);

    ZookeeperServerSet set = new ZookeeperServerSet(mockChildrenCacheFactory, zkClient, serviceReader, "foo", true);
    CuratorFramework mockZk = mock(CuratorFramework.class);

    set.childEvent(mockZk, childAddedEvent("/services/foo/node-1", data(server1)));
    assertTrue(set.getServers().contains(server1));
    assertFalse(set.getServers().contains(server2));

    set.childEvent(mockZk, childAddedEvent("/services/foo/node-2", data(server2)));
    assertTrue(set.getServers().contains(server1));
    assertTrue(set.getServers().contains(server2));

    set.childEvent(mockZk, childRemovedEvent("/services/foo/node-1", data(server1)));
    assertFalse(set.getServers().contains(server1));
    assertTrue(set.getServers().contains(server2));
    set.close();
  }

  private HostConfig getHostConfig(String id, ServerAddress server) {
    return new HostConfig(id, new ArrayList<Datastore>(), server);
  }

  private HostConfig getHostConfig(String id, InetSocketAddress server) {
    return getHostConfig(id, new ServerAddress(server.getHostString(), server.getPort()));
  }

  private byte[] data(InetSocketAddress server) throws TException {
    ServerAddress serverAddress = new ServerAddress(server.getHostString(), server.getPort());
    return serializer.serialize(serverAddress);
  }

  private byte[] data(HostConfig host) throws TException {
    return serializer.serialize(host);
  }

  private PathChildrenCacheEvent childAddedEvent(String path, byte[] data) throws TException {

    ChildData mockChildData = mock(ChildData.class);
    when(mockChildData.getData()).thenReturn(data);
    when(mockChildData.getPath()).thenReturn(path);

    PathChildrenCacheEvent mockEvent = mock(PathChildrenCacheEvent.class);
    when(mockEvent.getType()).thenReturn(PathChildrenCacheEvent.Type.CHILD_ADDED);
    when(mockEvent.getData()).thenReturn(mockChildData);

    return mockEvent;
  }

  private PathChildrenCacheEvent childRemovedEvent(String path, byte[] data) throws TException {
    ChildData mockChildData = mock(ChildData.class);
    when(mockChildData.getData()).thenReturn(data);
    when(mockChildData.getPath()).thenReturn(path);

    PathChildrenCacheEvent mockEvent = mock(PathChildrenCacheEvent.class);
    when(mockEvent.getType()).thenReturn(PathChildrenCacheEvent.Type.CHILD_REMOVED);
    when(mockEvent.getData()).thenReturn(mockChildData);

    return mockEvent;
  }

  private PathChildrenCacheEvent badChildEvent() {
    ChildData mockChildData = mock(ChildData.class);
    when(mockChildData.getData()).thenReturn("deadbeef".getBytes());
    when(mockChildData.getPath()).thenReturn("/foo/bar");

    PathChildrenCacheEvent mockEvent = mock(PathChildrenCacheEvent.class);
    when(mockEvent.getType()).thenReturn(PathChildrenCacheEvent.Type.CHILD_ADDED);
    when(mockEvent.getData()).thenReturn(mockChildData);

    return mockEvent;
  }

}
