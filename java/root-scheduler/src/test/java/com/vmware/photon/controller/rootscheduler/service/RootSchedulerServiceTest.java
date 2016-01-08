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

package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test cases for Root Scheduler Service.
 */

public class RootSchedulerServiceTest extends PowerMockTestCase {

  @Mock
  private SchedulerManager schedulerManager;

  @Mock
  private CuratorFramework zkClient;

  @Mock
  private ServerSet rootSchedulerServerSet;

  @Mock
  private BuildInfo buildInfo;

  private RootSchedulerService root;

  @BeforeMethod
  public void setUp() {
    root = new RootSchedulerService(schedulerManager, zkClient, rootSchedulerServerSet, buildInfo);
  }

  @Test
  public void testGetStatusNotReady() throws Exception{
    CuratorZookeeperClient zkLocalClient = mock(CuratorZookeeperClient.class);
    when(zkLocalClient.isConnected()).thenReturn(false);
    Mockito.doReturn(zkLocalClient).when(zkClient).getZookeeperClient();

    GetStatusRequest request = new GetStatusRequest();
    Status response = root.get_status(request);
    assertThat(response.getType(), is(StatusType.ERROR));
    assertThat(response.getStats(), is(nullValue()));

    when(zkLocalClient.isConnected()).thenReturn(true);

    //No matter it's the leader or not, while there's no root-scheduler leader
    //from zookeeper point of view, it's in INITIALIZING status
    root.onJoin();
    Set<InetSocketAddress> serverList = new HashSet<InetSocketAddress>();
    when(rootSchedulerServerSet.getServers()).thenReturn(serverList);

    response = root.get_status(request);
    assertThat(response.getType(), is(StatusType.INITIALIZING));
    assertThat(response.getStats(), is(nullValue()));

    // Not the leader
    root.onLeave();
    response = root.get_status(request);
    assertThat(response.getType(), is(StatusType.INITIALIZING));
    assertThat(response.getStats(), is(nullValue()));
  }

  @Test
  public void testGetStatusReady() throws Exception{
    CuratorZookeeperClient zkLocalClient = mock(CuratorZookeeperClient.class);
    when(zkLocalClient.isConnected()).thenReturn(true);
    Mockito.doReturn(zkLocalClient).when(zkClient).getZookeeperClient();

    Set<InetSocketAddress> serverList = new HashSet<InetSocketAddress>();
    InetSocketAddress socketAddress = new InetSocketAddress(12345);
    serverList.add(socketAddress);
    when(rootSchedulerServerSet.getServers()).thenReturn(serverList);

    Collection<ManagedScheduler> activateSchedulers = new HashSet();
    ManagedScheduler scheduler = mock(ManagedScheduler.class);
    activateSchedulers.add(scheduler);
    when(schedulerManager.getActiveSchedulers()).thenReturn(activateSchedulers);

    // Act as a leader
    root.onJoin();

    GetStatusRequest request = new GetStatusRequest();
    Status response = root.get_status(request);
    assertThat(response.getType(), is(StatusType.READY));
    assertThat(response.getStatsSize(), is(1));
    assertThat((String) response.getStats().values().toArray()[0], is("1"));

    // Not the leader
    root.onLeave();

    // Even though not the root-scheduler leader, return READY,
    // since there is another leader in the cluster.
    response = root.get_status(request);
    assertThat(response.getType(), is(StatusType.READY));
    assertThat(response.getStats(), is(nullValue()));
  }

  @Test
  public void testGetSchedulers() {

    Map<String, ManagedScheduler> managedSchedulers = new HashMap();

    String id1 = "leaf1";
    String id2 = "leaf2";

    InetSocketAddress addr1 = new InetSocketAddress("addr1", 12345);
    InetSocketAddress addr2 = new InetSocketAddress("addr2", 12346);


    ManagedScheduler leaf1 = mock(ManagedScheduler.class);
    ManagedScheduler leaf2 = mock(ManagedScheduler.class);

    when(leaf1.getId()).thenReturn(id1);
    when(leaf2.getId()).thenReturn(id2);

    when(leaf1.getAddress()).thenReturn(addr1);
    when(leaf2.getAddress()).thenReturn(addr2);

    when(leaf1.getOwnerHostId()).thenReturn("h1");
    when(leaf2.getOwnerHostId()).thenReturn("h2");

    managedSchedulers.put(leaf1.getId(), leaf1);
    managedSchedulers.put(leaf2.getId(), leaf2);

    when(schedulerManager.getManagedSchedulersMap()).thenReturn(managedSchedulers);

    root.onJoin();

    GetSchedulersResponse resp = root.get_schedulers();

    assertThat(resp.getSchedulers().size(), is(1));

    SchedulerRole schRole = resp.getSchedulers().get(0).getRole();
    assertThat(schRole.getId(), is(RootSchedulerService.ROOT_SCHEDULER_ID));

    assertThat(schRole.getScheduler_children().size(), is(2));

    Map<String, InetSocketAddress> leafSchedulers = new HashMap();
    Map<String, String> leafSchOwnerHosts = new HashMap();

    for (ChildInfo leaf : schRole.getScheduler_children()) {
      leafSchedulers.put(leaf.getId(), new InetSocketAddress(leaf.getAddress(), leaf.getPort()));
      leafSchOwnerHosts.put(leaf.getId(), leaf.getOwner_host());
    }

    assertThat(leafSchedulers.get(id1), is(addr1));
    assertThat(leafSchedulers.get(id2), is(addr2));
    assertThat(leafSchOwnerHosts.get(id1), is("h1"));
    assertThat(leafSchOwnerHosts.get(id2), is("h2"));
  }

}
